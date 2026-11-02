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