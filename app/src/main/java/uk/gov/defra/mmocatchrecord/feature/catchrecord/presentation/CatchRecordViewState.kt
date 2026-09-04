package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation

import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.core.architecture.ViewState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.CatchRecord

/** Immutable UI state for [CatchRecordViewModel]. */
data class CatchRecordViewState(
    val species: String = "",
    val weightKgInput: String = "",
    val validationMessage: String? = null,
    val status: UiStatus<List<CatchRecord>> = UiStatus.Idle,
) : ViewState

/** UI-originated events for the catch record screen. */
sealed interface CatchRecordEvent {
    data object Load : CatchRecordEvent

    data class SpeciesChanged(
        val value: String,
    ) : CatchRecordEvent

    data class WeightChanged(
        val value: String,
    ) : CatchRecordEvent

    data object SaveRequested : CatchRecordEvent
}
