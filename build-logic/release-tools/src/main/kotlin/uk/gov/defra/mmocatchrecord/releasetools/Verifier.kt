package uk.gov.defra.mmocatchrecord.releasetools

import com.android.tools.build.bundletool.commands.ValidateBundleCommand
import java.nio.file.Path

/** Checks a packaged AAB differs from the base only where ADR 0023 allows. */
object Verifier {
    fun verify(
        base: Path,
        packaged: Path,
        packageName: String,
        versionCode: Int,
        config: Path,
        validate: (Path) -> Unit = ::bundletoolValidate,
    ) {
        Packager.requireValidIdentity(packageName, versionCode)
        val configBytes = Packager.readConfig(config)
        val baseEntries = BundleIo.read(base).associateBy { it.name }
        val packagedEntries = BundleIo.read(packaged)
        val packagedByName = packagedEntries.associateBy { it.name }
        if (packagedByName.size != packagedEntries.size) throw ReleaseToolsException("package has duplicate entries")

        if (BundleLayout.CONFIG in baseEntries) throw ReleaseToolsException("base bundle must be config-free")
        checkEntries(baseEntries, packagedByName)
        checkConfig(packagedByName, configBytes)
        checkManifest(baseEntries, packagedByName, packageName, versionCode)
        validate(packaged)
    }

    private fun checkEntries(
        base: Map<String, BundleEntry>,
        packaged: Map<String, BundleEntry>,
    ) {
        packaged.keys.filter { it !in base && it != BundleLayout.CONFIG && !BundleLayout.isSignatureEntry(it) }
            .let { if (it.isNotEmpty()) throw ReleaseToolsException("unexpected entry in package: ${it.first()}") }
        base.keys.filter { it !in packaged && !BundleLayout.isSignatureEntry(it) }
            .let { if (it.isNotEmpty()) throw ReleaseToolsException("entry missing from package: ${it.first()}") }
        val changed =
            base.values.firstOrNull { entry ->
                val other = packaged[entry.name]
                other != null && !entry.name.isRewritten() && !entry.bytes.contentEquals(other.bytes)
            }
        if (changed != null) throw ReleaseToolsException("entry differs from base: ${changed.name}")
    }

    private fun String.isRewritten() = this == BundleLayout.MANIFEST || this == BundleLayout.RESOURCE_TABLE

    private fun checkConfig(
        packaged: Map<String, BundleEntry>,
        configBytes: ByteArray,
    ) {
        val entry = packaged[BundleLayout.CONFIG] ?: throw ReleaseToolsException("${BundleLayout.CONFIG} is missing")
        if (!entry.bytes.contentEquals(configBytes)) {
            throw ReleaseToolsException("${BundleLayout.CONFIG} differs from the validated config")
        }
    }

    private fun checkManifest(
        base: Map<String, BundleEntry>,
        packaged: Map<String, BundleEntry>,
        packageName: String,
        versionCode: Int,
    ) {
        val baseManifest = base[BundleLayout.MANIFEST] ?: throw ReleaseToolsException("base has no manifest")
        val packagedManifest =
            packaged[BundleLayout.MANIFEST] ?: throw ReleaseToolsException("package has no manifest")
        val actual = ManifestEditor.parse(packagedManifest.bytes)
        if (ManifestEditor.packageName(actual) != packageName) {
            throw ReleaseToolsException("package name is ${ManifestEditor.packageName(actual)}, expected $packageName")
        }
        if (ManifestEditor.versionCode(actual) != versionCode) {
            throw ReleaseToolsException("versionCode is ${ManifestEditor.versionCode(actual)}, expected $versionCode")
        }
        val expected = ManifestEditor.rewrite(ManifestEditor.parse(baseManifest.bytes), packageName, versionCode)
        if (expected != actual) throw ReleaseToolsException("manifest differs from the base beyond the expected changes")
        checkResourceTable(base, packaged, ManifestEditor.packageName(ManifestEditor.parse(baseManifest.bytes)), packageName)
    }

    private fun checkResourceTable(
        base: Map<String, BundleEntry>,
        packaged: Map<String, BundleEntry>,
        oldPackage: String,
        packageName: String,
    ) {
        val baseTable = base[BundleLayout.RESOURCE_TABLE] ?: return
        val actual = ResourceTableEditor.parse(packaged.getValue(BundleLayout.RESOURCE_TABLE).bytes)
        val expected = ResourceTableEditor.parse(ResourceTableEditor.rewrite(baseTable.bytes, oldPackage, packageName))
        if (expected != actual) {
            throw ReleaseToolsException("${BundleLayout.RESOURCE_TABLE} differs from the base beyond the package rename")
        }
    }

    private fun bundletoolValidate(bundle: Path) {
        try {
            ValidateBundleCommand.builder().setBundlePath(bundle).setPrintOutput(false).build().execute()
        } catch (e: RuntimeException) {
            throw ReleaseToolsException("bundletool validate failed: ${e.message?.lineSequence()?.firstOrNull()}")
        }
    }
}
