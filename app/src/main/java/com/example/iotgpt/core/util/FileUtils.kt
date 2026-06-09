package com.example.iotgpt.core.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import org.json.JSONObject

/**
 * File and attachment helpers used by multimodal chat input.
 */
object FileUtils {
    const val MAX_ATTACHMENT_BYTES = 10L * 1024L * 1024L
    private const val MAX_VISION_IMAGE_BYTES = 4L * 1024L * 1024L

    fun createImageUri(context: Context): Uri {
        val file = createCacheFile(context, "images", "photo", ".jpg")
        return file.toContentUri(context)
    }

    fun createImageCapture(context: Context, child: String = "images"): CapturedFile {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: context.cacheDir
        val dir = File(baseDir, child).apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        return CapturedFile(
            file = file,
            uri = file.toContentUri(context),
            displayPath = file.absolutePath
        )
    }

    fun createAudioUri(context: Context): Pair<File, Uri> {
        val file = createCacheFile(context, "audio", "recording", ".m4a")
        return file to file.toContentUri(context)
    }

    fun buildAttachmentJson(
        context: Context,
        type: String,
        uri: Uri,
        textPreview: String? = null
    ): String {
        val info = queryAttachmentInfo(context, uri)
        return JSONObject()
            .put("type", type)
            .put("uri", uri.toString())
            .put("displayName", info.displayName)
            .put("sizeBytes", info.sizeBytes ?: JSONObject.NULL)
            .put("mimeType", info.mimeType ?: JSONObject.NULL)
            .put("textPreview", textPreview ?: JSONObject.NULL)
            .toString()
    }

    fun parseAttachmentJson(json: String?): AttachmentPreview? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val item = JSONObject(json)
            AttachmentPreview(
                type = item.optString("type", "file"),
                uri = item.optString("uri"),
                displayName = item.optString("displayName", "附件"),
                sizeBytes = item.optLongOrNull("sizeBytes"),
                mimeType = item.optStringOrNull("mimeType"),
                textPreview = item.optStringOrNull("textPreview")
            )
        }.getOrNull()
    }

    fun queryAttachmentInfo(context: Context, uri: Uri): AttachmentInfo {
        var displayName: String? = null
        var sizeBytes: Long? = null
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) displayName = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        }

        return AttachmentInfo(
            displayName = displayName ?: uri.lastPathSegment ?: "附件",
            sizeBytes = sizeBytes,
            mimeType = context.contentResolver.getType(uri)
        )
    }

    fun readTextPreviewIfPossible(context: Context, uri: Uri): String? {
        val info = queryAttachmentInfo(context, uri)
        val name = info.displayName.lowercase()
        val isText = info.mimeType?.startsWith("text/") == true || name.endsWith(".txt")
        val size = info.sizeBytes
        if (!isText || size == null || size > MAX_ATTACHMENT_BYTES) return null

        return context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader().use { reader ->
                reader.readText().take(MAX_TEXT_PREVIEW_CHARS)
            }
        }
    }

    fun readImageDataUrlIfPossible(
        context: Context,
        attachment: AttachmentPreview?
    ): String? {
        if (attachment?.type != "image") return null
        val size = attachment.sizeBytes
        if (size != null && size > MAX_VISION_IMAGE_BYTES) return null

        val uri = attachment.uri.toUri()
        val mimeType = attachment.mimeType
            ?: context.contentResolver.getType(uri)
            ?: "image/jpeg"
        if (!mimeType.startsWith("image/")) return null

        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes()
        } ?: return null
        if (bytes.size > MAX_VISION_IMAGE_BYTES) return null

        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:$mimeType;base64,$encoded"
    }

    fun isTooLarge(context: Context, uri: Uri): Boolean {
        val size = queryAttachmentInfo(context, uri).sizeBytes
        return size != null && size > MAX_ATTACHMENT_BYTES
    }

    fun formatSize(sizeBytes: Long?): String {
        if (sizeBytes == null) return "大小未知"
        val kb = sizeBytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        return "%.1f MB".format(kb / 1024.0)
    }

    private fun createCacheFile(
        context: Context,
        child: String,
        prefix: String,
        suffix: String
    ): File {
        val dir = File(context.cacheDir, child).apply { mkdirs() }
        return File(dir, "${prefix}_${System.currentTimeMillis()}$suffix")
    }

    private fun File.toContentUri(context: Context): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            this
        )
    }

    private fun JSONObject.optLongOrNull(name: String): Long? {
        return if (has(name) && !isNull(name)) optLong(name) else null
    }

    private fun JSONObject.optStringOrNull(name: String): String? {
        return if (has(name) && !isNull(name)) optString(name) else null
    }

    private const val MAX_TEXT_PREVIEW_CHARS = 4000
}

data class AttachmentInfo(
    val displayName: String,
    val sizeBytes: Long?,
    val mimeType: String?
)

data class CapturedFile(
    val file: File,
    val uri: Uri,
    val displayPath: String
)

data class AttachmentPreview(
    val type: String,
    val uri: String,
    val displayName: String,
    val sizeBytes: Long?,
    val mimeType: String?,
    val textPreview: String?
)
