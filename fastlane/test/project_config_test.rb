require "minitest/autorun"
require "open3"
require_relative "../lib/cr_release"

# The app configuration standard (ADR 0017): the schema is the only maintained file; the asset is generated
# by the build from local.properties (debug) or the release pipeline (release), never committed.
class ProjectConfigTest < Minitest::Test
  ROOT = CrRelease::ROOT
  ASSET = "mmocr_app_config.json".freeze

  def tracked_files
    @tracked_files ||= begin
      output, status = Open3.capture2("git", "-C", ROOT, "ls-files")
      assert status.success?, "git ls-files failed"
      output.lines.map(&:chomp)
    end
  end

  def test_no_config_asset_is_committed
    assert_empty tracked_files.select { |path| File.basename(path) == ASSET }
  end

  def test_local_properties_is_never_committed
    refute_includes tracked_files, "local.properties"
  end

  def test_schema_asset_name_and_prefix_match_the_release_tooling
    schema = JSON.parse(File.read(CrRelease::AppConfig::SCHEMA_PATH))
    assert_equal ASSET, schema["assetName"]
    assert_equal CrRelease::AppConfig::PREFIX, schema["variablePrefix"]
    assert_equal "base/assets/#{ASSET}", CrRelease::BundleChecks::CONFIG_ENTRY
  end

  def test_gradle_reads_the_version_and_applies_the_config_plugin
    gradle = File.read(File.join(ROOT, "app", "build.gradle.kts"))
    assert_includes gradle, 'id("uk.gov.defra.mmocatchrecord.appconfig")'
    assert_includes gradle, "version.properties"
    refute_match(/productFlavors/, gradle)
    refute_match(/signingConfig/, gradle)
  end
end
