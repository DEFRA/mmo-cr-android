@file:Suppress("detekt.FunctionNaming")

package uk.gov.defra.mmocatchrecord.common.design

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * Sets `FLAG_SECURE` while composed to block screenshots and recents thumbnails; clears it only if set here.
 */
@Composable
fun SecureScreen() {
    if (LocalInspectionMode.current) return
    val window = LocalContext.current.findActivity()?.window
    DisposableEffect(window) {
        val setHere = window?.let { enableSecureFlag(it) } ?: false
        onDispose { if (setHere) disableSecureFlag(window) }
    }
}

/** Returns true when the flag was newly set by this call. */
internal fun enableSecureFlag(window: Window): Boolean {
    val alreadySet = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
    if (!alreadySet) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    return !alreadySet
}

internal fun disableSecureFlag(window: Window) {
    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
