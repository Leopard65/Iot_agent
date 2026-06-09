package com.example.iotgpt.core.worker

import android.content.Context
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.example.iotgpt.core.notification.NotificationHelper
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * WorkManager task for controlled classroom download demonstrations.
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    private val client = OkHttpClient()

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL).orEmpty()
        if (url.isBlank()) return Result.failure()

        return runCatching {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return Result.failure()
                val body = response.body ?: return Result.failure()
                val total = body.contentLength().coerceAtLeast(1L)
                val outputFile = createOutputFile(url)

                body.byteStream().use { input ->
                    outputFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val progress = ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                            setProgress(Data.Builder().putInt(KEY_PROGRESS, progress).build())
                        }
                    }
                }

                NotificationHelper(applicationContext).showTaskComplete(
                    taskId = id.toString(),
                    title = "下载任务完成",
                    content = outputFile.name
                )

                Result.success(
                    Data.Builder()
                        .putString(KEY_FILE_PATH, outputFile.absolutePath)
                        .build()
                )
            }
        }.getOrElse {
            Result.failure()
        }
    }

    private fun createOutputFile(url: String): File {
        val dir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(applicationContext.filesDir, "downloads")
        dir.mkdirs()
        val name = url.substringAfterLast('/').substringBefore('?').ifBlank {
            "download_${System.currentTimeMillis()}.bin"
        }
        return File(dir, name)
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_PROGRESS = "progress"
        const val KEY_FILE_PATH = "file_path"
    }
}
