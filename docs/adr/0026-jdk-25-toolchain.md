# 0026 - JDK 25 toolchain and detekt 2.0

## Status

Accepted (2026-11-18). Supersedes the JDK 21 toolchain referenced in
[0013](0013-offline-fisheries-map-rendering.md).

## Context

A critical JDK vulnerability (CVE-2026-114938) is fixed in 17.0.21.1, 21.0.13.1, 25.0.5.1 and 27.0.1.1.
Developer machines are restricted to Temurin 25.0.5; no patched JDK 21 was available, and Gradle's
auto-provisioned JDK 21 (21.0.7) and the Android Studio JBR 21.0.9 are older than the fix.

## Decision

1. Build on **JDK 25**: `jvmToolchain(25)`, `JavaVersion.VERSION_25`, `toolchainVersion=25` in
   `gradle/gradle-daemon-jvm.properties`, and Temurin `25` in every `setup-java` CI step.
2. Upgrade Gradle to 9.9.1.
3. Move detekt from 1.23.8 to **2.0.0-alpha.6** (`dev.detekt`): 1.x cannot run on JDK 25. The
   `LongParameterList` thresholds became `allowedFunctionParameters` / `allowedConstructorParameters`
   (threshold minus one).

## Consequences

- detekt 2.0 is an alpha release: revisit when a stable 2.x ships.
- Android Studio must use a patched JDK 25 as its Gradle JDK.
- minSdk and the Android runtime are unaffected; bytecode is dexed by AGP.

## Amendment (2026-11-24) - Java 21 bytecode target

Android Studio Compose Preview (layoutlib's ASM) cannot read class file major version 69 (Java 25)
and fails with `Unsupported class file major version 69`.

- The **toolchain stays JDK 25** (the CVE reason above still applies).
- Anything on the **app runtime classpath** emits **Java 21 bytecode**: `:app`
  (`JavaVersion.VERSION_21` + Kotlin `jvmTarget = JVM_21`) and the included-build `map-data-core`
  (Kotlin `jvmTarget = JVM_21` + `-Xjdk-release=21`, Java `options.release = 21`).
- Gradle-only modules (`map-data-plugin`, `release-tools`, `app-config-plugin`) stay on Java 25.
- Revisit: return to a Java 25 target once Android Studio/layoutlib supports class file version 69.
