package uk.gov.defra.mmocatchrecord.feature.signin.presentation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.AppLanguageProvider
import uk.gov.defra.mmocatchrecord.common.design.CrownLogo
import uk.gov.defra.mmocatchrecord.common.design.CustomWarningIcon
import uk.gov.defra.mmocatchrecord.common.design.GdsFieldCursorBrush
import uk.gov.defra.mmocatchrecord.common.design.GdsFieldDecorationBox
import uk.gov.defra.mmocatchrecord.common.design.GdsOfflineOverlay
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.common.design.gdsErrorAnnouncement
import uk.gov.defra.mmocatchrecord.common.design.gdsFieldTextStyle
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.core.connectivity.ConnectivityViewModel
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
    const val BROWSER_HANDOFF_MESSAGE = "signin_browser_handoff_message"
    const val INLINE_ERROR_SUFFIX = "inline_error"
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
    connectivityViewModel: ConnectivityViewModel = hiltViewModel(),
    onForgottenPassword: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val currentLanguage by languageViewModel.language.collectAsState()
    val isOffline by connectivityViewModel.isOffline.collectAsState()

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
            onBrowserNoticeShown = { viewModel.dispatch(SignInEvent.BrowserNoticeShown) },
            isOffline = isOffline,
            onRecheckConnection = connectivityViewModel::recheck,
            onForgottenPassword = onForgottenPassword,
            contentPadding = innerPadding,
        )
    }
}

@Composable
@Suppress("LongParameterList")
fun SignInContent(
    state: SignInViewState,
    currentLanguage: String,
    onLanguageToggle: () -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    onBrowserNoticeShown: () -> Unit = {},
    isOffline: Boolean = false,
    onRecheckConnection: () -> Unit = {},
    onForgottenPassword: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
) {
    val scrollState = rememberScrollState()
    Box(modifier = modifier.fillMaxSize()) {
        SignInFormContent(
            state = state,
            currentLanguage = currentLanguage,
            onLanguageToggle = onLanguageToggle,
            onUsernameChanged = onUsernameChanged,
            onPasswordChanged = onPasswordChanged,
            onSubmit = onSubmit,
            onBrowserNoticeShown = onBrowserNoticeShown,
            submitEnabled = !isOffline,
            onForgottenPassword = onForgottenPassword,
            scrollState = scrollState,
            modifier = Modifier.padding(contentPadding),
        )
        if (isOffline) {
            GdsOfflineOverlay(
                actionLabel = stringResource(R.string.signin_title),
                onAction = onRecheckConnection,
            )
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun SignInFormContent(
    state: SignInViewState,
    currentLanguage: String,
    onLanguageToggle: () -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onBrowserNoticeShown: () -> Unit,
    submitEnabled: Boolean,
    onForgottenPassword: () -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
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
            onBrowserNoticeShown = onBrowserNoticeShown,
            submitEnabled = submitEnabled,
        )

        SignInTroubleSection(
            onForgottenPassword = onForgottenPassword,
            forgottenPasswordEnabled = state.status !is UiStatus.Loading,
        )
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
    val credentialsError = state.error?.let { stringResource(it.messageRes) }
    SignInField(
        value = state.username,
        onValueChange = onUsernameChanged,
        inlineError = state.usernameError?.let { stringResource(it) },
        credentialsError = credentialsError,
        label = stringResource(R.string.email_address),
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Next,
        // SC 3.3.8: lets a password manager fill this field instead of recalling it.
        contentType = ContentType.Username,
        testTag = SignInScreenTestTags.USERNAME_FIELD,
    )
    SignInField(
        value = state.password,
        onValueChange = onPasswordChanged,
        inlineError = state.passwordError?.let { stringResource(it) },
        credentialsError = credentialsError,
        label = stringResource(R.string.password),
        keyboardType = KeyboardType.Password,
        imeAction = ImeAction.Done,
        contentType = ContentType.Password,
        testTag = SignInScreenTestTags.PASSWORD_FIELD,
        visualTransformation = PasswordVisualTransformation(),
    )
}

@Composable
@Suppress("LongParameterList")
private fun SignInField(
    value: String,
    onValueChange: (String) -> Unit,
    inlineError: String?,
    credentialsError: String?,
    label: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    contentType: ContentType,
    testTag: String,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val errorAnnouncement = gdsErrorAnnouncement(inlineError ?: credentialsError)
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
        if (inlineError != null) {
            SignInInlineError(text = inlineError, testTag = "${testTag}_${SignInScreenTestTags.INLINE_ERROR_SUFFIX}")
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = visualTransformation,
            textStyle = gdsFieldTextStyle(),
            cursorBrush = GdsFieldCursorBrush,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = imeAction,
                    autoCorrectEnabled = false,
                ),
            decorationBox = { innerTextField ->
                GdsFieldDecorationBox(
                    innerTextField = innerTextField,
                    isEmpty = value.isEmpty(),
                    enabled = true,
                    isError = errorAnnouncement != null,
                )
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(testTag)
                    .semantics {
                        contentDescription = label
                        this.contentType = contentType
                        if (errorAnnouncement != null) error(errorAnnouncement)
                    },
        )
    }
}

/** GOV.UK inline error: icon plus bold red text, announced politely when it appears (SC 4.1.3). */
@Composable
private fun SignInInlineError(
    text: String,
    testTag: String,
) {
    val announcement = gdsErrorAnnouncement(text).orEmpty()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(testTag)
                .clearAndSetSemantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = announcement
                },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        CustomWarningIcon(tint = MmoColors.ErrorRed, modifier = Modifier.size(Spacing.m))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = MmoColors.ErrorRed),
        )
    }
}

@Composable
private fun SignInActionsSection(
    state: SignInViewState,
    onSubmit: () -> Unit,
    onBrowserNoticeShown: () -> Unit = {},
    submitEnabled: Boolean = true,
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
        if (state.isRedirectingToBrowser) {
            // Fires once the notice composes, so the repository knows TalkBack has had a chance to speak it.
            LaunchedEffect(Unit) { onBrowserNoticeShown() }
            Text(
                text = stringResource(R.string.signin_redirecting_to_browser),
                color = MmoColors.Text,
                style = MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier
                        .padding(bottom = Spacing.s)
                        .testTag(SignInScreenTestTags.BROWSER_HANDOFF_MESSAGE)
                        // Announced politely (SC 4.1.3): a context switch to the browser is imminent.
                        .semantics { liveRegion = LiveRegionMode.Polite },
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
            enabled = submitEnabled,
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
