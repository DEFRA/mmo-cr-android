package uk.gov.defra.mmocatchrecord.core.language

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** English/Welsh language codes supported by [AppLanguageProvider][uk.gov.defra.mmocatchrecord.common.design.AppLanguageProvider]. */
object AppLanguage {
    const val ENGLISH = "en"
    const val WELSH = "cy"
}

private val Context.languageDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_language")

/**
 * App-wide, DataStore-persisted language preference (see ADR 0012) — the single source of truth for the
 * user's chosen language, surviving navigation and process death/restart. Deliberately holds no sensitive
 * data, so (unlike the SQLCipher catch-record database — see ADR 0006) it is *not* excluded from Android
 * backup/device-transfer.
 */
interface AppLanguageRepository {
    /** The persisted language code (`"en"`/`"cy"`), defaulting to [AppLanguage.ENGLISH] until first set. */
    val language: Flow<String>

    suspend fun setLanguage(language: String)
}

class DataStoreAppLanguageRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AppLanguageRepository {
        override val language: Flow<String> =
            context.languageDataStore.data.map { preferences ->
                preferences[LANGUAGE_KEY] ?: AppLanguage.ENGLISH
            }

        override suspend fun setLanguage(language: String) {
            context.languageDataStore.edit { preferences -> preferences[LANGUAGE_KEY] = language }
        }

        private companion object {
            val LANGUAGE_KEY = stringPreferencesKey("language")
        }
    }
