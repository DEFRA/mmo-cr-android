# Reference: project structure

A clean, testable, DEFRA-aligned Jetpack Compose layout. Adapt to any existing repo conventions.

```
mmo-cr-android/
├── settings.gradle.kts
├── build.gradle.kts                       # root build script (plugins via version catalog)
├── gradle/
│   └── libs.versions.toml                 # version catalog (single source of dependency versions)
├── gradle.properties
├── app/
│   ├── build.gradle.kts                   # app module: flavors dev/test/prod, signing, Compose, Hilt
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── res/
│   │   │   ├── xml/network_security_config.xml   # cleartextTrafficPermitted=false
│   │   │   ├── values/                    # strings, themes, colors
│   │   │   └── drawable/
│   │   └── java/mmo/catchrecording/android/
│   │       ├── MMOCatchRecordingApp.kt    # @HiltAndroidApp Application
│   │       ├── MainActivity.kt            # single-activity Compose host + NavHost
│   │       ├── di/                        # Hilt modules (composition root)
│   │       ├── feature/                   # one package per feature (screen + viewmodel + models)
│   │       │   └── catchrecording/
│   │       │       ├── CatchRecordingScreen.kt       # @Composable, stateless where possible
│   │       │       ├── CatchRecordingViewModel.kt    # UI state (StateFlow) + events
│   │       │       └── model/
│   │       ├── core/
│   │       │   ├── network/               # Retrofit API, DTOs, error mapping, OkHttp (timeouts/pinning)
│   │       │   ├── persistence/           # Room, SyncWorker (WorkManager), offline mutation queue, migrations, DataStore
│   │       │   ├── model/                 # shared domain models
│   │       │   └── designsystem/          # accessible components, Material 3 theme, typography, spacing
│   │       └── support/                   # logging (Timber), extensions, utilities
│   ├── src/test/                          # JVM unit tests mirror the source tree (JUnit, MockK, Turbine, Robolectric)
│   └── src/androidTest/                   # Compose UI + Espresso + accessibility tests
├── fastlane/                              # Fastfile, Appfile
├── .github/workflows/                     # GitHub Actions
├── docs/
│   ├── adr/                               # Architecture Decision Records (incl. native-app exception)
│   ├── solution-overview.md
│   └── architecture.md
├── config/
│   ├── detekt/detekt.yml
│   └── ktlint (optional)
├── Gemfile
└── README.md
```

## Notes
- **One package per feature** under `feature/`, each self-contained (screen + view model + models + tests).
- **core** holds cross-cutting concerns. Depend on interfaces; inject via **Hilt** modules in `di/`.
- **persistence** is the offline source of truth; `SyncWorker` (WorkManager) reconciles with the REST API
  and flushes a queued list of mutations on reconnect, with explicit conflict resolution. **DataStore** for
  preferences.
- **designsystem** components must be accessible by default (font scaling, `contentDescription`/roles,
  contrast, 48×48dp) and built on the Material 3 theme.
- **Product flavors** (`dev`/`test`/`prod`) carry non-sensitive per-environment values (endpoints, display
  name, `applicationIdSuffix`) via `BuildConfig`/resources. Secrets are injected at build time from CI,
  never committed.
- **docs/adr/0001-native-android-exception.md** should capture the agreed exception to the DEFRA
  "don't build native apps" mobile standard.
