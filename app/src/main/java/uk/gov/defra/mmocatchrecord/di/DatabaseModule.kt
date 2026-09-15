package uk.gov.defra.mmocatchrecord.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.CatchRecordDatabase
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.CatchRecordDraftDao
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.CatchRecordPassphraseProvider
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.SqlCipherLibraryLoader
import javax.inject.Singleton

/**
 * Hilt module providing the SQLCipher-encrypted [CatchRecordDatabase] (see ADR 0006) and its DAO.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DATABASE_NAME = "catch_record_draft.db"

    @Provides
    @Singleton
    fun provideCatchRecordPassphraseProvider(
        @ApplicationContext context: Context,
    ): CatchRecordPassphraseProvider = CatchRecordPassphraseProvider(context)

    @Provides
    @Singleton
    fun provideCatchRecordDatabase(
        @ApplicationContext context: Context,
        passphraseProvider: CatchRecordPassphraseProvider,
    ): CatchRecordDatabase {
        SqlCipherLibraryLoader.ensureLoaded()
        val passphrase = passphraseProvider.getOrCreatePassphrase()
        val supportFactory = SupportOpenHelperFactory(passphrase)
        return Room
            .databaseBuilder(context, CatchRecordDatabase::class.java, DATABASE_NAME)
            .openHelperFactory(supportFactory)
            // Pre-release app, no production data to preserve yet: destructively recreate on schema
            // bump (see Phase 3 GearUse.numberOfShots/confirmedUsedOnTrip columns) rather than writing a
            // real Migration. Revisit once the app has real users with drafts worth preserving.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    @Singleton
    fun provideCatchRecordDraftDao(database: CatchRecordDatabase): CatchRecordDraftDao = database.catchRecordDraftDao()
}
