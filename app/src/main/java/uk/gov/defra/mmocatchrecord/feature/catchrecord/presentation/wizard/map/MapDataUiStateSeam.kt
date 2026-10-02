package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Optional explicit [MapDataUiState] for [MapScreen]'s Hilt-backed overload. `null` (the default, and
 * always the case in the running app) resolves it from the real Hilt-backed [MapDataViewModel] instead —
 * see [rememberMapDataUiState]. Mirrors
 * [uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.LocalWizardScaffoldState]:
 * JVM/Robolectric unit tests that compose the Hilt-backed [MapScreen] overload without a real Hilt
 * component provide a fixed state here instead, since `hiltViewModel()` requires a real Hilt component.
 */
val LocalMapDataUiState = staticCompositionLocalOf<MapDataUiState?> { null }

/**
 * Resolves [MapDataUiState] from [LocalMapDataUiState] when provided (tests), otherwise from the real
 * Hilt-backed [MapDataViewModel] (always the case in the running app) — see [LocalMapDataUiState].
 */
@Composable
internal fun rememberMapDataUiState(): MapDataUiState {
    LocalMapDataUiState.current?.let { return it }
    val mapDataViewModel: MapDataViewModel = hiltViewModel()
    val mapStatus by mapDataViewModel.status.collectAsStateWithLifecycle()
    return MapDataUiState(mapStatus, mapDataViewModel::retry)
}
