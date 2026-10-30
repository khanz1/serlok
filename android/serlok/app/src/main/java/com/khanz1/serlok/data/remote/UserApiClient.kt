package com.khanz1.serlok.data.remote

import android.os.Build
import com.khanz1.serlok.BuildConfig
import com.khanz1.serlok.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class UserMe(val id: String, val name: String, val email: String)

object UserApiClient {

    private val client = OkHttpClient()

    private val baseUrl: String
        get() = BuildConfig.HTTP_BASE_URL.trimEnd('/')

    /** GET /api/users/me. Returns null on any failure (network, 401, parse). */
    suspend fun fetchMe(): UserMe? = withContext(Dispatchers.IO) {
        val token = AppSettings.token
        if (token.isBlank()) return@withContext null

        runCatching {
            val request = Request.Builder()
                .url("$baseUrl/api/users/me")
                .get()
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id",           AppSettings.deviceInstanceId)
                .header("X-Device-Manufacturer", Build.MANUFACTURER)
                .header("X-Device-Model",        Build.MODEL)
                .build()

            val response = client.newCall(request).execute()
            val raw = response.body?.string() ?: return@runCatching null
            if (response.code == 401) {
                AppSettings.clearAuth()
                return@runCatching null
            }
            if (!response.isSuccessful) return@runCatching null

            val data = JSONObject(raw).getJSONObject("data")
            UserMe(
                id    = data.getString("id"),
                name  = data.getString("name"),
                email = data.getString("email"),
            )
        }.getOrNull()
    }
}
