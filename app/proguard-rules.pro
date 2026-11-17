# Readable stack traces for user-shared diagnostics (ADR 0011) — see security.instructions.md.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: official keep rules so annotated route/DTO classes keep their generated
# $$serializer and Companion.serializer() — required by Navigation Compose routes and reference-data DTOs.
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class uk.gov.defra.mmocatchrecord.**$$serializer { *; }
-keepclassmembers class uk.gov.defra.mmocatchrecord.** {
    *** Companion;
}
-keepclasseswithmembers class uk.gov.defra.mmocatchrecord.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# SQLCipher at-rest encryption (ADR 0006) — JNI bridge needs its exact class names/members kept.
-keep class net.sqlcipher.** { *; }
-keep class net.zetetic.** { *; }
-dontwarn net.sqlcipher.**
-dontwarn net.zetetic.**

# MSAL (ADR 0019): nimbus-jose-jwt's optional Tink backend is unreachable at runtime — Tink is
# deliberately not a dependency of this project (ADR 0006), so R8 only needs to stop warning.
-dontwarn com.google.crypto.tink.**

# MSAL (ADR 0019): display-mask excluded in app/build.gradle.kts as unused Surface Duo hinge
# detection; the class is unreachable, so R8 only needs to stop warning, not keep it.
-dontwarn com.microsoft.device.display.**