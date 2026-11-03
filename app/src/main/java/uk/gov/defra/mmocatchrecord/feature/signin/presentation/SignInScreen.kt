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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.AppLanguageProvider
import uk.gov.defra.mmocatchrecord.common.design.CrownLogo
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
    const val ACTIONS_GROUP = "signin_actions_group"
    const val LANGUAGE_TOGGLE = "signin_language_toggle"
    const val FORGOTTEN_PASSWORD = "signin_forgotten_password"
}

/**
 * Sign-in feature screen. WCAG 2.2 AA: password-manager content types (SC 3.3.8), a polite live region for
 * errors (SC 4.1.3), text-only error identification (SC 3.3.1) and 48x48dp targets throughout.
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
        val switchLanguageLabel =
            stringResource(
                if (currentLanguage == "en") {
                    R.string.signin_language_switch_to_welsh
                } else {
                    R.string.signin_language_switch_to_english
                },
            )
        Text(
            text = if (currentLanguage == "en") "CYM" else "ENG",
            style =
                MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MmoColors.Text,
                ),
            modifier =
                Modifier
                    .testTag(SignInScreenTestTags.LANGUAGE_TOGGLE)
                    // SC 2.5.3: the accessible name stays "CYM"/"ENG" so speech input can target it.
                    .clickable(onClickLabel = switchLanguageLabel, role = Role.Button) { onLanguageToggle() }
                    .sizeIn(
                        minWidth = Spacing.minTouchTarget,
                        minHeight = Spacing.minTouchTarget,
                    ).wrapContentSize(Alignment.Center),
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
    // Flagged on both fields, never one: singling a field out would leak which value was wrong.
    val isError = state.error != null
    EmailAddressField(value = state.username, onValueChange = onUsernameChanged, isError = isError)
    PasswordField(value = state.password, onValueChange = onPasswordChanged, isError = isError)
}

@Composable
private fun EmailAddressField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
) {
    val label = stringResource(R.string.email_address)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = label,
            style =
                MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Normal,
                    color = MmoColors.Grey1,
                ),
            // Compose has no labelFor, so the label is voiced by the field below, not announced twice.
            modifier = Modifier.clearAndSetSemantics { },
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            isError = isError,
            shape = RectangleShape,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                    autoCorrectEnabled = false,
                ),
            colors = signInFieldColors(),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(SignInScreenTestTags.USERNAME_FIELD)
                    .semantics {
                        contentDescription = label
                        // SC 3.3.8: lets a password manager fill this field instead of recalling it.
                        contentType = ContentType.Username
                    },
        )
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
) {
    val label = stringResource(R.string.password)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = label,
            style =
                MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Normal,
                    color = MmoColors.Grey1,
                ),
            modifier = Modifier.clearAndSetSemantics { },
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            isError = isError,
            visualTransformation = PasswordVisualTransformation(),
            shape = RectangleShape,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    autoCorrectEnabled = false,
                ),
            colors = signInFieldColors(),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(SignInScreenTestTags.PASSWORD_FIELD)
                    .semantics {
                        contentDescription = label
                        contentType = ContentType.Password
                    },
        )
    }
}

@Composable
private fun signInFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MmoColors.Text,
        unfocusedBorderColor = MmoColors.Text,
        focusedContainerColor = MmoColors.White,
        unfocusedContainerColor = MmoColors.White,
        cursorColor = MmoColors.Text,
        focusedTextColor = MmoColors.Text,
        unfocusedTextColor = MmoColors.Text,
    )

@Composable
private fun SignInActionsSection(
    state: SignInViewState,
    onSubmit: () -> Unit,
) {
    val errorPrefix = stringResource(R.string.signin_error_prefix)
    Column(
        modifier =
            Modifier
                .testTag(SignInScreenTestTags.ACTIONS_GROUP)
                .semantics { isTraversalGroup = true },
    ) {
        if (state.error != null) {
            val message = stringResource(state.error.messageRes)
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier
                        .padding(bottom = Spacing.s)
                        .testTag(SignInScreenTestTags.ERROR_MESSAGE)
                        .semantics {
                            // Announced without stealing focus (SC 4.1.3). Natural top-to-bottom traversal
                            // is left intact so the error precedes the submit button (SC 1.3.2, SC 2.4.3).
                            liveRegion = LiveRegionMode.Polite
                            contentDescription = "$errorPrefix $message"
                        },
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
