module CrRelease
  module ReleaseTag
    PATTERN = /\Av(?<name>\d+(?:\.\d+)*)-BUILD_(?<code>\d+)\z/

    module_function

    # [versionName, versionCode] from a release tag v<versionName>-BUILD_<versionCode>.
    def parse!(ref_name)
      match = ref_name.to_s.match(PATTERN)
      CrRelease.fail!("Run on a release tag v<version>-BUILD_<code>, not '#{ref_name}'") unless match
      [match[:name], match[:code].to_i]
    end

    # The version comes only from the code at the tag; the run never chooses or overrides it.
    def expect_matches!(ref_name, version)
      return if parse!(ref_name) == [version.name, version.code]

      CrRelease.fail!("Tag #{ref_name} does not match version.properties at that tag (#{version.tag})")
    end
  end
end
