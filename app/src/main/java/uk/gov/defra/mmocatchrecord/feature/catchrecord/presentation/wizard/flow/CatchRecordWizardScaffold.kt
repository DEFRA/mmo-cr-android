@file:Suppress("detekt.FunctionNaming", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import uk.gov.defra.mmocatchrecord.common.design.AppLanguageProvider
import uk.gov.defra.mmocatchrecord.common.design.GdsTopAppBar
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.common.design.Spacing

data class WizardErrorSummaryItem(
    val message: String,
    val onClick: () -> Unit,
)

/**
 * [title] renders as the page's `headlineLarge` heading — pass a blank string only for the (rare) Phase 8
 * submission-result screens whose own coloured [uk.gov.defra.mmocatchrecord.common.design.GdsResultBanner]
 * *is* the page heading (that banner's own text carries the `heading()` semantics instead), so no separate
 * duplicate heading is rendered above it.
 */
@Suppress("FunctionNaming")
@Composable
fun CatchRecordWizardScaffold(
    screenTestTag: String,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    var currentLanguage by rememberSaveable { mutableStateOf("en") }
    val scrollState = rememberScrollState()

    AppLanguageProvider(language = currentLanguage) {
        Scaffold(
            topBar = {
                GdsTopAppBar(
                    currentLanguage = currentLanguage,
                    onLanguageToggle = { currentLanguage = if (currentLanguage == "en") "cy" else "en" },
                    onBackClick = onBack,
                )
            },
            modifier = modifier.fillMaxSize().testTag(screenTestTag),
        ) { innerPadding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            MmoColors.White,
                        ).verticalScroll(scrollState)
                        .padding(innerPadding)
                        .padding(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                if (title.isNotBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.semantics { heading() },
                    )
                }
                content()
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
fun WizardLoadingState(modifier: Modifier = Modifier) {
    CircularProgressIndicator(color = MmoColors.GovBlue, modifier = modifier)
}

@Suppress("FunctionNaming")
@Composable
fun WizardErrorState(
    message: String,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = message,
        color = MmoColors.ErrorRed,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier.testTag(testTag),
    )
}

@Suppress("FunctionNaming")
@Composable
fun WizardErrorSummary(
    title: String,
    items: List<WizardErrorSummaryItem>,
    focusRequester: FocusRequester,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    MmoColors.White,
                ).focusRequester(focusRequester)
                .focusable()
                .testTag(testTag)
                .padding(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MmoColors.ErrorRed,
        )
        items.forEach { item ->
            Text(
                text = item.message,
                color = MmoColors.Link,
                style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
                modifier = Modifier.clickable(onClick = item.onClick).heightIn(min = Spacing.minTouchTarget),
            )
        }
    }
}
