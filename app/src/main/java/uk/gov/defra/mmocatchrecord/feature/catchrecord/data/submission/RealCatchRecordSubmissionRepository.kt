package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.CatchRecordReferenceGenerator
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CatchRecordHttpException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CatchRecordRecoverySnapshotDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CatchRecordRemoteDataSource
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MissingVesselRegistrationException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.UnresolvedGearMeasurementException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.ReferenceDataRefreshScheduler
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.ReferenceDataRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val HTTP_CONFLICT = 409
private const val HTTP_PRECONDITION_FAILED = 412
private const val HTTP_NOT_FOUND = 404
private const val SERVER_STATUS_DRAFT = "DRAFT"
private const val SERVER_STATUS_SUBMITTED = "SUBMITTED"
private const val SERVER_STATUS_COMPLETE = "COMPLETE"
private const val STEP_CREATED = "created"
private const val STEP_REPLACED = "replaced"
private const val STEP_SUBMITTED = "submitted"
private const val MAX_REFERENCE_ATTEMPTS = 20
private const val LIST_RECOVERY_LIMIT = 100

/** A replace/submit conflict outcome: the refreshed draft to retry with, or the final one when [isDone]. */
private class WriteRecovery(
    val draft: CatchRecordDraft,
    val isDone: Boolean,
)

/** "Done" per ADR 0026 §6: submitted/complete server-side *and* no unsubmitted local changes pending. */
private fun isDone(server: CatchRecordRecoverySnapshotDto): Boolean =
    (server.status == SERVER_STATUS_SUBMITTED || server.status == SERVER_STATUS_COMPLETE) &&
        !server.hasUnsubmittedChanges

private suspend fun markSubmitted(
    draftRepository: CatchRecordDraftRepository,
    draft: CatchRecordDraft,
    server: CatchRecordRecoverySnapshotDto,
): CatchRecordDraft =
    draftRepository
        .saveDraft(draft.copy(serverVersion = server.version, submissionStep = STEP_SUBMITTED))
        .getOrThrow()

/** A SUBMITTED/COMPLETE record with unsubmitted changes cannot be amended by this flow (B2) — a
 *  PUT there would silently amend a regulatory submission, so this is terminal, not a retry. */
private fun nonDraftConflict(
    id: String,
    server: CatchRecordRecoverySnapshotDto,
): Nothing {
    error("Cannot write catch record '$id': server status is '${server.status}' with unsubmitted changes")
}

/** S5: a 404 on replace/submit means the server-side record is gone — clear every resumable field so
 *  the next attempt restarts cleanly from create, rather than retrying against a dead id forever. */
private suspend fun resetAfterNotFound(
    draftRepository: CatchRecordDraftRepository,
    draft: CatchRecordDraft,
) {
    draftRepository.saveDraft(
        draft.copy(
            serverCatchRecordId = null,
            serverVersion = null,
            submissionStep = null,
            createIdempotencyKey = null,
            submitIdempotencyKey = null,
        ),
    )
}

private suspend fun toReplacePayload(
    referenceDataRepository: ReferenceDataRepository,
    draft: CatchRecordDraft,
) = CatchRecordSubmissionPayloadMapper.toReplacePayload(
    draft,
    ports = referenceDataRepository.getPorts().getOrThrow(),
    gearTypes = referenceDataRepository.getGearTypes().getOrThrow(),
    species = referenceDataRepository.getSpecies().getOrThrow(),
    statisticalSubRectangles = referenceDataRepository.getStatisticalSubRectangles().getOrThrow(),
)

