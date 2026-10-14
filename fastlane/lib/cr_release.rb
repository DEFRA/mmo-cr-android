require "fileutils"
require "json"
require "fastlane_core"

# Release logic used by the lanes in fastlane/lanes. Fastlane actions are called only from lanes.
module CrRelease
  # Raised by pure validation; lanes only ever see Fastlane user errors (see AppConfig.from_env!).
  class Error < StandardError; end

  module_function

  # A Fastlane user error: a clean message, not a crash report.
  def fail!(message)
    FastlaneCore::UI.user_error!(message)
  end
end

require_relative "cr_release/settings"
require_relative "cr_release/version"
require_relative "cr_release/release_tag"
require_relative "cr_release/app_config"
require_relative "cr_release/signing"
require_relative "cr_release/bundle_checks"
require_relative "cr_release/play"
