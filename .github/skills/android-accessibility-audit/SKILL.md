---
name: android-accessibility-audit
description: "Audit and validate the MMO Catch Recording Android app against WCAG 2.2 AA and Android accessibility guidance (a DEFRA legal requirement). Use to review Jetpack Compose UI for TalkBack, font scaling, contrast, touch targets, reduced motion and assistive-technology support, and to prepare for a formal audit before public beta."
argument-hint: "e.g. 'audit the Catch Recording screen for accessibility'"
user-invocable: false
---

# Android accessibility audit

Accessibility is a **legal requirement** for DEFRA services (WCAG 2.2 AA). Use this skill to check code
and running builds, following the [accessibility instructions](../../instructions/accessibility.instructions.md).

## When to use
- Reviewing a new/changed Compose screen or component.
- Preparing for the **formal accessibility audit required before public beta**.
- Diagnosing a reported accessibility issue.

## Procedure

### 1. Static code review (per screen)
Check each interactive element for:
- [ ] Accessibility **label** via `contentDescription`/`semantics` (and role/state/action label where
      needed); decorative images set `contentDescription = null` / `hideFromAccessibility`.
- [ ] Scalable **`sp` type** (font scaling) — no fixed `dp`/px sizes that can't scale.
- [ ] **Contrast** ≥ 4.5:1 (normal) / 3:1 (large/bold) in light *and* dark theme; prefer Material 3
      `colorScheme`.
- [ ] Meaning never conveyed by **colour alone** (add text/icon/shape).
- [ ] Touch targets **≥ 48×48 dp** with adequate spacing (`minimumInteractiveComponentSize`).
- [ ] **Reduced animation scale** respected (`Settings.Global.ANIMATOR_DURATION_SCALE`).
- [ ] Stable **`testTag`s** for UI tests; logical traversal order; headings marked (`heading()`).
- [ ] Errors are perceivable, announced (`liveRegion`), and explain how to fix.

### 2. Automated checks
- Run **Accessibility Scanner** on each screen; use the
  [Accessibility Test Framework](https://github.com/google/Accessibility-Test-Framework-for-Android).
- Enable `AccessibilityChecks` in Espresso instrumented tests; add/extend Compose UI-test assertions on
  `contentDescription`/`testTag`/semantics and announced states.
- Run ktlint/detekt/Android Lint (accessibility lint checks) and the DEFRA SonarCloud gate.

### 3. Manual assistive-technology testing
- Navigate the whole flow with **TalkBack**; verify order, labels and announcements.
- Set **font size** and **display size** to the **largest**; verify no clipping and layouts reflow.
- Test **dark theme**, **high contrast / colour inversion**, **remove animations**, **Voice Access**
  ("tap <label>") and **Switch Access**.
- Test on a **real device** and across current + previous Android versions (DEFRA mobile standard).

### 4. Report & fix
- List issues by WCAG success criterion with severity and a concrete fix.
- Fix, then re-run the checks. Track residual items.
- Before public beta: obtain a **formal accessibility audit**, fix issues, and publish an
  **accessibility statement**.

## Output format
Produce a short report:
- **Summary** (pass/fail against WCAG 2.2 AA).
- **Findings** table: element → criterion → severity → recommended fix.
- **Validation**: which automated/manual checks were run and their results.
- **Follow-ups**: anything deferred, with rationale.
