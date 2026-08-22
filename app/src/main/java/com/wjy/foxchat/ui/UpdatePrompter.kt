package com.wjy.foxchat.ui

import androidx.activity.ComponentActivity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.wjy.foxchat.data.remote.UpdateInfo
import com.wjy.foxchat.data.remote.UpdateManager
import kotlinx.coroutines.launch
import java.io.File

/** 更新检查、提示、下载与安装的公共流程。 */
object UpdatePrompter {

    /** 检查更新并提示。silent = true 时仅在有新版本才弹窗（用于启动静默检查）。 */
    fun checkAndPrompt(activity: ComponentActivity, silent: Boolean = false) {
        if (!silent) {
            Toast.makeText(activity, "正在检查更新…", Toast.LENGTH_SHORT).show()
        }
        activity.lifecycleScope.launch {
            val info = UpdateManager.checkForUpdates().getOrNull()
            if (info == null) {
                if (!silent) {
                    Toast.makeText(activity, "已是最新版本", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            showDialog(activity, info)
        }
    }

    private fun showDialog(activity: ComponentActivity, info: UpdateInfo) {
        AlertDialog.Builder(activity)
            .setTitle("发现新版本 v${info.versionName}")
            .setMessage(info.changelog.ifBlank { "暂无更新说明" })
            .setPositiveButton("立即更新") { _, _ -> downloadAndInstall(activity, info) }
            .setNegativeButton("稍后", null)
            .show()
    }

    private fun downloadAndInstall(activity: ComponentActivity, info: UpdateInfo) {
        val progressDialog = ProgressDialog(activity).apply {
            setTitle("正在下载更新")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            setMax(100)
            setCancelable(false)
            show()
        }
        activity.lifecycleScope.launch {
            val result = UpdateManager.downloadApk(activity, info) { progress ->
                activity.runOnUiThread { progressDialog.progress = progress }
            }
            progressDialog.dismiss()
            result.onSuccess { file ->
                installApk(activity, file)
            }.onFailure { e ->
                Toast.makeText(activity, "下载失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun installApk(activity: ComponentActivity, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            Toast.makeText(activity, "请允许安装未知应用后再试", Toast.LENGTH_LONG).show()
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${activity.packageName}")
            })
            return
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }
}
