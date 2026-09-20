package com.novelreader.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Minimal AI translation client with two provider modes:
 *  - [PROVIDER_GEMINI]: Google Gemini native API (`v1beta generateContent`,
 *    key sent via `x-goog-api-key` header). Supports streaming via
 *    `:streamGenerateContent?alt=sse`.
 *  - [PROVIDER_OPENAI]: any OpenAI-compatible Chat Completions endpoint —
 *    OpenAI, OpenRouter, Groq, Ollama, LM Studio, vLLM, … Supports SSE
 *    streaming when `"stream": true` is set.
 *
 * Both modes share the numbered-paragraph protocol: paragraphs are sent as
 * "[N] text" lines and the model must answer one "[N] translation" per line;
 * [alignResults] parses the reply back into an aligned list, tolerating
 * formatting drift (wrapped lines, missing brackets, joined paragraphs).
 */
class AiTranslationApi(
    private val provider: String,
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) {
    class TranslationException(message: String, val httpCode: Int? = null) : Exception(message)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    init {
        if (provider != PROVIDER_GOOGLE_MTL) {
            require(baseUrl.isValidHttpUrl()) { "Base URL tidak valid: $baseUrl" }
        }
    }

    // ---- non-streaming (used by tests, fallback) ----

    /**
     * Translate a batch of paragraphs in one request (non-streaming).
     * Returns one translated string per input, same order.
     */
    suspend fun translateBatch(texts: List<String>, targetLang: String): List<String> =
        withContext(Dispatchers.IO) {
            if (texts.isEmpty()) return@withContext emptyList()
            if (provider == PROVIDER_GOOGLE_MTL) {
                return@withContext translateGoogleMtl(texts, targetLang)
            }
            val userContent = texts.mapIndexed { i, t -> "[${i + 1}] $t" }.joinToString("\n")
            val sp = systemPrompt(targetLang)
            val request = when (provider) {
                PROVIDER_GEMINI -> geminiRequest(sp, userContent, stream = false)
                else -> openAiRequest(sp, userContent, stream = false)
            }
            val content = executeAndParse(request)
            alignResults(content, texts.size)
        }

    // ---- streaming (for responsive live UI) ----

    /**
     * Translate ALL paragraphs via SSE streaming.
     * [onDelta] is called with every clean text delta as it arrives (feed it
     * to a [StreamParser] to get live paragraphs). Returns the FULL
     * accumulated response text at the end.
     */
    suspend fun streamTranslateBatch(
        texts: List<String>,
        targetLang: String,
        onDelta: (String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext ""
        if (provider == PROVIDER_GOOGLE_MTL) {
            return@withContext streamGoogleMtl(texts, targetLang, onDelta)
        }
        val userContent = texts.mapIndexed { i, t -> "[${i + 1}] $t" }.joinToString("\n")
        val sp = systemPrompt(targetLang)
        val request = when (provider) {
            PROVIDER_GEMINI -> geminiRequest(sp, userContent, stream = true)
            else -> openAiRequest(sp, userContent, stream = true)
        }
        val full = StringBuilder()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                val msg = parseErrorMessage(body) ?: "HTTP ${resp.code}"
                throw TranslationException(msg, resp.code)
            }
            val source = resp.body?.source()
                ?: throw TranslationException("Respons tanpa body")
            while (isActive) {
                val line = source.readUtf8Line() ?: break
                val trimmed = line.trim()
                if (!trimmed.startsWith("data:")) continue // SSE comment/heartbeat
                val json = trimmed.removePrefix("data:").trim()
                if (json == "[DONE]") break
                val delta = when (provider) {
                    PROVIDER_GEMINI -> geminiDelta(json)
                    else -> openAiDelta(json)
                }
                if (delta.isNotEmpty()) {
                    onDelta(delta)
                    full.append(delta)
                }
            }
        }
        full.toString()
    }

    /** Extract the text delta from one Gemini SSE event (may be empty). */
    private fun geminiDelta(json: String): String {
        if (json.isEmpty()) return ""
        return runCatching {
            JSONObject(json)
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                .orEmpty()
        }.getOrDefault("")
    }

    /** Extract the text delta from one OpenAI-compatible SSE event (may be empty). */
    private fun openAiDelta(json: String): String {
        if (json.isEmpty()) return ""
        return runCatching {
            val choice = JSONObject(json).optJSONArray("choices")?.optJSONObject(0)
                ?: return ""
            val delta = choice.optJSONObject("delta")?.optString("content")
            if (!delta.isNullOrEmpty()) delta
            else choice.optJSONObject("message")?.optString("content").orEmpty()
        }.getOrDefault("")
    }

    // ---- providers ----

    internal fun systemPrompt(targetLang: String): String = buildString {
        append("You are a professional novel translator. ")
        append("Translate the following numbered paragraphs into $targetLang. ")
        append("Rules: preserve the [N] numbering exactly; translate each paragraph on its own line; ")
        append("keep any HTML tags unchanged; keep names, honorifics and terms consistent; ")
        append("translate naturally as fiction, not word-by-word; ")
        append("do not add commentary, explanations, or omit any paragraph.")
    }

    // ---- Gemini native (v1beta generateContent) ----

    internal fun geminiRequest(
        systemPrompt: String,
        userContent: String,
        stream: Boolean,
    ): Request {
        val url = if (stream) {
            "$baseUrl/models/$model:streamGenerateContent?alt=sse"
        } else {
            "$baseUrl/models/$model:generateContent"
        }
        val generationConfig = JSONObject().put("temperature", 0.3)
        if (THINKING_CAPABLE.containsMatchIn(model)) {
            generationConfig.put(
                "thinkingConfig",
                JSONObject().put("thinkingBudget", 0),
            )
        }
        val body = JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemPrompt)),
                ),
            )
            put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", userContent)),
                        ),
                ),
            )
            put("generationConfig", generationConfig)
        }
        return Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
    }

    // ---- OpenAI-compatible (chat/completions) ----

    internal fun openAiRequest(
        systemPrompt: String,
        userContent: String,
        stream: Boolean,
    ): Request {
        val body = JSONObject().apply {
            put("model", model)
            put("temperature", 0.3)
            put("stream", stream)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemPrompt))
                put(JSONObject().put("role", "user").put("content", userContent))
            })
            if (REASONING_OPENAI.containsMatchIn(model)) {
                put("reasoning_effort", "low")
            }
        }
        return Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
    }

    // ---- response parsing ----

    private fun executeAndParse(request: Request): String {
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = parseErrorMessage(body) ?: "HTTP ${resp.code}"
                throw TranslationException(msg, resp.code)
            }
            return when (provider) {
                PROVIDER_GEMINI -> parseGeminiContent(body)
                else -> parseOpenAiContent(body)
            }
        }
    }

    private fun parseGeminiContent(body: String): String {
        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: throw TranslationException("Respons bukan JSON: ${body.take(120)}")
        val parts = json.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?: throw TranslationException("Respons Gemini tanpa candidates")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            sb.append(parts.optJSONObject(i)?.optString("text").orEmpty())
        }
        return sb.toString()
    }

    private fun parseOpenAiContent(body: String): String {
        val content = runCatching {
            JSONObject(body)
                .getJSONArray("choices")
                .optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
        }.getOrNull()
        if (content.isNullOrBlank()) {
            throw TranslationException("Respons tanpa choices: ${body.take(120)}")
        }
        return content
    }

    // ---- Google Machine Translation (free, no API key required) ----

    private fun translateGoogleMtl(texts: List<String>, targetLang: String): List<String> {
        val raw = streamGoogleMtl(texts, targetLang) {}
        return alignResults(raw, texts.size)
    }

    private fun streamGoogleMtl(
        texts: List<String>,
        targetLang: String,
        onDelta: (String) -> Unit,
    ): String {
        val full = StringBuilder()
        val tl = normalizeGoogleLang(targetLang)
        val chunkSize = 15
        for (chunkIndex in texts.indices step chunkSize) {
            val chunk = texts.subList(chunkIndex, minOf(chunkIndex + chunkSize, texts.size))
            val chunkText = chunk.mapIndexed { i, t ->
                "[${chunkIndex + i + 1}] $t"
            }.joinToString("\n")

            val body = FormBody.Builder()
                .add("client", "gtx")
                .add("sl", "auto")
                .add("tl", tl)
                .add("dt", "t")
                .add("q", chunkText)
                .build()

            val request = Request.Builder()
                .url("https://translate.googleapis.com/translate_a/single")
                .post(body)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13)")
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    android.util.Log.w("BLN", "Google MTL HTTP ${resp.code}: $body")
                    throw TranslationException("Google MTL HTTP ${resp.code}: ${body.take(100)}", resp.code)
                }
                val jsonStr = resp.body?.string().orEmpty()
                val translated = parseGoogleMtlResponse(jsonStr)
                android.util.Log.i("BLN", "Google MTL chunk done len=${translated.length}: ${translated.take(60)}")
                val delta = translated + "\n"
                full.append(delta)
                onDelta(delta)
            }
        }
        return full.toString()
    }

    // ---- error parsing + validation ----

    private fun parseErrorMessage(body: String): String? = try {
        JSONObject(body).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    companion object {
        const val PROVIDER_GEMINI = "gemini"
        const val PROVIDER_OPENAI = "openai"
        const val PROVIDER_GOOGLE_MTL = "google_mtl"

        internal fun normalizeGoogleLang(lang: String): String {
            val l = lang.trim().lowercase()
            return when {
                l == "indonesian" || l == "indonesia" || l == "id" -> "id"
                l == "english" || l == "inggris" || l == "en" -> "en"
                l == "japanese" || l == "jepang" || l == "ja" -> "ja"
                l == "korean" || l == "korea" || l == "ko" -> "ko"
                l == "chinese" || l == "mandarin" || l == "zh" || l == "zh-cn" -> "zh-CN"
                l == "spanish" || l == "spanyol" || l == "es" -> "es"
                l == "french" || l == "prancis" || l == "fr" -> "fr"
                l == "german" || l == "jerman" || l == "de" -> "de"
                l == "russian" || l == "rusia" || l == "ru" -> "ru"
                l == "arabic" || l == "arab" || l == "ar" -> "ar"
                l.length in 2..5 -> l
                else -> "id"
            }
        }

        internal fun parseGoogleMtlResponse(jsonStr: String): String {
            val arr = runCatching { JSONArray(jsonStr).optJSONArray(0) }.getOrNull() ?: return ""
            val sb = StringBuilder()
            for (i in 0 until arr.length()) {
                val segment = arr.optJSONArray(i)?.optString(0) ?: ""
                sb.append(segment)
            }
            return sb.toString()
        }

        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        private val THINKING_CAPABLE = Regex("""gemini-(2\.5|3|4)""")
        private val REASONING_OPENAI = Regex("""^(o\d|gpt-5|gpt-4\.1|deepseek-r)""")

        /**
         * Only http/https is allowed; localhost, loopback, link-local,
         * private and other reserved hosts are rejected so a stray Base URL
         * can never point the app at internal endpoints. Pure (no DNS) —
         * hostname classification happens on literals; named hosts pass here
         * and are resolved normally by OkHttp at request time. Unit-tested.
         */
        fun String.isValidHttpUrl(): Boolean {
            val url = runCatching { java.net.URL(this) }.getOrNull() ?: return false
            if (url.protocol != "http" && url.protocol != "https") return false
            val host = url.host.lowercase()
            if (host.isEmpty()) return false
            if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) return false
            if (host == "0.0.0.0" || host == "255.255.255.255") return false
            val v4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""").find(host)
            if (v4 != null) {
                val o = v4.groupValues.drop(1).map { it.toIntOrNull() ?: return false }
                if (o.any { it !in 0..255 }) return false
                val (a, b) = o
                return when {
                    a == 10 -> false
                    a == 172 && b in 16..31 -> false
                    a == 192 && b == 168 -> false
                    a == 100 && b in 64..127 -> false
                    a == 169 && b == 254 -> false
                    a == 127 -> false
                    a == 0 -> false
                    else -> true
                }
            }
            if (host.contains(":")) {
                val addr = runCatching { java.net.InetAddress.getByName(host) }.getOrNull() ?: return false
                return !(addr.isLoopbackAddress || addr.isLinkLocalAddress ||
                    addr.isAnyLocalAddress || addr.isMulticastAddress)
            }
            return true
        }

        /**
         * Parse model output back into an aligned list of [expected] paragraphs;
         * tolerant of formatting drift. Pure function — unit-tested.
         */
        fun alignResults(content: String, expected: Int): List<String> {
            val byIndex = HashMap<Int, StringBuilder>()
            val regex = Regex("""^\s*\[(\d+)]\s*(.*)$""")
            var currentIdx = -1

            fun append(idx: Int, text: String) {
                if (idx <= 0 || text.isEmpty()) return
                byIndex.getOrPut(idx) { StringBuilder() }.apply {
                    if (isNotEmpty()) append(' ')
                    append(text)
                }
            }

            for (line in content.lines()) {
                val m = regex.find(line)
                if (m != null) {
                    currentIdx = m.groupValues[1].toIntOrNull() ?: -1
                    append(currentIdx, m.groupValues[2].trim())
                } else if (currentIdx > 0 && line.isNotBlank()) {
                    append(currentIdx, line.trim())
                }
            }

            if (byIndex.isEmpty() && expected == 1) return listOf(content.trim())
            return (1..expected).map { i -> byIndex[i]?.toString()?.trim().orEmpty() }
        }
    }
}