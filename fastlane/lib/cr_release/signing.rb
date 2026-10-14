require "base64"
require "tmpdir"

module CrRelease
  # Upload-key signing (Play App Signing holds the app signing key); passwords reach jarsigner only via env vars.
  module Signing
    STORE_PASSWORD_ENV = "UPLOAD_KEYSTORE_PASSWORD".freeze
    KEY_PASSWORD_ENV = "UPLOAD_KEY_PASSWORD".freeze
    KEYSTORE_PREFIX = "mmo-cr-upload-".freeze

    module_function

    # Decodes the keystore into a 0600 temp file for the block and always deletes it.
    def with_upload_keystore(base64, dir: ENV.fetch("RUNNER_TEMP", Dir.tmpdir))
      bytes = begin
        Base64.strict_decode64(base64.to_s.gsub(/\s+/, ""))
      rescue ArgumentError
        CrRelease.fail!("UPLOAD_KEYSTORE_BASE64 is not valid base64")
      end
      CrRelease.fail!("UPLOAD_KEYSTORE_BASE64 is empty") if bytes.empty?

      path = File.join(dir, "#{KEYSTORE_PREFIX}#{Process.pid}.keystore")
      File.open(path, File::WRONLY | File::CREAT | File::EXCL, 0o600) { |file| file.write(bytes) }
      yield path
    ensure
      File.delete(path) if path && File.exist?(path)
    end

    def sign_command(aab:, keystore:, key_alias:)
      ["jarsigner", "-sigalg", "SHA256withRSA", "-digestalg", "SHA-256", "-keystore", keystore,
       "-storepass:env", STORE_PASSWORD_ENV, "-keypass:env", KEY_PASSWORD_ENV, aab, key_alias]
    end

    def verify_command(aab)
      ["jarsigner", "-verify", aab]
    end

    def expect_verified!(verify_output)
      return if verify_output.include?("jar verified.")

      CrRelease.fail!("The AAB signature did not verify")
    end
  end
end
