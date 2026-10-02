package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.core.language.AppLanguage

/**
 * [MmoTheme] plus a fixed [WizardScaffoldState] via [LocalWizardScaffoldState], so screens built on
 * [CatchRecordWizardScaffold] can be composed by `createComposeRule()`'s plain, non-Hilt activity
 * (the scaffold otherwise resolves its state with `hiltViewModel()`).
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
