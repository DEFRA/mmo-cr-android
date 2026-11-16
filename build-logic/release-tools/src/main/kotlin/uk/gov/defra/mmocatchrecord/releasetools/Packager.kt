package uk.gov.defra.mmocatchrecord.releasetools

import java.nio.file.Files
import java.nio.file.Path

/** Repackages the compile-once base bundle for one environment (ADR 0023); everything else stays byte-identical. */
object Packager {
    private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
    private const val MAX_VERSION_CODE = 2_100_000_000

    fun packageBundle(
        base: Path,
        out: Path,
        packageName: String,
        versionCode: Int,
        config: Path,
    ) {
        requireValidIdentity(packageName, versionCode)
        val configBytes = readConfig(config)
        val entries = BundleIo.read(base)
        if (entries.any { it.name == BundleLayout.CONFIG }) {
            throw ReleaseToolsException("base bundle already contains ${BundleLayout.CONFIG}; it must be config-free")
        }
        entries.firstOrNull { BundleLayout.isOtherModuleManifest(it.name) }?.let {
            throw ReleaseToolsException("unsupported module manifest ${it.name}; only the base module is repackaged")
        }
        val manifestEntry =
            entries.firstOrNull { it.name == BundleLayout.MANIFEST }
                ?: throw ReleaseToolsException("${BundleLayout.MANIFEST} is missing from $base")

        val baseManifest = ManifestEditor.parse(manifestEntry.bytes)
        val oldPackage = ManifestEditor.packageName(baseManifest)
        val manifest = ManifestEditor.rewrite(baseManifest, packageName, versionCode)
        val packaged =
            entries.filterNot { BundleLayout.isSignatureEntry(it.name) }.map { entry ->
                when (entry.name) {
                    BundleLayout.MANIFEST -> entry.withBytes(manifest.toByteArray())
                    BundleLayout.RESOURCE_TABLE ->
                        entry.withBytes(ResourceTableEditor.rewrite(entry.bytes, oldPackage, packageName))
                    else -> entry
                }
            } + BundleEntry(BundleLayout.CONFIG, configBytes, java.util.zip.ZipEntry.DEFLATED, manifestEntry.time)
        BundleIo.write(out, packaged)
    }

    fun requireValidIdentity(
        packageName: String,
        versionCode: Int,
    ) {
        if (!PACKAGE_NAME.matches(packageName)) throw ReleaseToolsException("'$packageName' is not a valid package name")
        if (versionCode !in 1..MAX_VERSION_CODE) {
            throw ReleaseToolsException("versionCode $versionCode must be between 1 and $MAX_VERSION_CODE")
        }
    }

    fun readConfig(config: Path): ByteArray {
        if (!Files.isRegularFile(config)) throw ReleaseToolsException("config not found: $config")
        val bytes = Files.readAllBytes(config)
        val text = bytes.toString(Charsets.UTF_8).trim()
        if (!text.startsWith("{") || !text.endsWith("}")) throw ReleaseToolsException("config must be a JSON object")
        return bytes
    }
}
