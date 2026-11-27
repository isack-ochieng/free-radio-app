package tech.iox.freeradio.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class InstalledOemRadio(
    val packageName: String,
    val label: String
)

/**
 * Many phone FM apps are signed/preloaded by the OEM and hold privileged
 * permissions unavailable to third-party apps. This bridge opens the handset's
 * own radio UI when direct FM access is unavailable; it does not claim to tune
 * that private OEM receiver from inside this app.
 */
object OemRadioLauncher {
    private val knownPackages = listOf(
        "com.transsion.fmradio",       // Infinix / TECNO / itel (WOW FM)
        "com.funbase.xradio",          // Some Transsion builds
        "com.sec.android.app.fm",      // Samsung Radio
        "com.miui.fmradio",            // Xiaomi / Redmi
        "com.android.fmradio",         // AOSP-derived OEM builds
        "com.lge.fmradio",             // LG
        "com.huawei.android.FMRadio"   // Huawei
    )

    fun findInstalled(context: Context): InstalledOemRadio? {
        val manager = context.packageManager
        for (packageName in knownPackages) {
            val launchIntent = manager.getLaunchIntentForPackage(packageName) ?: continue
            val label = runCatching {
                manager.getApplicationInfo(packageName, PackageManager.MATCH_ALL)
                    .loadLabel(manager)
                    .toString()
            }.getOrDefault("Phone FM radio")
            if (launchIntent.component != null) {
                return InstalledOemRadio(packageName, label)
            }
        }
        return null
    }

    fun launch(context: Context): Boolean {
        val app = findInstalled(context) ?: return false
        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            ?: return false
        return runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
