package uk.gov.defra.mmocatchrecord.di

import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.RealCatchRecordSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.StubCatchRecordSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordSubmissionRepository

/** Selects the stub when `USE_STUB_AUTH` is set (no backend, e.g. local), else the real HTTP repository (ADR 0026). */
internal fun submissionRepositoryFor(
    useStubSubmission: Boolean,
    real: RealCatchRecordSubmissionRepository,
    stub: StubCatchRecordSubmissionRepository,
): CatchRecordSubmissionRepository = if (useStubSubmission) stub else real
