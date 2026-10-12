package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.sync

import kotlinx.coroutines.flow.first
import uk.gov.defra.mmocatchrecord.core.error.CatchRecordErrorClassification
import uk.gov.defra.mmocatchrecord.core.error.SafeErrorMapper
import uk.gov.defra.mmocatchrecord.core.language.AppLanguageRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.ReferenceDataDao
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.ReferenceDataMetadataEntity
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.ManifestDatasetDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.ManifestDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.ManifestFetchResult
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.ReferenceDataFetchResult
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.ReferenceDataRemoteDataSource
import javax.inject.Inject

internal const val DATASET_VESSELS = "vessels"
internal const val DATASET_PORTS = "ports"
internal const val DATASET_SPECIES = "species"
internal const val DATASET_GEARS = "gears"
private const val GEARS_DATASET_KEY_PREFIX = "gears:"

internal fun gearsDatasetKey(band: String): String = "$GEARS_DATASET_KEY_PREFIX$band"

/** Per-dataset outcome of one [ReferenceDataRefresher.refresh] run, keyed by dataset (`gears:<band>` for gears). */
internal data class ReferenceDataRefreshResult(
    val datasetOutcomes: Map<String, Result<Unit>>,
) {
    val hasAnyFailure: Boolean get() = datasetOutcomes.values.any { it.isFailure }

    val hasAnyTransientFailure: Boolean
        get() =
            datasetOutcomes.values.any { result ->
                result.exceptionOrNull()?.let {
                    SafeErrorMapper.classify(
                        it,
                    ) == CatchRecordErrorClassification.Transient
                } ==
                    true
            }
}

/**
 * Manifest/ETag-driven refresh of the cached reference-data tables (ADR 0016): compares each dataset's
 * manifest `version`/`etag` against the stored metadata, fetches only what changed (or, for species, when
 * the cached [AppLanguageRepository] language differs), and persists each dataset's own response ETag for
 * the next run's `If-None-Match`. Every dataset is refreshed independently so one failure never discards
 * another dataset's successful update.
 */
