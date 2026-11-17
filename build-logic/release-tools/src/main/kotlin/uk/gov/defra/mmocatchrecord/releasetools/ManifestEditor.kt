package uk.gov.defra.mmocatchrecord.releasetools

import com.android.aapt.Resources.Item
import com.android.aapt.Resources.Primitive
import com.android.aapt.Resources.XmlAttribute
import com.android.aapt.Resources.XmlNode
import com.google.protobuf.InvalidProtocolBufferException

/** Edits of the protobuf AndroidManifest.xml inside an AAB: package, versionCode and package-prefixed values. */
object ManifestEditor {
    const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    const val VERSION_CODE_RESOURCE_ID = 0x0101021b
    private const val PACKAGE_ATTRIBUTE = "package"
    private const val VERSION_NAME_ATTRIBUTE = "versionName"

    fun parse(bytes: ByteArray): XmlNode =
        try {
            XmlNode.parseFrom(bytes).also {
                if (!it.hasElement() || it.element.name != "manifest") {
                    throw ReleaseToolsException("${BundleLayout.MANIFEST} has no <manifest> root")
                }
            }
        } catch (e: InvalidProtocolBufferException) {
            throw ReleaseToolsException("${BundleLayout.MANIFEST} is not a protobuf XML node: ${e.message}")
        }

    fun packageName(root: XmlNode): String =
        root.element.attributeList.firstOrNull { it.namespaceUri.isEmpty() && it.name == PACKAGE_ATTRIBUTE }?.value
            ?: throw ReleaseToolsException("manifest has no package attribute")

    fun versionCode(root: XmlNode): Int = versionCodeAttribute(root).compiledItem.prim.intDecimalValue

    fun versionName(root: XmlNode): String? =
        root.element.attributeList.firstOrNull {
            it.namespaceUri == ANDROID_NAMESPACE && it.name == VERSION_NAME_ATTRIBUTE
        }?.value

    /** The manifest as the packaged bundle must carry it: new package, new versionCode, prefixed values moved. */
    fun rewrite(
        root: XmlNode,
        newPackage: String,
        versionCode: Int,
    ): XmlNode {
        val oldPackage = packageName(root)
        versionCodeAttribute(root)
        val rewritten = rewriteNode(root, oldPackage, newPackage)
        val element = rewritten.element
        val attributes =
            element.attributeList.map { attribute ->
                if (isVersionCode(attribute)) withVersionCode(attribute, versionCode) else attribute
            }
        return rewritten.toBuilder()
            .setElement(element.toBuilder().clearAttribute().addAllAttribute(attributes))
            .build()
    }

    /** Moves `old` and `old.*` segments to the new package; `;`-separated lists are handled per segment. */
    fun rewriteValue(
        value: String,
        oldPackage: String,
        newPackage: String,
    ): String {
        if (!value.contains(oldPackage)) return value
        return value.split(";").joinToString(";") { segment ->
            when {
                segment == oldPackage -> newPackage
                segment.startsWith("$oldPackage.") -> newPackage + segment.substring(oldPackage.length)
                else -> segment
            }
        }
    }

    private fun versionCodeAttribute(root: XmlNode): XmlAttribute =
        root.element.attributeList.firstOrNull(::isVersionCode)
            ?: throw ReleaseToolsException("manifest has no android:versionCode attribute")

    private fun isVersionCode(attribute: XmlAttribute): Boolean =
        attribute.namespaceUri == ANDROID_NAMESPACE && attribute.resourceId == VERSION_CODE_RESOURCE_ID

    private fun withVersionCode(
        attribute: XmlAttribute,
        versionCode: Int,
    ): XmlAttribute =
        attribute.toBuilder()
            .setValue(versionCode.toString())
            .setCompiledItem(Item.newBuilder().setPrim(Primitive.newBuilder().setIntDecimalValue(versionCode)))
            .build()

    private fun rewriteNode(
        node: XmlNode,
        oldPackage: String,
        newPackage: String,
    ): XmlNode {
        if (!node.hasElement()) return node
        val element = node.element
        return node.toBuilder()
            .setElement(
                element.toBuilder()
                    .clearAttribute()
                    .addAllAttribute(element.attributeList.map { rewriteAttribute(it, oldPackage, newPackage) })
                    .clearChild()
                    .addAllChild(element.childList.map { rewriteNode(it, oldPackage, newPackage) }),
            ).build()
    }

    private fun rewriteAttribute(
        attribute: XmlAttribute,
        oldPackage: String,
        newPackage: String,
    ): XmlAttribute {
        val builder = attribute.toBuilder().setValue(rewriteValue(attribute.value, oldPackage, newPackage))
        val item = attribute.compiledItem
        if (item.hasStr()) {
            val value = rewriteValue(item.str.value, oldPackage, newPackage)
            builder.setCompiledItem(item.toBuilder().setStr(item.str.toBuilder().setValue(value)))
        } else if (item.hasRawStr()) {
            val value = rewriteValue(item.rawStr.value, oldPackage, newPackage)
            builder.setCompiledItem(item.toBuilder().setRawStr(item.rawStr.toBuilder().setValue(value)))
        }
        return builder.build()
    }
}
