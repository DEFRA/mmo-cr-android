require "minitest/autorun"
require "tmpdir"
require_relative "../lib/cr_release"

class AppConfigTest < Minitest::Test
  AppConfig = CrRelease::AppConfig

  SCHEMA = {
    "keys" => {
      "API_BASE_URL" => { "type" => "url", "required" => true, "description" => "Backend" },
      "SUPPORT_URL" => { "type" => "url", "required" => false, "log" => false, "description" => "Support" },
      "BANNER_TEXT" => { "type" => "string", "required" => false, "description" => "Banner" },
      "USE_STUB_AUTH" => { "type" => "bool", "required" => false, "description" => "Stub auth" }
    }
  }.freeze

  def schema
    AppConfig.build_schema(SCHEMA)
  end

  def resolve(vars)
    AppConfig.resolve(schema, JSON.generate(vars))
  end

  def schema_error(spec, name: "KEY")
    error = assert_raises(CrRelease::Error) { AppConfig.build_schema("keys" => { name => spec }) }
    error.message
  end

  def valid_spec(overrides = {})
    { "type" => "string", "required" => false, "description" => "d" }.merge(overrides)
  end

  # The CI schema guard: the committed allow-list must always load.
  def test_committed_schema_is_valid
    keys = AppConfig.load_schema
    assert_equal "url", keys.dig("API_BASE_URL", "type")
    assert_equal "bool", keys.dig("USE_STUB_AUTH", "type")
    assert keys.dig("API_BASE_URL", "required")
  end

  def test_from_env_reports_problems_as_fastlane_user_errors
    error = assert_raises(FastlaneCore::Interface::FastlaneError) { AppConfig.from_env!("{}") }
    assert_match(/CR_APP_CFG_API_BASE_URL is required/, error.message)
    config = AppConfig.from_env!('{"CR_APP_CFG_API_BASE_URL":"https://a.example","CR_APP_CFG_USE_STUB_AUTH":"true",' \
                                 '"CR_APP_CFG_USE_FAKE_SIGN_IN":"false"}')
    assert_equal({ "API_BASE_URL" => "https://a.example", "USE_STUB_AUTH" => true, "USE_FAKE_SIGN_IN" => false }, config.values)
  end

  def test_real_schema_accepts_the_entra_sign_in_keys
    config = AppConfig.from_env!(JSON.generate(
      "CR_APP_CFG_API_BASE_URL" => "https://a.example", "CR_APP_CFG_USE_STUB_AUTH" => "false",
      "CR_APP_CFG_USE_FAKE_SIGN_IN" => "false", "CR_APP_CFG_MSAL_CLIENT_ID" => "client-1",
      "CR_APP_CFG_MSAL_AUTHORITY_URL" => "https://tenant.ciamlogin.com/tenant.onmicrosoft.com/",
      "CR_APP_CFG_MSAL_REDIRECT_URI" => "msauth://mmo.catchrecording.dev.android/abc%3D",
      "CR_APP_CFG_MSAL_API_SCOPE" => "api://app-1/access_as_user"
    ))
    assert_equal "client-1", config.values["MSAL_CLIENT_ID"]
    assert_equal "msauth://mmo.catchrecording.dev.android/abc%3D", config.values["MSAL_REDIRECT_URI"]
    assert_equal "api://app-1/access_as_user", config.values["MSAL_API_SCOPE"]
    error = assert_raises(FastlaneCore::Interface::FastlaneError) do
      AppConfig.from_env!('{"CR_APP_CFG_API_BASE_URL":"https://a.example","CR_APP_CFG_USE_STUB_AUTH":"true",' \
                          '"CR_APP_CFG_USE_FAKE_SIGN_IN":"false","CR_APP_CFG_MSAL_AUTHORITY_URL":"http://insecure"}')
    end
    assert_match(/CR_APP_CFG_MSAL_AUTHORITY_URL must be an https/, error.message)
  end

  def test_real_schema_ignores_msal_keys_when_fake_sign_in_is_on
    config = AppConfig.from_env!(JSON.generate(
      "CR_APP_CFG_API_BASE_URL" => "https://a.example", "CR_APP_CFG_USE_STUB_AUTH" => "true",
      "CR_APP_CFG_USE_FAKE_SIGN_IN" => "true", "CR_APP_CFG_MSAL_CLIENT_ID" => "not-set",
      "CR_APP_CFG_MSAL_AUTHORITY_URL" => "not-set", "CR_APP_CFG_MSAL_API_SCOPE" => "not-set"
    ))
    assert_equal({ "API_BASE_URL" => "https://a.example", "USE_STUB_AUTH" => true, "USE_FAKE_SIGN_IN" => true },
                 config.values)
    assert_equal %w[CR_APP_CFG_MSAL_API_SCOPE CR_APP_CFG_MSAL_AUTHORITY_URL CR_APP_CFG_MSAL_CLIENT_ID],
                 config.unused.keys.sort
  end

  def test_schema_rejects_bad_unused_when
    keys = { "FLAG" => valid_spec("type" => "bool"), "TEXT" => valid_spec }
    error = assert_raises(CrRelease::Error) do
      AppConfig.build_schema("keys" => keys.merge("KEY" => valid_spec("unusedWhen" => "TEXT")))
    end
    assert_match(/unusedWhen must name a bool key/, error.message)
    error = assert_raises(CrRelease::Error) do
      AppConfig.build_schema("keys" => keys.merge("KEY" => valid_spec("required" => true, "unusedWhen" => "FLAG")))
    end
    assert_match(/required key cannot be unusedWhen/, error.message)
  end

  def test_schema_rejects_secret_like_names
    assert_match(/secrets must never be packaged/, schema_error(valid_spec, name: "AUTH_TOKEN"))
  end

  def test_schema_rejects_bad_key_names
    assert_match(/must match/, schema_error(valid_spec, name: "apiUrl"))
  end

  def test_schema_rejects_tracking_urls
    assert_match(/Data safety/, schema_error(valid_spec("type" => "url", "tracking" => true)))
  end

  def test_schema_rejects_unknown_types_and_fields
    assert_match(/type must be one of/, schema_error(valid_spec("type" => "int")))
    assert_match(/unknown fields default/, schema_error(valid_spec("default" => "x")))
  end

  def test_schema_requires_boolean_required_and_description
    assert_match(/required must be true or false/, schema_error(valid_spec("required" => "yes")))
    assert_match(/description is required/, schema_error(valid_spec("description" => " ")))
  end

  def test_resolves_typed_values_and_ignores_other_variables
    config = resolve(
      "CR_APP_CFG_API_BASE_URL" => " https://api.example.gov.uk/v1 ",
      "CR_APP_CFG_BANNER_TEXT" => "Hello",
      "CR_APP_CFG_USE_STUB_AUTH" => "false",
      "OTHER_VARIABLE" => "ignored"
    )
    assert_equal(
      { "API_BASE_URL" => "https://api.example.gov.uk/v1", "BANNER_TEXT" => "Hello", "USE_STUB_AUTH" => false },
      config.values
    )
    assert_empty config.unknown
  end

  def test_lists_undeclared_prefixed_variables_as_unknown
    config = resolve("CR_APP_CFG_API_BASE_URL" => "https://a.example", "CR_APP_CFG_NEW_THING" => "x")
    assert_equal ["CR_APP_CFG_NEW_THING"], config.unknown
    refute config.values.key?("NEW_THING")
  end

  def test_missing_required_key_fails
    error = assert_raises(CrRelease::Error) { resolve({}) }
    assert_match(/CR_APP_CFG_API_BASE_URL is required/, error.message)
  end

  def test_rejects_non_https_and_credentials_in_urls
    { "http://a.example" => /https/, "https://" => /https/, "not a url" => /valid URL|https/,
      "https://user:pass@a.example" => /user name or password/,
      "https://a.example/#{'x' * 2048}" => /at most 2048/ }.each do |url, message|
      error = assert_raises(CrRelease::Error) { resolve("CR_APP_CFG_API_BASE_URL" => url) }
      assert_match message, error.message
      refute_includes error.message, url if url.include?("pass")
    end
  end

  def test_rejects_multiline_and_long_strings
    base = { "CR_APP_CFG_API_BASE_URL" => "https://a.example" }
    assert_raises(CrRelease::Error) { resolve(base.merge("CR_APP_CFG_BANNER_TEXT" => "a\nb")) }
    assert_raises(CrRelease::Error) { resolve(base.merge("CR_APP_CFG_BANNER_TEXT" => "a" * 513)) }
  end

  def test_rejects_non_boolean_flags
    error = assert_raises(CrRelease::Error) do
      resolve("CR_APP_CFG_API_BASE_URL" => "https://a.example", "CR_APP_CFG_USE_STUB_AUTH" => "yes")
    end
    assert_match(/true or false/, error.message)
  end

  def test_invalid_json_does_not_echo_input
    error = assert_raises(CrRelease::Error) { AppConfig.resolve(schema, "{\"CR_APP_CFG_X\": \"leak") }
    refute_includes error.message, "leak"
  end

  def test_loggable_masks_keys_marked_not_logged
    config = resolve("CR_APP_CFG_API_BASE_URL" => "https://a.example", "CR_APP_CFG_SUPPORT_URL" => "https://s.example")
    assert_equal({ "API_BASE_URL" => "https://a.example", "SUPPORT_URL" => "(not logged)" }, config.loggable)
  end

  def test_write_produces_the_packaged_json
    Dir.mktmpdir do |dir|
      path = File.join(dir, "app-config.json")
      resolve("CR_APP_CFG_API_BASE_URL" => "https://a.example", "CR_APP_CFG_USE_STUB_AUTH" => "true").write!(path)
      assert_equal({ "API_BASE_URL" => "https://a.example", "USE_STUB_AUTH" => true }, JSON.parse(File.read(path)))
    end
  end
end
