module CrRelease
  # The app version in version.properties — the single place a person or an automated bump edits it.
  module Version
    PATH = File.join(ROOT, "version.properties").freeze
    NAME_PATTERN = /\A\d+(?:\.\d+)*\z/
    CODE_PATTERN = /\A[1-9]\d*\z/
    MAX_CODE = 2_100_000_000

    Value = Struct.new(:name, :code) do
      def tag
        "v#{name}-BUILD_#{code}"
      end
    end

    module_function

    def read(path = PATH)
      parse(File.read(path))
    rescue Errno::ENOENT
      CrRelease.fail!("#{File.basename(path)} not found")
    end

    def parse(text)
      values = text.each_line.filter_map do |line|
        line = line.strip
        next if line.empty? || line.start_with?("#")

        line.split("=", 2).map(&:strip)
      end.to_h
      name = values["VERSION_NAME"].to_s
      code = values["VERSION_CODE"].to_s
      CrRelease.fail!("VERSION_NAME '#{name}' must look like 1.2.3") unless name.match?(NAME_PATTERN)
      unless code.match?(CODE_PATTERN) && code.to_i <= MAX_CODE
        CrRelease.fail!("VERSION_CODE '#{code}' must be a whole number from 1 to #{MAX_CODE}")
      end

      Value.new(name, code.to_i)
    end
  end
end
