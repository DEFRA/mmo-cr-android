require "minitest/autorun"
require "open3"
require_relative "../lib/cr_release"

# The app configuration standard (ADR 0024): the schema is the only maintained file; the asset is generated
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
    assert_equal "base/assets/#{ASSET}", CrRelease::ReleaseTools::CONFIG_ENTRY
  end

  def test_gradle_reads_the_version_and_applies_the_config_plugin
    gradle = File.read(File.join(ROOT, "app", "build.gradle.kts"))
    assert_includes gradle, 'id("uk.gov.defra.mmocatchrecord.appconfig")'
    assert_includes gradle, "version.properties"
    refute_match(/productFlavors/, gradle)
    refute_match(/signingConfig/, gradle)
  end

  def test_app_code_never_reads_the_build_time_identity
    sources = Dir.glob(File.join(ROOT, "app", "src", "**", "*.{kt,java}"))
    refute_empty sources
    offenders = sources.select { |path| File.read(path).match?(/BuildConfig\.(APPLICATION_ID|VERSION_CODE)/) }
    assert_empty offenders.map { |path| path.delete_prefix("#{ROOT}/") }, "repackaging changes the package name and versionCode"
  end

  # Dev/Test apps use another package, so code must use Context.packageName (e.g. for authorities).
  def test_app_code_never_hard_codes_the_prod_package
    prod = CrRelease::APPS.fetch(:prod).fetch(:package)
    sources = Dir.glob(File.join(ROOT, "app", "src", "{main,release}", "**", "*.{kt,java,xml}"))
    offenders = sources.reject { |path| File.basename(path) == "AndroidManifest.xml" }.select { |path| File.read(path).include?(prod) }
    assert_empty offenders.map { |path| path.delete_prefix("#{ROOT}/") }
  end

  # Repackaging rewrites manifest values prefixed with the package, so class names must sit outside it.
  def test_namespace_is_outside_the_package_name
    gradle = File.read(File.join(ROOT, "app", "build.gradle.kts"))
    namespace = gradle[/namespace\s*=\s*"([^"]+)"/, 1]
    refute_nil namespace
    CrRelease::APPS.each_value do |app|
      refute(namespace == app[:package] || namespace.start_with?("#{app[:package]}."), "namespace #{namespace} overlaps #{app[:package]}")
    end
  end
end
