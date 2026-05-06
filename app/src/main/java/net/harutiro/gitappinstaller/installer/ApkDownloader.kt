package net.harutiro.gitappinstaller.installer

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

sealed interface DownloadEvent {
    data class Progress(val downloadedBytes: Long, val totalBytes: Long) : DownloadEvent
    data class Completed(val file: File) : DownloadEvent
    data class Failed(val reason: String) : DownloadEvent
}

class ApkDownloader(private val context: Context) {

    private val dm: DownloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun download(
        url: String,
        fileName: String,
        headers: Map<String, String> = emptyMap(),
    ): Flow<DownloadEvent> = callbackFlow {
        val safeName = fileName.ifBlank { "download.apk" }
        // Ensure target dir
        val targetDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "")
        targetDir?.mkdirs()
        val destFile = File(targetDir, safeName)
        if (destFile.exists()) destFile.delete()

        val request = DownloadManager.Request(Uri.parse(url))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setTitle(safeName)
            .setMimeType("application/vnd.android.package-archive")
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, safeName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        headers.forEach { (k, v) -> request.addRequestHeader(k, v) }

        val downloadId = dm.enqueue(request)
        Log.i(TAG, "enqueued: id=$downloadId url=$url headers=${headers.keys} dest=${destFile.absolutePath}")

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                if (id != downloadId) return
                val query = DownloadManager.Query().setFilterById(downloadId)
                dm.query(query)?.use { cursor: Cursor ->
                    if (cursor.moveToFirst()) {
                        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                Log.i(TAG, "download successful: id=$id file=${destFile.absolutePath} exists=${destFile.exists()} size=${destFile.length()}")
                                trySend(DownloadEvent.Completed(destFile))
                                close()
                            }
                            DownloadManager.STATUS_FAILED -> {
                                val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                                val reason = if (reasonIdx >= 0) cursor.getInt(reasonIdx).toString() else "unknown"
                                Log.w(TAG, "download failed: id=$id reason=$reason")
                                trySend(DownloadEvent.Failed("DownloadManager failed: $reason"))
                                close()
                            }
                        }
                    }
                }
            }
        }

        // ACTION_DOWNLOAD_COMPLETE is a system broadcast from system_server, so the
        // receiver must be RECEIVER_EXPORTED on Android 13+ to be invoked.
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)

        awaitClose {
            // Note: DownloadManager.remove() deletes the downloaded file, so we deliberately
            // do not call it here. The collector is responsible for cleaning up the APK after
            // installation succeeds (or it can be left in app-private storage).
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    companion object { private const val TAG = "ApkDownloader" }
}
