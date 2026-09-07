package uk.gov.defra.mmocatchrecord.feature.signin.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus

/** Compose test tags for [SignInScreen], kept in one place so tests don't hard-code strings. */
object SignInScreenTestTags {
    const val SCREEN = "signin_screen"
    const val USERNAME_FIELD = "signin_username_field"
    const val PASSWORD_FIELD = "signin_password_field"
    const val SUBMIT_ACTION = "signin_submit_action"
    const val ERROR_MESSAGE = "signin_error_message"
}

/**
 * Sign-in feature screen. Accessible: heading semantics on the title, labelled text fields, an explicit
 * error state conveyed by text (never colour alone), and a 48x48dp minimum touch target on the submit
 * action.
 */
@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    if (state.status is UiStatus.Content) {
        onSignedIn()
    }

    Scaffold(modifier = modifier.fillMaxSize().testTag(SignInScreenTestTags.SCREEN)) { innerPadding ->
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
                text = "Sign in",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            OutlinedTextField(
                value = state.username,
                onValueChange = { viewModel.dispatch(SignInEvent.UsernameChanged(it)) },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(SignInScreenTestTags.USERNAME_FIELD),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = { viewModel.dispatch(SignInEvent.PasswordChanged(it)) },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().testTag(SignInScreenTestTags.PASSWORD_FIELD),
            )
            if (state.status is UiStatus.Error) {
                Text(
                    text = (state.status as UiStatus.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(SignInScreenTestTags.ERROR_MESSAGE),
                )
            }
            if (state.status is UiStatus.Loading) {
                CircularProgressIndicator()
            }
            Button(
                onClick = { viewModel.dispatch(SignInEvent.SubmitRequested) },
                modifier =
                    Modifier
                        .testTag(SignInScreenTestTags.SUBMIT_ACTION)
                        .heightIn(min = Spacing.minTouchTarget),
            ) {
                Text("Sign in")
            }
        }
    }
}
