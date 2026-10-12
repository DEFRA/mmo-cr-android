package uk.gov.defra.mmocatchrecord.core.language

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Test-only in-memory [AppLanguageRepository] (the real one is DataStore-backed). */
class FakeAppLanguageRepository(
    initialLanguage: String = AppLanguage.ENGLISH,
) : AppLanguageRepository {
    private val current = MutableStateFlow(initialLanguage)

    override val language: Flow<String> = current

    override suspend fun setLanguage(language: String) {
        current.value = language
    }
}
