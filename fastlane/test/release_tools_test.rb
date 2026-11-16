require "minitest/autorun"
require "tmpdir"
require_relative "../lib/cr_release"

# Argument arrays must match build-logic/release-tools Main.kt exactly.
class ReleaseToolsTest < Minitest::Test
  UserError = FastlaneCore::Interface::FastlaneError
  Tools = CrRelease::ReleaseTools
  PREFIX = ["java", "-cp", File.join(CrRelease::TOOLS_DIR, "lib", "*"), "uk.gov.defra.mmocatchrecord.releasetools.MainKt"].freeze

  def version
    CrRelease::Version::Value.new("2.0.0", 19)
  end

  def inspected(overrides = {})
    { "packageName" => "mmo.catchrecording.android", "versionCode" => 19, "versionName" => "2.0.0",
      "hasConfigAsset" => false }.merge(overrides)
  end

  def test_inspect_command
    assert_equal PREFIX + ["inspect", "--bundle", "b.aab", "--out", "m.json"], Tools.inspect_command(bundle: "b.aab", out: "m.json")
  end

  def test_package_command
    command = Tools.package_command(base: "b.aab", out: "o.aab", package_name: "mmo.x", version_code: 10_019, config: "c.json")
    assert_equal PREFIX + ["package", "--base", "b.aab", "--out", "o.aab", "--package-name", "mmo.x", "--version-code", "10019",
                           "--config", "c.json"], command
  end

  def test_verify_command
    command = Tools.verify_command(base: "b.aab", package: "p.aab", package_name: "mmo.x", version_code: 20_019, config: "c.json")
    assert_equal PREFIX + ["verify", "--base", "b.aab", "--package", "p.aab", "--package-name", "mmo.x", "--version-code", "20019",
                           "--config", "c.json"], command
  end

  def test_read_inspect_parses_json_and_reports_missing_files
    Dir.mktmpdir do |dir|
      path = File.join(dir, "m.json")
      assert_raises(UserError) { Tools.read_inspect(path) }

      File.write(path, "{")
      assert_raises(UserError) { Tools.read_inspect(path) }

      File.write(path, JSON.generate(inspected))
      assert_equal 19, Tools.read_inspect(path)["versionCode"]
    end
  end

  def test_base_must_be_the_config_free_prod_build_of_the_tag
    assert_nil Tools.expect_base!(inspected, version)
    [{ "hasConfigAsset" => true }, { "packageName" => "mmo.catchrecordingdev.android" }, { "versionCode" => 10_019 },
     { "versionName" => "1.0.0" }].each do |override|
      assert_raises(UserError) { Tools.expect_base!(inspected(override), version) }
    end
  end

  def test_stage_base_replaces_the_base_folder
    Dir.mktmpdir do |dir|
      aab = File.join(dir, "app.aab")
      tools = File.join(dir, "tools")
      FileUtils.mkdir_p(File.join(tools, "lib"))
      File.write(aab, "aab")
      File.write(File.join(tools, "lib", "release-tools.jar"), "jar")
      base = File.join(dir, "base")
      FileUtils.mkdir_p(base)
      File.write(File.join(base, "stale"), "x")

      Tools.stage_base!(aab: aab, tools: tools, base_dir: base)
      assert_equal "aab", File.read(File.join(base, "base.aab"))
      assert File.file?(File.join(base, "release-tools", "lib", "release-tools.jar"))
      refute File.exist?(File.join(base, "stale"))
      assert Tools.staged_base?(base_dir: base)
    end
  end

  def test_staged_base_needs_the_bundle_and_the_tools
    Dir.mktmpdir do |dir|
      refute Tools.staged_base?(base_dir: dir)
      File.write(File.join(dir, "base.aab"), "aab")
      refute Tools.staged_base?(base_dir: dir)
    end
  end

  COMMIT = "a" * 40
  TAG = "v2.0.0-BUILD_19".freeze

  def with_recorded_base
    Dir.mktmpdir do |dir|
      aab = File.join(dir, "base.aab")
      record = File.join(dir, "base-provenance.json")
      File.write(aab, "aab")
      sha = Tools.write_provenance!(commit: COMMIT, tag: TAG, aab: aab, path: record)
      yield aab, record, sha
    end
  end

  def expect(aab, record, **options)
    Tools.expect_provenance!(commit: COMMIT, tag: TAG, aab: aab, path: record, **options)
  end

  def test_provenance_records_the_commit_tag_and_hash
    with_recorded_base do |aab, record, sha|
      assert_equal Digest::SHA256.hexdigest("aab"), sha
      assert_equal({ "commit" => COMMIT, "tag" => TAG, "baseSha256" => sha }, JSON.parse(File.read(record)))
      assert_equal sha, expect(aab, record, expected_sha: sha.upcase, required: true)
    end
  end

  def test_a_changed_base_is_rejected
    with_recorded_base do |aab, record, _sha|
      File.write(aab, "tampered")
      error = assert_raises(UserError) { expect(aab, record) }
      assert_match(/cannot be trusted: its SHA-256 .* differs from the recorded/, error.message)
    end
  end

  def test_a_base_from_another_commit_or_tag_is_rejected
    with_recorded_base do |aab, record, _sha|
      error = assert_raises(UserError) { Tools.expect_provenance!(commit: "b" * 40, tag: "v2.0.0-BUILD_20", aab: aab, path: record) }
      assert_match(/built from commit a+, not b+/, error.message)
      assert_match(/built for v2.0.0-BUILD_19, not v2.0.0-BUILD_20/, error.message)
    end
  end

  def test_the_compile_job_hash_must_match_when_given
    with_recorded_base do |aab, record, _sha|
      error = assert_raises(UserError) { expect(aab, record, expected_sha: "0" * 64) }
      assert_match(/differs from the compile job's 0{64}/, error.message)
    end
  end

  def test_ci_requires_the_compile_job_hash
    with_recorded_base do |aab, record, _sha|
      error = assert_raises(UserError) { expect(aab, record, expected_sha: "", required: true) }
      assert_match(/EXPECTED_BASE_SHA256 is not set/, error.message)
    end
  end

  def test_a_base_without_provenance_is_rejected
    Dir.mktmpdir do |dir|
      aab = File.join(dir, "base.aab")
      File.write(aab, "aab")
      error = assert_raises(UserError) { expect(aab, File.join(dir, "base-provenance.json")) }
      assert_match(/no recorded provenance/, error.message)
    end
  end

  def test_job_outputs_are_appended_only_in_actions
    Dir.mktmpdir do |dir|
      out = File.join(dir, "output")
      CrRelease::Report.write_output("base_sha256", "abc", path: out)
      CrRelease::Report.write_output("ignored", "x", path: "")
      assert_equal "base_sha256=abc\n", File.read(out)
    end
  end
end
