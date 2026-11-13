package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.CatchRecordReferenceGenerator
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CatchRecordHttpException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CatchRecordRemoteDataSource
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MissingVesselRegistrationException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.ReferenceDataRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val HTTP_CONFLICT = 409
private const val HTTP_PRECONDITION_FAILED = 412
private const val SERVER_STATUS_SUBMITTED = "SUBMITTED"
private const val STEP_CREATED = "created"
private const val STEP_REPLACED = "replaced"
private const val STEP_SUBMITTED = "submitted"
private const val MAX_REFERENCE_ATTEMPTS = 20

/** Resumable create → replace → submit sequence; per-draft [Mutex] guards against double-submit. See ADR 0022. */
@Singleton
internal class RealCatchRecordSubmissionRepository
    @Inject
    constructor(
        private val remoteDataSource: CatchRecordRemoteDataSource,
        private val draftRepository: CatchRecordDraftRepository,
        private val referenceDataRepository: ReferenceDataRepository,
        private val idFactory: () -> String,
        private val clock: () -> Long,
    ) : CatchRecordSubmissionRepository {
        private val locksByDraftId = ConcurrentHashMap<String, Mutex>()

        override suspend fun submit(draft: CatchRecordDraft): Result<Unit> =
            locksByDraftId.getOrPut(draft.id) { Mutex() }.withLock {
                runCatching {
                    var current = ensureConformingReference(draft)
                    current = ensureCreated(current)
                    current = ensureReplaced(current)
                    ensureSubmitted(current)
                }
            }

        /** MIGRATION_9_10's `-2`/`-3`-suffixed legacy references never matched the server's regex. */
        private suspend fun ensureConformingReference(draft: CatchRecordDraft): CatchRecordDraft {
            val reference = draft.catchRecordReference
            if (reference != null && CatchRecordReferenceGenerator.matches(reference)) return draft
            val pln = resolveNormalisedPln(draft.vesselId)
            var candidate = CatchRecordReferenceGenerator.generate(pln, clock())
            repeat(MAX_REFERENCE_ATTEMPTS) {
                val saved = draftRepository.saveDraft(draft.copy(catchRecordReference = candidate))
                if (saved.isSuccess) return saved.getOrThrow()
                candidate = CatchRecordReferenceGenerator.nextAfter(candidate)
            }
            error("No free catch record reference found while regenerating '$reference'")
        }

        /** Both failure paths mean the same thing to the caller: the vessel has no usable PLN. */
        private suspend fun resolveNormalisedPln(vesselId: String): String {
            val vessel =
                referenceDataRepository.getVessels().getOrThrow().firstOrNull { it.id == vesselId }
                    ?: throw MissingVesselRegistrationException()
            return CatchRecordReferenceGenerator.normaliseRssNumber(vessel.pln)
                ?: throw MissingVesselRegistrationException()
        }

        private suspend fun ensureCreated(draft: CatchRecordDraft): CatchRecordDraft {
            if (draft.submissionStep != null) return draft
            val idempotencyKey = draft.createIdempotencyKey ?: idFactory()
            val withKey = draftRepository.saveDraft(draft.copy(createIdempotencyKey = idempotencyKey)).getOrThrow()
            val reference = requireNotNull(withKey.catchRecordReference) { "Draft has no catch record reference" }
            val payload = CatchRecordSubmissionPayloadMapper.toCreatePayload(withKey)
            return try {
                val response = remoteDataSource.createDraft(payload, idempotencyKey)
                draftRepository
                    .saveDraft(
                        withKey.copy(
                            serverCatchRecordId = response.id,
                            serverVersion = response.version,
                            submissionStep = STEP_CREATED,
                        ),
                    ).getOrThrow()
            } catch (conflict: CatchRecordHttpException) {
                if (!conflict.isConflict) throw conflict
                recoverAfterCreateConflict(withKey, reference)
            }
        }

        private suspend fun recoverAfterCreateConflict(
            draft: CatchRecordDraft,
            reference: String,
        ): CatchRecordDraft {
            val id =
                remoteDataSource
                    .list()
                    .items
                    .firstOrNull { it.catchRecordReference == reference }
                    ?.id
                    ?: error("Idempotency conflict for '$reference' but no matching record found")
            val current = remoteDataSource.retrieve(id)
            return draftRepository
                .saveDraft(
                    draft.copy(
                        serverCatchRecordId = current.id,
                        serverVersion = current.version,
                        submissionStep = STEP_CREATED,
                    ),
                ).getOrThrow()
        }

        private suspend fun ensureReplaced(draft: CatchRecordDraft): CatchRecordDraft {
            if (draft.submissionStep == STEP_REPLACED || draft.submissionStep == STEP_SUBMITTED) return draft
            val id = requireNotNull(draft.serverCatchRecordId) { "Draft has no server catch record id" }
            val version = requireNotNull(draft.serverVersion) { "Draft has no server version" }
            return try {
                val payload = toReplacePayload(draft)
                val response = remoteDataSource.replace(id, version.toString(), payload)
                draftRepository
                    .saveDraft(draft.copy(serverVersion = response.version, submissionStep = STEP_REPLACED))
                    .getOrThrow()
            } catch (conflict: CatchRecordHttpException) {
                if (!conflict.isConflict) throw conflict
                val current = remoteDataSource.retrieve(id)
                ensureReplaced(draftRepository.saveDraft(draft.copy(serverVersion = current.version)).getOrThrow())
            }
        }

        private suspend fun toReplacePayload(draft: CatchRecordDraft) =
            CatchRecordSubmissionPayloadMapper.toReplacePayload(
                draft,
                ports = referenceDataRepository.getPorts().getOrThrow(),
                gearTypes = referenceDataRepository.getGearTypes().getOrThrow(),
                species = referenceDataRepository.getSpecies().getOrThrow(),
                statisticalSubRectangles = referenceDataRepository.getStatisticalSubRectangles().getOrThrow(),
            )

        private suspend fun ensureSubmitted(draft: CatchRecordDraft) {
            if (draft.submissionStep == STEP_SUBMITTED) return
            val id = requireNotNull(draft.serverCatchRecordId) { "Draft has no server catch record id" }
            val version = requireNotNull(draft.serverVersion) { "Draft has no server version" }
            val idempotencyKey = draft.submitIdempotencyKey ?: idFactory()
            val withKey = draftRepository.saveDraft(draft.copy(submitIdempotencyKey = idempotencyKey)).getOrThrow()
            val alreadyHandled =
                try {
                    remoteDataSource.submit(id, version.toString(), idempotencyKey)
                    false
                } catch (conflict: CatchRecordHttpException) {
                    if (!conflict.isConflict) throw conflict
                    resolveSubmitConflict(id, withKey)
                }
            if (!alreadyHandled) draftRepository.saveDraft(withKey.copy(submissionStep = STEP_SUBMITTED)).getOrThrow()
        }

        /** `true` once the submitted step is already persisted, either here or via its own recursive retry. */
        private suspend fun resolveSubmitConflict(
            id: String,
            draft: CatchRecordDraft,
        ): Boolean {
            val current = remoteDataSource.retrieve(id)
            if (current.status == SERVER_STATUS_SUBMITTED) {
                Timber.i("Submission for draft %s already completed server-side; treating as success", draft.id)
                draftRepository.saveDraft(draft.copy(submissionStep = STEP_SUBMITTED)).getOrThrow()
                return true
            }
            val refreshed = draftRepository.saveDraft(draft.copy(serverVersion = current.version)).getOrThrow()
            ensureSubmitted(refreshed)
            return true
        }

        private val CatchRecordHttpException.isConflict: Boolean
            get() = httpStatusCode == HTTP_CONFLICT || httpStatusCode == HTTP_PRECONDITION_FAILED
    }
