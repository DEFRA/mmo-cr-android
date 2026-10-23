package uk.gov.defra.mmocatchrecord.common.design

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalResources
import java.util.Locale

/** Overrides the composition's locale (Context, Configuration, Resources) without `Locale.setDefault`, per ADR 0012. */
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
                LocalizedContextWrapper(context, context.createConfigurationContext(config).resources)
            }
        // stringResource reads LocalResources (Compose 1.9+), so overriding LocalContext alone is not enough.
        CompositionLocalProvider(
            LocalContext provides localizedContext,
            LocalConfiguration provides localizedContext.resources.configuration,
            LocalResources provides localizedContext.resources,
        ) {
            content()
        }
    }
}

/** Keeps the Activity in the base-context chain (needed by hiltViewModel) while serving localised resources. */
private class LocalizedContextWrapper(
    base: Context,
    private val localizedResources: Resources,
) : ContextWrapper(base) {
    override fun getResources(): Resources = localizedResources
}

/** The active app locale, as set by the nearest [AppLanguageProvider]. */
@SuppressLint("LocalContextConfigurationRead")
@Composable
fun appLocale(): Locale = LocalContext.current.resources.configuration.locales[0] ?: Locale.UK
