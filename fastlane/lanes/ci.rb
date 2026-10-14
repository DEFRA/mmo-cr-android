platform :android do
  desc "Static analysis: ktlint, detekt and Android Lint."
  lane :lint do
    gradle(tasks: ["ktlintCheck", "detekt", "lintDebug"], flags: "#{CrRelease::GRADLE_FLAGS} --continue")
  end

  desc "Compile the debug variant — the CI compile gate."
  lane :build do
    gradle(task: "assembleDebug", flags: CrRelease::GRADLE_FLAGS)
  end

  desc "Run the local unit tests with Kover coverage, plus the app-config Gradle plugin tests (used by CI)."
  lane :test do
    gradle(
      tasks: ["testDebugUnitTest", "koverXmlReportDebug", ":build-logic:app-config-plugin:test"],
      flags: CrRelease::GRADLE_FLAGS
    )
  end

  desc "Run the instrumented Compose UI / accessibility tests on a connected device or emulator."
  lane :instrumented_test do
    gradle(task: "connectedDebugAndroidTest", flags: CrRelease::GRADLE_FLAGS)
  end
end
