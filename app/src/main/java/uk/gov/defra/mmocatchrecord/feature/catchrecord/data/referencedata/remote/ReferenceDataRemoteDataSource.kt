package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response
import timber.log.Timber
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Vessel
import java.io.IOException
import javax.inject.Inject

private const val HTTP_NOT_MODIFIED = 304
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_SERVER_ERROR_THRESHOLD = 500

/** A dataset fetch's outcome: a fresh/changed collection, or a confirmed-unchanged `304`. */
internal sealed interface ReferenceDataFetchResult<out T> {
    data class Modified<T>(
        val items: T,
        val collectionId: String,
        val schemaVersion: String,
        val datasetVersion: String,
        val responseETag: String?,
    ) : ReferenceDataFetchResult<T>

    data object NotModified : ReferenceDataFetchResult<Nothing>
}

/** The manifest has no single `schemaVersion` of its own (each of its datasets carries one) so it gets
 *  its own result shape rather than being forced through [ReferenceDataFetchResult].
 */
internal sealed interface ManifestFetchResult {
    data class Modified(
        val manifest: ManifestDto,
        val responseETag: String?,
    ) : ManifestFetchResult

    data object NotModified : ManifestFetchResult
}

/**
 * Thin wrapper over [ReferenceDataApi] turning its raw [Response]s into [ReferenceDataFetchResult] (schema
 * guard + DTO→domain mapping, ADR 0015). Not yet wired into `ReferenceDataRepository` — a later phase.
 */
internal class ReferenceDataRemoteDataSource
    @Inject
    constructor(
        private val api: ReferenceDataApi,
        private val json: Json,
    ) {
        suspend fun fetchVessels(ifNoneMatch: String? = null): ReferenceDataFetchResult<List<Vessel>> =
            handleEnvelope(api.vessels(ifNoneMatch = ifNoneMatch), "vessels") { envelope ->
                envelope.items.map { it.toDomain() }
            }

        suspend fun fetchPorts(ifNoneMatch: String? = null): ReferenceDataFetchResult<List<Port>> =
            handleEnvelope(api.ports(ifNoneMatch = ifNoneMatch), "ports") { envelope ->
                envelope.items.map { it.toDomain() }
            }

        suspend fun fetchSpecies(ifNoneMatch: String? = null): ReferenceDataFetchResult<List<Species>> =
            handleEnvelope(api.species(ifNoneMatch = ifNoneMatch), "species") { envelope ->
                envelope.items.map { it.toDomain() }
            }

        suspend fun fetchGears(
            vesselLengthMetres: Double? = null,
            ifNoneMatch: String? = null,
        ): ReferenceDataFetchResult<List<GearType>> =
            handleEnvelope(
                api.gears(vesselLengthMetres = vesselLengthMetres, ifNoneMatch = ifNoneMatch),
                "gears",
            ) { envelope ->
                val measurementsById = envelope.measurements.associateBy { it.id }
                envelope.items.map { it.toDomain(measurementsById) }
            }

        suspend fun fetchManifest(ifNoneMatch: String? = null): ManifestFetchResult {
            val response = api.manifest(ifNoneMatch = ifNoneMatch)
            if (response.code() == HTTP_NOT_MODIFIED) return ManifestFetchResult.NotModified
            if (!response.isSuccessful) throw toApiException("manifest", response)
            val manifest = response.body() ?: throw ReferenceDataMalformedResponseException("manifest")
            return ManifestFetchResult.Modified(manifest, response.headers()["ETag"])
        }

        private fun <E : ReferenceDataEnvelopeMetadata, T> handleEnvelope(
            response: Response<E>,
            dataset: String,
            mapItems: (E) -> T,
        ): ReferenceDataFetchResult<T> {
            if (response.code() == HTTP_NOT_MODIFIED) return ReferenceDataFetchResult.NotModified
            if (!response.isSuccessful) throw toApiException(dataset, response)
            val envelope = response.body() ?: throw ReferenceDataMalformedResponseException(dataset)
            requireSupportedSchemaVersion(dataset, envelope.schemaVersion)
            return ReferenceDataFetchResult.Modified(
                items = mapItems(envelope),
                collectionId = envelope.collectionId,
                schemaVersion = envelope.schemaVersion,
                datasetVersion = envelope.version,
                responseETag = response.headers()["ETag"],
            )
        }

        private fun toApiException(
            dataset: String,
            response: Response<*>,
        ): ReferenceDataApiException {
            val httpStatusCode = response.code()
            if (httpStatusCode == HTTP_UNAUTHORIZED) return ReferenceDataUnauthorizedException(dataset)
            val errorBody = parseErrorBody(response)
            val retryable = errorBody?.retryable ?: (httpStatusCode >= HTTP_SERVER_ERROR_THRESHOLD)
            return ReferenceDataHttpException(dataset, httpStatusCode, errorBody?.code, retryable)
        }

        private fun parseErrorBody(response: Response<*>): ErrorBodyDto? {
            val raw = response.errorBody()?.string() ?: return null
            return try {
                json.decodeFromString<ErrorEnvelopeDto>(raw).error
            } catch (error: SerializationException) {
                Timber.w(error, "ReferenceData: malformed error body, falling back to status-based retryable")
                null
            } catch (error: IOException) {
                Timber.w(error, "ReferenceData: error body unreadable, falling back to status-based retryable")
                null
            }
        }
    }
