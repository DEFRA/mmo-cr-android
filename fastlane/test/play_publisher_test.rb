require "minitest/autorun"
require_relative "../lib/cr_release"

# PlayPublisher against a fake service: no network, no credentials.
class PlayPublisherTest < Minitest::Test
  UserError = FastlaneCore::Interface::FastlaneError
  Api = Google::Apis::AndroidpublisherV3
  PACKAGE = "mmo.catchrecording.android".freeze

  class FakeService
    attr_reader :calls, :commits
    attr_accessor :tracks, :uploaded_code, :failures, :bundles

    def initialize
      @calls = []
      @commits = []
      @tracks = {}
      @bundles = []
      @uploaded_code = 10_019
      @failures = {}
      @edits = 0
    end

    def insert_edit(package)
      run(:insert_edit, package) { Api::AppEdit.new(id: "edit-#{@edits += 1}") }
    end

    def list_edit_bundles(_package, _edit_id)
      run(:list_edit_bundles) { Api::BundlesListResponse.new(bundles: @bundles.map { |code| Api::Bundle.new(version_code: code) }) }
    end

    def list_edit_tracks(_package, _edit_id)
      run(:list_edit_tracks) { Api::TracksListResponse.new(tracks: @tracks.map { |name, codes| track(name, codes) }) }
    end

    def get_edit_track(_package, _edit_id, name)
      run(:get_edit_track, name) { track(name, @tracks.fetch(name, [])) }
    end

    def upload_edit_bundle(_package, _edit_id, upload_source:, content_type:)
      run(:upload_edit_bundle, upload_source, content_type) { Api::Bundle.new(version_code: @uploaded_code) }
    end

    def update_edit_track(_package, _edit_id, name, track_object)
      run(:update_edit_track, name, track_object)
    end

    def commit_edit(_package, _edit_id, **options)
      @commits << options
      run(:commit_edit, options)
    end

    def delete_edit(_package, edit_id)
      run(:delete_edit, edit_id)
    end

    def called(name)
      @calls.select { |call| call.first == name }
    end

    private

    def track(name, codes)
      Api::Track.new(track: name, releases: [Api::TrackRelease.new(version_codes: codes, status: "completed", name: "rel")])
    end

    def run(name, *args)
      @calls << [name, *args]
      failure = @failures[name]
      if failure
        error = failure.is_a?(Array) ? failure.shift : failure
        @failures.delete(name) if failure.is_a?(Array) && failure.empty?
        raise error if error
      end
      block_given? ? yield : nil
    end
  end

  def setup
    @service = FakeService.new
    @sleeps = []
    @publisher = CrRelease::PlayPublisher.new(service: @service, sleeper: ->(seconds) { @sleeps << seconds })
  end

  def client_error(body, status = 400)
    Google::Apis::ClientError.new("request failed", status_code: status, body: body)
  end

  def test_version_codes_lists_every_track_and_deletes_the_edit
    @service.tracks = { "internal" => [20_019], "prep-prod" => [10_019, 10_018] }
    assert_equal({ "internal" => [20_019], "prep-prod" => [10_019, 10_018] }, @publisher.version_codes(PACKAGE))
    assert_equal 1, @service.called(:delete_edit).size
    assert_empty @service.called(:commit_edit)
  end

  def test_release_state_includes_uploaded_bundles
    @service.tracks = { "internal" => [20_019] }
    @service.bundles = [20_019, 10_019]
    state = @publisher.release_state(PACKAGE)
    assert_equal({ "internal" => [20_019] }, state.tracks)
    assert_equal [20_019, 10_019], state.bundles
    assert_empty @service.called(:commit_edit)
  end

  def test_release_state_reads_the_qa_alias_as_internal
    @service.tracks = { "qa" => [20_019], "prep-prod" => [10_019] }
    assert_equal({ "internal" => [20_019], "prep-prod" => [10_019] }, @publisher.release_state(PACKAGE).tracks)
  end

  def state(tracks, bundles = [])
    CrRelease::PlayPublisher::State.new(tracks, bundles)
  end

  def next_step(state, track = "prep-prod")
    CrRelease::PlayPublisher.next_step(state, package: PACKAGE, track: track, version_code: 10_019)
  end

  def test_rerun_skips_a_stage_already_on_its_track
    assert_equal :skip, next_step(state({ "prep-prod" => [10_019], "production" => [10_019] }, [10_019]))
  end

  def test_rerun_releases_a_bundle_an_earlier_attempt_uploaded
    assert_equal :release_uploaded, next_step(state({ "prep-prod" => [10_018] }, [10_018, 10_019]))
  end

  def test_first_run_uploads
    assert_equal :upload, next_step(state({ "internal" => [20_019] }, [20_019]))
  end

  def test_code_on_another_track_fails
    error = assert_raises(UserError) { next_step(state({ "production" => [10_019] }, [10_019])) }
    assert_match(/already on the production track/, error.message)
  end

  def test_release_uploaded_updates_the_track_without_uploading
    @publisher.release_uploaded(package: PACKAGE, track: "prep-prod", version_code: 10_019, release_name: "n")
    _, name, track = @service.called(:update_edit_track).first
    assert_equal "prep-prod", name
    assert_equal [10_019], track.releases.first.version_codes
    assert_empty @service.called(:upload_edit_bundle)
    assert_equal [{ changes_in_review_behavior: "ERROR_IF_IN_REVIEW" }], @service.commits
  end

  def test_internal_upload_commits_without_review_behaviour
    code = @publisher.upload(package: PACKAGE, track: "internal", aab: "a.aab", release_name: "2.0.0 (20019) uat")
    assert_equal 10_019, code
    assert_equal [{}], @service.commits
    assert_equal ["a.aab", "application/octet-stream"], @service.called(:upload_edit_bundle).first.last(2)
  end

  def test_closed_track_upload_refuses_to_restart_a_review
    @publisher.upload(package: PACKAGE, track: "prep-prod", aab: "a.aab", release_name: "n")
    assert_equal [{ changes_in_review_behavior: "ERROR_IF_IN_REVIEW" }], @service.commits
  end

  def test_upload_releases_the_assigned_code_as_completed_with_notes
    @publisher.upload(package: PACKAGE, track: "prep-prod", aab: "a.aab", release_name: "n", notes: { "en-GB" => "Fixes." })
    _, track_name, track = @service.called(:update_edit_track).first
    release = track.releases.first
    assert_equal "prep-prod", track_name
    assert_equal [10_019], release.version_codes
    assert_equal "completed", release.status
    assert_equal "n", release.name
    assert_equal ["Fixes."], release.release_notes.map(&:text)
  end

  def test_failure_deletes_the_edit_and_never_commits
    @service.failures = { upload_edit_bundle: RuntimeError.new("boom") }
    assert_raises(RuntimeError) { @publisher.upload(package: PACKAGE, track: "internal", aab: "a.aab", release_name: "n") }
    assert_equal 1, @service.called(:delete_edit).size
    assert_empty @service.called(:commit_edit)
  end

  def test_delete_errors_do_not_hide_the_original_failure
    @service.failures = { upload_edit_bundle: RuntimeError.new("boom"), delete_edit: RuntimeError.new("gone") }
    error = assert_raises(RuntimeError) { @publisher.upload(package: PACKAGE, track: "internal", aab: "a.aab", release_name: "n") }
    assert_equal "boom", error.message
  end

  def test_changes_in_review_becomes_a_clear_user_error
    @service.failures = { commit_edit: client_error('{"error":{"message":"CHANGES_ALREADY_IN_REVIEW"}}') }
    error = assert_raises(UserError) { @publisher.upload(package: PACKAGE, track: "prep-prod", aab: "a.aab", release_name: "n") }
    assert_equal "Another change in #{PACKAGE} is in review; re-run this job after it completes. No review was restarted.", error.message
    assert_equal 1, @service.called(:delete_edit).size
    assert_empty @sleeps
  end

  def test_other_client_errors_are_user_errors_without_retry
    @service.failures = { upload_edit_bundle: client_error("bad bundle") }
    error = assert_raises(UserError) { @publisher.upload(package: PACKAGE, track: "internal", aab: "a.aab", release_name: "n") }
    assert_match(/Google Play API request failed \(HTTP 400\)/, error.message)
    assert_equal 1, @service.called(:insert_edit).size
  end

  def test_invalidated_edit_is_retried_with_backoff
    @service.failures = { commit_edit: [client_error("The edit has been deleted", 404), nil] }
    assert_equal 10_019, @publisher.upload(package: PACKAGE, track: "internal", aab: "a.aab", release_name: "n")
    assert_equal 2, @service.called(:insert_edit).size
    assert_equal [5], @sleeps
  end

  def test_retries_stop_after_three_attempts
    @service.failures = { commit_edit: client_error("edit invalid", 409) }
    assert_raises(UserError) { @publisher.upload(package: PACKAGE, track: "internal", aab: "a.aab", release_name: "n") }
    assert_equal 3, @service.called(:insert_edit).size
    assert_equal [5, 10], @sleeps
  end

  def test_promote_releases_the_code_from_the_source_track_and_refuses_to_restart_a_review
    @service.tracks = { "prep-prod" => [10_019] }
    @publisher.promote(package: PACKAGE, from_track: "prep-prod", to_track: "production", version_code: 10_019, notes: { "en-GB" => "Fixes." })
    _, name, track = @service.called(:update_edit_track).first
    assert_equal "production", name
    assert_equal [10_019], track.releases.first.version_codes
    assert_equal "rel", track.releases.first.name
    assert_empty @service.called(:upload_edit_bundle)
    assert_equal [{ changes_in_review_behavior: "ERROR_IF_IN_REVIEW" }], @service.commits
  end

  def test_promote_fails_when_the_source_track_lacks_the_code
    @service.tracks = { "prep-prod" => [10_018] }
    error = assert_raises(UserError) do
      @publisher.promote(package: PACKAGE, from_track: "prep-prod", to_track: "production", version_code: 10_019, notes: nil)
    end
    assert_match(/versionCode 10019 is not on the prep-prod track/, error.message)
    assert_empty @service.called(:commit_edit)
    assert_equal 1, @service.called(:delete_edit).size
  end

  def test_missing_credentials_fail_clearly_without_a_service
    original = ENV.delete("PLAY_SERVICE_ACCOUNT_JSON")
    error = assert_raises(UserError) { CrRelease::PlayPublisher.new.version_codes(PACKAGE) }
    assert_match(/PLAY_SERVICE_ACCOUNT_JSON is not set/, error.message)
  ensure
    ENV["PLAY_SERVICE_ACCOUNT_JSON"] = original if original
  end

  def test_invalid_credentials_never_echo_the_secret
    original = ENV["PLAY_SERVICE_ACCOUNT_JSON"]
    ENV["PLAY_SERVICE_ACCOUNT_JSON"] = "not-json-super-secret"
    error = assert_raises(UserError) { CrRelease::PlayPublisher.new.version_codes(PACKAGE) }
    refute_includes error.message, "super-secret"
  ensure
    original ? ENV["PLAY_SERVICE_ACCOUNT_JSON"] = original : ENV.delete("PLAY_SERVICE_ACCOUNT_JSON")
  end
end
