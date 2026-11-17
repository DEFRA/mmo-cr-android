package uk.gov.defra.mmocatchrecord.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uk.gov.defra.mmocatchrecord.core.config.AppConfiguration
import uk.gov.defra.mmocatchrecord.debug.DebugSubmissionFailureToggle
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.FailureInjectingSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.RealCatchRecordSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.StubCatchRecordSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordSubmissionRepository
import javax.inject.Singleton

/** Debug-variant-only bindings (CRAR-152 Phase C C5) — see `app/src/release` for the release counterpart. */
@Module
@InstallIn(SingletonComponent::class)
object SubmissionModule {
    @Provides
    @Singleton
    internal fun provideCatchRecordSubmissionRepository(
        appConfiguration: AppConfiguration,
        toggle: DebugSubmissionFailureToggle,
        real: RealCatchRecordSubmissionRepository,
    ): CatchRecordSubmissionRepository =
        FailureInjectingSubmissionRepository(
            submissionRepositoryFor(
                useStubSubmission = appConfiguration.useStubAuth ?: false,
                real = real,
                stub = StubCatchRecordSubmissionRepository(),
            ),
            toggle,
        )
}
