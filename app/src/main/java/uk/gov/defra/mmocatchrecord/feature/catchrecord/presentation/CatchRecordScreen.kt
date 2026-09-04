package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import org.koin.androidx.compose.koinViewModel
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.CatchRecord

/** Compose test tags for [CatchRecordScreen]. */
object CatchRecordScreenTestTags {
    const val SCREEN = "catch_record_screen"
    const val SPECIES_FIELD = "catch_record_species_field"
    const val WEIGHT_FIELD = "catch_record_weight_field"
    const val SAVE_ACTION = "catch_record_save_action"
    const val VALIDATION_MESSAGE = "catch_record_validation_message"
}

/** Catch record feature screen: capture a new catch and list previously recorded catches. */
@Composable
fun CatchRecordScreen(
    modifier: Modifier = Modifier,
    viewModel: CatchRecordViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(modifier = modifier.fillMaxSize().testTag(CatchRecordScreenTestTags.SCREEN)) { innerPadding ->
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
                text = "Record a catch",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            CatchRecordForm(state = state, onEvent = viewModel::dispatch)
            if (state.status is UiStatus.Content) {
                CatchRecordList((state.status as UiStatus.Content).value)
            }
        }
    }
}

@Composable
private fun CatchRecordForm(
    state: CatchRecordViewState,
    onEvent: (CatchRecordEvent) -> Unit,
) {
    OutlinedTextField(
        value = state.species,
        onValueChange = { onEvent(CatchRecordEvent.SpeciesChanged(it)) },
        label = { Text("Species") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(CatchRecordScreenTestTags.SPECIES_FIELD),
    )
    OutlinedTextField(
        value = state.weightKgInput,
        onValueChange = { onEvent(CatchRecordEvent.WeightChanged(it)) },
        label = { Text("Weight (kg)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().testTag(CatchRecordScreenTestTags.WEIGHT_FIELD),
    )
    state.validationMessage?.let { message ->
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(CatchRecordScreenTestTags.VALIDATION_MESSAGE),
        )
    }
    Button(
        onClick = { onEvent(CatchRecordEvent.SaveRequested) },
        modifier =
            Modifier
                .testTag(CatchRecordScreenTestTags.SAVE_ACTION)
                .heightIn(min = Spacing.minTouchTarget),
    ) {
        Text("Save catch")
    }
}

@Composable
private fun CatchRecordList(records: List<CatchRecord>) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(records) { record ->
            Text(
                text = "${record.species} — ${record.weightKg}kg",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = Spacing.xxs),
            )
        }
    }
}
