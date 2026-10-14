require "minitest/autorun"
require "tmpdir"
require "zip"
require_relative "../lib/cr_release"

# Pure release logic; runs without Android tools or Play credentials.
class CrReleaseTest < Minitest::Test
  UserError = FastlaneCore::Interface::FastlaneError

  def version(name = "1.2.0", code = 7)
    CrRelease::Version::Value.new(name, code)
  end

  def test_targets_map_to_play_tracks
    assert_equal "dev", CrRelease.target!(:dev)
    assert_nil CrRelease::TARGETS.fetch(CrRelease.target!("build-only"))
    assert_equal %w[internal dev], CrRelease::PLAY_TRACKS
  end

  def test_unknown_target_fails_with_known_list
    error = assert_raises(UserError) { CrRelease.target!("production") }
    assert_match(/Unknown target 'production'. Known: build-only, internal, dev/, error.message)
  end

  def test_release_paths_sit_under_the_repository_build_folder
    assert CrRelease::RELEASE_DIR.end_with?("/build/release")
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
     "VERSION_NAME=2.0\nVERSION_CODE=2100000001", "VERSION_NAME=2.0\nVERSION_CODE=1.5"].each do |text|
      assert_raises(UserError) { CrRelease::Version.parse(text) }
    end
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

  def test_used_version_code_fails_with_the_tracks
    assert_nil CrRelease::Play.expect_unused!(7, "internal" => [5, 6], "dev" => [])
    error = assert_raises(UserError) { CrRelease::Play.expect_unused!(7, "internal" => ["7"], "dev" => [7]) }
    assert_match(/already on the internal, dev track/, error.message)
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

  def test_bundle_config_must_match_the_validated_values
    config = CrRelease::AppConfig::Config.new({ "API_BASE_URL" => {} }, { "API_BASE_URL" => "https://a.example" }, [])
    Dir.mktmpdir do |dir|
      aab = File.join(dir, "app.aab")
      write_aab(aab, CrRelease::BundleChecks::CONFIG_ENTRY => '{"API_BASE_URL":"https://a.example"}')
      assert_nil CrRelease::BundleChecks.verify_config!(aab, config)

      write_aab(aab, CrRelease::BundleChecks::CONFIG_ENTRY => '{"API_BASE_URL":"https://b.example"}')
      assert_raises(UserError) { CrRelease::BundleChecks.verify_config!(aab, config) }

      write_aab(aab, "base/dex/classes.dex" => "dex")
      error = assert_raises(UserError) { CrRelease::BundleChecks.verify_config!(aab, config) }
      assert_match(/is missing/, error.message)
    end
  end

  private

  def write_aab(path, entries)
    FileUtils.rm_f(path)
    Zip::File.open(path, create: true) do |zip|
      entries.each { |name, content| zip.get_output_stream(name) { |io| io.write(content) } }
    end
  end
end
