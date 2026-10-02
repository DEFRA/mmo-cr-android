package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.core.language.AppLanguage

/**
 * JVM/Robolectric counterpart of the androidTest `WizardTestTheme` (kept in this source set so
 * Compose screens built on [CatchRecordWizardScaffold] can be exercised by `createComposeRule()`'s
 * plain, non-Hilt host under `testDebugUnitTest` — see that source set's identically named file for
 * why this exists: the scaffold otherwise resolves its state via `hiltViewModel()`, which is
 * unavailable outside a real Hilt-backed Activity).
 */
@Suppress("FunctionNaming")
@Composable
fun WizardTestTheme(
    isOffline: Boolean = false,
    language: String = AppLanguage.ENGLISH,
    onLanguageToggle: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalWizardScaffoldState provides
            WizardScaffoldState(
                currentLanguage = language,
                onLanguageToggle = onLanguageToggle,
                isOffline = isOffline,
            ),
    ) {
        MmoTheme(content = content)
    }
}
