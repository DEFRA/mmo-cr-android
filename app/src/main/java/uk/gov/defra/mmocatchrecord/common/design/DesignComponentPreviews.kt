@file:Suppress("detekt.FunctionNaming", "detekt.TooManyFunctions")

package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import uk.gov.defra.mmocatchrecord.feature.home.domain.CatchRecordStatus

/**
 * Compose @Preview harnesses for the reusable GOV.UK design-system components. Preview-only â€”
 * not shipped in the app UI. Grouped in their own file to respect the per-file function limit.
 */
@Preview(name = "Important banner", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun ImportantNotificationBannerPreview() {
    MmoTheme {
        ImportantNotificationBanner(
            title = "Important",
            message = "The Catch Records service will be available from 1 October 2026.",
            modifier = Modifier.padding(Spacing.m),
        )
    }
}

@Preview(name = "Primary action button", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun PrimaryActionButtonPreview() {
    MmoTheme {
        PrimaryActionButton(
            text = "Create a new catch record",
            onClick = {},
            modifier = Modifier.padding(Spacing.m),
        )
    }
}

@Preview(name = "Secondary action button", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun SecondaryActionButtonPreview() {
    MmoTheme {
        SecondaryActionButton(
            text = "Add port",
            onClick = {},
            modifier = Modifier.padding(Spacing.m),
        )
    }
}

@Preview(name = "Radio group", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun GdsRadioGroupPreview() {
    MmoTheme {
        GdsRadioGroup(
            options = listOf(GdsRadioOption("yes", "Yes"), GdsRadioOption("no", "No")),
            selectedOptionId = "yes",
            onOptionSelected = {},
            modifier = Modifier.padding(Spacing.m),
        )
    }
}

@Preview(name = "Date input", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun GdsDateInputPreview() {
    MmoTheme {
        GdsDateInput(
            value = GdsDateInputValue(day = "11", month = "09", year = "2026"),
            onValueChange = {},
            dayTestTag = "preview_day",
            monthTestTag = "preview_month",
            yearTestTag = "preview_year",
            modifier = Modifier.padding(Spacing.m),
        )
    }
}

@Preview(name = "Autocomplete", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun GdsAutocompleteFieldPreview() {
    MmoTheme {
        var value by remember { mutableStateOf("Ha") }
        GdsAutocompleteField(
            label = "Enter a port",
            value = value,
            options = listOf(GdsAutocompleteOption("port-hastings", "Hastings")),
            onValueChange = { value = it },
            onOptionSelected = { value = it.label },
            fieldTestTag = "preview_port_field",
            liveRegionTestTag = "preview_live_region",
            suggestionTestTagPrefix = "preview_suggestion",
            noMatchesTestTag = "preview_no_matches",
            modifier = Modifier.padding(Spacing.m),
        )
    }
}

@Preview(name = "Status tags", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun StatusTagPreview() {
    MmoTheme {
        Column(
            modifier = Modifier.padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            CatchRecordStatus.entries.forEach { status ->
                StatusTag(status = status)
            }
        }
    }
}

@Preview(name = "Pagination bar", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun PaginationBarPreview() {
    MmoTheme {
        PaginationBar(
            pageStart = 1,
            pageEnd = 4,
            totalCount = 4,
            onNextClick = {},
            modifier = Modifier.padding(horizontal = Spacing.m),
        )
    }
}

@Preview(name = "Expandable details", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun ExpandableDetailsPreview() {
    MmoTheme {
        ExpandableDetails(
            title = "Create an account",
            modifier = Modifier.padding(Spacing.m),
        ) {
            Text(text = "Ask your vessel owner to add you. They can do this from their account.")
        }
    }
}
