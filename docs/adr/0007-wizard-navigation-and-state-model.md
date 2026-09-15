# 0007 - Wizard navigation & state model for the catch-record flow

## Status

Accepted

## Context

The "Create a Catch Record" journey is a multi-step wizard (draft resume → vessel selection → trip-today →
departure date → return date → departure port → return port → per-gear loop → species/weights → landing
storage → review/submit). Steps share a single underlying draft aggregate, later steps depend on earlier
answers (FR10: changing an earlier answer, e.g. a stat-rectangle, must invalidate/clear dependent
downstream data such as species entries scoped to it), and the flow must autosave after every step and
correctly rehydrate an in-progress draft after process death.

Google's Navigation Compose guidance supports scoping a `ViewModel` to a navigation graph (rather than a
single destination) via `hiltViewModel(backStackEntry)` against the graph's own `NavBackStackEntry`, so
that all destinations in that graph share one `ViewModel` instance and its `SavedStateHandle`.

## Decision

Use a **single nav-graph-scoped `ViewModel`**, `CatchRecordFlowViewModel`, obtained via
`hiltViewModel(navController.getBackStackEntry(catchRecordGraphRoute))` (or the equivalent nested-graph
back-stack-entry helper), instead of one `ViewModel` per wizard screen.

- `CatchRecordFlowViewModel` extends the existing `BaseViewModel<S, E>` base class (§ `core/architecture`)
  and holds the **entire draft aggregate** plus a sealed `WizardStep` model describing the current step,
  visited history, and per-step validation/completion state.
- Wizard screens are lightweight, stateless composables that read a slice of `CatchRecordFlowViewModel`'s
  state and dispatch step-scoped events; they do **not** each own a persistence-aware `ViewModel`.
- Navigation between steps uses **type-safe Navigation Compose** (`@Serializable` route objects), per
  current Navigation Compose guidance, rather than hand-built string routes, for compile-time-checked
  arguments as the wizard grows.
- On every "Save and continue" transition, the flow `ViewModel` autosaves the current step's data to Room
  (via `CatchRecordDraftRepository`) **before** advancing the `WizardStep`, so an app kill mid-flow loses at
  most the in-progress (unsaved) fields of the current step, never previously completed steps.
- On first entry to the graph, the `ViewModel` loads the vessel's active draft if one exists (resuming) or
  starts a new one; this correctly rehydrates state after process death because the graph-scoped
  `ViewModel` is recreated from `SavedStateHandle`/repository state, not from in-memory-only fields.
- FR10 dependent-data invalidation is modelled explicitly in the reducer: mutating an earlier answer that
  invalidates downstream data (e.g. stat-rectangle selection) clears/reset the dependent `WizardStep` state
  and re-persists the truncated aggregate, rather than leaving stale dependent rows in Room.

## Consequences

- Per-screen ViewModels are **not** used for this feature; this is a deliberate deviation from a
  "ViewModel per screen" default and is recorded here so it isn't mistaken for an oversight in review.
- All wizard screens must be written as pure, stateless composables driven by the shared state, keeping
  them trivially previewable and unit-testable in isolation from the flow `ViewModel`.
- `CatchRecordFlowViewModel` becomes a single, larger class — it must be kept a thin
  orchestrator/dispatcher over domain use-cases (validation, persistence) rather than embedding business
  logic directly, to keep it unit-testable and within the ≥95% core-business-logic coverage target.
- The nested navigation graph and `WizardStep` sealed model are scaffolded now (Phase 0); later phases add
  the real screens and fill in placeholder `WizardStep` entries without needing further plumbing changes.
