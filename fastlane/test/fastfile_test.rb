require "minitest/autorun"
require "fastlane"

# Loads the real Fastfile, so broken imports or lane wiring fail in CI rather than in a release.
class FastfileTest < Minitest::Test
  PUBLIC_LANES = %i[lint build test instrumented_test release].freeze
  PRIVATE_LANES = %i[sign_bundle play_json_key_data build_release_bundle].freeze

  def lanes
    @lanes ||= Dir.chdir(File.expand_path("../..", __dir__)) do
      Fastlane.load_actions
      Fastlane::FastFile.new(File.expand_path("../Fastfile", __dir__)).runner.lanes.fetch(:android)
    end
  end

  def test_public_lanes_are_unchanged
    assert_equal PUBLIC_LANES.sort, lanes.reject { |_, lane| lane.is_private }.keys.sort
  end

  def test_shared_steps_are_private_lanes
    assert_equal PRIVATE_LANES.sort, lanes.select { |_, lane| lane.is_private }.keys.sort
  end

  def test_every_lane_has_a_description
    lanes.each { |name, lane| refute_empty lane.description.join, "#{name} has no desc" }
  end
end
