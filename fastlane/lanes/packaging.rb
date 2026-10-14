platform :android do
  desc "Write the validated stage config, build the unsigned release AAB and check the config it packaged."
  private_lane :build_release_bundle do |options|
    config = options.fetch(:config)

    FileUtils.rm_rf(CrRelease::RELEASE_DIR)
    FileUtils.mkdir_p(CrRelease::RELEASE_DIR)
    config.write!(CrRelease::CONFIG_PATH)

    gradle(
      tasks: ["clean", "bundleRelease"],
      flags: CrRelease::GRADLE_FLAGS,
      properties: { "crAppConfigFile" => CrRelease::CONFIG_PATH, "crGitSha" => ENV["GITHUB_SHA"].to_s[0, 7] }
    )

    FileUtils.cp(CrRelease::GRADLE_AAB_PATH, CrRelease::AAB_PATH)
    CrRelease::BundleChecks.verify_config!(CrRelease::AAB_PATH, config)
    CrRelease::AAB_PATH
  end
end
