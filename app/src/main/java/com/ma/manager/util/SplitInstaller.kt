package com.ma.manager.util

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import org.json.JSONObject
import java.io.File

/**
 * Installer for split packages: .apks (bundletool), .xapk and .apkm.
 *
 *  - root:    `pm install-create / install-write / install-commit` (silent)
 *  - no root: [PackageInstaller] session, the system shows its confirm dialog
 *
 * .xapk expansion files (.obb) are copied to /sdcard/Android/obb/<package>/.
 */
object SplitInstaller {

    private val EXTS = setOf("apks", "xapk", "apkm")

    fun isSplitPackage(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in EXTS

    private class Pack(val apks: List<File>, val obbs: List<Pair<String, File>>, val pkg: String?)

    private fun shq(s: String) = "'" + s.replace("'", "'\\''") + "'"

    private fun norm(abi: String) = abi.lowercase().replace('-', '_')

    /** ABI config splits for architectures this device cannot run are skipped. */
    private fun abiAllowed(fileName: String): Boolean {
        val m = Regex("(arm64[_-]v8a|armeabi[_-]v7a|x86_64|x86|armeabi)", RegexOption.IGNORE_CASE)
            .find(fileName.substringAfterLast('/')) ?: return true
        val supported = Build.SUPPORTED_ABIS.map { norm(it) }
        return norm(m.value) in supported
    }

    private fun extract(archive: String, dir: File): Pack? {
        dir.deleteRecursively()
        dir.mkdirs()
        val zip = ZipFile(archive)
        val headers = zip.fileHeaders.filter { !it.isDirectory }
        val allApks = headers.filter { it.fileName.lowercase().endsWith(".apk") }
        // bundletool: use splits/ only (standalones/ would be a second, conflicting set)
        val chosen = allApks.filter { it.fileName.startsWith("splits/") }.ifEmpty { allApks }
            .filter { abiAllowed(it.fileName) }
        if (chosen.isEmpty()) return null

        val files = chosen.mapIndexed { i, h ->
            val out = File(dir, "split_$i.apk")
            zip.getInputStream(h).use { input -> out.outputStream().use { input.copyTo(it, 65536) } }
            out
        }
        val obbs = headers.filter { it.fileName.lowercase().endsWith(".obb") }.map { h ->
            val out = File(dir, "obb_" + h.fileName.substringAfterLast('/'))
            zip.getInputStream(h).use { input -> out.outputStream().use { input.copyTo(it, 65536) } }
            h.fileName to out
        }
        var pkg: String? = null
        headers.firstOrNull { it.fileName == "manifest.json" }?.let { h ->
            runCatching {
                val text = zip.getInputStream(h).use { it.readBytes().toString(Charsets.UTF_8) }
                pkg = JSONObject(text).optString("package_name").ifEmpty { null }
            }
        }
        if (pkg == null) {
            // Android/obb/<package>/main.1.<package>.obb
            pkg = obbs.firstNotNullOfOrNull { (name, _) ->
                Regex("Android/obb/([^/]+)/", RegexOption.IGNORE_CASE).find(name)?.groupValues?.get(1)
            }
        }
        return Pack(files, obbs, pkg)
    }

    /** Returns (success, message). */
    suspend fun install(
        ctx: Context,
        archivePath: String,
        useRoot: Boolean,
        onStatus: (String) -> Unit
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            onStatus("正在解析安装包...")
            val pack = extract(archivePath, File(ctx.cacheDir, "split"))
                ?: return@withContext false to "安装包内没有 APK"

            val obbNote = copyObb(pack, useRoot)

            if (useRoot) {
                onStatus("正在安装（Root）...")
                val (ok, msg) = installRoot(pack.apks)
                if (ok) return@withContext true to ("安装成功" + obbNote)
                onStatus("Root 安装失败：$msg，改用系统安装器")
            }
            val r = installSession(ctx, pack.apks)
            if (r.first) r.first to (r.second + obbNote) else r
        } catch (e: Exception) {
            e.printStackTrace()
            false to "安装失败：${e.message}"
        }
    }

    private fun copyObb(pack: Pack, useRoot: Boolean): String {
        if (pack.obbs.isEmpty()) return ""
        val pkg = pack.pkg ?: return "（未识别包名，OBB 未复制）"
        val dest = File("/storage/emulated/0/Android/obb/$pkg")
        var ok = true
        for ((name, f) in pack.obbs) {
            val target = File(dest, name.substringAfterLast('/'))
            var done = runCatching {
                dest.mkdirs()
                f.inputStream().use { i -> target.outputStream().use { i.copyTo(it, 65536) } }
                true
            }.getOrDefault(false)
            if (!done && useRoot) {
                done = Shell.cmd(
                    "mkdir -p ${shq(dest.path)} && cp ${shq(f.path)} ${shq(target.path)}"
                ).exec().isSuccess
            }
            if (!done) ok = false
        }
        return if (ok) "，已复制 OBB" else "，OBB 复制失败，请手动放入 Android/obb/$pkg"
    }

    private fun installRoot(apks: List<File>): Pair<Boolean, String> {
        val total = apks.sumOf { it.length() }
        val create = Shell.cmd("pm install-create -r -d -S $total").exec()
        val createText = (create.out + create.err).joinToString("\n")
        val sid = Regex("\\[(\\d+)]").find(createText)?.groupValues?.get(1)
            ?: return false to createText.lines().lastOrNull { it.isNotBlank() }.orEmpty().ifEmpty { "无法创建安装会话" }
        for ((i, f) in apks.withIndex()) {
            val w = Shell.cmd("pm install-write -S ${f.length()} $sid split_$i.apk - < ${shq(f.path)}").exec()
            val t = (w.out + w.err).joinToString("\n")
            if (!t.contains("Success")) {
                Shell.cmd("pm install-abandon $sid").exec()
                return false to t.lines().lastOrNull { it.isNotBlank() }.orEmpty().ifEmpty { "写入失败" }
            }
        }
        val c = Shell.cmd("pm install-commit $sid").exec()
        val ct = (c.out + c.err).joinToString("\n")
        return if (ct.contains("Success")) true to "ok"
        else false to ct.lines().lastOrNull { it.isNotBlank() }.orEmpty().ifEmpty { "提交失败" }
    }

    private fun installSession(ctx: Context, apks: List<File>): Pair<Boolean, String> {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    android.net.Uri.parse("package:${ctx.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return true to "请先允许“安装未知应用”，授权后再安装一次"
        }
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setSize(apks.sumOf { it.length() })
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            for (f in apks) {
                session.openWrite(f.name, 0, f.length()).use { out ->
                    f.inputStream().use { it.copyTo(out, 65536) }
                    session.fsync(out)
                }
            }
            val intent = Intent(ctx, InstallResultReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pi.intentSender)
        }
        return true to "已提交，请在系统安装界面确认"
    }
}

/** Receives PackageInstaller session results and opens the system confirm dialog. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
            }
            PackageInstaller.STATUS_SUCCESS ->
                Toast.makeText(context, "安装成功", Toast.LENGTH_SHORT).show()
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                Toast.makeText(context, "安装失败($status) $msg", Toast.LENGTH_LONG).show()
            }
        }
    }
}
