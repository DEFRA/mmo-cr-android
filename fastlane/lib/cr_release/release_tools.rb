require "fileutils"
require "json"
require "digest"
require "open3"

module CrRelease
  # Argument arrays for the release-tools CLI (build-logic/release-tools Main.kt), run as plain processes.
  module ReleaseTools
    MAIN_CLASS = "uk.gov.defra.mmocatchrecord.releasetools.MainKt".freeze
    CONFIG_ENTRY = "base/assets/mmocr_app_config.json".freeze

    module_function

    def classpath(tools_dir = TOOLS_DIR)
      File.join(tools_dir, "lib", "*")
    end

    def command(subcommand, options, tools_dir: TOOLS_DIR)
      ["java", "-cp", classpath(tools_dir), MAIN_CLASS, subcommand, *options.flat_map { |flag, value| ["--#{flag}", value.to_s] }]
    end

    def inspect_command(bundle:, out:, **opts)
      command("inspect", { bundle: bundle, out: out }, **opts)
    end

    def package_command(base:, out:, package_name:, version_code:, config:, **opts)
      command("package", { base: base, out: out, "package-name": package_name, "version-code": version_code, config: config }, **opts)
    end

    def verify_command(base:, package:, package_name:, version_code:, config:, **opts)
      command("verify", { base: base, package: package, "package-name": package_name, "version-code": version_code, config: config }, **opts)
    end

    def read_inspect(path)
      JSON.parse(File.read(path))
    rescue Errno::ENOENT
      CrRelease.fail!("#{File.basename(path)} not found; run compile_release first")
    rescue JSON::ParserError
      CrRelease.fail!("#{File.basename(path)} is not valid JSON")
    end

    # The base must be the config-free prod build of exactly the tagged version.
    def expect_base!(inspected, version)
      problems = []
      problems << "it carries a config asset" if inspected["hasConfigAsset"] != false
      problems << "packageName is #{inspected['packageName']}" if inspected["packageName"] != APPS.fetch(:prod).fetch(:package)
      problems << "versionCode is #{inspected['versionCode']}" if inspected["versionCode"] != version.code
      problems << "versionName is #{inspected['versionName']}" if inspected["versionName"] != version.name
      return if problems.empty?

      CrRelease.fail!("The base bundle does not match #{version.tag}: #{problems.join('; ')}")
    end

    def staged_base?(base_dir: BASE_DIR)
      File.file?(File.join(base_dir, File.basename(BASE_AAB))) && File.directory?(File.join(base_dir, File.basename(TOOLS_DIR), "lib"))
    end

    # The commit being released: GITHUB_SHA in CI, HEAD on a laptop.
    def current_commit
      sha = ENV["GITHUB_SHA"].to_s
      return sha unless sha.empty?

      output, status = Open3.capture2("git", "-C", ROOT, "rev-parse", "HEAD")
      CrRelease.fail!("Cannot tell which commit is being released (no GITHUB_SHA, git failed)") unless status.success?
      output.strip
    end

    # Pins the base to its commit, tag and hash so every later job, and a reused base, can prove it is unchanged.
    def write_provenance!(commit:, tag:, aab: BASE_AAB, path: BASE_PROVENANCE)
      record = { "commit" => commit, "tag" => tag, "baseSha256" => Digest::SHA256.file(aab).hexdigest }
      File.write(path, "#{JSON.pretty_generate(record)}\n")
      record["baseSha256"]
    end

    # Returns the base SHA-256 after checking it against the record and, in CI, the compile job's output.
    def expect_provenance!(commit:, tag:, expected_sha: nil, required: false, aab: BASE_AAB, path: BASE_PROVENANCE)
      expected = expected_sha.to_s.strip.downcase
      CrRelease.fail!("EXPECTED_BASE_SHA256 is not set; the compile job's base hash is required") if required && expected.empty?
      CrRelease.fail!("#{File.basename(path)} is missing; the base has no recorded provenance") unless File.file?(path)

      record = read_inspect(path)
      actual = Digest::SHA256.file(aab).hexdigest
      problems = []
      problems << "its SHA-256 #{actual} differs from the recorded #{record['baseSha256']}" if actual != record["baseSha256"]
      problems << "its SHA-256 #{actual} differs from the compile job's #{expected}" if !expected.empty? && actual != expected
      problems << "it was built from commit #{record['commit']}, not #{commit}" if record["commit"] != commit
      problems << "it was built for #{record['tag']}, not #{tag}" if record["tag"] != tag
      CrRelease.fail!("The base bundle cannot be trusted: #{problems.join('; ')}") unless problems.empty?

      actual
    end

    # Replaces BASE_DIR with the Gradle bundle and the installed CLI so later lanes need no Gradle.
    def stage_base!(aab: GRADLE_AAB_PATH, tools: TOOLS_INSTALL_DIR, base_dir: BASE_DIR)
      CrRelease.fail!("#{aab} was not produced") unless File.file?(aab)
      CrRelease.fail!("#{tools} was not produced") unless File.directory?(tools)

      FileUtils.rm_rf(base_dir)
      FileUtils.mkdir_p(base_dir)
      FileUtils.cp(aab, File.join(base_dir, File.basename(BASE_AAB)))
      FileUtils.cp_r(tools, File.join(base_dir, File.basename(TOOLS_DIR)))
    end
  end
end
