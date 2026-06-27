package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object LyricsService {
    private const val TAG = "LyricsService"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    /**
     * Searches for synced lyrics for a song.
     * Tries Musixmatch first (if key is set), then falls back to Gemini API LRC generator.
     */
    suspend fun searchLyrics(title: String, artist: String): String = withContext(Dispatchers.IO) {
        val musixmatchKey = BuildConfig.MUSIXMATCH_API_KEY
        val isMusixmatchConfigured = musixmatchKey.isNotEmpty() && musixmatchKey != "MY_MUSIXMATCH_API_KEY"

        if (isMusixmatchConfigured) {
            try {
                val trackId = searchMusixmatchTrack(title, artist, musixmatchKey)
                if (trackId != null) {
                    val subtitle = fetchMusixmatchSubtitle(trackId, musixmatchKey)
                    if (!subtitle.isNullOrBlank()) {
                        Log.d(TAG, "Successfully fetched synced lyrics from Musixmatch!")
                        return@withContext subtitle
                    }
                    
                    // Fallback to plain lyrics if synced subtitle is not available
                    val lyricsBody = fetchMusixmatchLyrics(trackId, musixmatchKey)
                    if (!lyricsBody.isNullOrBlank()) {
                        Log.d(TAG, "Fetched plain lyrics from Musixmatch, converting to timed...")
                        return@withContext convertPlainToLrcWithGemini(title, artist, lyricsBody)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Musixmatch search failed: ${e.message}. Falling back to Gemini...", e)
            }
        } else {
            Log.d(TAG, "Musixmatch API key not configured or is placeholder. Using Gemini generator...")
        }

        // Gemini Fallback lyric generation
        return@withContext generateLrcWithGemini(title, artist)
    }

    private fun searchMusixmatchTrack(title: String, artist: String, apiKey: String): Long? {
        val url = "https://api.musixmatch.com/ws/1.1/track.search" +
                "?q_track=${UriEncoder.encode(title)}" +
                "&q_artist=${UriEncoder.encode(artist)}" +
                "&apikey=$apiKey" +
                "&page_size=1" +
                "&s_track_rating=desc"

        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bodyString = response.body?.string() ?: return null
            val json = JSONObject(bodyString)
            val message = json.optJSONObject("message") ?: return null
            val header = message.optJSONObject("header") ?: return null
            val statusCode = header.optInt("status_code", 0)
            if (statusCode != 200) return null

            val body = message.optJSONObject("body") ?: return null
            val trackList = body.optJSONArray("track_list") ?: return null
            if (trackList.length() == 0) return null

            val trackObj = trackList.getJSONObject(0).optJSONObject("track") ?: return null
            return trackObj.optLong("track_id")
        }
    }

    private fun fetchMusixmatchSubtitle(trackId: Long, apiKey: String): String? {
        val url = "https://api.musixmatch.com/ws/1.1/track.subtitle.get" +
                "?track_id=$trackId" +
                "&apikey=$apiKey"

        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bodyString = response.body?.string() ?: return null
            val json = JSONObject(bodyString)
            val message = json.optJSONObject("message") ?: return null
            val body = message.optJSONObject("body") ?: return null
            val subtitle = body.optJSONObject("subtitle") ?: return null
            return subtitle.optString("subtitle_body")
        }
    }

    private fun fetchMusixmatchLyrics(trackId: Long, apiKey: String): String? {
        val url = "https://api.musixmatch.com/ws/1.1/track.lyrics.get" +
                "?track_id=$trackId" +
                "&apikey=$apiKey"

        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bodyString = response.body?.string() ?: return null
            val json = JSONObject(bodyString)
            val message = json.optJSONObject("message") ?: return null
            val body = message.optJSONObject("body") ?: return null
            val lyrics = body.optJSONObject("lyrics") ?: return null
            return lyrics.optString("lyrics_body")
        }
    }

    /**
     * Calls Gemini to generate synchronized LRC lyrics from scratch.
     */
    private suspend fun generateLrcWithGemini(title: String, artist: String): String = withContext(Dispatchers.IO) {
        val geminiKey = BuildConfig.GEMINI_API_KEY
        if (geminiKey.isEmpty() || geminiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API key is not set!")
            return@withContext generateLocalMockLrc(title, artist)
        }

        val prompt = """
            You are a professional music synchronization specialist.
            Generate complete synchronized karaoke lyrics in standard LRC format [mm:ss.xx] for the song:
            Title: "$title"
            Artist: "$artist"

            Guidelines:
            1. Provide accurate, real, complete lyrics for this song.
            2. Add precise standard timestamp tags, like [00:15.20], at the beginning of each lyric line matching a standard performance.
            3. Synchronize instrumentals/intros using tags like [00:00.00] (Instrumental).
            4. Format each line correctly: '[mm:ss.xx] Lyric text'.
            5. Output ONLY the raw LRC text. Do NOT wrap it in markdown code blocks, and do NOT add any conversational preamble or postscript.
        """.trimIndent()

        try {
            val responseText = callGeminiApi(prompt, geminiKey)
            if (responseText.contains("[00:") || responseText.contains("[01:")) {
                return@withContext cleanGeminiOutput(responseText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini LRC generation failed: ${e.message}", e)
        }

        return@withContext generateLocalMockLrc(title, artist)
    }

    /**
     * Calls Gemini to add LRC timing stamps to existing plain text lyrics.
     */
    private suspend fun convertPlainToLrcWithGemini(title: String, artist: String, plainLyrics: String): String = withContext(Dispatchers.IO) {
        val geminiKey = BuildConfig.GEMINI_API_KEY
        if (geminiKey.isEmpty() || geminiKey == "MY_GEMINI_API_KEY") {
            return@withContext generateLocalMockLrc(title, artist)
        }

        val prompt = """
            You are a music synchronization assistant.
            Convert these plain lyrics into standard timed LRC karaoke format [mm:ss.xx] for:
            Song: "$title" by "$artist"

            Plain lyrics:
            $plainLyrics

            Guidelines:
            1. Add realistic timestamp tags at the start of each lyric line (e.g. [00:10.50]).
            2. Match standard pacing of this song.
            3. Output ONLY the raw LRC content. Do not include markdown or explanations.
        """.trimIndent()

        try {
            val responseText = callGeminiApi(prompt, geminiKey)
            if (responseText.contains("[00:") || responseText.contains("[01:")) {
                return@withContext cleanGeminiOutput(responseText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini lyrics tagger failed: ${e.message}", e)
        }

        return@withContext generateLocalMockLrc(title, artist)
    }

    private fun callGeminiApi(prompt: String, apiKey: String): String {
        val requestUrl = "$GEMINI_URL?key=$apiKey"

        val jsonRequest = JSONObject()
        val contentsArray = JSONArray()
        val contentObject = JSONObject()
        val partsArray = JSONArray()
        val partObject = JSONObject()
        partObject.put("text", prompt)
        partsArray.put(partObject)
        contentObject.put("parts", partsArray)
        contentsArray.put(contentObject)
        jsonRequest.put("contents", contentsArray)

        val body = jsonRequest.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(requestUrl)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "Gemini API error code: ${response.code}, body: ${response.body?.string()}")
                throw Exception("HTTP ${response.code}")
            }
            val bodyString = response.body?.string() ?: throw Exception("Empty response body")
            val json = JSONObject(bodyString)
            val candidates = json.getJSONArray("candidates")
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            val firstPart = parts.getJSONObject(0)
            return firstPart.getString("text")
        }
    }

    private fun cleanGeminiOutput(text: String): String {
        // Strip markdown code blocks like ```lrc ... ``` or ``` ... ```
        var cleaned = text.trim()
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substringAfter("\n")
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substringBeforeLast("```")
            }
        }
        return cleaned.trim()
    }

    /**
     * Simple offline local LRC builder that returns a beautiful set of simulated synchronized lyrics
     * so that the application is ALWAYS functional, even completely offline without API keys.
     */
    fun generateLocalMockLrc(title: String, artist: String): String {
        return """
            [00:00.00] (Instrumental Intro)
            [00:04.00] Welcome to LyricSync Karaoke!
            [00:08.00] Playing: $title
            [00:12.00] Artist: $artist
            [00:16.00] This is a synchronized karaoke-style lyric player.
            [00:20.00] Let the music guide your soul.
            [00:24.00] ♪
            [00:28.00] We search Musixmatch for high-precision synchronization.
            [00:32.00] Or use Gemini AI to generate lyrics automatically.
            [00:36.00] You can edit metadata tags anytime!
            [00:40.00] Just tap the Edit button at the top.
            [00:44.00] ♪
            [00:48.00] Feel free to load your own FLAC or MP3 files.
            [00:52.00] The app syncs automatically with your local media.
            [00:56.00] Click any lyric line to jump directly to that part of the song!
            [01:00.00] Export lyrics as text or LRC for offline viewing.
            [01:04.00] ♪ (Guitar Solo)
            [01:15.00] Singing out loud, let the rhythm flow,
            [01:19.00] Under the flashing lights, we put on a show!
            [01:23.00] Real-time tracking keeps us in the zone,
            [01:27.00] On this beautiful screen, we are never alone.
            [01:31.00] Thank you for using LyricSync!
            [01:35.00] (Outro - Instrumental Fading out)
        """.trimIndent()
    }
}

/**
 * Minimal helper to encode URI components without needing android.net.Uri dependency on JVM/Robolectric
 */
object UriEncoder {
    fun encode(s: String): String {
        return java.net.URLEncoder.encode(s, "UTF-8")
    }
}
