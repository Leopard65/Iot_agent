package com.example.iotgpt.core.util

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Cache-based WAV recorder. MiMo ASR accepts WAV/MP3 data URLs, so local
 * recordings are stored as PCM WAV instead of AAC/M4A.
 */
class AudioRecorder(
    private val context: Context
) {
    private var recorder: AudioRecord? = null
    private var outputFile: File? = null
    private var outputUri: Uri? = null
    private var recordingThread: Thread? = null
    @Volatile
    private var isRecording: Boolean = false

    fun start(): Uri {
        stopSilently()
        val (file, uri) = FileUtils.createAudioUri(context)
        outputFile = file
        outputUri = uri

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        val bufferSize = minBufferSize.coerceAtLeast(SAMPLE_RATE / 2)
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
        recorder = audioRecord
        isRecording = true

        RandomAccessFile(file, "rw").use { wav ->
            wav.setLength(0)
            wav.write(ByteArray(WAV_HEADER_BYTES))
        }

        audioRecord.startRecording()
        recordingThread = Thread(
            {
                writePcmToFile(file, audioRecord, bufferSize)
            },
            "iotgpt-wav-recorder"
        ).apply { start() }

        return uri
    }

    fun stop(): Uri? {
        val uri = outputUri
        val file = outputFile
        stopSilently()
        if (file != null && file.exists()) {
            updateWavHeader(file)
        }
        return uri
    }

    fun cancel() {
        val file = outputFile
        stopSilently()
        file?.delete()
    }

    private fun writePcmToFile(
        file: File,
        audioRecord: AudioRecord,
        bufferSize: Int
    ) {
        val buffer = ByteArray(bufferSize)
        runCatching {
            RandomAccessFile(file, "rw").use { wav ->
                wav.seek(WAV_HEADER_BYTES.toLong())
                while (isRecording) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        wav.write(buffer, 0, read)
                    }
                }
            }
        }
    }

    private fun stopSilently() {
        isRecording = false
        runCatching {
            recordingThread?.join(700)
        }
        recordingThread = null
        runCatching {
            recorder?.stop()
        }
        recorder?.release()
        recorder = null
    }

    private fun updateWavHeader(file: File) {
        val pcmBytes = (file.length() - WAV_HEADER_BYTES).coerceAtLeast(0)
        val totalDataLen = pcmBytes + 36
        val byteRate = SAMPLE_RATE * CHANNEL_COUNT * BITS_PER_SAMPLE / 8
        val blockAlign = CHANNEL_COUNT * BITS_PER_SAMPLE / 8

        val header = ByteBuffer.allocate(WAV_HEADER_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray(Charsets.US_ASCII))
            .putInt(totalDataLen.toInt())
            .put("WAVE".toByteArray(Charsets.US_ASCII))
            .put("fmt ".toByteArray(Charsets.US_ASCII))
            .putInt(16)
            .putShort(1.toShort())
            .putShort(CHANNEL_COUNT.toShort())
            .putInt(SAMPLE_RATE)
            .putInt(byteRate)
            .putShort(blockAlign.toShort())
            .putShort(BITS_PER_SAMPLE.toShort())
            .put("data".toByteArray(Charsets.US_ASCII))
            .putInt(pcmBytes.toInt())
            .array()

        RandomAccessFile(file, "rw").use { wav ->
            wav.seek(0)
            wav.write(header)
        }
    }

    private companion object {
        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL_COUNT = 1
        private const val BITS_PER_SAMPLE = 16
        private const val WAV_HEADER_BYTES = 44
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
}
