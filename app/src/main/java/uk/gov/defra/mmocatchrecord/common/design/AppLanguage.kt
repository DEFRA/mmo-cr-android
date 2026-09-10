package uk.gov.defra.mmocatchrecord.common.design

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Screen-level composition wrapper to temporarily override the Context's active locale
 * so nested calls to [stringResource] resolve English/Welsh without restarting the activity.
 */
@Composable
fun AppLanguageProvider(
    language: String, // "en" or "cy"
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val currentConfig = LocalConfiguration.current
    val localizedContext =
        remember(context, language, currentConfig) {
            val locale = Locale.forLanguageTag(language)
            Locale.setDefault(locale)
            val config = Configuration(currentConfig)
            config.setLocale(locale)
            context.createConfigurationContext(config)
        }
    CompositionLocalProvider(LocalContext provides localizedContext) {
        content()
    }
}
