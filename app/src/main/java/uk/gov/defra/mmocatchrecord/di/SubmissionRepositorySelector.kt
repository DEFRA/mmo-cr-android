package uk.gov.defra.mmocatchrecord.di

import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.RealCatchRecordSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.StubCatchRecordSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordSubmissionRepository

/** `local` has no backend and uses the stub; `dev`/`prod` use the real HTTP repository (ADR 0022). */
internal fun submissionRepositoryFor(
    useStubSubmission: Boolean,
    real: RealCatchRecordSubmissionRepository,
    stub: StubCatchRecordSubmissionRepository,
): CatchRecordSubmissionRepository = if (useStubSubmission) stub else real
