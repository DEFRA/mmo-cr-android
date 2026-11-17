package uk.gov.defra.mmocatchrecord.releasetools

import com.android.aapt.Resources.ResourceTable
import com.google.protobuf.InvalidProtocolBufferException

/** The resource table names its package after the applicationId, so it must follow the manifest. */
object ResourceTableEditor {
    fun parse(bytes: ByteArray): ResourceTable =
        try {
            ResourceTable.parseFrom(bytes)
        } catch (e: InvalidProtocolBufferException) {
            throw ReleaseToolsException("${BundleLayout.RESOURCE_TABLE} is not a resource table: ${e.message}")
        }

    /** Returns the original bytes untouched when no package carries the old name. */
    fun rewrite(
        bytes: ByteArray,
        oldPackage: String,
        newPackage: String,
    ): ByteArray {
        val table = parse(bytes)
        if (table.packageList.none { it.packageName == oldPackage }) return bytes
        val builder = table.toBuilder()
        builder.packageBuilderList.filter { it.packageName == oldPackage }.forEach { it.packageName = newPackage }
        return builder.build().toByteArray()
    }
}
