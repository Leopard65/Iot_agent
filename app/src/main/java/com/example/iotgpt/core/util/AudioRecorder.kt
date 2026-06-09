package com.example.iotgpt.core.util

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import java.io.File

/**
 * Small MediaRecorder wrapper for cache-based audio capture.
 */
class AudioRecorder(
    private val context: Context
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var outputUri: Uri? = null

    fun start(): Uri {
        stopSilently()
        val (file, uri) = FileUtils.createAudioUri(context)
        outputFile = file
        outputUri = uri

        recorder = newRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        return uri
    }

    fun stop(): Uri? {
        val uri = outputUri
        stopSilently()
        return uri
    }

    fun cancel() {
        val file = outputFile
        stopSilently()
        file?.delete()
    }

    private fun stopSilently() {
        runCatching {
            recorder?.stop()
        }
        recorder?.release()
        recorder = null
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
    }
}
