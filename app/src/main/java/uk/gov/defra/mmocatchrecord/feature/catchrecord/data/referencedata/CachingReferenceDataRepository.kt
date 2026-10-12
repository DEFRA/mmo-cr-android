package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import uk.gov.defra.mmocatchrecord.core.connectivity.NetworkConnectivityChecker
import uk.gov.defra.mmocatchrecord.core.language.AppLanguageRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.ReferenceDataDao
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.ReferenceDataMetadataEntity
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.ReferenceDataFetchResult
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.ReferenceDataRemoteDataSource
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.toDomain
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.sync.DATASET_PORTS
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.sync.DATASET_SPECIES
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.sync.DATASET_VESSELS
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.sync.VesselLengthBands
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.sync.WorkManagerReferenceDataSyncScheduler
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.sync.gearsDatasetKey
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.sync.measurementEntitiesFor
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.sync.toEntity
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.ReferenceDataRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.StatisticalSubRectangle
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Vessel
import javax.inject.Inject

/** A cache older than this (and online) triggers a background refresh rather than blocking the read (ADR 0016). */
internal const val CACHE_FRESHNESS_TTL_MILLIS = 60L * 60L * 1000L
private const val INLINE_FETCH_TIMEOUT_MILLIS = 5_000L
private const val UNEXPECTED_NOT_MODIFIED_MESSAGE = "Unexpected 304 for an uncached dataset fetched with no ETag"

/** Cache-first [ReferenceDataRepository] (ADR 0016): a hit returns immediately (refreshing in the
 *  background if stale); an empty cache fetches once inline when online, else falls back to [stub]. */
