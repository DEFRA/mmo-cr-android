---
description: "Accessibility standards (WCAG 2.2 AA, DEFRA/GDS legal requirement, Material Design 3) for the MMO Catch Recording Android app. Use when building UI, reviewing Compose UI, or auditing TalkBack, font scaling, contrast, touch targets and assistive technology support."
applyTo: "**/*.kt"
---

# Accessibility standards (legal requirement)

Meeting accessibility is a **legal requirement** for DEFRA services under the Public Sector Bodies
(Websites and Mobile Applications) Accessibility Regulations 2018 and the Equality Act 2010. The app
**must meet [WCAG 2.2 level AA](https://www.gov.uk/service-manual/helping-people-to-use-your-service/understanding-wcag)**
and work with common assistive technologies. This applies even to staff-facing/internal apps.

References: [DEFRA accessibility](https://digital.defra.gov.uk/accessibility) ·
[GDS testing for accessibility](https://www.gov.uk/service-manual/technology/testing-for-accessibility) ·
[Android accessibility principles](https://developer.android.com/guide/topics/ui/accessibility/principles) ·
[Compose accessibility](https://developer.android.com/develop/ui/compose/accessibility).

## Build accessibility in from the start

Consider it at every stage (design → code → test), not as a retrofit. Fixing late is far more expensive.

## Vision

- **Font scaling:** Use scalable `sp` units for text and support enlargement to at least 200% (system
  font-size and display-size settings). Never hard-code `dp`/pixel font sizes that cannot scale. Test at
  the largest accessibility font scales and ensure layouts reflow (scrollable containers, avoid
  truncation/clipping).
- **Contrast:** Meet WCAG AA — **4.5:1** for normal text, **3:1** for large (≥18sp or bold ≥14sp) text
  and meaningful UI/graphics. Prefer Material 3 theme/semantic colours that adapt to dark theme and
  high-contrast/inverted settings.
- **Don't rely on colour alone:** Pair colour with text, icon or shape to convey state (e.g. error =
  red + icon + message).

## TalkBack & assistive tech

- Give every meaningful control an accessibility label via `contentDescription` (or a `semantics { }`
  block with `contentDescription`/`text`); add `stateDescription`, `role` and action labels
  (`onClickLabel`/`onLongClickLabel`) where needed.
- Group related elements with `Modifier.semantics(mergeDescendants = true)`; mark decorative images with
  `contentDescription = null` and purely decorative elements with `hideFromAccessibility`.
- Mark headings with `Modifier.semantics { heading() }`; give distinct panes descriptive `paneTitle`s.
- Ensure a logical focus/traversal order and that custom components announce their role and state (use
  `triStateToggleable`/`toggleable`/`selectable` or explicit `semantics`).
- Support TalkBack, Switch Access, Voice Access (label elements so "tap <label>" works) and external
  keyboards.

## Mobility

- **Touch targets ≥ 48×48 dp** with adequate spacing (`minimumInteractiveComponentSize`/padding to avoid
  mis-taps).
- Offer non-gesture alternatives to every gesture (e.g. a visible button as well as swipe-to-dismiss) via
  `customActions` in `semantics`.
- Avoid custom multi-finger gestures for core actions.

## Cognitive & motion

- Keep flows simple and consistent; break multi-step tasks into single-purpose screens.
- Avoid time-boxed auto-dismissing UI; prefer explicit dismissal.
- Respect **Reduce Motion / animation scale** (check `Settings.Global.ANIMATOR_DURATION_SCALE`) — replace
  large animations with fades; avoid parallax/zoom when reduced.
- Confirm destructive/irreversible actions.

## Compose checklist

- [ ] All interactive elements have labels/roles and are reachable by TalkBack.
- [ ] Layout works with the largest font scale and display size without clipping.
- [ ] Contrast meets AA in light and dark theme.
- [ ] No information conveyed by colour alone.
- [ ] Touch targets ≥ 48×48 dp.
- [ ] Reduced animation scale respected.
- [ ] Errors are perceivable (text + icon), announced, and describe how to fix.

## Testing (do both automated and manual)

- **Automated:** Accessibility Scanner and the
  [Accessibility Test Framework](https://github.com/google/Accessibility-Test-Framework-for-Android);
  Compose UI-test / Espresso assertions on `contentDescription`/`testTag`/semantics; enable
  `AccessibilityChecks` in instrumented tests.
- **Manual:** Navigate the whole app with TalkBack; test at max font scale and display size; test dark
  theme, high contrast, reduced animations, Voice Access and Switch Access.
- Get a **formal accessibility audit and fix issues before public beta**, and publish an accessibility
  statement. See the [android-accessibility-audit skill](../skills/android-accessibility-audit/SKILL.md).
