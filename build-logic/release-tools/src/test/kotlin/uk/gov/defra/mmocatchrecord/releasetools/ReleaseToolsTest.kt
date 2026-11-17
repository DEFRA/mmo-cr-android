package uk.gov.defra.mmocatchrecord.releasetools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class ReleaseToolsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var base: Path
    private lateinit var config: Path
    private lateinit var out: Path

    @Before
    fun setUp() {
        base = folder.newFile("base.aab").toPath()
        config = folder.newFile("config.json").toPath().also { it.toFile().writeText(CONFIG_JSON) }
        out = folder.root.toPath().resolve("out.aab")
        writeZip(base, baseEntries().filterNot { BundleLayout.isSignatureEntry(it.name) })
    }

    private fun packageBase(
        pkg: String = "mmo.catchrecording.dev.android",
        code: Int = 10015,
    ) = Packager.packageBundle(base, out, pkg, code, config)

    private fun failure(block: () -> Unit): String {
        try {
            block()
        } catch (e: ReleaseToolsException) {
            return e.message.orEmpty()
        }
        fail("expected a ReleaseToolsException")
        return ""
    }

    private fun packagedManifest() = ManifestEditor.parse(BundleIo.read(out).first { it.name == BundleLayout.MANIFEST }.bytes)

    private fun attributes(node: com.android.aapt.Resources.XmlNode): List<com.android.aapt.Resources.XmlAttribute> =
        node.element.attributeList + node.element.childList.flatMap { attributes(it) }

    @Test
    fun `package rewrites the package and versionCode`() {
        packageBase()

        val manifest = packagedManifest()
        assertEquals("mmo.catchrecording.dev.android", ManifestEditor.packageName(manifest))
        assertEquals(10015, ManifestEditor.versionCode(manifest))
        val code = manifest.element.attributeList.first { it.resourceId == ManifestEditor.VERSION_CODE_RESOURCE_ID }
        assertEquals("10015", code.value)
        assertEquals(10015, code.compiledItem.prim.intDecimalValue)
    }

    @Test
    fun `package moves prefixed values but not look-alikes`() {
        packageBase()

        val values = attributes(packagedManifest()).associate { it.name + "/" + it.value to it }.keys
        assertTrue(values.contains("name/mmo.catchrecording.dev.android.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"))
        assertTrue(values.contains("authorities/mmo.catchrecording.dev.android.androidx-startup"))
        assertTrue(values.contains("label/mmo.catchrecording.androidx.decoy"))
        assertTrue(values.contains("name/uk.gov.defra.mmocatchrecord.App"))
    }

    @Test
    fun `package rewrites every segment of an authority list and the compiled string items`() {
        packageBase()

        val authorities = attributes(packagedManifest()).filter { it.name == "authorities" }
        assertTrue(authorities.any { it.value == "mmo.catchrecording.dev.android.one;other.thing;mmo.catchrecording.dev.android.two" })
        val permission = attributes(packagedManifest()).first { it.name == "name" && it.compiledItem.hasStr() }
        assertEquals(permission.value, permission.compiledItem.str.value)
        assertTrue(permission.compiledItem.str.value.startsWith("mmo.catchrecording.dev.android."))
    }

    @Test
    fun `rewriteValue respects the package boundary`() {
        val old = "mmo.catchrecording.android"
        assertEquals("a.b", ManifestEditor.rewriteValue(old, old, "a.b"))
        assertEquals("a.b.X", ManifestEditor.rewriteValue("$old.X", old, "a.b"))
        assertEquals("${old}x.Y", ManifestEditor.rewriteValue("${old}x.Y", old, "a.b"))
        assertEquals("a.b.X;z;a.b.Y", ManifestEditor.rewriteValue("$old.X;z;$old.Y", old, "a.b"))
        assertEquals("prefix.$old.X", ManifestEditor.rewriteValue("prefix.$old.X", old, "a.b"))
    }

    @Test
    fun `package injects the config, drops signatures and keeps every other entry`() {
        writeZip(base, baseEntries())

        packageBase()

        val names = readNames(out)
        assertEquals(baseEntries().map { it.name }.filterNot { BundleLayout.isSignatureEntry(it) } + BundleLayout.CONFIG, names)
        ZipFile(out.toFile()).use { zip ->
            assertEquals(CONFIG_JSON, zip.getInputStream(zip.getEntry(BundleLayout.CONFIG)).readBytes().decodeToString())
            assertEquals(ZipEntry.STORED, zip.getEntry("base/res/raw/stored.bin").method)
            assertEquals(ZipEntry.DEFLATED, zip.getEntry("base/dex/classes.dex").method)
        }
        val baseDex = BundleIo.read(base).first { it.name == "base/dex/classes.dex" }.bytes
        assertTrue(baseDex.contentEquals(BundleIo.read(out).first { it.name == "base/dex/classes.dex" }.bytes))
    }

    @Test
    fun `package fails when the base already has a config asset`() {
        writeZip(base, baseEntries() + BundleEntry(BundleLayout.CONFIG, "{}".toByteArray(), ZipEntry.DEFLATED, null))

        assertTrue(failure { packageBase() }.contains("config-free"))
    }

    @Test
    fun `package rejects bad identity and config`() {
        assertTrue(failure { packageBase(pkg = "not a package") }.contains("not a valid package name"))
        assertTrue(failure { packageBase(code = 0) }.contains("versionCode"))
        config.toFile().writeText("[1]")
        assertTrue(failure { packageBase() }.contains("JSON object"))
    }

    @Test
    fun `package renames the resource table package and verify rejects a stale one`() {
        packageBase()

        val packaged = BundleIo.read(out)
        val table = ResourceTableEditor.parse(packaged.first { it.name == BundleLayout.RESOURCE_TABLE }.bytes)
        assertEquals(listOf("mmo.catchrecording.dev.android"), table.packageList.map { it.packageName })

        val stale = baseEntries().first { it.name == BundleLayout.RESOURCE_TABLE }
        writeZip(out, packaged.map { if (it.name == BundleLayout.RESOURCE_TABLE) stale else it })
        assertTrue(failure { Verifier.verify(base, out, "mmo.catchrecording.dev.android", 10015, config, validate = {}) }.contains("differs from the base beyond the package rename"))
    }

    @Test
    fun `verify accepts a faithful package`() {
        packageBase()

        Verifier.verify(base, out, "mmo.catchrecording.dev.android", 10015, config, validate = {})
    }

    @Test
    fun `verify accepts a signed package`() {
        packageBase()
        val signed = BundleIo.read(out) + listOf(
            BundleEntry("META-INF/MANIFEST.MF", "m".toByteArray(), ZipEntry.DEFLATED, null),
            BundleEntry("META-INF/UPLOAD.SF", "sf".toByteArray(), ZipEntry.DEFLATED, null),
            BundleEntry("META-INF/UPLOAD.RSA", "rsa".toByteArray(), ZipEntry.DEFLATED, null),
        )
        writeZip(out, signed)

        Verifier.verify(base, out, "mmo.catchrecording.dev.android", 10015, config, validate = {})
    }

    @Test
    fun `verify fails on a changed dex`() {
        packageBase()
        val tampered = BundleIo.read(out).map { if (it.name == "base/dex/classes.dex") it.withBytes(byteArrayOf(9)) else it }
        writeZip(out, tampered)

        val message = failure { Verifier.verify(base, out, "mmo.catchrecording.dev.android", 10015, config, validate = {}) }
        assertEquals("entry differs from base: base/dex/classes.dex", message)
    }

    @Test
    fun `verify fails on an extra or missing entry`() {
        packageBase()
        val entries = BundleIo.read(out)
        writeZip(out, entries + BundleEntry("base/dex/classes2.dex", byteArrayOf(1), ZipEntry.DEFLATED, null))
        assertEquals(
            "unexpected entry in package: base/dex/classes2.dex",
            failure { Verifier.verify(base, out, "mmo.catchrecording.dev.android", 10015, config, validate = {}) },
        )

        writeZip(out, entries.filterNot { it.name == "BundleConfig.pb" })
        assertEquals(
            "entry missing from package: BundleConfig.pb",
            failure { Verifier.verify(base, out, "mmo.catchrecording.dev.android", 10015, config, validate = {}) },
        )
    }

    @Test
    fun `verify fails on a wrong package or versionCode`() {
        packageBase()

        assertTrue(failure { Verifier.verify(base, out, "mmo.catchrecording.test.android", 10015, config, validate = {}) }.contains("package name is"))
        assertTrue(failure { Verifier.verify(base, out, "mmo.catchrecording.dev.android", 20015, config, validate = {}) }.contains("versionCode is 10015, expected 20015"))
    }

    @Test
    fun `verify fails when the manifest differs beyond the expected changes`() {
        packageBase()
        val tamperedManifest = packagedManifest().toBuilder().setElement(
            packagedManifest().element.toBuilder().addChild(
                com.android.aapt.Resources.XmlNode.newBuilder()
                    .setElement(com.android.aapt.Resources.XmlElement.newBuilder().setName("uses-permission")),
            ),
        ).build()
        writeZip(out, BundleIo.read(out).map { if (it.name == BundleLayout.MANIFEST) it.withBytes(tamperedManifest.toByteArray()) else it })

        assertTrue(failure { Verifier.verify(base, out, "mmo.catchrecording.dev.android", 10015, config, validate = {}) }.contains("manifest differs"))
    }

    @Test
    fun `verify fails when the config differs or is missing`() {
        packageBase()
        config.toFile().writeText("""{"API_BASE_URL":"https://other.example/"}""")
        assertTrue(failure { Verifier.verify(base, out, "mmo.catchrecording.dev.android", 10015, config, validate = {}) }.contains("differs from the validated config"))

        config.toFile().writeText(CONFIG_JSON)
        writeZip(out, BundleIo.read(out).filterNot { it.name == BundleLayout.CONFIG })
        assertTrue(failure { Verifier.verify(base, out, "mmo.catchrecording.dev.android", 10015, config, validate = {}) }.contains("is missing"))
    }

    @Test
    fun `verify reports a bundletool validation failure`() {
        packageBase()

        val message = failure {
            Verifier.verify(base, out, "mmo.catchrecording.dev.android", 10015, config, validate = { throw ReleaseToolsException("bundletool validate failed: x") })
        }
        assertEquals("bundletool validate failed: x", message)
        assertTrue(failure { Verifier.verify(base, out, "mmo.catchrecording.dev.android", 10015, config) }.startsWith("bundletool validate failed"))
    }

    @Test
    fun `inspect reports identity, config presence, resource packages and entry hashes`() {
        val json = folder.root.toPath().resolve("base.json")
        assertEquals(0, runCli(arrayOf("inspect", "--bundle", base.toString(), "--out", json.toString())))

        val text = json.toFile().readText()
        assertTrue(text.contains("\"packageName\":\"mmo.catchrecording.android\""))
        assertTrue(text.contains("\"versionCode\":15"))
        assertTrue(text.contains("\"versionName\":\"2.0.0\""))
        assertTrue(text.contains("\"hasConfigAsset\":false"))
        assertTrue(text.contains("\"resourceTablePackages\":[\"mmo.catchrecording.android\"]"))
        val dex = BundleIo.read(base).first { it.name == "base/dex/classes.dex" }
        assertTrue(text.contains("\"base/dex/classes.dex\":\"${dex.sha256()}\""))
        assertFalse(text.contains("\"base/\":"))

        packageBase()
        val packagedJson = folder.root.toPath().resolve("out.json")
        assertEquals(0, runCli(arrayOf("inspect", "--bundle", out.toString(), "--out", packagedJson.toString())))
        assertTrue(packagedJson.toFile().readText().contains("\"hasConfigAsset\":true"))
    }

    @Test
    fun `cli prints a one-line error and exits 1`() {
        val err = ByteArrayOutputStream()
        val code = runCli(arrayOf("inspect", "--bundle", "/does/not/exist.aab", "--out", "x"), err = PrintStream(err))

        assertEquals(1, code)
        val lines = err.toString().trim().lines()
        assertEquals(1, lines.size)
        assertTrue(lines.single().startsWith("error: bundle not found"))
        assertEquals(1, runCli(arrayOf("frobnicate"), err = PrintStream(ByteArrayOutputStream())))
        assertEquals(1, runCli(arrayOf("package", "--base"), err = PrintStream(ByteArrayOutputStream())))
    }

    @Test
    fun `cli package succeeds and verify rejects a fixture that is not a real bundle`() {
        val ok = ByteArrayOutputStream()
        assertEquals(
            0,
            runCli(
                arrayOf("package", "--base", base.toString(), "--out", out.toString(), "--package-name", "mmo.catchrecording.test.android", "--version-code", "20015", "--config", config.toString()),
                out = PrintStream(ok),
            ),
        )
        assertEquals(
            1,
            runCli(
                arrayOf("verify", "--base", base.toString(), "--package", out.toString(), "--package-name", "mmo.catchrecording.test.android", "--version-code", "20015", "--config", config.toString()),
                err = PrintStream(ByteArrayOutputStream()),
            ),
        )
    }
}
