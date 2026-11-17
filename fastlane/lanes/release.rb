platform :android do
  desc "Compile the unsigned, config-free base AAB and the release-tools CLI, then check the base matches the tag."
  lane :compile_release do |options|
    version = CrRelease::Version.read
    CrRelease::ReleaseTag.expect_matches!(ENV["GITHUB_REF_NAME"], version)

    # A full re-run reuses the base an earlier attempt built, so every stage ships the same binary.
    reused = options[:reuse].to_s == "true" && CrRelease::ReleaseTools.staged_base?
    if reused
      UI.important("Reusing the base bundle from an earlier attempt of this run")
    else
      gradle(
        tasks: ["clean", "bundleRelease", ":build-logic:release-tools:installDist"],
        flags: CrRelease::GRADLE_FLAGS,
        properties: { "crGitSha" => ENV["GITHUB_SHA"].to_s[0, 7] }
      )
      CrRelease::ReleaseTools.stage_base!
    end
    sh(*CrRelease::ReleaseTools.inspect_command(bundle: CrRelease::BASE_AAB, out: CrRelease::BASE_MANIFEST), log: false)
    inspected = CrRelease::ReleaseTools.read_inspect(CrRelease::BASE_MANIFEST)
    CrRelease::ReleaseTools.expect_base!(inspected, version)

    commit = CrRelease::ReleaseTools.current_commit
    base_sha =
      if reused
        CrRelease::ReleaseTools.expect_provenance!(commit: commit, tag: version.tag)
      else
        CrRelease::ReleaseTools.write_provenance!(commit: commit, tag: version.tag)
      end
    CrRelease::Report.write_output("base_sha256", base_sha)
    UI.success("Base #{version.tag} (#{inspected['packageName']}, versionCode #{inspected['versionCode']}) SHA-256 #{base_sha}")
    CrRelease::Report.append_summary(
      "Base bundle",
      { "Tag" => version.tag, "Commit" => commit, "Package" => inspected["packageName"],
        "versionCode" => inspected["versionCode"], "Base SHA-256" => base_sha,
        "Provenance" => reused ? "reused base verified against its record" : "recorded" }
    )
  end

  desc "Repackage the base for one environment, sign and verify it, then upload it to Play or leave it for manual upload."
  lane :release_environment do |options|
    env_key = options[:env].to_s
    env = CrRelease.environment!(env_key)
    distribution = CrRelease.distribution!(options[:distribution] || "manual")
    version = CrRelease::Version.read
    CrRelease::ReleaseTag.expect_matches!(ENV["GITHUB_REF_NAME"], version)
    config = CrRelease::AppConfig.from_env!(ENV.fetch("CR_APP_CFG_VARS", ""))
    CrRelease::ReleaseTools.expect_base!(CrRelease::ReleaseTools.read_inspect(CrRelease::BASE_MANIFEST), version)
    base_sha = CrRelease::ReleaseTools.expect_provenance!(
      commit: CrRelease::ReleaseTools.current_commit, tag: version.tag,
      expected_sha: ENV["EXPECTED_BASE_SHA256"], required: ENV["GITHUB_ACTIONS"] == "true"
    )

    code = CrRelease::Version.channel_code(version.code, env[:channel])
    package = CrRelease.package_for(env_key)
    track = env[:track]
    summary = { "Tag" => version.tag, "Commit" => ENV["GITHUB_SHA"], "Environment" => env_key, "Package" => package,
                "Track" => track, "versionCode" => code, "Distribution" => distribution,
                "Base SHA-256" => "#{base_sha} (verified)" }

    if distribution == "play"
      publisher = CrRelease::PlayPublisher.new
      release_name = "#{version.name} (#{code}) #{env_key}"
      step = CrRelease::PlayPublisher.next_step(publisher.release_state(package), package: package, track: track, version_code: code)
      if step == :skip
        UI.success("#{package} versionCode #{code} is already on #{track}; skipping this stage")
        CrRelease::Report.append_summary("Release #{env_key}", summary.merge("Result" => "already on #{track} (skipped)"))
        next
      end
      if step == :release_uploaded
        publisher.release_uploaded(package: package, track: track, version_code: code, release_name: release_name)
        UI.success("#{package} versionCode #{code} was already uploaded; released it to #{track}")
        CrRelease::Report.append_summary("Release #{env_key}", summary.merge("Result" => "released earlier upload to #{track}"))
        next
      end
    end

    FileUtils.mkdir_p(CrRelease::OUT_DIR)
    config_path = File.join(CrRelease::OUT_DIR, "#{env_key}-config.json")
    config.write!(config_path)
    unsigned = package_environment(env_key: env_key, config_path: config_path, code: code)
    aab = File.join(CrRelease::OUT_DIR, "#{env_key}.aab")
    FileUtils.cp(unsigned, aab)

    sign_bundle(aab: aab)
    CrRelease::Signing.expect_certificate!(sh("keytool", "-printcert", "-jarfile", aab, log: false), ENV["UPLOAD_CERT_SHA256"])
    sh(*CrRelease::ReleaseTools.verify_command(base: CrRelease::BASE_AAB, package: aab, package_name: package,
                                               version_code: code, config: config_path), log: false)
    summary.merge!("Package SHA-256" => CrRelease::Report.sha256(aab))

    if distribution == "play"
      uploaded = publisher.upload(package: package, track: track, aab: aab, release_name: release_name)
      CrRelease.fail!("Play assigned versionCode #{uploaded}, expected #{code}") unless uploaded == code
      UI.success("#{package} #{version.tag} (#{code}) -> #{track}")
      summary["Result"] = "uploaded to #{track}"
    else
      CrRelease::Report.manual_checklist(env_key: env_key, package: package, track: track, aab: aab).each { |step| UI.important(step) }
      summary["Result"] = "signed AAB ready at #{aab}"
    end
    CrRelease::Report.append_summary("Release #{env_key}", summary)
  end

  desc "Promote the versionCode on prep-prod to the production track, or print the manual promotion checklist."
  lane :promote_production do |options|
    distribution = CrRelease.distribution!(options[:distribution] || "manual")
    version = CrRelease::Version.read
    CrRelease::ReleaseTag.expect_matches!(ENV["GITHUB_REF_NAME"], version)

    from = CrRelease::PRODUCTION.fetch(:from)
    to = CrRelease::PRODUCTION.fetch(:track)
    package = CrRelease::APPS.fetch(CrRelease::PRODUCTION.fetch(:app)).fetch(:package)
    code = CrRelease::Version.channel_code(version.code, CrRelease::ENVIRONMENTS.fetch(from).fetch(:channel))
    notes = CrRelease::Report.release_notes
    summary = { "Tag" => version.tag, "Commit" => ENV["GITHUB_SHA"], "Package" => package, "From" => from, "Track" => to,
                "versionCode" => code, "Distribution" => distribution }

    if distribution == "play"
      publisher = CrRelease::PlayPublisher.new
      codes = publisher.version_codes(package)
      CrRelease.fail!("versionCode #{code} is not on the #{from} track of #{package}; release prep-prod first") unless codes.fetch(from, []).include?(code)

      if codes.fetch(to, []).include?(code)
        UI.success("#{package} versionCode #{code} is already on #{to}")
        summary["Result"] = "already on #{to}"
      else
        publisher.promote(package: package, from_track: from, to_track: to, version_code: code, notes: notes)
        UI.success("#{package} #{version.tag} (#{code}) #{from} -> #{to}")
        UI.important("After Google review: click Publish changes at the agreed go-live time")
        summary["Result"] = "promoted to #{to}; publish after review"
      end
    else
      CrRelease::Report.production_checklist(package: package, from_track: from, version_code: code).each { |step| UI.important(step) }
      summary["Result"] = "manual promotion checklist printed"
    end
    CrRelease::Report.append_summary("Promote production", summary)
  end
end
