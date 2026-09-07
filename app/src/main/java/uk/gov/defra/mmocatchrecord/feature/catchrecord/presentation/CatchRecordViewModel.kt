package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import uk.gov.defra.mmocatchrecord.core.architecture.BaseViewModel
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.GetCatchRecordsUseCase
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.SaveCatchRecordUseCase
import java.util.UUID
import javax.inject.Inject

/** ViewModel for the catch record feature: capture a new catch and list previous entries. */
@HiltViewModel
class CatchRecordViewModel
    @Inject
    constructor(
        private val saveCatchRecordUseCase: SaveCatchRecordUseCase,
        private val getCatchRecordsUseCase: GetCatchRecordsUseCase,
        private val idFactory: () -> String = { UUID.randomUUID().toString() },
        defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) : BaseViewModel<CatchRecordViewState, CatchRecordEvent>(
        initialState = CatchRecordViewState(),
        defaultDispatcher = defaultDispatcher,
    ) {
    init {
        dispatch(CatchRecordEvent.Load)
    }

    override fun dispatch(event: CatchRecordEvent) {
        when (event) {
            CatchRecordEvent.Load -> load()
            is CatchRecordEvent.SpeciesChanged -> updateState { it.copy(species = event.value) }
            is CatchRecordEvent.WeightChanged -> updateState { it.copy(weightKgInput = event.value) }
            CatchRecordEvent.SaveRequested -> save()
        }
    }

    private fun load() {
        updateState { it.copy(status = UiStatus.Loading) }
        launchInViewModelScope { refreshRecords() }
    }

    private fun save() {
        val species = currentState.species
        val weightKg = currentState.weightKgInput.toDoubleOrNull()

        if (weightKg == null) {
            updateState { it.copy(validationMessage = "Weight must be a number greater than zero") }
            return
        }

        launchInViewModelScope {
            val result = saveCatchRecordUseCase(idFactory(), species, weightKg)
            result.fold(
                onSuccess = {
                    // Clear the form fields and enter Loading in a single emission, otherwise the
                    // field-clearing update and the Loading transition would be observed as two
                    // separate state emissions with a stale (pre-refresh) status in between.
                    updateState {
                        it.copy(
                            species = "",
                            weightKgInput = "",
                            validationMessage = null,
                            status = UiStatus.Loading,
                        )
                    }
                    refreshRecords()
                },
                onFailure = { error ->
                    updateState { it.copy(validationMessage = error.message ?: "Unable to save catch record") }
                },
            )
        }
    }

    private suspend fun refreshRecords() {
        val result = getCatchRecordsUseCase()
        result.fold(
            onSuccess = { records -> updateState { it.copy(status = UiStatus.Content(records)) } },
            onFailure = { error ->
                updateState {
                    it.copy(
                        status =
                            UiStatus.Error(
                                message = error.message ?: "Unable to load catch records.",
                                isRetryable = true,
                            ),
                    )
                }
            },
        )
    }
}
