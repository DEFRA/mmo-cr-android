require "minitest/autorun"
require "tmpdir"
require_relative "../lib/cr_release"

# Pure release logic; runs without Android tools or Play credentials.
class CrReleaseTest < Minitest::Test
  UserError = FastlaneCore::Interface::FastlaneError

  FINGERPRINT = "0A:1B:2C:3D:4E:5F:60:71:82:93:A4:B5:C6:D7:E8:F9:0A:1B:2C:3D:4E:5F:60:71:82:93:A4:B5:C6:D7:E8:F9".freeze
  PRINTCERT = <<~TEXT.freeze
    Signer #1:

    Signature:

    Owner: CN=MMO Upload, O=DEFRA
    Issuer: CN=MMO Upload, O=DEFRA
    Serial number: 1a2b3c
    Certificate fingerprints:
    \t SHA1: AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD
    \t SHA256: #{FINGERPRINT}
    Signature algorithm name: SHA256withRSA
  TEXT

  def version(name = "1.2.0", code = 7)
    CrRelease::Version::Value.new(name, code)
  end

  def test_settings_invariants
    apps = CrRelease::APPS
    assert_equal apps.size, apps.values.map { |app| app[:package] }.uniq.size
    assert(CrRelease::ENVIRONMENTS.values.all? { |env| apps.key?(env[:app]) })
    assert_includes apps.keys, CrRelease::PRODUCTION[:app]
  end

  def test_tracks_are_unique_within_each_app_and_internal_outranks_closed
    CrRelease::ENVIRONMENTS.values.group_by { |env| env[:app] }.each do |app, envs|
      assert_equal envs.size, envs.map { |env| env[:track] }.uniq.size, "#{app} reuses a track"
      internal, closed = envs.partition { |env| env[:track] == "internal" }
      closed.each { |env| assert_operator internal.first[:channel], :>, env[:channel], "#{app} internal channel must exceed #{env[:track]}" }
    end
  end

  def test_production_promotes_from_a_prod_environment
    from = CrRelease::ENVIRONMENTS.fetch(CrRelease::PRODUCTION[:from])
    assert_equal CrRelease::PRODUCTION[:app], from[:app]
    refute_includes CrRelease::ENVIRONMENTS.values.map { |env| env[:track] }, CrRelease::PRODUCTION[:track]
  end

  def test_only_the_production_app_uses_managed_publishing
    assert_equal [:prod], CrRelease::APPS.select { |_, app| app[:managed_publishing] }.keys
  end

  def test_environment_and_package_lookup
    assert_equal "mmo.catchrecordingtest.android", CrRelease.package_for(:"perf-test")
    assert_equal "perf-test", CrRelease.environment!("perf-test")[:track]
    error = assert_raises(UserError) { CrRelease.environment!("production") }
    assert_match(/Unknown environment 'production'. Known: dev, test, perf-test, uat, prep-prod/, error.message)
  end

  def test_distribution_lookup
    assert_equal "play", CrRelease.distribution!(:play)
    error = assert_raises(UserError) { CrRelease.distribution!("fastlane") }
    assert_match(/Known: manual, play/, error.message)
  end

  def test_release_paths_sit_under_the_repository_build_folder
    assert CrRelease::BASE_DIR.end_with?("/build/release/base")
    assert CrRelease::OUT_DIR.end_with?("/build/release/out")
    assert File.file?(File.join(CrRelease::ROOT, "version.properties"))
  end

  def test_committed_version_properties_parse
    value = CrRelease::Version.read
    assert_match(/\A\d+(\.\d+)*\z/, value.name)
    assert_operator value.code, :>, 0
  end

  def test_version_parses_and_builds_its_tag
    value = CrRelease::Version.parse("# comment\nVERSION_NAME = 2.0.0\n\nVERSION_CODE=14\n")
    assert_equal "v2.0.0-BUILD_14", value.tag
  end

  def test_version_rejects_bad_values
    ["VERSION_NAME=2.0.0", "VERSION_NAME=v2\nVERSION_CODE=1", "VERSION_NAME=2.0\nVERSION_CODE=0",
     "VERSION_NAME=2.0\nVERSION_CODE=10000", "VERSION_NAME=2.0\nVERSION_CODE=1.5"].each do |text|
      assert_raises(UserError) { CrRelease::Version.parse(text) }
    end
  end

  def test_version_accepts_the_largest_build_number
    assert_equal 9_999, CrRelease::Version.parse("VERSION_NAME=2.0\nVERSION_CODE=9999").code
  end

  def test_channel_code_prefixes_the_channel
    assert_equal 10_019, CrRelease::Version.channel_code(19, 1)
    assert_equal 29_999, CrRelease::Version.channel_code(9_999, 2)
  end

  def test_channel_codes_never_collide_across_channels
    codes = [1, 2].product((1..9_999).to_a).map { |channel, n| CrRelease::Version.channel_code(n, channel) }
    assert_equal codes.size, codes.uniq.size
  end

  def test_release_tag_gives_version_name_and_code
    assert_equal ["2.0.0", 14], CrRelease::ReleaseTag.parse!("v2.0.0-BUILD_14")
  end

  def test_release_tag_rejects_branches_and_malformed_tags
    [nil, "main", "v2.0.0", "v2.0.0-BUILD_14.1", "2.0.0-BUILD_14", "android-v2.0.0"].each do |ref|
      error = assert_raises(UserError) { CrRelease::ReleaseTag.parse!(ref) }
      assert_match(/Run on a release tag/, error.message)
    end
  end

  def test_release_tag_must_match_version_properties
    assert_nil CrRelease::ReleaseTag.expect_matches!("v1.2.0-BUILD_7", version)
    error = assert_raises(UserError) { CrRelease::ReleaseTag.expect_matches!("v1.2.0-BUILD_8", version) }
    assert_match(/does not match version.properties .*v1.2.0-BUILD_7/, error.message)
  end

  def test_signing_passes_passwords_only_through_env_vars
    command = CrRelease::Signing.sign_command(aab: "a.aab", keystore: "k.keystore", key_alias: "upload")
    assert_equal %w[-storepass:env UPLOAD_KEYSTORE_PASSWORD], command[command.index("-storepass:env"), 2]
    assert_equal %w[-keypass:env UPLOAD_KEY_PASSWORD], command[command.index("-keypass:env"), 2]
    assert_equal %w[a.aab upload], command.last(2)
  end

  def test_upload_keystore_is_private_and_always_deleted
    Dir.mktmpdir do |dir|
      seen = nil
      assert_raises(RuntimeError) do
        CrRelease::Signing.with_upload_keystore(["keystore-bytes"].pack("m"), dir: dir) do |path|
          seen = path
          assert_equal "keystore-bytes", File.binread(path)
          assert_equal 0o600, File.stat(path).mode & 0o777
          raise "build failed"
        end
      end
      refute File.exist?(seen)
    end
  end

  def test_invalid_keystore_base64_does_not_echo_the_value
    error = assert_raises(UserError) { CrRelease::Signing.with_upload_keystore("not*base64") { flunk } }
    assert_match(/not valid base64/, error.message)
    refute_includes error.message, "not*base64"
  end

  def test_signature_must_verify
    assert_nil CrRelease::Signing.expect_verified!("jar verified.\n")
    assert_raises(UserError) { CrRelease::Signing.expect_verified!("jar is unsigned.") }
  end

  def test_certificate_sha256_is_read_from_the_sha256_line_not_sha1
    assert_equal FINGERPRINT.delete(":"), CrRelease::Signing.certificate_sha256(PRINTCERT)
    assert_nil CrRelease::Signing.certificate_sha256("Owner: CN=x\nSHA1: AA:BB\n")
  end

  def test_certificate_must_match_ignoring_case_and_colons
    assert_nil CrRelease::Signing.expect_certificate!(PRINTCERT, FINGERPRINT.downcase.delete(":"))
    assert_nil CrRelease::Signing.expect_certificate!(PRINTCERT, FINGERPRINT)
  end

  def test_certificate_mismatch_names_both_fingerprints
    other = "FF" * 32
    error = assert_raises(UserError) { CrRelease::Signing.expect_certificate!(PRINTCERT, other) }
    assert_includes error.message, FINGERPRINT.delete(":")
    assert_includes error.message, other
  end

  def test_certificate_check_fails_when_expected_is_blank_or_output_unreadable
    ["", nil, "  "].each do |blank|
      error = assert_raises(UserError) { CrRelease::Signing.expect_certificate!(PRINTCERT, blank) }
      assert_match(/UPLOAD_CERT_SHA256 is not set/, error.message)
    end
    error = assert_raises(UserError) { CrRelease::Signing.expect_certificate!("jar is unsigned.", FINGERPRINT) }
    assert_match(/Could not read the signer certificate/, error.message)
  end

  def test_release_notes_require_english_and_respect_the_play_limit
    Dir.mktmpdir do |dir|
      error = assert_raises(UserError) { CrRelease::Report.release_notes(dir) }
      assert_match(/missing or empty/, error.message)

      File.write(File.join(dir, "en-GB.txt"), "Fixes.\n")
      assert_equal({ "en-GB" => "Fixes." }, CrRelease::Report.release_notes(dir))

      File.write(File.join(dir, "cy.txt"), "Atgyweiriadau.")
      assert_equal %w[en-GB cy], CrRelease::Report.release_notes(dir).keys

      File.write(File.join(dir, "cy.txt"), "x" * 501)
      assert_raises(UserError) { CrRelease::Report.release_notes(dir) }
    end
  end

  def test_committed_production_release_notes_are_valid
    assert_includes CrRelease::Report.release_notes.keys, "en-GB"
  end

  def test_manual_checklist_mentions_the_review_steps_per_environment
    checklist = ->(env) { CrRelease::Report.manual_checklist(env_key: env, package: "p", track: "t", aab: "a.aab").join("\n") }
    assert_match(/Publish changes for sanity testers/, checklist.call("prep-prod"))
    assert_match(/publishes automatically after review/, checklist.call("perf-test"))
    refute_match(/Publish changes|automatically/, checklist.call("dev"))
  end

  def test_summary_is_appended_only_when_a_path_is_given
    Dir.mktmpdir do |dir|
      path = File.join(dir, "summary.md")
      CrRelease::Report.append_summary("Title", { "Tag" => "v1" }, path: path)
      assert_match(/### Title.*\| Tag \| v1 \|/m, File.read(path))
      assert_nil CrRelease::Report.append_summary("Title", {}, path: "")
    end
  end
end
