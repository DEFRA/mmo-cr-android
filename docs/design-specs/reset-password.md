# Design spec — Reset a forgotten password

**Source:** the Jira acceptance criteria (AC1–AC19) and the supplied screenshots. **No Figma link was supplied**
(plan Q14); if one arrives it is read only through the read-only `fetch-figma-design` skill, never the Figma MCP
server. **Decision record:** [ADR 0027](../adr/0027-self-service-password-reset-native-auth.md). This spec
records the copy, states, component mapping and deviations as built (approved plan Q1–Q15 defaults accepted).

Journey: **Sign in → Forgotten password → 1 Email → 2 Reset code → 3 New password → 4 Success → Sign in.**

Common to every screen: `GdsTopAppBar` (Back + CYM/ENG toggle), a heading marked for TalkBack, a pane title,
the error summary above the fields, `PrimaryActionButton` / `SecondaryActionButton`, and the offline notice
when offline. All copy is in `values/` and `values-cy/` (no hard-coded text); error messages resolve on screen
so the CYM toggle re-translates them. Light theme only.

## Copy (English; Welsh is draft)

The Welsh strings in `values-cy/` are **draft**: they must be reviewed by the translation service (Q12) before
release. This is a release gate.

| Key area | English |
| --- | --- |
| Error summary title | There is a problem |
| Inline error prefix (visually hidden) | Error: |
| Offline notice | **You are offline** — Connect to the internet and try again. [Try again] |
| Password toggle | Show / Hide (spoken: "Show new password", "Hide new password") |

### 1. Reset your password (email) — AC1–AC3

- Heading: **Reset your password**
- Guidance: "Enter the email address linked to your account. If we can use it, we'll email you a reset code."
- Field: **Email address** (email keyboard, autocorrect off, email/username autofill)
- Button: **Send reset code**
- Errors: blank → "Enter your email address"; bad format → "Enter an email address in the correct format, like
  name@example.com"
- Account not found is **identical** to code sent (ADR 0027).

### 2. Enter your reset code — AC4–AC10

- Heading: **Enter your reset code**
- Generic message (top of screen, never shows the email, code length or channel): "If the email address you
  entered can be used to reset a password, we've sent a code to it. Check you typed it correctly."
- Hint: "The code is valid for {minutes} minutes." (minutes from packaged config, Q5)
- Field: **Reset code** (number keyboard, paste allowed, **no autofill type** — Compose offers SMS only)
- Buttons: **Continue**; secondary **Send a new code** (returns to a blank email screen — AC9)
- **Open your email app** (Q4): a tap-only secondary button. The app never opens an email app by itself, shows
  a message if the device has no email app, and needs no `<queries>` entry.
- Errors: blank → "Enter the reset code"; invalid → "Enter the correct reset code"; expired → "Your reset code has
  expired. Send a new code."

### 3. Create a new password — AC11–AC16

- Heading: **Create a new password**
- Hint: "Your password must be 8 to 256 characters and include at least 3 of: lower case letters, upper case
  letters, numbers, symbols."
- Fields: **New password**, **Confirm new password**, each with its own Show/Hide toggle (AC12)
- Button: **Save new password**
- Errors: blank → "Enter a new password" / "Confirm your new password"; rule failure → "Your password does not
  meet the password requirements"; mismatch → "The passwords do not match"; Entra rejection → same requirements
  message.
- Back → "Cancel resetting your password?" dialog (Q8). `FLAG_SECURE` on this screen only (Q11).
- Both fields are cleared on a page-level failure (e.g. network) but kept when the password is only rejected.
- Entra reports the session expired here → back to a blank email screen with the page error "Your reset has
  expired. Enter your email address to start again."; all typed values are wiped.

### 4. Your password has been reset — AC17

- Heading/banner: **Your password has been reset**
- Button: **Sign in** (returns to Sign in, clearing the reset graph; system Back does the same).
- No automatic sign-in.

### Offline — AC18

`GdsOfflineNotice` on Sign in and each reset screen: title "You are offline", text "Connect to the internet and
try again.", action **"Try again"** (deviation D8: re-checks the connection and dismisses the notice; it also
clears itself when the connection returns). Submit actions are blocked while offline.

### Failures — AC19

Network → "We could not reach the service. Check your connection and try again." (step, token and non-secret
values kept). Throttled → "Wait a few minutes and try again." Unknown → generic error, no step change. Browser
required → hand-off message then the system browser.

## Component mapping

| Need | Component | Notes |
| --- | --- | --- |
| Email, reset code | `GdsTextInput` (new) | Label, hint, inline error with hidden "Error:" prefix, error semantics, 48dp target, `FocusRequester` for the summary link |
| New / confirm password | `GdsPasswordInput` (new) | `OutlinedSecureTextField`; independent toggle per field (role Button, visible "Show"/"Hide", own spoken name, polite state announcement) |
| Validation summary | `GdsErrorSummary` (new) | Title, one link per error that focuses its field, focus moves to the summary when it appears, announced |
| AC18 | `GdsOfflineNotice` (new) | Blue GOV.UK notification style, polite live region, pane title |
| Screenshot block | `SecureScreen` (new) | `FLAG_SECURE` on enter, cleared on leave |
| Buttons, top bar, dialog | `PrimaryActionButton`, `SecondaryActionButton`, `GdsTopAppBar`, `UnsavedChangesDialog` style | Existing |

## Deviations record (R7 / Q3) — for governance (Delivery Architecture)

| # | Deviation | Why | Standard it departs from |
| --- | --- | --- | --- |
| D1 | A **confirm-password** field is built | The AC and design require it (AC11, AC14) | GOV.UK passwords pattern: a second field doesn't help users |
| D2 | The design's **separate "uppercase / number / special character" errors are replaced by Entra's 3-of-4 rule** | Entra accepts any 3 of 4 categories; must-have-all rules would reject valid passwords and conflict with GOV.UK/NCSC advice against complexity rules | Design / AC13 wording |
| D3 | A **greyed-out offline overlay** is replaced by an inline `GdsOfflineNotice` that blocks actions | An overlay hides the screen from assistive tech and breaks reading order (WCAG 1.3.2, 4.1.3) | Design |
| D4 | The design's **"already used" code message is dropped** | MSAL V1 cannot detect a used code, and a code can't be reused in-app | AC8 variant of the design |
| D5 | The generic message replaces any "we sent a code to *{email}*" wording; `sentTo` / code length never shown | Prevents account disclosure (AC3) | Design |
| D6 | Email field is cleared as soon as the code is sent (and on "Send a new code") | AC9 security requirement; may conflict with WCAG 3.3.7 (Redundant Entry) — autofill still works | WCAG 2.2 3.3.7 (recorded exception) |
| D7 | Code lifetime stated as fixed text, no countdown | Avoids a live timer; "Send a new code" always works | WCAG 2.2 2.2.1 (to include in the formal audit) |
| D8 | Offline notice action is "Try again" (re-checks the connection) on Sign in and every reset screen | The spec's "Sign in" action would leave the reset flow; re-checking is the useful recovery | None - to confirm with Delivery Architecture |

Accessibility and security override the design where they conflict. Open items for the identity team: real code
lifetime (Q5) and reset email ownership (Q6) are Entra-side.
