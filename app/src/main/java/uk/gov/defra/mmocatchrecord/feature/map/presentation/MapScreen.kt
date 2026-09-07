package uk.gov.defra.mmocatchrecord.feature.map.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus

/** Compose test tags for [MapScreen]. */
object MapScreenTestTags {
    const val SCREEN = "map_screen"
    const val ERROR_MESSAGE = "map_error_message"
    const val EMPTY_MESSAGE = "map_empty_message"
}

/**
 * Map feature screen. **Placeholder (list-based) rendering only** — no map SDK/rendering engine
 * (e.g. Google Maps Compose) has been added yet; that choice requires its own tech-stack confirmation
 * before a real map view is built. This screen lists catch locations accessibly in the meantime.
 */
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(modifier = modifier.fillMaxSize().testTag(MapScreenTestTags.SCREEN)) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Catch locations",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            when (val status = state.status) {
                UiStatus.Idle, UiStatus.Loading -> CircularProgressIndicator()
                is UiStatus.Content -> {
                    if (status.value.isEmpty()) {
                        Text(
                            text = "No catch locations recorded yet.",
                            modifier = Modifier.testTag(MapScreenTestTags.EMPTY_MESSAGE),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(status.value) { location ->
                                Text(
                                    text = "${location.label} (${location.latitude}, ${location.longitude})",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                }
                is UiStatus.Error -> {
                    Text(
                        text = status.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(MapScreenTestTags.ERROR_MESSAGE),
                    )
                }
            }
        }
    }
}
