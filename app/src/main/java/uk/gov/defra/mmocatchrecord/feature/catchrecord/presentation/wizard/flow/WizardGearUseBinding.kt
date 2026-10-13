package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse

/** Forward-flow route for the per-gear species steps, bound to [gearUseId]; other steps use [routeFor]. */
fun gearRouteFor(
    step: WizardStep,
    gearUseId: String,
): Any =
    when (step) {
        WizardStep.GearSpeciesSearch -> GearSpeciesSearchRoute(gearUseId = gearUseId)
        WizardStep.GearSpeciesChecklist -> GearSpeciesChecklistRoute(gearUseId = gearUseId)
        else -> routeFor(step)
    }

/** The gear a per-gear step shows: [editGearUseId] or [boundGearUseId] if set (authoritative, never
 *  retargeted), else the next gear still pending this step. */
fun resolveWizardGearUse(
    draft: CatchRecordDraft?,
    editGearUseId: String?,
    boundGearUseId: String?,
    nextPending: (CatchRecordDraft) -> GearUse?,
): GearUse? {
    val explicitId = editGearUseId ?: boundGearUseId
    return if (explicitId != null) {
        draft?.gearUses?.firstOrNull { it.id == explicitId }
    } else {
        draft?.let(nextPending)
    }
}

/** Pins the first resolved gear to this back-stack entry so navigating Back here after it is completed
 *  still shows that gear (with its saved answers) rather than an error. */
@Composable
fun rememberWizardGearUse(
    draft: CatchRecordDraft?,
    editGearUseId: String?,
    initialGearUseId: String?,
    nextPending: (CatchRecordDraft) -> GearUse?,
): GearUse? {
    var boundGearUseId by rememberSaveable { mutableStateOf(initialGearUseId) }
    val gearUse = resolveWizardGearUse(draft, editGearUseId, boundGearUseId, nextPending)
    val resolvedId = gearUse?.id
    LaunchedEffect(resolvedId) {
        if (editGearUseId == null && boundGearUseId == null && resolvedId != null) boundGearUseId = resolvedId
    }
    return gearUse
}
