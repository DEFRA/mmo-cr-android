# Design Spec - Remove a species

**Screen:** `RemoveSpeciesScreen` (`feature/catchrecord/presentation/wizard/gear/RemoveSpeciesScreen.kt`),
reached from the "Remove a species" link on `GearSpeciesChecklistScreen`.

## Layout

1. Catch-record reference caption and heading "Remove a species caught with {gear}".
2. Instruction: "Select the species you want to remove, then delete them."
3. Inline error (polite live region) when Delete is pressed with nothing selected.
4. Checkbox per species added to the gear use.
5. **Delete** button (`PrimaryActionButton`).

Cancelling is via the scaffold's top-bar **Back** button or system back; there is no separate Cancel link.

## Deviation register

| Date | Deviation | Standard deviated from | Reason / approval |
| ---- | --------- | ---------------------- | ----------------- |
| 2026-11-24 | Delete uses the green `PrimaryActionButton` rather than a red warning button. | GOV.UK Design System Button - warning buttons for destructive actions. | Explicit product/developer request; the DesignSystem has no warning button yet. |
| 2026-11-24 | Cancel link removed; Back is the only way to leave without deleting. | GOV.UK pattern of a secondary "Cancel" link beside destructive actions. | Explicit product/developer request. |

Both deviations should be logged with Delivery Architecture (`delivery.architecture@defra.gov.uk`).

## Follow-ups

- Consider a DesignSystem `WarningActionButton` (`MmoColors.ErrorRed`) for destructive actions.
- Consider a delete confirmation dialog (as `DraftResumeScreen` uses) to reduce accidental removal.
