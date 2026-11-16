platform :android do
  desc "Repackage the base AAB for one environment: package name, versionCode and the validated config asset."
  private_lane :package_environment do |options|
    env_key = options.fetch(:env_key)
    out = File.join(CrRelease::OUT_DIR, "#{env_key}-unsigned.aab")

    FileUtils.mkdir_p(CrRelease::OUT_DIR)
    FileUtils.rm_f(out)
    sh(*CrRelease::ReleaseTools.package_command(base: CrRelease::BASE_AAB, out: out, package_name: CrRelease.package_for(env_key),
                                                version_code: options.fetch(:code), config: options.fetch(:config_path)),
       log: false)
    out
  end
end
