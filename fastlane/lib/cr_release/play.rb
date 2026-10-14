module CrRelease
  module Play
    module_function

    # codes_by_track = { "internal" => [3, 2], ... } from google_play_track_version_codes.
    def expect_unused!(version_code, codes_by_track)
      used = codes_by_track.select { |_, codes| codes.map(&:to_i).include?(version_code) }.keys
      return if used.empty?

      CrRelease.fail!("versionCode #{version_code} is already on the #{used.join(', ')} track; " \
                      "bump VERSION_CODE in version.properties and release the new tag")
    end
  end
end
