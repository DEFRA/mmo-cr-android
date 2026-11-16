module CrRelease
  GRADLE_FLAGS = "--console=plain".freeze
  ROOT = File.expand_path("../../..", __dir__).freeze
  RELEASE_DIR = File.join(ROOT, "build", "release").freeze
  BASE_DIR = File.join(RELEASE_DIR, "base").freeze
  BASE_AAB = File.join(BASE_DIR, "base.aab").freeze
  BASE_MANIFEST = File.join(BASE_DIR, "base-manifest.json").freeze
  BASE_PROVENANCE = File.join(BASE_DIR, "base-provenance.json").freeze
  TOOLS_DIR = File.join(BASE_DIR, "release-tools").freeze
  OUT_DIR = File.join(RELEASE_DIR, "out").freeze
  GRADLE_AAB_PATH = File.join(ROOT, "app", "build", "outputs", "bundle", "release", "app-release.aab").freeze
  TOOLS_INSTALL_DIR = File.join(ROOT, "build-logic", "release-tools", "build", "install", "release-tools").freeze
  RELEASE_NOTES_DIR = File.join(ROOT, "fastlane", "release-notes", "production").freeze

  # Uploaded versionCode = channel * VERSION_WIDTH + build number N (ADR 0022).
  VERSION_WIDTH = 10_000

  APPS = {
    dev: { package: "mmo.catchrecordingdev.android", managed_publishing: false },
    test: { package: "mmo.catchrecordingtest.android", managed_publishing: false },
    prod: { package: "mmo.catchrecording.android", managed_publishing: true }
  }.freeze

  ENVIRONMENTS = {
    "dev" => { app: :dev, track: "internal", channel: 1 },
    "test" => { app: :test, track: "internal", channel: 2 },
    "perf-test" => { app: :test, track: "perf-test", channel: 1 },
    "uat" => { app: :prod, track: "internal", channel: 2 },
    "prep-prod" => { app: :prod, track: "prep-prod", channel: 1 }
  }.freeze

  # Production promotes the versionCode already on the prep-prod track; it never uploads.
  PRODUCTION = { app: :prod, from: "prep-prod", track: "production" }.freeze

  DISTRIBUTIONS = %w[manual play].freeze

  module_function

  def environment!(key)
    name = key.to_s
    return ENVIRONMENTS.fetch(name) if ENVIRONMENTS.key?(name)

    fail!("Unknown environment '#{key}'. Known: #{ENVIRONMENTS.keys.join(', ')}")
  end

  def distribution!(value)
    name = value.to_s
    return name if DISTRIBUTIONS.include?(name)

    fail!("Unknown distribution '#{value}'. Known: #{DISTRIBUTIONS.join(', ')}")
  end

  def package_for(env_key)
    APPS.fetch(environment!(env_key).fetch(:app)).fetch(:package)
  end
end
