package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.submission

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme

/** Moved out of [LateSubmissionWarningScreen.kt] (excluded from Sonar coverage as `*Previews.kt`) — its
 * behaviour is already exercised directly via [LateSubmissionWarningScreenContent] in the test suite. */
@Preview(showBackground = true)
@Suppress("FunctionNaming")
@Composable
fun LateSubmissionWarningScreenContentPreview() {
    MmoTheme {
        LateSubmissionWarningScreenContent(
            onCheckTripEndDate = {},
            onSubmit = {},
        )
    }
}
