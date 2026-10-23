package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate

/** Drives a wheel the way TalkBack does (SetProgress), so tests don't depend on fling physics. */
fun SemanticsNodeInteractionsProvider.setWheel(
    tag: String,
    value: Int,
) {
    onNodeWithTag(tag).performSemanticsAction(SemanticsActions.SetProgress) { it(value.toFloat()) }
}

/** Year then month then day, because the day wheel is clamped to the selected month's length. */
fun SemanticsNodeInteractionsProvider.spinTo(
    tags: WizardDateStepTestTags,
    date: DmyDate,
) {
    setWheel(tags.yearWheel, date.year)
    setWheel(tags.monthWheel, date.month)
    setWheel(tags.dayWheel, date.day)
}

fun SemanticsNodeInteractionsProvider.spinToAndConfirm(
    tags: WizardDateStepTestTags,
    date: DmyDate,
) {
    spinTo(tags, date)
    onNodeWithTag(tags.okAction).performSemanticsAction(SemanticsActions.OnClick)
}

fun SemanticsNodeInteractionsProvider.saveAndContinue(tags: WizardDateStepTestTags) {
    onNodeWithTag(tags.saveAction).performSemanticsAction(SemanticsActions.OnClick)
}
