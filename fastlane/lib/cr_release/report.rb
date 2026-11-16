require "fileutils"
require "digest"

module CrRelease
  # Human-facing output: Play Console checklists, production release notes and the Actions step summary.
  module Report
    NOTE_LANGUAGES = { "en-GB" => "en-GB.txt", "cy" => "cy.txt" }.freeze
    MAX_NOTE_LENGTH = 500

    module_function

    def manual_checklist(env_key:, package:, track:, aab:)
      steps = ["Play Console > app #{package} > track #{track}: create a release and upload #{aab}"]
      steps << "After Google review: Publish changes for sanity testers" if env_key == "prep-prod"
      steps << "This track publishes automatically after review" if env_key == "perf-test"
      steps
    end

    def production_checklist(package:, from_track:, version_code:)
      ["Play Console > app #{package} > Production: promote versionCode #{version_code} from #{from_track}",
       "After Google review: click Publish changes at the agreed go-live time"]
    end

    # en-GB is required; cy is optional. Play rejects notes over 500 characters.
    def release_notes(dir = RELEASE_NOTES_DIR)
      notes = NOTE_LANGUAGES.each_with_object({}) do |(language, file), result|
        path = File.join(dir, file)
        text = File.file?(path) ? File.read(path).strip : ""
        result[language] = text unless text.empty?
      end
      CrRelease.fail!("Release notes #{File.join(dir, 'en-GB.txt')} are missing or empty") unless notes.key?("en-GB")

      too_long = notes.select { |_, text| text.length > MAX_NOTE_LENGTH }.keys
      CrRelease.fail!("Release notes for #{too_long.join(', ')} exceed #{MAX_NOTE_LENGTH} characters") unless too_long.empty?

      notes
    end

    def sha256(path)
      Digest::SHA256.file(path).hexdigest
    end

    def append_summary(title, rows, path: ENV["GITHUB_STEP_SUMMARY"])
      return if path.to_s.empty?

      lines = ["### #{title}", "", "| | |", "|---|---|"] + rows.map { |key, value| "| #{key} | #{value} |" }
      File.open(path, "a") { |file| file.puts(lines, "") }
    end

    def write_output(name, value, path: ENV["GITHUB_OUTPUT"])
      return if path.to_s.empty?

      File.open(path, "a") { |file| file.puts("#{name}=#{value}") }
    end
  end
end