@Suppress("TooManyFunctions", "LongParameterList") // One method per dataset + the shared `served` helper.
internal class CachingReferenceDataRepository
    @Inject
    constructor(
        private val dao: ReferenceDataDao,
        private val remoteDataSource: ReferenceDataRemoteDataSource,
        private val syncScheduler: WorkManagerReferenceDataSyncScheduler,
        private val connectivityChecker: NetworkConnectivityChecker,
        private val appLanguageRepository: AppLanguageRepository,
        private val stub: ReferenceDataRepository,
        private val clock: () -> Long = System::currentTimeMillis,
        private val inlineFetchTimeoutMillis: Long = INLINE_FETCH_TIMEOUT_MILLIS,
    ) : ReferenceDataRepository {
        override suspend fun getVessels(): Result<List<Vessel>> =
            served(
                datasetKey = DATASET_VESSELS,
                readCache = { dao.getVessels().map { it.toDomain() } },
                fetchAndCache = {
                    val fetch = requireModified(remoteDataSource.fetchVessels())
                    dao.replaceVessels(fetch.items.map { it.toEntity() })
                    dao.upsertMetadata(metadataFrom(DATASET_VESSELS, fetch))
                    fetch.items.map { it.toDomain() }
                },
                stubFallback = { stub.getVessels() },
            )

        override suspend fun getPorts(): Result<List<Port>> =
            served(
                datasetKey = DATASET_PORTS,
                readCache = { dao.getPorts().map { it.toDomain() } },
                fetchAndCache = {
                    val fetch = requireModified(remoteDataSource.fetchPorts())
                    dao.replacePorts(fetch.items.map { it.toEntity() })
                    dao.upsertMetadata(metadataFrom(DATASET_PORTS, fetch))
                    fetch.items.map { it.toDomain() }
                },
                stubFallback = { stub.getPorts() },
            )

        override suspend fun getSpecies(): Result<List<Species>> =
            served(
                datasetKey = DATASET_SPECIES,
                readCache = { dao.getSpecies().map { it.toDomain() } },
                fetchAndCache = {
                    val language = appLanguageRepository.language.first()
                    val fetch = requireModified(remoteDataSource.fetchSpecies())
                    dao.replaceSpecies(fetch.items.map { it.toEntity() })
                    dao.upsertMetadata(metadataFrom(DATASET_SPECIES, fetch, languageTag = language))
                    fetch.items.map { it.toDomain() }
                },
                stubFallback = { stub.getSpecies() },
            )

        override suspend fun getGearTypes(vesselLengthMetres: Double?): Result<List<GearType>> {
            val band = VesselLengthBands.bandFor(vesselLengthMetres)
            val representativeLength = VesselLengthBands.cacheableVariants.first { it.first == band }.second
            val datasetKey = gearsDatasetKey(band)
            return served(
                datasetKey = datasetKey,
                readCache = {
                    val gears = dao.getGearsForBand(band)
                    val measurementsByGearId = dao.getGearMeasurementsForBand(band).groupBy { it.gearId }
                    gears.toDomainGearTypes(measurementsByGearId)
                },
                fetchAndCache = {
                    val fetch = requireModified(remoteDataSource.fetchGears(vesselLengthMetres = representativeLength))
                    val payload = fetch.items
                    val gearEntities = payload.items.mapIndexed { index, gear -> gear.toEntity(band, index) }
                    val measurementEntities =
                        payload.items.flatMap { it.measurementEntitiesFor(band, payload.measurementsById) }
                    dao.replaceGearsForBand(band, gearEntities, measurementEntities)
                    dao.upsertMetadata(metadataFrom(datasetKey, fetch))
                    payload.items.map { it.toDomain(payload.measurementsById) }
                },
                stubFallback = { stub.getGearTypes(vesselLengthMetres) },
            )
        }

        /** [stub] is keyed by stub vessel ids only, so a live vessel always (correctly) yields no favourites
         *  yet — no local source exists (ADR 0016 follow-up: derive from draft/submission history). */
        override suspend fun getPreviouslyUsedPorts(vesselId: String): Result<List<Port>> =
            stub.getPreviouslyUsedPorts(vesselId)

        override suspend fun getStatisticalSubRectangles(): Result<List<StatisticalSubRectangle>> =
            stub.getStatisticalSubRectangles()

        /** Resolves [portId] against cached/live ports (stub ports only if the cache is empty) so a live
         *  port GUID still matches its [Port.statisticalAreaId]; an unresolved port yields an empty list. */
        override suspend fun getStatisticalSubRectanglesForPort(portId: String): Result<List<StatisticalSubRectangle>> {
            val cachedPorts = dao.getPorts().map { it.toDomain() }
            val ports = cachedPorts.ifEmpty { stub.getPorts().getOrDefault(emptyList()) }
            val statisticalAreaId = ports.firstOrNull { it.id == portId }?.statisticalAreaId
            val rectangles = stub.getStatisticalSubRectangles().getOrDefault(emptyList())
            return Result.success(rectangles.filter { it.statisticalAreaId == statisticalAreaId })
        }

        /** Cache hit → return (maybe background-refresh); empty cache → bounded inline fetch, else [stub]. */
        private suspend fun <T> served(
            datasetKey: String,
            readCache: suspend () -> List<T>,
            fetchAndCache: suspend () -> List<T>,
            stubFallback: suspend () -> Result<List<T>>,
        ): Result<List<T>> {
            val cached = readCache()
            if (cached.isNotEmpty()) {
                maybeTriggerBackgroundRefresh(datasetKey)
                return Result.success(cached)
            }
            if (!connectivityChecker.isConnected()) return stubFallback()
            val fetched =
                withTimeoutOrNull(inlineFetchTimeoutMillis) { runCatching { fetchAndCache() }.getOrNull() }
            return fetched?.let { Result.success(it) } ?: stubFallback()
        }

        private suspend fun maybeTriggerBackgroundRefresh(datasetKey: String) {
            val metadata = dao.getMetadata(datasetKey)
            val isStale = metadata == null || clock() - metadata.fetchedAtEpochMillis > CACHE_FRESHNESS_TTL_MILLIS
            if (isStale) syncScheduler.scheduleStartupRefresh()
        }

        /** An uncached dataset is always fetched with no `If-None-Match`, so the server can never say 304. */
        private fun <T> requireModified(fetch: ReferenceDataFetchResult<T>): ReferenceDataFetchResult.Modified<T> {
            check(fetch is ReferenceDataFetchResult.Modified) { UNEXPECTED_NOT_MODIFIED_MESSAGE }
            return fetch
        }

        private fun metadataFrom(
            datasetKey: String,
            fetch: ReferenceDataFetchResult.Modified<*>,
            languageTag: String? = null,
        ): ReferenceDataMetadataEntity =
            ReferenceDataMetadataEntity(
                datasetKey = datasetKey,
                collectionId = fetch.collectionId,
                schemaVersion = fetch.schemaVersion,
                manifestVersion = null,
                manifestEtag = null,
                responseETag = fetch.responseETag,
                fetchedAtEpochMillis = clock(),
                languageTag = languageTag,
            )
    }
