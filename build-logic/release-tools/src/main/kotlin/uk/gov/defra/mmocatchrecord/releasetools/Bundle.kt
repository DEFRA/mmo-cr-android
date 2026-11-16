package uk.gov.defra.mmocatchrecord.releasetools

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** A failure the CLI reports as one line and exit code 1. */
class ReleaseToolsException(message: String) : Exception(message)

object BundleLayout {
    const val MANIFEST = "base/manifest/AndroidManifest.xml"
    const val CONFIG = "base/assets/mmocr_app_config.json"
    const val RESOURCE_TABLE = "base/resources.pb"
    private val MODULE_MANIFEST = Regex("^[^/]+/manifest/AndroidManifest\\.xml$")
    private val SIGNATURE_EXTENSIONS = listOf(".SF", ".RSA", ".DSA", ".EC")

    /** Jar signature files at the AAB root, which `jarsigner` adds and packaging drops. */
    fun isSignatureEntry(name: String): Boolean {
        if (!name.startsWith("META-INF/") || name.removePrefix("META-INF/").contains('/')) return false
        return name == "META-INF/MANIFEST.MF" || SIGNATURE_EXTENSIONS.any { name.endsWith(it) }
    }

    fun isOtherModuleManifest(name: String): Boolean = name != MANIFEST && MODULE_MANIFEST.matches(name)
}

class BundleEntry(
    val name: String,
    val bytes: ByteArray,
    val method: Int,
    val time: FileTime?,
) {
    val isDirectory: Boolean get() = name.endsWith("/")

    fun sha256(): String = sha256Hex(bytes)

    fun withBytes(newBytes: ByteArray): BundleEntry = BundleEntry(name, newBytes, method, time)
}

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

object BundleIo {
    fun read(path: Path): List<BundleEntry> {
        if (!Files.isRegularFile(path)) throw ReleaseToolsException("bundle not found: $path")
        try {
            ZipFile(path.toFile()).use { zip ->
                return zip.entries().asSequence().map { entry ->
                    val bytes = if (entry.isDirectory) ByteArray(0) else zip.getInputStream(entry).use { it.readBytes() }
                    BundleEntry(entry.name, bytes, entry.method, entry.lastModifiedTime)
                }.toList()
            }
        } catch (e: java.io.IOException) {
            throw ReleaseToolsException("cannot read $path as a zip: ${e.message}")
        }
    }

    /** Writes entries in order, keeping each entry's compression method (STORED needs size and CRC). */
    fun write(
        path: Path,
        entries: List<BundleEntry>,
    ) {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            entries.forEach { entry ->
                val zipEntry = ZipEntry(entry.name)
                zipEntry.method = entry.method
                entry.time?.let { zipEntry.lastModifiedTime = it }
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
        path.toAbsolutePath().parent?.let { Files.createDirectories(it) }
        Files.write(path, buffer.toByteArray())
    }
}
