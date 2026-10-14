platform :android do
  desc "Build, package and sign the release AAB with this Environment's config, then upload it to the target track."
  lane :release do |options|
    target = CrRelease.target!(options[:target])
    track = CrRelease::TARGETS.fetch(target)
    version = CrRelease::Version.read
    CrRelease::ReleaseTag.expect_matches!(ENV["GITHUB_REF_NAME"], version)
    config = CrRelease::AppConfig.from_env!(ENV.fetch("CR_APP_CFG_VARS", ""))

    if track
      json_key_data = play_json_key_data
      codes_by_track = CrRelease::PLAY_TRACKS.to_h do |play_track|
        [play_track, google_play_track_version_codes(track: play_track, json_key_data: json_key_data)]
      end
      # A recompile is not the code already uploaded, so a used versionCode means a version bump.
      CrRelease::Play.expect_unused!(version.code, codes_by_track)
    end

    aab = build_release_bundle(config: config)
    sign_bundle(aab: aab)

    if track.nil?
      UI.success("Signed #{version.tag} AAB at #{aab} (not uploaded)")
      next
    end

    upload_to_play_store(
      track: track,
      aab: aab,
      json_key_data: json_key_data,
      release_status: "completed",
      skip_upload_apk: true,
      skip_upload_metadata: true,
      skip_upload_changelogs: true,
      skip_upload_images: true,
      skip_upload_screenshots: true
    )
    UI.success("#{CrRelease::PACKAGE_NAME} #{version.tag} -> #{track} track")
  end
end
