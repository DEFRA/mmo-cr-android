package uk.gov.defra.mmocatchrecord.releasetools

import java.nio.file.Path

data class InspectResult(
    val packageName: String,
    val versionCode: Int,
    val versionName: String?,
    val hasConfigAsset: Boolean,
    val resourceTablePackages: List<String>,
    val entries: Map<String, String>,
) {
    fun toJson(): String =
        buildString {
            append("{")
            append("\"packageName\":${jsonString(packageName)},")
            append("\"versionCode\":$versionCode,")
            append("\"versionName\":${versionName?.let(::jsonString) ?: "null"},")
            append("\"hasConfigAsset\":$hasConfigAsset,")
            append("\"resourceTablePackages\":[${resourceTablePackages.joinToString(",") { jsonString(it) }}],")
            append("\"entries\":{${entries.entries.joinToString(",") { (name, sha) -> "${jsonString(name)}:${jsonString(sha)}" }}}")
            append("}")
        }

    private fun jsonString(value: String): String =
        buildString {
            append('"')
            value.forEach { char ->
                when {
                    char == '"' -> append("\\\"")
                    char == '\\' -> append("\\\\")
                    char < ' ' -> append("\\u%04x".format(char.code))
                    else -> append(char)
                }
            }
            append('"')
        }
}

object Inspector {
    fun inspect(bundle: Path): InspectResult {
        val entries = BundleIo.read(bundle)
        val manifestEntry =
            entries.firstOrNull { it.name == BundleLayout.MANIFEST }
                ?: throw ReleaseToolsException("${BundleLayout.MANIFEST} is missing from $bundle")
        val manifest = ManifestEditor.parse(manifestEntry.bytes)
        return InspectResult(
            packageName = ManifestEditor.packageName(manifest),
            versionCode = ManifestEditor.versionCode(manifest),
            versionName = ManifestEditor.versionName(manifest),
            hasConfigAsset = entries.any { it.name == BundleLayout.CONFIG },
            resourceTablePackages = resourceTablePackages(entries),
            entries = entries.filterNot { it.isDirectory }.associate { it.name to it.sha256() },
        )
    }

    private fun resourceTablePackages(entries: List<BundleEntry>): List<String> {
        val table = entries.firstOrNull { it.name == BundleLayout.RESOURCE_TABLE } ?: return emptyList()
        return ResourceTableEditor.parse(table.bytes).packageList.map { it.packageName }
    }
}
