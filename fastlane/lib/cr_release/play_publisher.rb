require "google/apis/androidpublisher_v3"
require "googleauth"
require "stringio"

module CrRelease
  # Play Developer API edits. Used instead of fastlane supply so non-internal commits can refuse to
  # restart a review that is already in progress.
  class PlayPublisher
    Api = Google::Apis::AndroidpublisherV3
    SCOPE = "https://www.googleapis.com/auth/androidpublisher".freeze
    CREDENTIALS_ENV = "PLAY_SERVICE_ACCOUNT_JSON".freeze
    INTERNAL_TRACK = "internal".freeze
    IN_REVIEW_MARKER = "CHANGES_ALREADY_IN_REVIEW".freeze
    IN_REVIEW_BEHAVIOR = "ERROR_IF_IN_REVIEW".freeze
    MAX_ATTEMPTS = 3
    BACKOFF_SECONDS = 5
    EDIT_INVALIDATED = /edit.{0,40}(deleted|invalid|expired|not found)|editNotFound/i

    def initialize(service: nil, sleeper: Kernel.method(:sleep))
      @service = service
      @sleeper = sleeper
    end

    # What a (re-)run must do for this stage: :skip, :release_uploaded or :upload.
    def self.next_step(state, package:, track:, version_code:)
      return :skip if state.tracks.fetch(track, []).include?(version_code)

      elsewhere = state.tracks.reject { |name, _| name == track }.select { |_, codes| codes.include?(version_code) }.keys
      CrRelease.fail!("versionCode #{version_code} is already on the #{elsewhere.join(', ')} track of #{package}, not #{track}") unless elsewhere.empty?

      state.bundles.include?(version_code) ? :release_uploaded : :upload
    end

    State = Struct.new(:tracks, :bundles)

    # tracks: { "internal" => [10019], ... }; bundles: every versionCode uploaded to the app.
    def release_state(package)
      with_edit(package, discard: true) do |edit_id|
        tracks = Array(service.list_edit_tracks(package, edit_id).tracks)
        bundles = Array(service.list_edit_bundles(package, edit_id).bundles)
        State.new(tracks.to_h { |track| [track_key(track.track), release_codes(track)] }, bundles.map { |bundle| bundle.version_code.to_i })
      end
    end

    def version_codes(package)
      release_state(package).tracks
    end

    # Uploads the bundle and releases it to the track; returns the versionCode Play assigned.
    def upload(package:, track:, aab:, release_name:, notes: nil)
      with_edit(package) do |edit_id|
        bundle = service.upload_edit_bundle(package, edit_id, upload_source: aab, content_type: "application/octet-stream")
        service.update_edit_track(package, edit_id, track, track_object(track, bundle.version_code, release_name, notes))
        commit(package, edit_id, track)
        bundle.version_code
      end
    end

    # Releases a bundle an earlier attempt uploaded but never released (its track update or commit failed).
    def release_uploaded(package:, track:, version_code:, release_name:)
      with_edit(package) do |edit_id|
        service.update_edit_track(package, edit_id, track, track_object(track, version_code, release_name, nil))
        commit(package, edit_id, track)
        version_code
      end
    end

    # Releases a versionCode already on from_track to to_track without uploading anything.
    def promote(package:, from_track:, to_track:, version_code:, notes:)
      with_edit(package) do |edit_id|
        source = service.get_edit_track(package, edit_id, from_track)
        release = Array(source.releases).find { |candidate| Array(candidate.version_codes).map(&:to_i).include?(version_code) }
        CrRelease.fail!("versionCode #{version_code} is not on the #{from_track} track of #{package}") if release.nil?

        service.update_edit_track(package, edit_id, to_track, track_object(to_track, version_code, release.name, notes))
        commit(package, edit_id, to_track)
        version_code
      end
    end

    private

    def service
      @service ||= build_service
    end

    def build_service
      json = ENV[CREDENTIALS_ENV].to_s
      CrRelease.fail!("#{CREDENTIALS_ENV} is not set for this Environment") if json.strip.empty?

      api = Api::AndroidPublisherService.new
      api.authorization = Google::Auth::ServiceAccountCredentials.make_creds(json_key_io: StringIO.new(json), scope: SCOPE)
      api
    rescue CrRelease::Error, FastlaneCore::Interface::FastlaneError
      raise
    rescue StandardError
      CrRelease.fail!("#{CREDENTIALS_ENV} is not a valid service-account key")
    end

    def release_codes(track)
      Array(track.releases).flat_map { |release| Array(release.version_codes).map(&:to_i) }.uniq
    end

    # Google documents the internal track as "qa"; uploads use "internal", so reads accept both.
    def track_key(name)
      name == "qa" ? INTERNAL_TRACK : name
    end

    def track_object(track, version_code, release_name, notes)
      release = Api::TrackRelease.new(version_codes: [version_code], status: "completed", name: release_name)
      release.release_notes = notes.map { |language, text| Api::LocalizedText.new(language: language, text: text) } if notes && !notes.empty?
      Api::Track.new(track: track, releases: [release])
    end

    def commit(package, edit_id, track)
      options = track == INTERNAL_TRACK ? {} : { changes_in_review_behavior: IN_REVIEW_BEHAVIOR }
      service.commit_edit(package, edit_id, **options)
    end

    # Runs the block in a fresh edit, discarding it on failure; retries when another commit invalidated it.
    def with_edit(package, discard: false)
      attempts = 0
      begin
        attempts += 1
        edit_id = service.insert_edit(package).id
        run_in_edit(package, edit_id, discard) { |id| yield id }
      rescue Google::Apis::ClientError => e
        CrRelease.fail!(in_review_message(package)) if in_review?(e)
        if edit_invalidated?(e) && attempts < MAX_ATTEMPTS
          @sleeper.call(BACKOFF_SECONDS * attempts)
          retry
        end
        CrRelease.fail!("Google Play API request failed (HTTP #{e.status_code}): #{e.message}")
      end
    end

    def run_in_edit(package, edit_id, discard)
      result = yield edit_id
      delete_edit(package, edit_id) if discard
      result
    rescue StandardError
      delete_edit(package, edit_id)
      raise
    end

    def delete_edit(package, edit_id)
      service.delete_edit(package, edit_id)
    rescue StandardError
      nil
    end

    def in_review?(error)
      "#{error.body} #{error.message}".include?(IN_REVIEW_MARKER)
    end

    def in_review_message(package)
      "Another change in #{package} is in review; re-run this job after it completes. No review was restarted."
    end

    def edit_invalidated?(error)
      error.status_code.to_i == 409 || "#{error.body} #{error.message}".match?(EDIT_INVALIDATED)
    end
  end
end
