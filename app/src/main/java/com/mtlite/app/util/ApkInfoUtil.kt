package com.mtlite.app.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.graphics.drawable.Drawable
import android.os.Build
import java.security.MessageDigest

/**
 * Read-only APK metadata extracted via PackageManager.
 * Deliberately does NOT parse/modify dex, resources or signatures —
 * information display only, no reverse-engineering modification.
 */
data class ApkInfo(
    val path: String,
    val label: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val sizeBytes: Long,
    val isSystem: Boolean,
    val permissions: List<String>,
    val activityCount: Int,
    val serviceCount: Int,
    val receiverCount: Int,
    val signatureSha256: String,
    val signatureSha1: String,
    val signatureMd5: String,
    val installer: String?,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val dataDir: String?,
    val sourceDir: String?,
    val abis: List<String>
) {
    fun sdkName(code: Int): String = when (code) {
        21 -> "5.0 (Lollipop)"
        22 -> "5.1 (Lollipop)"
        23 -> "6.0 (Marshmallow)"
        24 -> "7.0 (Nougat)"
        25 -> "7.1 (Nougat)"
        26 -> "8.0 (Oreo)"
        27 -> "8.1 (Oreo)"
        28 -> "9 (Pie)"
        29 -> "10 (Q)"
        30 -> "11 (R)"
        31 -> "12 (S)"
        32 -> "12L (Sv2)"
        33 -> "13 (T)"
        34 -> "14 (U)"
        35 -> "15 (V)"
        else -> "SDK $code"
    }
}

object ApkInfoUtil {

    /** Parse an APK file on disk (not installed). */
    fun parseApkFile(context: Context, path: String): ApkInfo? {
        return try {
            val pm = context.packageManager
            val flags = PackageManager.GET_PERMISSIONS or
                PackageManager.GET_ACTIVITIES or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_SIGNING_CERTIFICATES or
                PackageManager.GET_CONFIGURATIONS
            val info = pm.getPackageArchiveInfo(path, flags) ?: return null
            info.applicationInfo?.let { ai ->
                ai.sourceDir = path
                ai.publicSourceDir = path
            }
            val appInfo = info.applicationInfo
            val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: info.packageName
            val perms = info.requestedPermissions?.toList() ?: emptyList()
            val sigs = getSignatures(info)
            ApkInfo(
                path = path,
                label = label,
                packageName = info.packageName,
                versionName = info.versionName ?: "",
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong(),
                minSdk = appInfo?.minSdkVersion ?: 0,
                targetSdk = appInfo?.targetSdkVersion ?: 0,
                sizeBytes = java.io.File(path).length(),
                isSystem = appInfo?.let { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 } ?: false,
                permissions = perms,
                activityCount = info.activities?.size ?: 0,
                serviceCount = info.services?.size ?: 0,
                receiverCount = info.receivers?.size ?: 0,
                signatureSha256 = sigs.first,
                signatureSha1 = sigs.second,
                signatureMd5 = sigs.third,
                installer = null,
                firstInstallTime = info.firstInstallTime,
                lastUpdateTime = info.lastUpdateTime,
                dataDir = appInfo?.dataDir,
                sourceDir = path,
                abis = emptyList()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Info for an installed package. */
    fun parseInstalled(context: Context, packageName: String): ApkInfo? {
        return try {
            val pm = context.packageManager
            val flags = PackageManager.GET_PERMISSIONS or
                PackageManager.GET_ACTIVITIES or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_SIGNING_CERTIFICATES
            val info = pm.getPackageInfo(packageName, flags)
            val appInfo = info.applicationInfo
            val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: packageName
            val sigs = getSignatures(info)
            ApkInfo(
                path = appInfo?.sourceDir ?: "",
                label = label,
                packageName = info.packageName,
                versionName = info.versionName ?: "",
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong(),
                minSdk = appInfo?.minSdkVersion ?: 0,
                targetSdk = appInfo?.targetSdkVersion ?: 0,
                sizeBytes = runCatching { java.io.File(appInfo?.sourceDir ?: "").length() }.getOrDefault(0L),
                isSystem = appInfo?.let { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 } ?: false,
                permissions = info.requestedPermissions?.toList() ?: emptyList(),
                activityCount = info.activities?.size ?: 0,
                serviceCount = info.services?.size ?: 0,
                receiverCount = info.receivers?.size ?: 0,
                signatureSha256 = sigs.first,
                signatureSha1 = sigs.second,
                signatureMd5 = sigs.third,
                installer = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        pm.getInstallSourceInfo(packageName).installingPackageName
                    } else {
                        pm.getInstallerPackageName(packageName)
                    }
                }.getOrNull(),
                firstInstallTime = info.firstInstallTime,
                lastUpdateTime = info.lastUpdateTime,
                dataDir = appInfo?.dataDir,
                sourceDir = appInfo?.sourceDir,
                abis = appInfo?.let { it.nativeLibraryDir?.let { d -> listOf(d.substringAfterLast('/')) } } ?: emptyList()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun loadIcon(context: Context, path: String): Drawable? {
        return try {
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(path, 0) ?: return null
            info.applicationInfo?.let { ai ->
                ai.sourceDir = path
                ai.publicSourceDir = path
            }
            info.applicationInfo?.loadIcon(pm)
        } catch (_: Exception) {
            null
        }
    }

    private fun getSignatures(info: PackageInfo): Triple<String, String, String> {
        val sigs: Array<out Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        val first = sigs?.firstOrNull() ?: return Triple("", "", "")
        return Triple(
            hash(first.toByteArray(), "SHA-256"),
            hash(first.toByteArray(), "SHA-1"),
            hash(first.toByteArray(), "MD5")
        )
    }

    private fun hash(bytes: ByteArray, algo: String): String =
        MessageDigest.getInstance(algo).digest(bytes).joinToString("") { "%02x".format(it) }
}
