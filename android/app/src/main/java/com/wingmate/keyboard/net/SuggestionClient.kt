package com.wingmate.keyboard.net

import com.wingmate.keyboard.data.ChatMessage
import com.wingmate.keyboard.data.Tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class SuggestRequest(
    val messages: List<ChatMessage>,
    val draft: String? = null,
    val tone: String = "any",
    val userProfile: String? = null,
    val count: Int = 3,
)

@Serializable
data class Suggestion(val text: String, val tone: String)

@Serializable
data class SuggestResponse(val suggestions: List<Suggestion>, val read: String = "")

class SuggestionException(message: String) : Exception(message)

/** Talks to the backend proxy described in /shared/api-contract.md. */
class SuggestionClient(
    private val baseUrl: () -> String,
    private val appToken: () -> String,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    suspend fun suggest(
        messages: List<ChatMessage>,
        draft: String?,
        tone: Tone,
        aboutMe: String?,
        count: Int = 3,
    ): SuggestResponse = withContext(Dispatchers.IO) {
        val payload = SuggestRequest(
            messages = messages.takeLast(30).map { it.copy(text = it.text.take(1000)) },
            draft = draft?.takeIf { it.isNotBlank() }?.take(1000),
            tone = tone.wire,
            userProfile = aboutMe?.takeIf { it.isNotBlank() }?.take(500),
            count = count,
        )
        try {
            val request = Request.Builder()
                .url("${baseUrl()}/v1/suggest") // throws IllegalArgumentException on a bad URL
                .header("X-App-Token", appToken())
                .post(json.encodeToString(payload).toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(request).execute().use { res ->
                val body = res.body?.string().orEmpty()
                when (res.code) {
                    200 -> json.decodeFromString<SuggestResponse>(body)
                    401 -> throw SuggestionException("App token rejected. Check Settings.")
                    429 -> throw SuggestionException("Slow down a little and try again.")
                    else -> throw SuggestionException("Couldn't get suggestions (${res.code}).")
                }
            }
        } catch (e: IOException) {
            throw SuggestionException("Can't reach the server. Check your connection.")
        } catch (e: SerializationException) {
            throw SuggestionException("Got an unexpected reply from the server.")
        } catch (e: IllegalArgumentException) {
            throw SuggestionException("Server address looks wrong. Check Settings.")
        }
    }
}
