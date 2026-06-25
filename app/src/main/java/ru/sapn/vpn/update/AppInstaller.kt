package ru.sapn.vpn.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import ru.sapn.vpn.R

/**
 * Скачивает APK обновления через системный DownloadManager (умеет редиректы
 * GitHub → CDN, показывает прогресс в шторке) и по завершении запускает системную
 * установку. Это надёжнее, чем открывать ссылку в браузере (там загрузка часто
 * «висит на 100%» и установка не стартует).
 *
 * Требует разрешения REQUEST_INSTALL_PACKAGES (в манифесте); при первой установке
 * система попросит включить «Установку из неизвестных источников» для приложения.
 */
object AppInstaller {

    private const val TAG = "AppInstaller"
    private const val MIME = "application/vnd.android.package-archive"

    fun downloadAndInstall(context: Context, url: String, versionName: String) {
        val appCtx = context.applicationContext
        val dm = appCtx.getSystemService(DownloadManager::class.java)
        if (dm == null) {
            fallbackOpen(appCtx, url)
            return
        }

        val request = runCatching {
            DownloadManager.Request(Uri.parse(url))
                .setTitle(appCtx.getString(R.string.update_download_title, versionName))
                .setDescription(appCtx.getString(R.string.update_download_description))
                .setMimeType(MIME)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(appCtx, Environment.DIRECTORY_DOWNLOADS, "sapn-update.apk")
        }.getOrElse {
            fallbackOpen(appCtx, url)
            return
        }

        val id = runCatching { dm.enqueue(request) }.getOrElse {
            fallbackOpen(appCtx, url)
            return
        }
        Toast.makeText(appCtx, appCtx.getString(R.string.update_downloading), Toast.LENGTH_SHORT).show()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val doneId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (doneId != id) return
                runCatching { appCtx.unregisterReceiver(this) }
                val uri = runCatching { dm.getUriForDownloadedFile(id) }.getOrNull()
                if (uri == null) {
                    Log.w(TAG, "downloaded file uri is null")
                    Toast.makeText(appCtx, appCtx.getString(R.string.update_install_failed), Toast.LENGTH_SHORT).show()
                    return
                }
                val install = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, MIME)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { appCtx.startActivity(install) }
                    .onFailure { Log.w(TAG, "install intent failed: ${it.message}") }
            }
        }
        ContextCompat.registerReceiver(
            appCtx,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    /** Фолбэк: открыть ссылку (старое поведение), если DownloadManager недоступен. */
    private fun fallbackOpen(context: Context, url: String) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(i) }
    }
}
