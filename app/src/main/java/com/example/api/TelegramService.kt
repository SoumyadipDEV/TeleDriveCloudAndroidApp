package com.example.api

import android.content.Context
import android.util.Log
import com.example.data.VFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class TelegramService(baseUrlInput: String? = null) {

    private val baseApiUrl: String = validateBaseUrl(baseUrlInput)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseApiUrl)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val api = retrofit.create(TelegramBotApi::class.java)

    // Helper request body to track upload progress
    class ProgressRequestBody(
        private val file: File,
        private val contentType: String?,
        private val onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
    ) : RequestBody() {
        override fun contentType() = contentType?.toMediaTypeOrNull() ?: "application/octet-stream".toMediaTypeOrNull()
        override fun contentLength() = file.length()
        override fun writeTo(sink: BufferedSink) {
            val fileLength = file.length()
            val buffer = ByteArray(4096)
            val inStream = FileInputStream(file)
            var uploaded: Long = 0
            inStream.use { input ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    sink.write(buffer, 0, read)
                    uploaded += read
                    onProgress(uploaded, fileLength)
                }
            }
        }
    }

    // Custom request body for inline chunks (bytes)
    class BytesProgressRequestBody(
        private val bytes: ByteArray,
        private val contentType: String?,
        private val onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
    ) : RequestBody() {
        override fun contentType() = contentType?.toMediaTypeOrNull() ?: "application/octet-stream".toMediaTypeOrNull()
        override fun contentLength() = bytes.size.toLong()
        override fun writeTo(sink: BufferedSink) {
            val totalSize = bytes.size.toLong()
            val bufferSize = 4096
            var offset = 0
            while (offset < bytes.size) {
                val toWrite = minOf(bufferSize, bytes.size - offset)
                sink.write(bytes, offset, toWrite)
                offset += toWrite
                onProgress(offset.toLong(), totalSize)
            }
        }
    }

    /**
     * Uploads a file to Telegram.
     * If the file is larger than the chunk size, we chunk it into multiple uploads.
     */
    suspend fun uploadFile(
        context: Context,
        file: File,
        mimeType: String?,
        token: String,
        chatId: String,
        onProgress: (bytesUploaded: Long, totalBytes: Long, speedBps: Long, percent: Int) -> Unit
    ): UploadResult {
        val totalLength = file.length()
        if (totalLength <= 0) {
            return UploadResult.Error("Empty file")
        }

        // We use a chunk size of 15MB for bot uploads to make chunking easy to observe and safe for memory
        val chunkSize = 15 * 1024 * 1024L 

        if (totalLength <= chunkSize) {
            // Single file upload
            return uploadSingleFile(file, mimeType, token, chatId) { written, total ->
                val percent = ((written * 100) / total).toInt()
                onProgress(written, total, 0, percent) // speed calculated by ViewModel
            }
        } else {
            // Chunked upload
            val numChunks = ((totalLength + chunkSize - 1) / chunkSize).toInt()
            val fileIds = mutableListOf<String>()
            var totalBytesUploaded = 0L

            for (i in 0 until numChunks) {
                val start = i * chunkSize
                val end = minOf(start + chunkSize, totalLength)
                val currentChunkSize = (end - start).toInt()

                // Read chunk bytes
                val chunkBytes = ByteArray(currentChunkSize)
                FileInputStream(file).use { fis ->
                    fis.skip(start)
                    fis.read(chunkBytes)
                }

                val partName = "${file.name}.part${i + 1}_of_$numChunks"
                val body = BytesProgressRequestBody(chunkBytes, mimeType) { written, total ->
                    val progressForThisChunk = totalBytesUploaded + written
                    val percent = ((progressForThisChunk * 100) / totalLength).toInt()
                    onProgress(progressForThisChunk, totalLength, 0, percent)
                }

                val docPart = MultipartBody.Part.createFormData("document", partName, body)
                val chatPart = chatId.toRequestBody("text/plain".toMediaTypeOrNull())

                val response = withContext(Dispatchers.IO) {
                    try {
                        api.sendDocument(token, chatPart, docPart)
                    } catch (e: Exception) {
                        null
                    }
                }

                if (response != null && response.isSuccessful && response.body()?.ok == true) {
                    val fileId = response.body()?.result?.document?.file_id
                    if (fileId != null) {
                        fileIds.add(fileId)
                    } else {
                        return UploadResult.Error("Failed to get file_id for part ${i + 1}")
                    }
                } else {
                    val errorMsg = response?.errorBody()?.string() ?: "Network error or connection lost"
                    return UploadResult.Error("Chunk ${i + 1}/$numChunks failed: $errorMsg")
                }

                totalBytesUploaded += currentChunkSize
            }

            // Return success with comma-separated fileIds representing the chunks
            return UploadResult.Success(
                fileId = fileIds.joinToString(","),
                size = totalLength,
                isChunked = true
            )
        }
    }

    private suspend fun uploadSingleFile(
        file: File,
        mimeType: String?,
        token: String,
        chatId: String,
        onProgress: (Long, Long) -> Unit
    ): UploadResult = withContext(Dispatchers.IO) {
        try {
            val reqBody = ProgressRequestBody(file, mimeType, onProgress)
            val docPart = MultipartBody.Part.createFormData("document", file.name, reqBody)
            val chatPart = chatId.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = api.sendDocument(token, chatPart, docPart)
            if (response.isSuccessful && response.body()?.ok == true) {
                val doc = response.body()?.result?.document
                if (doc != null) {
                    UploadResult.Success(doc.file_id, doc.file_size, false)
                } else {
                    UploadResult.Error("Document upload response parsed but document was null")
                }
            } else {
                UploadResult.Error(response.errorBody()?.string() ?: "Failed with code: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e("TelegramService", "Upload exception", e)
            UploadResult.Error(e.localizedMessage ?: "Unknown network error")
        }
    }

    /**
     * Resolves a Telegram file ID into a direct streaming URL
     */
    suspend fun getStreamUrl(token: String, fileId: String): String? = withContext(Dispatchers.IO) {
        try {
            // If it's a multi-part chunked file, we stream the first chunk as a proxy or explain.
            // But let's fetch the path for the fileId
            val singleFileId = fileId.split(",").firstOrNull() ?: return@withContext null
            val response = api.getFile(token, singleFileId)
            if (response.isSuccessful && response.body()?.ok == true) {
                val filePath = response.body()?.result?.file_path
                if (filePath != null) {
                    "${baseApiUrl}file/bot$token/$filePath"
                } else null
            } else null
        } catch (e: Exception) {
            Log.e("TelegramService", "getFile exception", e)
            null
        }
    }

    /**
     * Downloads a file (including chunked files) to a local destination
     */
    suspend fun downloadFile(
        token: String,
        fileId: String,
        destination: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val fileIds = fileId.split(",")
            val totalParts = fileIds.size
            var totalBytesDownloaded = 0L

            FileOutputStream(destination).use { fos ->
                for (i in 0 until totalParts) {
                    val partId = fileIds[i]
                    val pathResponse = api.getFile(token, partId)
                    if (!pathResponse.isSuccessful || pathResponse.body()?.ok != true) {
                        return@withContext false
                    }

                    val filePath = pathResponse.body()?.result?.file_path ?: return@withContext false
                    val downloadUrl = "${baseApiUrl}file/bot$token/$filePath"

                    val downloadClient = OkHttpClient()
                    val request = okhttp3.Request.Builder().url(downloadUrl).build()
                    val response = downloadClient.newCall(request).execute()

                    if (!response.isSuccessful) return@withContext false

                    val body = response.body ?: return@withContext false
                    val totalLength = body.contentLength()

                    body.byteStream().use { input ->
                        val buffer = ByteArray(4096)
                        var read: Int
                        var partDownloaded = 0L
                        while (input.read(buffer).also { read = it } != -1) {
                            fos.write(buffer, 0, read)
                            partDownloaded += read
                            totalBytesDownloaded += read
                            onProgress(totalBytesDownloaded, totalLength * totalParts) // estimation
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e("TelegramService", "Download exception", e)
            false
        }
    }

    /**
     * Validates a Bot Token and Chat ID.
     * Returns a Pair: first is success/failure Boolean, second is an optional error/success string (like the chat title).
     */
    suspend fun validateCredentials(token: String, chatId: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val trimmedToken = token.trim()
        val trimmedChatId = chatId.trim()

        if (trimmedToken.isBlank()) {
            return@withContext Pair(false, "Bot token cannot be empty.")
        }
        if (trimmedChatId.isBlank()) {
            return@withContext Pair(false, "Chat / Channel ID cannot be empty.")
        }
        if (trimmedToken.contains(" ") || !trimmedToken.contains(":")) {
            return@withContext Pair(false, "Malformed Bot Token. It should look like '123456789:ABC-DEF1234ghIkl-zyx57W2v1u...' without spaces.")
        }

        try {
            // 1. Verify Bot Token
            val meResponse = api.getMe(trimmedToken)
            if (!meResponse.isSuccessful || meResponse.body()?.ok != true) {
                val errorMsg = meResponse.body()?.description ?: "Invalid bot token (Unauthorized)"
                return@withContext Pair(false, "Bot verification failed: $errorMsg")
            }
            
            val botUser = meResponse.body()?.result
            val botUsername = botUser?.username ?: "Bot"

            // 2. Verify Chat / Channel Access
            val chatResponse = api.getChat(trimmedToken, trimmedChatId)
            if (!chatResponse.isSuccessful || chatResponse.body()?.ok != true) {
                val errorMsg = chatResponse.body()?.description ?: "Bot cannot access this chat/channel"
                return@withContext Pair(false, "Chat access failed: $errorMsg. (Tip: Make sure your bot is added to your channel/group as an admin/member!)")
            }

            val chat = chatResponse.body()?.result
            val chatTitle = chat?.title ?: chat?.username ?: "Telegram Cloud"
            Pair(true, chatTitle)
        } catch (e: Exception) {
            Log.e("TelegramService", "validateCredentials exception", e)
            Pair(false, "Connection error: ${e.localizedMessage ?: "Please check your network and try again."}")
        }
    }

    companion object {
        private const val DEFAULT_BASE_URL = "https://api.telegram.org/"

        fun validateBaseUrl(input: String?): String {
            val trimmed = input?.trim() ?: ""
            if (trimmed.isEmpty()) {
                return DEFAULT_BASE_URL
            }

            // 1. Ensure scheme is present
            var urlWithScheme = trimmed
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                urlWithScheme = "https://$trimmed"
            }

            // 2. Ensure trailing slash
            if (!urlWithScheme.endsWith("/")) {
                urlWithScheme = "$urlWithScheme/"
            }

            // 3. Try parsing with OkHttp's HttpUrl to see if it is a well-formed base URL
            val httpUrl = urlWithScheme.toHttpUrlOrNull()
            if (httpUrl == null) {
                // If it's malformed, fallback to DEFAULT_BASE_URL to avoid app crash
                Log.e("TelegramService", "Malformed base URL input: $trimmed. Falling back to default.")
                return DEFAULT_BASE_URL
            }

            return httpUrl.toString()
        }
    }
}

sealed class UploadResult {
    data class Success(val fileId: String, val size: Long, val isChunked: Boolean) : UploadResult()
    data class Error(val message: String) : UploadResult()
}
