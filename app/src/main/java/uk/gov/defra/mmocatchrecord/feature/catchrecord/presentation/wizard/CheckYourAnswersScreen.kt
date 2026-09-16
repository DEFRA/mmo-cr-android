@file:Suppress("detekt.FunctionNaming", "detekt.LongMethod", "detekt.LongParameterList", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.GdsInsetText
import uk.gov.defra.mmocatchrecord.common.design.GdsLinkAction
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Vessel

object CheckYourAnswersScreenTestTags {
    const val SCREEN = "check_your_answers_screen"
    const val ROW_PREFIX = "check_your_answers_row"
    const val CHANGE_ACTION_PREFIX = "check_your_answers_change_action"
    const val DECLARATION_PANEL = "check_your_answers_declaration_panel"
    const val SUBMIT_ACTION = "check_your_answers_submit_action"
    const val ERROR_MESSAGE = "check_your_answers_error_message"
}

/**
 * Phase 8, screen 2 — the GDS "check your answers" review screen. Section/row data is built by the pure
 * [CheckYourAnswersSupport.buildSections] (unit-tested independently); this composable is purely a
 * presentational renderer over that data plus the declaration panel and "Accept and submit trip details"
 * action, which dispatches [CatchRecordFlowEvent.AcceptDeclarationAndSubmit] — the ViewModel owns the
 * online/offline submission branching (see [CatchRecordFlowViewModel.acceptDeclarationAndSubmit]).
 *
 * TODO(Phase 6/7): once landing-storage capture exists, its section slots into the list returned by
 * [CheckYourAnswersSupport.buildSections] — no rework needed here, since this renderer already iterates an
 * arbitrary list of sections rather than hardcoding one layout per section kind.
 */
@Suppress("FunctionNaming")
@Composable
fun CheckYourAnswersScreen(
    viewModel: CatchRecordFlowViewModel,
    onNavigate: (WizardStep) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // "Accept and submit trip details" is a genuinely asynchronous action (connectivity check + stub
    // submit call — see `CatchRecordFlowViewModel.acceptDeclarationAndSubmit`): unlike every earlier
    // wizard screen, this screen cannot compute its next step synchronously in the click handler, since
    // the destination (SubmissionSuccess vs SubmissionPendingSync) depends on that async result. So,
    // mirroring `CatchRecordFlowEntryScreen`'s own "react to a ViewModel-driven step change" pattern, this
    // effect is what actually performs the navigation once the ViewModel has decided the outcome and
    // updated `state.currentStep` — without it, tapping submit only updates ViewModel state and never
    // navigates anywhere.
    var hasNavigatedToSubmissionResult by remember { mutableStateOf(false) }
    LaunchedEffect(state.currentStep) {
        val isSubmissionResultStep =
            state.currentStep == WizardStep.SubmissionSuccess || state.currentStep == WizardStep.SubmissionPendingSync
        if (isSubmissionResultStep && !hasNavigatedToSubmissionResult) {
            hasNavigatedToSubmissionResult = true
            onNavigate(state.currentStep)
        }
    }

    CatchRecordWizardScaffold(
        screenTestTag = CheckYourAnswersScreenTestTags.SCREEN,
        title = stringResource(R.string.check_your_answers_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        when (val status = state.status) {
            UiStatus.Idle, UiStatus.Loading -> WizardLoadingState()
            is UiStatus.Error -> WizardErrorState(status.message, CheckYourAnswersScreenTestTags.ERROR_MESSAGE)
            is UiStatus.Content ->
                CheckYourAnswersScreenContent(
                    draft = status.value,
                    vessels = state.vessels,
                    ports = state.ports,
                    gearTypes = state.gearTypes,
                    species = state.species,
                    onChangeRow = { onNavigate(it) },
                    onSubmit = { viewModel.dispatch(CatchRecordFlowEvent.AcceptDeclarationAndSubmit) },
                )
        }
    }
}

@Composable
fun CheckYourAnswersScreenContent(
    draft: CatchRecordDraft,
    vessels: List<Vessel>,
    ports: List<Port>,
    gearTypes: List<GearType>,
    species: List<Species>,
    onChangeRow: (WizardStep) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sections = CheckYourAnswersSupport.buildSections(draft, vessels, ports, gearTypes, species)
    val sectionsByKind = sections.groupBy { it.kind }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        sectionsByKind.forEach { (kind, sectionsForKind) ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(
                    text = stringResource(sectionHeaderRes(kind)),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.semantics { heading() },
                )
                sectionsForKind.forEach { section ->
                    section.heading?.let {
                        Text(text = it, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    section.rows.forEach { row -> CheckYourAnswersRowView(row, onChangeRow) }
                    HorizontalDivider()
                }
            }
        }

        DeclarationPanel()

        PrimaryActionButton(
            text = stringResource(R.string.check_your_answers_submit_action),
            onClick = onSubmit,
            modifier = Modifier.testTag(CheckYourAnswersScreenTestTags.SUBMIT_ACTION),
        )
    }
}

@Composable
private fun CheckYourAnswersRowView(
    row: CheckYourAnswersRow,
    onChangeRow: (WizardStep) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = row.dynamicLabel ?: stringResource(rowLabelRes(row.kind))
    val displayValue =
        if (row.kind == CheckYourAnswersFieldKind.NotLandedStraightAway) stringResource(R.string.yes) else row.value
    Row(
        modifier = modifier.fillMaxWidth().testTag("${CheckYourAnswersScreenTestTags.ROW_PREFIX}_${row.kind}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
            Text(text = displayValue, style = MaterialTheme.typography.bodyLarge)
        }
        GdsLinkAction(
            text = stringResource(R.string.check_your_answers_change_action),
            onClick = { onChangeRow(row.changeStep) },
            testTag = "${CheckYourAnswersScreenTestTags.CHANGE_ACTION_PREFIX}_${row.kind}",
            accessibleLabel = stringResource(R.string.check_your_answers_change_action_with_label, label),
        )
    }
}

/**
 * GDS "declaration" panel: an icon + bold intro + bullet list, conveying its meaning via icon **and** text
 * together (see [uk.gov.defra.mmocatchrecord.common.design.GdsInsetText] — not colour alone), per WCAG 2.2
 * AA.
 */
@Composable
private fun DeclarationPanel(modifier: Modifier = Modifier) {
    GdsInsetText(modifier = modifier.testTag(CheckYourAnswersScreenTestTags.DECLARATION_PANEL)) {
        Text(
            text = stringResource(R.string.check_your_answers_declaration_intro),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
        )
        listOf(
            R.string.check_your_answers_declaration_bullet_weight_accurate,
            R.string.check_your_answers_declaration_bullet_tolerance,
            R.string.check_your_answers_declaration_bullet_action,
        ).forEach { bulletRes ->
            Row {
                Text(text = "• ", style = MaterialTheme.typography.bodyLarge)
                Text(text = stringResource(bulletRes), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun sectionHeaderRes(kind: CheckYourAnswersSectionKind): Int =
    when (kind) {
        CheckYourAnswersSectionKind.Trip -> R.string.check_your_answers_section_trip
        CheckYourAnswersSectionKind.GearUsed -> R.string.check_your_answers_section_gear_used
        CheckYourAnswersSectionKind.SpeciesCaught -> R.string.check_your_answers_section_species_caught
        CheckYourAnswersSectionKind.SpeciesNotLanded -> R.string.check_your_answers_section_species_not_landed
    }

@Suppress("CyclomaticComplexMethod")
private fun rowLabelRes(kind: CheckYourAnswersFieldKind): Int =
    when (kind) {
        CheckYourAnswersFieldKind.Vessel -> R.string.check_your_answers_row_vessel
        CheckYourAnswersFieldKind.DepartureDate -> R.string.check_your_answers_row_departure_date
        CheckYourAnswersFieldKind.ReturnDate -> R.string.check_your_answers_row_return_date
        CheckYourAnswersFieldKind.DeparturePort -> R.string.check_your_answers_row_departure_port
        CheckYourAnswersFieldKind.ReturnPort -> R.string.check_your_answers_row_return_port
        CheckYourAnswersFieldKind.StatisticalSubArea -> R.string.check_your_answers_row_statistical_sub_area
        CheckYourAnswersFieldKind.GearType -> R.string.check_your_answers_row_gear_type
        CheckYourAnswersFieldKind.TimesShot -> R.string.check_your_answers_row_times_shot
        CheckYourAnswersFieldKind.Measurement -> R.string.check_your_answers_row_gear_type // overridden by dynamicLabel
        CheckYourAnswersFieldKind.Species -> R.string.check_your_answers_row_species
        CheckYourAnswersFieldKind.WeightAboveMinimumSize -> R.string.check_your_answers_row_weight_above_minimum_size
        CheckYourAnswersFieldKind.WeightBelowMinimumSize -> R.string.check_your_answers_row_weight_below_minimum_size
        CheckYourAnswersFieldKind.WeightLegallyDiscarded -> R.string.check_your_answers_row_weight_legally_discarded
        CheckYourAnswersFieldKind.NotLandedStraightAway -> R.string.check_your_answers_row_not_landed_straight_away
        CheckYourAnswersFieldKind.WeightKeptOnboard -> R.string.check_your_answers_row_weight_kept_onboard
    }
