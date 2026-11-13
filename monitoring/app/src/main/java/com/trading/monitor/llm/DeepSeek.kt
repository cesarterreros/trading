package com.trading.monitor.llm

import com.trading.monitor.Settings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class DeepSeek(private val http: OkHttpClient, private val settings: Settings) {

    data class Result(val content: String, val tokens: Long)

    /** Blocking; call from a background thread. Throws IOException on network or API errors. */
    fun chat(system: String, user: String): Result {
        val body = JSONObject()
            .put("model", settings.model)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user))
            )
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("temperature", 0)
            .put("max_tokens", 600)

        val request = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .header("Authorization", "Bearer ${settings.apiKey}")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("DeepSeek HTTP ${response.code}: ${text.take(200)}")
            try {
                val json = JSONObject(text)
                val content = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                val tokens = json.optJSONObject("usage")?.optLong("total_tokens", 0) ?: 0
                return Result(content, tokens)
            } catch (e: Exception) {
                throw IOException("DeepSeek returned an unexpected response: ${text.take(200)}")
            }
        }
    }
}
