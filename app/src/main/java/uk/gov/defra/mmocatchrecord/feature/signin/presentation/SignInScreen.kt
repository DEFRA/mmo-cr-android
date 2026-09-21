package uk.gov.defra.mmocatchrecord.feature.signin.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.AppLanguageProvider
import uk.gov.defra.mmocatchrecord.common.design.CrownLogo
import uk.gov.defra.mmocatchrecord.common.design.ExpandableDetails
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.core.language.AppLanguageViewModel

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
    languageViewModel: AppLanguageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val currentLanguage by languageViewModel.language.collectAsState()

    if (state.status is UiStatus.Content) {
        onSignedIn()
    }

    AppLanguageProvider(language = currentLanguage) {
        Scaffold(
            modifier =
                modifier
                    .fillMaxSize()
                    .testTag(SignInScreenTestTags.SCREEN),
        ) { innerPadding ->
            SignInContent(
                state = state,
                currentLanguage = currentLanguage,
                onLanguageToggle = languageViewModel::toggleLanguage,
                onUsernameChanged = { viewModel.dispatch(SignInEvent.UsernameChanged(it)) },
                onPasswordChanged = { viewModel.dispatch(SignInEvent.PasswordChanged(it)) },
                onSubmit = { viewModel.dispatch(SignInEvent.SubmitRequested) },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
fun SignInContent(
    state: SignInViewState,
    currentLanguage: String,
    onLanguageToggle: () -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .background(MmoColors.White)
                .padding(horizontal = Spacing.m, vertical = Spacing.s),
        horizontalAlignment = Alignment.Start,
    ) {
        SignInHeaderSection(
            currentLanguage = currentLanguage,
            onLanguageToggle = onLanguageToggle,
        )

        SignInFieldsSection(
            state = state,
            onUsernameChanged = onUsernameChanged,
            onPasswordChanged = onPasswordChanged,
        )

        SignInActionsSection(
            state = state,
            onSubmit = onSubmit,
        )

        SignInTroubleSection()
    }
}

@Composable
private fun SignInHeaderSection(
    currentLanguage: String,
    onLanguageToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = if (currentLanguage == "en") "CYM" else "ENG",
            style =
                MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MmoColors.Text,
                ),
            modifier =
                Modifier
                    .clickable { onLanguageToggle() }
                    .padding(vertical = Spacing.xs),
        )
    }

    Spacer(modifier = Modifier.height(Spacing.l))

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        CrownLogo(modifier = Modifier.padding(bottom = Spacing.m))
    }

    Text(
        text = stringResource(R.string.signin_title),
        style =
            MaterialTheme.typography.headlineLarge.copy(
                color = MmoColors.Text,
                fontWeight = FontWeight.Bold,
            ),
        modifier =
            Modifier
                .semantics { heading() }
                .padding(top = Spacing.s, bottom = Spacing.m),
    )
}

@Composable
private fun SignInFieldsSection(
    state: SignInViewState,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
) {
    EmailAddressField(value = state.username, onValueChange = onUsernameChanged)
    PasswordField(value = state.password, onValueChange = onPasswordChanged)
}

@Composable
private fun EmailAddressField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.email_address),
            style =
                MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Normal,
                    color = MmoColors.Grey1,
                ),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            shape = RectangleShape,
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MmoColors.Text,
                    unfocusedBorderColor = MmoColors.Text,
                    focusedContainerColor = MmoColors.White,
                    unfocusedContainerColor = MmoColors.White,
                    cursorColor = MmoColors.Text,
                    focusedTextColor = MmoColors.Text,
                    unfocusedTextColor = MmoColors.Text,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(SignInScreenTestTags.USERNAME_FIELD),
        )
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.password),
            style =
                MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Normal,
                    color = MmoColors.Grey1,
                ),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = RectangleShape,
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MmoColors.Text,
                    unfocusedBorderColor = MmoColors.Text,
                    focusedContainerColor = MmoColors.White,
                    unfocusedContainerColor = MmoColors.White,
                    cursorColor = MmoColors.Text,
                    focusedTextColor = MmoColors.Text,
                    unfocusedTextColor = MmoColors.Text,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(SignInScreenTestTags.PASSWORD_FIELD),
        )
    }
}

@Composable
private fun SignInActionsSection(
    state: SignInViewState,
    onSubmit: () -> Unit,
) {
    if (state.status is UiStatus.Error) {
        Text(
            text = state.status.message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier =
                Modifier
                    .padding(bottom = Spacing.s)
                    .testTag(SignInScreenTestTags.ERROR_MESSAGE),
        )
    }
    if (state.status is UiStatus.Loading) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.s),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = MmoColors.GovBlue)
        }
    }

    PrimaryActionButton(
        text = stringResource(R.string.signin_title),
        onClick = onSubmit,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.l)
                .testTag(SignInScreenTestTags.SUBMIT_ACTION),
    )
}

@Composable
private fun SignInTroubleSection() {
    Text(
        text = stringResource(R.string.having_trouble_signing_in),
        style =
            MaterialTheme.typography.titleLarge.copy(
                color = MmoColors.Text,
                fontWeight = FontWeight.Bold,
            ),
        modifier = Modifier.padding(bottom = Spacing.s),
    )

    Text(
        text = stringResource(R.string.forgotten_your_password),
        style =
            MaterialTheme.typography.bodyLarge.copy(
                color = MmoColors.Link,
                textDecoration = TextDecoration.Underline,
            ),
        modifier =
            Modifier
                .clickable { /* Web workflow in later stages */ }
                .padding(bottom = Spacing.l),
    )

    ExpandableDetails(
        title = stringResource(R.string.create_an_account),
        modifier = Modifier.padding(bottom = Spacing.xl),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                text = stringResource(R.string.vessel_owner_label),
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MmoColors.Text,
                    ),
            )
            Text(
                text = stringResource(R.string.vessel_owner_text),
                style = MaterialTheme.typography.bodyMedium.copy(color = MmoColors.Text),
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.skipper_or_agent_label),
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MmoColors.Text,
                    ),
            )
            Text(
                text = stringResource(R.string.skipper_or_agent_text),
                style = MaterialTheme.typography.bodyMedium.copy(color = MmoColors.Text),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun SignInScreenPreview() {
    val state =
        SignInViewState(
            username = "james.wilson@company.co.uk",
            password = "",
        )
    MmoTheme {
        AppLanguageProvider(language = "en") {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MmoColors.White),
            ) {
                SignInContent(
                    state = state,
                    currentLanguage = "en",
                    onLanguageToggle = {},
                    onUsernameChanged = {},
                    onPasswordChanged = {},
                    onSubmit = {},
                )
            }
        }
    }
}
