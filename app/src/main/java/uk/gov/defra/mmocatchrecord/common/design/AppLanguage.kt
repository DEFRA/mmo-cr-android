package uk.gov.defra.mmocatchrecord.common.design

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import java.util.Locale

/**
 * Screen-level composition wrapper to temporarily override the Context's active locale
 * so nested calls to stringResource resolve English/Welsh without restarting the activity.
 *
 * Deliberately does **not** call `Locale.setDefault` (see ADR 0012): mutating the JVM-wide default locale
 * from a composable is an unsafe, process-global side effect that would silently change locale-sensitive
 * behaviour for every other component in the process, not just this composition subtree. Only the
 * `Context` provided to nested composables via [LocalContext] is locale-overridden.
 */
@Composable
fun AppLanguageProvider(
    language: String, // "en" or "cy"
    content: @Composable () -> Unit,
) {
    val isPreview = LocalInspectionMode.current
    if (isPreview) {
        content()
    } else {
        val context = LocalContext.current
        val currentConfig = LocalConfiguration.current
        val localizedContext =
            remember(context, language, currentConfig) {
                val locale = Locale.forLanguageTag(language)
                val config = Configuration(currentConfig)
                config.setLocale(locale)
                context.createConfigurationContext(config)
            }
        CompositionLocalProvider(LocalContext provides localizedContext) {
            content()
        }
    }
}
