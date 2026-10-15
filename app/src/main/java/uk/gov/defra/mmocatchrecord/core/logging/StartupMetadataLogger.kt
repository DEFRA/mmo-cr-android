package uk.gov.defra.mmocatchrecord.core.logging

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import timber.log.Timber
import uk.gov.defra.mmocatchrecord.core.config.AppConfiguration

/** Logs non-sensitive build and config metadata at WARN so it survives [ReleaseTree]. */
internal object StartupMetadataLogger {
    fun log(
        packageManager: PackageManager,
        packageName: String,
        gitCommitSha: String,
        appConfiguration: AppConfiguration,
    ) {
        val packageInfo = packageInfo(packageManager, packageName)
        Timber.w(
            "App startup: versionName=%s versionCode=%d gitCommitSha=%s apiHost=%s useStubAuth=%s",
            packageInfo.versionName ?: "unknown",
            PackageInfoCompat.getLongVersionCode(packageInfo),
            gitCommitSha,
            appConfiguration.apiHostForStartupLog() ?: "unavailable",
            appConfiguration.useStubAuth?.toString() ?: "unavailable",
        )
    }

    private fun packageInfo(
        packageManager: PackageManager,
        packageName: String,
    ): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            legacyPackageInfo(packageManager, packageName)
        }

    @Suppress("DEPRECATION")
    private fun legacyPackageInfo(
        packageManager: PackageManager,
        packageName: String,
    ): PackageInfo = packageManager.getPackageInfo(packageName, 0)
}
