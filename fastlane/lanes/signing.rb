platform :android do
  desc "Sign the AAB with the upload key from the Environment secrets, then verify the signature."
  private_lane :sign_bundle do |options|
    aab = options.fetch(:aab)
    CrRelease::Signing.with_upload_keystore(ENV.fetch("UPLOAD_KEYSTORE_BASE64")) do |keystore|
      sh(*CrRelease::Signing.sign_command(aab: aab, keystore: keystore, key_alias: ENV.fetch("UPLOAD_KEY_ALIAS")),
         log: false)
    end
    CrRelease::Signing.expect_verified!(sh(*CrRelease::Signing.verify_command(aab), log: false))
  end

  desc "Google Play Developer API service-account JSON from the Environment secret."
  private_lane :play_json_key_data do
    ENV.fetch("PLAY_SERVICE_ACCOUNT_JSON")
  end
end
