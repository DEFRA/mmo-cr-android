package uk.gov.defra.mmocatchrecord.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uk.gov.defra.mmocatchrecord.common.design.DebugSettingsSection
import uk.gov.defra.mmocatchrecord.debug.NoOpDebugSettingsSection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.StubCatchRecordSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordSubmissionRepository
import javax.inject.Singleton

/** Release-variant-only bindings (CRAR-152 Phase C C5) — see `app/src/debug` for the debug counterpart. */
@Module
@InstallIn(SingletonComponent::class)
object SubmissionModule {
    @Provides
    @Singleton
    fun provideCatchRecordSubmissionRepository(): CatchRecordSubmissionRepository =
        StubCatchRecordSubmissionRepository()

    @Provides
    @Singleton
    fun provideDebugSettingsSection(): DebugSettingsSection = NoOpDebugSettingsSection()
}