/** Resumable create → replace → submit sequence; per-draft [Mutex] guards against double-submit. See ADR 0026. */
@Singleton
internal class RealCatchRecordSubmissionRepository
    @Inject
    constructor(
        private val remoteDataSource: CatchRecordRemoteDataSource,
        private val draftRepository: CatchRecordDraftRepository,
        private val referenceDataRepository: ReferenceDataRepository,
        private val idFactory: () -> String,
        private val referenceDataRefreshScheduler: ReferenceDataRefreshScheduler = ReferenceDataRefreshScheduler {},
        private val clock: () -> Long,
    ) : CatchRecordSubmissionRepository {
        private val locksByDraftId = ConcurrentHashMap<String, Mutex>()

        /** Reloads the latest persisted row first (B1): a concurrent caller, or a previous partial attempt,
         *  may already have advanced [CatchRecordDraft.submissionStep] past what [draft] still carries. */
        override suspend fun submit(draft: CatchRecordDraft): Result<CatchRecordDraft> =
            locksByDraftId.getOrPut(draft.id) { Mutex() }.withLock {
                runCatching {
                    val latest = draftRepository.getDraftById(draft.id).getOrThrow() ?: error("Draft no longer exists")
                    var current = ensureConformingReference(latest)
                    current = ensureCreated(current)
                    current = ensureReplaced(current)
                    ensureSubmitted(current)
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    scheduleRefreshIfStaleGearData(error)
                }
            }

        /** A blank `apiMeasurementId` means the cached gears dataset is stale, not permanently wrong (ADR 0026). */
        private fun scheduleRefreshIfStaleGearData(error: Throwable) {
            if (error is UnresolvedGearMeasurementException) {
                referenceDataRefreshScheduler.scheduleRefreshAfterSignIn()
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

        /** Mirrors [ensureReplaced]/[ensureSubmitted]'s own recovery: a recovered SUBMITTED/COMPLETE or
         *  non-DRAFT snapshot must not be overwritten with a stale `created` step (re-review finding #2). */
        private suspend fun recoverAfterCreateConflict(
            draft: CatchRecordDraft,
            reference: String,
        ): CatchRecordDraft {
            val id =
                remoteDataSource
                    .list(limit = LIST_RECOVERY_LIMIT)
                    .items
                    .firstOrNull { it.catchRecordReference == reference }
                    ?.id
                    ?: error("Idempotency conflict for '$reference' but no matching record found")
            val server = remoteDataSource.recover(id)
            val withId = draft.copy(serverCatchRecordId = id)
            return when {
                isDone(server) -> markSubmitted(draftRepository, withId, server)
                server.status != SERVER_STATUS_DRAFT -> nonDraftConflict(id, server)
                else ->
                    draftRepository
                        .saveDraft(withId.copy(serverVersion = server.version, submissionStep = STEP_CREATED))
                        .getOrThrow()
            }
        }

        /** At most one version-refresh retry (B2): a second conflict after refreshing is terminal, not looped. */
        private suspend fun ensureReplaced(draft: CatchRecordDraft): CatchRecordDraft {
            if (draft.submissionStep == STEP_REPLACED || draft.submissionStep == STEP_SUBMITTED) return draft
            val id = requireNotNull(draft.serverCatchRecordId) { "Draft has no server catch record id" }
            var current = draft
            var refreshed = false
            while (true) {
                val version = requireNotNull(current.serverVersion) { "Draft has no server version" }
                try {
                    val payload = toReplacePayload(referenceDataRepository, current)
                    val response = remoteDataSource.replace(id, version.toString(), payload)
                    return draftRepository
                        .saveDraft(current.copy(serverVersion = response.version, submissionStep = STEP_REPLACED))
                        .getOrThrow()
                } catch (httpError: CatchRecordHttpException) {
                    val recovery =
                        recoverFromWriteFailure(httpError, "replace", id, current, refreshed) { stale, version ->
                            stale.copy(serverVersion = version)
                        }
                    if (recovery.isDone) return recovery.draft
                    current = recovery.draft
                    refreshed = true
                }
            }
        }

        /** As [ensureReplaced]: at most one version-refresh retry, and a stale key is rotated (B2) rather
         *  than replayed, since reusing it against a new `If-Match` otherwise loops on IDEMPOTENCY_CONFLICT. */
        private suspend fun ensureSubmitted(draft: CatchRecordDraft): CatchRecordDraft {
            if (draft.submissionStep == STEP_SUBMITTED) return draft
            val id = requireNotNull(draft.serverCatchRecordId) { "Draft has no server catch record id" }
            var current = draft
            var refreshed = false
            while (true) {
                val version = requireNotNull(current.serverVersion) { "Draft has no server version" }
                val idempotencyKey = current.submitIdempotencyKey ?: idFactory()
                val withKey =
                    draftRepository.saveDraft(current.copy(submitIdempotencyKey = idempotencyKey)).getOrThrow()
                try {
                    remoteDataSource.submit(id, version.toString(), idempotencyKey)
                    return draftRepository.saveDraft(withKey.copy(submissionStep = STEP_SUBMITTED)).getOrThrow()
                } catch (httpError: CatchRecordHttpException) {
                    val recovery =
                        recoverFromWriteFailure(httpError, "submit", id, withKey, refreshed) { stale, serverVersion ->
                            stale.copy(serverVersion = serverVersion, submitIdempotencyKey = null)
                        }
                    if (recovery.isDone) return recovery.draft
                    current = recovery.draft
                    refreshed = true
                }
            }
        }

        /** Shared replace/submit failure handling: a 404 resets for a clean restart, and a first conflict
         *  refreshes the version via [refreshed] (or settles a server-side done record); else rethrows. */
        private suspend fun recoverFromWriteFailure(
            httpError: CatchRecordHttpException,
            operation: String,
            id: String,
            draft: CatchRecordDraft,
            alreadyRefreshed: Boolean,
            refreshed: (CatchRecordDraft, Int) -> CatchRecordDraft,
        ): WriteRecovery {
            if (httpError.httpStatusCode == HTTP_NOT_FOUND) {
                resetAfterNotFound(draftRepository, draft)
                throw httpError
            }
            if (!httpError.isConflict) throw httpError
            if (alreadyRefreshed) error("Unresolved $operation conflict for catch record '$id' after one refresh")
            val server = remoteDataSource.recover(id)
            return when {
                isDone(server) -> WriteRecovery(markSubmitted(draftRepository, draft, server), isDone = true)
                server.status != SERVER_STATUS_DRAFT -> nonDraftConflict(id, server)
                else -> WriteRecovery(draftRepository.saveDraft(refreshed(draft, server.version)).getOrThrow(), false)
            }
        }

        private val CatchRecordHttpException.isConflict: Boolean
            get() = httpStatusCode == HTTP_CONFLICT || httpStatusCode == HTTP_PRECONDITION_FAILED
    }
