package com.example.iotgpt.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
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
        val file = createCacheFile(context, "audio", "recording", ".wav")
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

    fun withTextPreview(json: String?, textPreview: String): String? {
        if (json.isNullOrBlank()) return json
        return runCatching {
            JSONObject(json)
                .put("textPreview", textPreview)
                .toString()
        }.getOrDefault(json)
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
        val size = info.sizeBytes
        if (size != null && size > MAX_ATTACHMENT_BYTES) return null

        return when {
            isReadableText(info.mimeType, name) -> {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader().use { reader ->
                        reader.readText().take(MAX_TEXT_PREVIEW_CHARS)
                    }
                }
            }
            info.mimeType == "application/pdf" || name.endsWith(".pdf") -> {
                readPdfText(context, uri)
            }
            info.mimeType == DOCX_MIME_TYPE || name.endsWith(".docx") -> {
                readDocxText(context, uri)
            }
            else -> null
        }
    }

    fun unsupportedDocumentReason(info: AttachmentInfo): String? {
        val name = info.displayName.lowercase()
        return when {
            info.mimeType?.startsWith("text/") == true ||
                READABLE_TEXT_EXTENSIONS.any { name.endsWith(it) } ||
                info.mimeType == "application/pdf" ||
                info.mimeType == DOCX_MIME_TYPE ||
                name.endsWith(".pdf") ||
                name.endsWith(".docx") -> null
            name.endsWith(".doc") || info.mimeType == "application/msword" ->
                "旧版 .doc 是二进制 Word 格式，当前本地解析器暂不直接提取正文，请另存为 .docx 或 .pdf 后上传。"
            else ->
                "当前仅支持提取 txt、md、csv、json、xml、代码文本、PDF 和 DOCX 正文；该文件会作为附件信息发送。"
        }
    }

    fun readImageDataUrlIfPossible(
        context: Context,
        attachment: AttachmentPreview?
    ): String? {
        if (attachment?.type != "image") return null

        val uri = attachment.uri.toUri()
        val mimeType = attachment.mimeType
            ?: context.contentResolver.getType(uri)
            ?: "image/jpeg"
        if (!mimeType.startsWith("image/")) return null

        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes()
        } ?: return null

        val payload = if (bytes.size <= MAX_VISION_IMAGE_BYTES) {
            bytes to mimeType
        } else {
            val compressed = compressImageForVision(bytes) ?: return null
            compressed to "image/jpeg"
        }

        val encoded = Base64.encodeToString(payload.first, Base64.NO_WRAP)
        return "data:${payload.second};base64,$encoded"
    }

    fun readAttachmentBytesIfPossible(
        context: Context,
        uri: Uri,
        maxBytes: Long = MAX_ATTACHMENT_BYTES
    ): ByteArray? {
        val size = queryAttachmentInfo(context, uri).sizeBytes
        if (size != null && size > maxBytes) return null
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes()
        } ?: return null
        return bytes.takeIf { it.size <= maxBytes }
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

    private fun isReadableText(mimeType: String?, name: String): Boolean {
        return mimeType?.startsWith("text/") == true ||
            READABLE_TEXT_EXTENSIONS.any { name.endsWith(it) }
    }

    private fun readPdfText(context: Context, uri: Uri): String? {
        return runCatching {
            PDFBoxResourceLoader.init(context)
            context.contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input).use { document ->
                    PDFTextStripper().getText(document)
                        .trim()
                        .take(MAX_TEXT_PREVIEW_CHARS)
                        .takeIf { it.isNotBlank() }
                }
            }
        }.getOrNull()
    }

    private fun readDocxText(context: Context, uri: Uri): String? {
        return runCatching {
            val documentXml = context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    generateSequence { zip.nextEntry }
                        .firstOrNull { it.name == "word/document.xml" }
                        ?.let { zip.readBytes() }
                }
            } ?: return@runCatching null

            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
            }
            val document = factory.newDocumentBuilder()
                .parse(ByteArrayInputStream(documentXml))
            val nodes = document.getElementsByTagNameNS("*", "t")
            buildString {
                for (index in 0 until nodes.length) {
                    val text = nodes.item(index).textContent
                    if (text.isNotBlank()) {
                        if (isNotEmpty()) append(' ')
                        append(text.trim())
                    }
                }
            }.trim().take(MAX_TEXT_PREVIEW_CHARS).takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun compressImageForVision(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateVisionSampleSize(bounds.outWidth, bounds.outHeight)
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        return try {
            var quality = 88
            var fallback: ByteArray? = null
            while (quality >= 48) {
                val output = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
                val candidate = output.toByteArray()
                fallback = candidate
                if (candidate.size <= MAX_VISION_IMAGE_BYTES) {
                    return candidate
                }
                quality -= 10
            }
            fallback?.takeIf { it.size <= MAX_VISION_IMAGE_BYTES }
        } finally {
            bitmap.recycle()
        }
    }

    private fun calculateVisionSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > MAX_VISION_IMAGE_DIMENSION ||
            height / sampleSize > MAX_VISION_IMAGE_DIMENSION
        ) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private const val MAX_TEXT_PREVIEW_CHARS = 4000
    private const val DOCX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    private const val MAX_VISION_IMAGE_DIMENSION = 1600
    private val READABLE_TEXT_EXTENSIONS = setOf(
        ".txt",
        ".md",
        ".markdown",
        ".csv",
        ".json",
        ".xml",
        ".log",
        ".yaml",
        ".yml",
        ".ini",
        ".gradle",
        ".kt",
        ".java",
        ".py",
        ".js",
        ".ts",
        ".html",
        ".css",
        ".c",
        ".cpp",
        ".h"
    )
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