internal class ReferenceDataRefresher
    @Inject
    constructor(
        private val remoteDataSource: ReferenceDataRemoteDataSource,
        private val dao: ReferenceDataDao,
        private val appLanguageRepository: AppLanguageRepository,
        private val clock: () -> Long = System::currentTimeMillis,
    ) {
        suspend fun refresh(): ReferenceDataRefreshResult {
            val manifest =
                (remoteDataSource.fetchManifest() as? ManifestFetchResult.Modified)?.manifest
                    ?: return ReferenceDataRefreshResult(emptyMap())
            val currentLanguage = appLanguageRepository.language.first()

            val outcomes = linkedMapOf<String, Result<Unit>>()
            outcomes[DATASET_VESSELS] = runCatching { refreshVessels(manifest) }
            outcomes[DATASET_PORTS] = runCatching { refreshPorts(manifest) }
            outcomes[DATASET_SPECIES] = runCatching { refreshSpecies(manifest, currentLanguage) }
            VesselLengthBands.cacheableVariants.forEach { (band, representativeLength) ->
                outcomes[gearsDatasetKey(band)] = runCatching { refreshGears(manifest, band, representativeLength) }
            }
            return ReferenceDataRefreshResult(outcomes)
        }

        private suspend fun refreshVessels(manifest: ManifestDto) {
            val manifestEntry = manifest.datasets.firstOrNull { it.dataset == DATASET_VESSELS } ?: return
            val stored = dao.getMetadata(DATASET_VESSELS)
            if (isUnchanged(stored, manifestEntry)) return
            when (val fetch = remoteDataSource.fetchVessels(ifNoneMatch = stored?.responseETag)) {
                is ReferenceDataFetchResult.NotModified -> stored?.let { touch(it, manifestEntry) }
                is ReferenceDataFetchResult.Modified -> {
                    dao.replaceVessels(fetch.items.map { it.toEntity() })
                    dao.upsertMetadata(metadataFor(DATASET_VESSELS, manifestEntry, fetch))
                }
            }
        }

        private suspend fun refreshPorts(manifest: ManifestDto) {
            val manifestEntry = manifest.datasets.firstOrNull { it.dataset == DATASET_PORTS } ?: return
            val stored = dao.getMetadata(DATASET_PORTS)
            if (isUnchanged(stored, manifestEntry)) return
            when (val fetch = remoteDataSource.fetchPorts(ifNoneMatch = stored?.responseETag)) {
                is ReferenceDataFetchResult.NotModified -> stored?.let { touch(it, manifestEntry) }
                is ReferenceDataFetchResult.Modified -> {
                    dao.replacePorts(fetch.items.map { it.toEntity() })
                    dao.upsertMetadata(metadataFor(DATASET_PORTS, manifestEntry, fetch))
                }
            }
        }

        private suspend fun refreshSpecies(
            manifest: ManifestDto,
            languageTag: String,
        ) {
            val manifestEntry = manifest.datasets.firstOrNull { it.dataset == DATASET_SPECIES } ?: return
            val stored = dao.getMetadata(DATASET_SPECIES)
            val languageChanged = stored?.languageTag != languageTag
            if (!languageChanged && isUnchanged(stored, manifestEntry)) return
            val ifNoneMatch = if (languageChanged) null else stored.responseETag
            when (val fetch = remoteDataSource.fetchSpecies(ifNoneMatch = ifNoneMatch)) {
                is ReferenceDataFetchResult.NotModified -> stored?.let { touch(it, manifestEntry, languageTag) }
                is ReferenceDataFetchResult.Modified -> {
                    dao.replaceSpecies(fetch.items.map { it.toEntity() })
                    dao.upsertMetadata(metadataFor(DATASET_SPECIES, manifestEntry, fetch, languageTag))
                }
            }
        }

        private suspend fun refreshGears(
            manifest: ManifestDto,
            band: String,
            representativeLength: Double?,
        ) {
            val datasetKey = gearsDatasetKey(band)
            val manifestEntry = manifest.datasets.firstOrNull { it.dataset == DATASET_GEARS } ?: return
            val stored = dao.getMetadata(datasetKey)
            if (isUnchanged(stored, manifestEntry)) return
            val fetch =
                remoteDataSource.fetchGears(
                    vesselLengthMetres = representativeLength,
                    ifNoneMatch = stored?.responseETag,
                )
            when (fetch) {
                is ReferenceDataFetchResult.NotModified -> stored?.let { touch(it, manifestEntry) }
                is ReferenceDataFetchResult.Modified -> {
                    val payload = fetch.items
                    val gears = payload.items.mapIndexed { index, gear -> gear.toEntity(band, index) }
                    val measurements =
                        payload.items.flatMap {
                            it.measurementEntitiesFor(
                                band,
                                payload.measurementsById,
                            )
                        }
                    dao.replaceGearsForBand(band, gears, measurements)
                    dao.upsertMetadata(metadataFor(datasetKey, manifestEntry, fetch))
                }
            }
        }

        private fun isUnchanged(
            stored: ReferenceDataMetadataEntity?,
            manifestEntry: ManifestDatasetDto,
        ): Boolean =
            stored != null &&
                stored.manifestVersion == manifestEntry.version &&
                stored.manifestEtag == manifestEntry.etag

        private suspend fun touch(
            stored: ReferenceDataMetadataEntity,
            manifestEntry: ManifestDatasetDto,
            languageTag: String? = stored.languageTag,
        ) {
            dao.upsertMetadata(
                stored.copy(
                    manifestVersion = manifestEntry.version,
                    manifestEtag = manifestEntry.etag,
                    fetchedAtEpochMillis = clock(),
                    languageTag = languageTag,
                ),
            )
        }

        private fun metadataFor(
            datasetKey: String,
            manifestEntry: ManifestDatasetDto,
            fetch: ReferenceDataFetchResult.Modified<*>,
            languageTag: String? = null,
        ): ReferenceDataMetadataEntity =
            ReferenceDataMetadataEntity(
                datasetKey = datasetKey,
                collectionId = fetch.collectionId,
                schemaVersion = fetch.schemaVersion,
                manifestVersion = manifestEntry.version,
                manifestEtag = manifestEntry.etag,
                responseETag = fetch.responseETag,
                fetchedAtEpochMillis = clock(),
                languageTag = languageTag,
            )
    }
