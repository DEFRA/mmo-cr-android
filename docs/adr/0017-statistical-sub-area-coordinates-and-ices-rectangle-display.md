# 0017 - Statistical sub-area coordinates and ICES rectangle display

## Status

Accepted

## Context

CRAR QA review of the "Manually Enter Statistical Sub Area" ticket found FR5-FR7 unmet: the "Other"
type-ahead search (ADR 0008/Track A of the same review) showed only the statistical sub-rectangle code —
not its coordinates or related ICES rectangle name — and nothing was persisted beyond the code on
[`GearUse`](../../app/src/main/java/uk/gov/defra/mmocatchrecord/feature/catchrecord/domain/draft/DraftModels.kt).
This ADR covers adding that display and persistence without duplicating reference data into the draft or
overstating what the stored coordinate actually represents.

## Decision

### The dataset centroid is "the centre of the area", never an observed position

The bundled offline map dataset (ADR 0013) already carries each sub-rectangle's geometric centroid. This is
**not** a fishing position — it is simply the midpoint of a roughly 30'x1' rectangle. Every string, Kotlin
symbol name and log line introduced here calls it the sub-area's "centre" (`centroidLatitude`/
`centroidLongitude` on `StatisticalSubRectangle`, "Centre of area:" in the UI) and never "position" or
"location". A governance note is raised with Delivery Architecture
(`delivery.architecture@defra.gov.uk`) to confirm this matches MMO's actual sub-area coordinate convention
before this feature is relied on for compliance reporting — this is a flagged risk, not a blocker, since the
ticket's own FR5/FR6 only ask for "associated coordinates" display, not a verified fishing position.

### Two nullable `REAL` columns on the existing gear-use table, not a new table or foreign key

`GearUseEntity`/`GearUse` gain `statisticalSubRectangleLatitude`/`statisticalSubRectangleLongitude:
Double?`, appended (v8 -> v9, `MIGRATION_8_9`) after the existing columns to keep the exported schema's
column order append-only, mirroring ADR 0014's additive-migration pattern. A new reference table or a
foreign key into a `StatisticalSubRectangle` row was rejected: the coordinate is a simple derived value
captured once at selection time, not a relationship that needs to stay in sync with the reference dataset
after the fact — a gear use's recorded coordinate must reflect what was shown to the fisher *at the moment
they chose it*, not whatever the dataset says today. No `DEFAULT`/backfill is applied to pre-v9 rows for the
same reason as ADR 0014's audit timestamps: inventing a historical coordinate for an already-saved draft
would misrepresent a regulatory record.

### The ICES rectangle name is displayed but not persisted

The related ICES rectangle name is shown in the search results (FR5) but is **not** stored on `GearUse`. It
is wholly derivable from the stored statistical sub-rectangle code via the same offline dataset
(`MapSupport.rectangleForCode`), so persisting it would duplicate reference data inside a regulatory draft
for no benefit, and would need its own invalidation handling if the dataset's naming ever changed.

### No coordinate row on Check-your-answers; no submission/sync payload change

Check-your-answers continues to show only the statistical sub-rectangle code (unchanged). Nothing currently
serialises `GearUse.statisticalSubRectangleCode` into an API/sync payload (verified by a repository-wide
search before this change), so the new coordinate fields are not threaded into one either — they exist
purely to satisfy FR5-FR7's UI/persistence requirement. If submission/export ever needs coordinates, that is
a new, separately-scoped decision.

### `GdsAutocompleteOption` gains an optional secondary line and accessibility-label override

`common/design/GdsAutocompleteField.kt`'s `GdsAutocompleteOption(id, label)` gains
`secondaryLabel: String? = null` and `accessibilityLabel: String? = null`, both defaulted so every existing
call site (port search, gear search, gear-species search) is unaffected. `AutocompleteSuggestions`'s single
`Text` becomes a `Column` with an optional second line, and the row's `semantics {}` block sets
`contentDescription` only when `accessibilityLabel` is non-null. The secondary line reuses
`MmoColors.Text`/`White` (matching the primary line's colour, not `MmoColors.Grey1`) because `Grey1` on
`MmoColors.AutocompleteOddRow` is 4.37:1 — below the WCAG 1.4.3 AA 4.5:1 threshold for body text; no
`maxLines`/`TextOverflow.Ellipsis` is applied, for WCAG 1.4.10 Reflow at 200% font. Suggestion rows read
code → ICES rectangle → coordinates, with direction words ("north"/"east") spoken in full in the
accessibility label rather than the visual "N"/"E" letters, which TalkBack would otherwise spell out
letter-by-letter or mispronounce.

### Live coordinate display is debounced before it is spoken

The visible "Centre of area" text below the "Other" search field (FR6) updates on every keystroke once the
typed value exactly matches a known code. A **separate, hidden, polite live region** announces the same
text, but only after a 400ms debounce from the last change — announcing on every keystroke would queue a
growing backlog of stale TTS output and would talk over
[`GdsAutocompleteField`](../../app/src/main/java/uk/gov/defra/mmocatchrecord/common/design/GdsAutocompleteField.kt)'s
own existing suggestion-count live region. The debounce is implemented with a plain `LaunchedEffect` +
`delay`, the same pattern already used by `SyncConfirmationMessage`; tests drive it with
`composeTestRule.mainClock.autoAdvance = false` + `advanceTimeBy`, never `Thread.sleep`, to avoid flaking.

### The Check-your-answers "Change" edit path must carry coordinates too

`CatchRecordFlowEvent.EditGearStatRectangle` and `CatchRecordFlowViewModel.editGearStatRectangle` gained the
two coordinate parameters alongside the existing code parameter, and `MapScreen`'s edit-path `onSubmit`
reads them off the already-resolved `GearUse` (set centrally in `MapScreenStatusContent`'s `onSubmit` via
`MapSupport.rectangleForCode`). Without this, editing an already-completed gear's statistical sub-area via
"Change" would silently save the new code with the *previous* (or no) coordinates, since the edit path is a
separate dispatch path from the normal add-path `SaveAndContinue`.

### `DraftInvalidation` nulls coordinates everywhere it already nulls the code

Both places that clear `statisticalSubRectangleCode` (an un-confirmed gear use reverting to unconfirmed; a
gear use whose gear type is no longer in the live reference-data cache, ADR 0016) now also clear the two
coordinate fields in the same `copy()` call, so a cleared code can never be left with stale coordinates. The
`statRectangleChanged` comparison driving species/weight invalidation stays code-based — comparing
coordinates as well would be redundant, since they are wholly derived from the code.

## Consequences

- `app/schemas/.../CatchRecordDatabase/9.json` is Gradle-generated (`:app:assembleDebug` or
  `kspDebugKotlin`), never hand-written, and committed alongside this change — see ADR 0010's established
  practice for this database.
- The MMO sub-area coordinate convention governance question (is the dataset centroid the right thing to
  show as "coordinates" for this ticket's purposes) remains open with Delivery Architecture; if the answer
  is "no", a follow-up ADR will need to revisit what is stored and shown here.
- A future "submit coordinates to the API" requirement is a new, separately-scoped decision — this ADR
  deliberately keeps the two new columns device-local only.
