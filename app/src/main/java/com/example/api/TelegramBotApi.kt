package com.example.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

import com.squareup.moshi.JsonClass

interface TelegramBotApi {

    @Multipart
    @POST("bot{token}/sendDocument")
    suspend fun sendDocument(
        @Path("token") token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part document: MultipartBody.Part
    ): Response<TelegramResponse<TelegramMessage>>

    @GET("bot{token}/getFile")
    suspend fun getFile(
        @Path("token") token: String,
        @Query("file_id") fileId: String
    ): Response<TelegramResponse<TelegramFile>>

    @GET("bot{token}/getMe")
    suspend fun getMe(
        @Path("token") token: String
    ): Response<TelegramResponse<TelegramUser>>

    @GET("bot{token}/getChat")
    suspend fun getChat(
        @Path("token") token: String,
        @Query("chat_id") chatId: String
    ): Response<TelegramResponse<TelegramChat>>
}

@JsonClass(generateAdapter = true)
data class TelegramResponse<T>(
    val ok: Boolean,
    val result: T?,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramUser(
    val id: Long,
    val is_bot: Boolean,
    val first_name: String,
    val username: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramChat(
    val id: Long,
    val type: String,
    val title: String? = null,
    val username: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramMessage(
    val message_id: Int,
    val document: TelegramDocument? = null
)

@JsonClass(generateAdapter = true)
data class TelegramDocument(
    val file_id: String,
    val file_unique_id: String,
    val file_name: String? = null,
    val mime_type: String? = null,
    val file_size: Long = 0L
)

@JsonClass(generateAdapter = true)
data class TelegramFile(
    val file_id: String,
    val file_unique_id: String,
    val file_size: Long = 0L,
    val file_path: String? = null
)
