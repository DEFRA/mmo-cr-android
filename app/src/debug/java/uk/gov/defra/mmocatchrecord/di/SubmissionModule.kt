package uk.gov.defra.mmocatchrecord.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uk.gov.defra.mmocatchrecord.common.design.DebugSettingsSection
import uk.gov.defra.mmocatchrecord.debug.DebugSettingsSectionImpl
import uk.gov.defra.mmocatchrecord.debug.DebugSubmissionFailureToggle
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.FailureInjectingSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.StubCatchRecordSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordSubmissionRepository
import javax.inject.Singleton

/** Debug-variant-only bindings (CRAR-152 Phase C C5) — see `app/src/release` for the release counterpart. */
@Module
@InstallIn(SingletonComponent::class)
object SubmissionModule {
    @Provides
    @Singleton
    fun provideCatchRecordSubmissionRepository(toggle: DebugSubmissionFailureToggle): CatchRecordSubmissionRepository =
        FailureInjectingSubmissionRepository(StubCatchRecordSubmissionRepository(), toggle)

    @Provides
    @Singleton
    fun provideDebugSettingsSection(toggle: DebugSubmissionFailureToggle): DebugSettingsSection =
        DebugSettingsSectionImpl(toggle)
}
