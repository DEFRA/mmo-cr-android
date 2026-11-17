package uk.gov.defra.mmocatchrecord.releasetools

import com.android.aapt.Resources.Item
import com.android.aapt.Resources.Primitive
import com.android.aapt.Resources.ResourceTable
import com.android.aapt.Resources.XmlAttribute
import com.android.aapt.Resources.XmlElement
import com.android.aapt.Resources.XmlNode
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

const val BASE_PACKAGE = "mmo.catchrecording.android"
const val BASE_CODE = 15
const val CONFIG_JSON = """{"API_BASE_URL":"https://dev.example.test/","USE_STUB_AUTH":false}"""

private fun plainAttribute(
    name: String,
    value: String,
): XmlAttribute = XmlAttribute.newBuilder().setName(name).setValue(value).build()

private fun androidAttribute(
    name: String,
    value: String,
    resourceId: Int = 0,
    stringItem: Boolean = false,
): XmlAttribute {
    val builder =
        XmlAttribute.newBuilder()
            .setNamespaceUri(ManifestEditor.ANDROID_NAMESPACE)
            .setName(name)
            .setValue(value)
            .setResourceId(resourceId)
    if (stringItem) {
        builder.setCompiledItem(
            Item.newBuilder().setStr(com.android.aapt.Resources.String.newBuilder().setValue(value)),
        )
    }
    return builder.build()
}

private fun element(
    name: String,
    attributes: List<XmlAttribute> = emptyList(),
    children: List<XmlNode> = emptyList(),
): XmlNode =
    XmlNode.newBuilder()
        .setElement(
            XmlElement.newBuilder().setName(name).addAllAttribute(attributes).addAllChild(children),
        ).build()

/** A manifest shaped like the merged release manifest: package, versionCode, permission and provider authorities. */
fun fixtureManifest(
    packageName: String = BASE_PACKAGE,
    versionCode: Int = BASE_CODE,
): XmlNode {
    val versionAttribute =
        androidAttribute("versionCode", versionCode.toString(), ManifestEditor.VERSION_CODE_RESOURCE_ID)
            .toBuilder()
            .setCompiledItem(Item.newBuilder().setPrim(Primitive.newBuilder().setIntDecimalValue(versionCode)))
            .build()
    return element(
        "manifest",
        listOf(plainAttribute("package", packageName), versionAttribute, androidAttribute("versionName", "2.0.0")),
        listOf(
            element(
                "permission",
                listOf(androidAttribute("name", "$packageName.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION", 0x01010003, true)),
            ),
            element(
                "uses-permission",
                listOf(androidAttribute("name", "$packageName.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION", 0x01010003, true)),
            ),
            element(
                "application",
                listOf(androidAttribute("name", "uk.gov.defra.mmocatchrecord.App", 0x01010003, true)),
                listOf(
                    element(
                        "provider",
                        listOf(
                            androidAttribute("authorities", "$packageName.androidx-startup", 0x01010018, true),
                            androidAttribute("label", "${packageName}x.decoy", 0x01010001, true),
                        ),
                    ),
                    element(
                        "provider",
                        listOf(androidAttribute("authorities", "$packageName.one;other.thing;$packageName.two", 0x01010018)),
                    ),
                ),
            ),
        ),
    )
}

fun resourceTable(vararg packageNames: String): ByteArray =
    ResourceTable.newBuilder()
        .also { table ->
            packageNames.forEach { name ->
                table.addPackage(com.android.aapt.Resources.Package.newBuilder().setPackageName(name))
            }
        }.build().toByteArray()

/** Entries of the fixture base AAB; STORED and DEFLATED so both write paths are covered. */
fun baseEntries(manifest: XmlNode = fixtureManifest()): List<BundleEntry> =
    listOf(
        BundleEntry("BundleConfig.pb", byteArrayOf(1, 2, 3), ZipEntry.DEFLATED, null),
        BundleEntry("base/", ByteArray(0), ZipEntry.STORED, null),
        BundleEntry(BundleLayout.MANIFEST, manifest.toByteArray(), ZipEntry.DEFLATED, null),
        BundleEntry(BundleLayout.RESOURCE_TABLE, resourceTable(BASE_PACKAGE), ZipEntry.DEFLATED, null),
        BundleEntry("base/dex/classes.dex", ByteArray(2048) { (it % 251).toByte() }, ZipEntry.DEFLATED, null),
        BundleEntry("base/res/raw/stored.bin", "stored bytes".toByteArray(), ZipEntry.STORED, null),
        BundleEntry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n".toByteArray(), ZipEntry.DEFLATED, null),
        BundleEntry("META-INF/UPLOAD.SF", "sf".toByteArray(), ZipEntry.DEFLATED, null),
        BundleEntry("META-INF/UPLOAD.RSA", "rsa".toByteArray(), ZipEntry.DEFLATED, null),
    )

fun writeZip(
    path: Path,
    entries: List<BundleEntry>,
) {
    val buffer = ByteArrayOutputStream()
    ZipOutputStream(buffer).use { zip ->
        entries.forEach { entry ->
            val zipEntry = ZipEntry(entry.name).apply { method = entry.method }
            if (entry.method == ZipEntry.STORED) {
                zipEntry.size = entry.bytes.size.toLong()
                zipEntry.compressedSize = entry.bytes.size.toLong()
                zipEntry.crc = CRC32().apply { update(entry.bytes) }.value
            }
            zip.putNextEntry(zipEntry)
            zip.write(entry.bytes)
            zip.closeEntry()
        }
    }
    Files.write(path, buffer.toByteArray())
}

fun readNames(path: Path): List<String> = ZipFile(path.toFile()).use { zip -> zip.entries().asSequence().map { it.name }.toList() }

fun tempDir(): File = Files.createTempDirectory("release-tools-test").toFile().also { it.deleteOnExit() }
