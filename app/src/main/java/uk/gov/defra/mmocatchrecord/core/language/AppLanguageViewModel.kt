package uk.gov.defra.mmocatchrecord.core.language

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Thin ViewModel wrapper exposing [AppLanguageRepository]'s DataStore-persisted language preference as
 * Compose-observable state — see ADR 0012. Used by every real (non-preview) screen that shows the language
 * toggle (`CatchRecordWizardScaffold`, `HomeScreen`, `SignInScreen`); each obtains its own
 * destination-scoped instance via `hiltViewModel()`, but all read/write the same underlying persisted
 * value, so the effective language is consistent app-wide and survives navigation/process recreation.
 */
@HiltViewModel
class AppLanguageViewModel
    @Inject
    constructor(
        private val repository: AppLanguageRepository,
    ) : ViewModel() {
        val language: StateFlow<String> =
            repository.language.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = AppLanguage.ENGLISH,
            )

        fun setLanguage(language: String) {
            viewModelScope.launch { repository.setLanguage(language) }
        }

        fun toggleLanguage() {
            val next = if (language.value == AppLanguage.ENGLISH) AppLanguage.WELSH else AppLanguage.ENGLISH
            setLanguage(next)
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
