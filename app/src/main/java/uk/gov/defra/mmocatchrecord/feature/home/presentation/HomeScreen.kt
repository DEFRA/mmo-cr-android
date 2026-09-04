package uk.gov.defra.mmocatchrecord.feature.home.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import org.koin.androidx.compose.koinViewModel
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus

/** Compose test tags for [HomeScreen]. */
object HomeScreenTestTags {
    const val SCREEN = "home_feature_screen"
    const val SIGN_OUT_ACTION = "home_feature_sign_out_action"
    const val ERROR_MESSAGE = "home_feature_error_message"
}

/** Home feature screen: shows the signed-in user's summary and a sign-out action. */
@Composable
fun HomeScreen(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(modifier = modifier.fillMaxSize().testTag(HomeScreenTestTags.SCREEN)) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Home",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            when (val status = state.status) {
                UiStatus.Idle, UiStatus.Loading -> CircularProgressIndicator()
                is UiStatus.Content -> {
                    Text(
                        text = "Signed in as ${status.value.signedInUserId}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Pending catch records: ${status.value.pendingCatchRecordCount}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                is UiStatus.Error -> {
                    Text(
                        text = status.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(HomeScreenTestTags.ERROR_MESSAGE),
                    )
                }
            }
            Button(
                onClick = onSignOut,
                modifier =
                    Modifier
                        .testTag(HomeScreenTestTags.SIGN_OUT_ACTION)
                        .heightIn(min = Spacing.minTouchTarget),
            ) {
                Text("Sign out")
            }
        }
    }
}
