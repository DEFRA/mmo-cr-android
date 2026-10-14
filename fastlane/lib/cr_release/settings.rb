module CrRelease
  PACKAGE_NAME = "mmo.catchrecording.android".freeze
  GRADLE_FLAGS = "--console=plain".freeze
  ROOT = File.expand_path("../../..", __dir__).freeze
  RELEASE_DIR = File.join(ROOT, "build", "release").freeze
  CONFIG_PATH = File.join(RELEASE_DIR, "app-config.json").freeze
  AAB_PATH = File.join(RELEASE_DIR, "mmo-cr-android.aab").freeze
  GRADLE_AAB_PATH = File.join(ROOT, "app", "build", "outputs", "bundle", "release", "app-release.aab").freeze

  # Release workflow target -> Play track; build-only signs the AAB without uploading it.
  TARGETS = { "build-only" => nil, "internal" => "internal", "dev" => "dev" }.freeze
  PLAY_TRACKS = TARGETS.values.compact.freeze

  module_function

  def target!(name)
    key = name.to_s
    return key if TARGETS.key?(key)

    fail!("Unknown target '#{name}'. Known: #{TARGETS.keys.join(', ')}")
  end
end
