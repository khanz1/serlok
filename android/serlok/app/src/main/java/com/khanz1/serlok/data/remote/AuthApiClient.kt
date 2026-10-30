package com.khanz1.serlok.data.remote

import android.os.Build
import com.khanz1.serlok.BuildConfig
import com.khanz1.serlok.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class AuthUser(val id: String, val name: String, val email: String)
data class AuthResult(val token: String, val user: AuthUser)

class AuthApiClient(
    serverUrl: String = BuildConfig.HTTP_BASE_URL,
    private val deviceInstanceId: String = AppSettings.deviceInstanceId,
    private val deviceManufacturer: String = Build.MANUFACTURER,
    private val deviceModel: String = Build.MODEL,
    private val client: OkHttpClient = OkHttpClient(),
) {

    private val baseUrl = serverUrl
        .replace("wss://", "https://")
        .replace("ws://",  "http://")
        .trimEnd('/')

    private val json = "application/json".toMediaType()

    suspend fun register(name: String, email: String, password: String): Result<AuthResult> =
        post(
            "$baseUrl/api/auth/register",
            JSONObject().apply {
                put("name", name)
                put("email", email)
                put("password", password)
            },
        )

    suspend fun login(email: String, password: String): Result<AuthResult> =
        post(
            "$baseUrl/api/auth/login",
            JSONObject().apply {
                put("email", email)
                put("password", password)
            },
        )

    suspend fun logout(): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/auth/logout")
                    .post("{}".toRequestBody(json))
                    .header("Authorization", "Bearer ${AppSettings.token}")
                    .build()
                val response = client.newCall(request).execute()
                val raw = response.body?.string() ?: "{}"
                val parsed = JSONObject(raw)
                if (!response.isSuccessful) {
                    val msg = parsed.optString("error", "Request failed (${response.code})")
                    return@withContext Result.failure(Exception(msg))
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun post(url: String, body: JSONObject): Result<AuthResult> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .post(body.toString().toRequestBody(json))
                    .header("X-Device-Id", deviceInstanceId)
                    .header("X-Device-Manufacturer", deviceManufacturer)
                    .header("X-Device-Model", deviceModel)
                    .build()

                val response = client.newCall(request).execute()
                val raw = response.body?.string() ?: ""
                val parsed = JSONObject(raw)

                if (!response.isSuccessful) {
                    val msg = parsed.optString("error", "Request failed (${response.code})")
                    return@withContext Result.failure(Exception(msg))
                }

                val data = parsed.getJSONObject("data")
                val user = data.getJSONObject("user")
                Result.success(
                    AuthResult(
                        token = data.getString("token"),
                        user = AuthUser(
                            id = user.getString("id"),
                            name = user.getString("name"),
                            email = user.getString("email"),
                        ),
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
