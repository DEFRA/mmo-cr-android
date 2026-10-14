require "json"
require "zip"

module CrRelease
  module BundleChecks
    CONFIG_ENTRY = "base/assets/mmocr_app_config.json".freeze

    module_function

    def packaged_config(aab)
      Zip::File.open(aab) do |zip|
        entry = zip.find_entry(CONFIG_ENTRY)
        return entry && JSON.parse(entry.get_input_stream.read)
      end
    end

    # The AAB must carry exactly this Environment's validated config, nothing more or less.
    def verify_config!(aab, config)
      packaged = packaged_config(aab)
      CrRelease.fail!("#{CONFIG_ENTRY} is missing from #{File.basename(aab)}") if packaged.nil?
      return if packaged == config.values

      CrRelease.fail!("#{CONFIG_ENTRY} differs from this Environment's validated config")
    end
  end
end
