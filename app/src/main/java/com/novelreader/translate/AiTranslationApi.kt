package com.novelreader.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Minimal AI translation client with two provider modes:
 *  - [PROVIDER_GEMINI]: Google Gemini native API (`v1beta generateContent`,
 *    key sent via `x-goog-api-key` header).
 *  - [PROVIDER_OPENAI]: any OpenAI-compatible Chat Completions endpoint —
 *    OpenAI, OpenRouter, Groq, Ollama (v1), LM Studio, vLLM, …
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

    /**
     * Translate a batch of paragraphs. Each input string may contain inline HTML.
     * Returns one translated string per input, same order.
     */
    suspend fun translateBatch(texts: List<String>, targetLang: String): List<String> =
        withContext(Dispatchers.IO) {
            if (texts.isEmpty()) return@withContext emptyList()

            val userContent = texts.mapIndexed { i, t -> "[${i + 1}] $t" }.joinToString("\n")
            val systemPrompt = systemPrompt(targetLang)

            val request = when (provider) {
                PROVIDER_GEMINI -> geminiRequest(systemPrompt, userContent)
                else -> openAiRequest(systemPrompt, userContent)
            }

            client.newCall(request).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = parseErrorMessage(respBody) ?: "HTTP ${resp.code}"
                    throw TranslationException(msg, resp.code)
                }
                val content = when (provider) {
                    PROVIDER_GEMINI -> parseGeminiContent(respBody)
                    else -> parseOpenAiContent(respBody)
                }
                alignResults(content, texts.size)
            }
        }

    fun systemPrompt(targetLang: String): String = buildString {
        append("You are a professional novel translator. ")
        append("Translate the following numbered paragraphs into $targetLang. ")
        append("Rules: preserve the [N] numbering exactly; translate each paragraph on its own line; ")
        append("keep any HTML tags unchanged; keep names, honorifics and terms consistent; ")
        append("translate naturally as fiction, not word-by-word; ")
        append("do not add commentary, explanations, or omit any paragraph.")
    }

    // ---- Gemini native (v1beta generateContent) ----

    internal fun geminiRequest(systemPrompt: String, userContent: String): Request {
        val url = "$baseUrl/models/$model:generateContent"
        val generationConfig = JSONObject().put("temperature", 0.3)
        // 2.5+/3.x models think by default (30-90s per request!) — translation
        // needs no reasoning; disable it. Older models reject the field, so
        // only send it to models known to support thinkingConfig.
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

    // ---- OpenAI-compatible (chat/completions) ----

    internal fun openAiRequest(systemPrompt: String, userContent: String): Request {
        val body = JSONObject().apply {
            put("model", model)
            put("temperature", 0.3)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemPrompt))
                put(JSONObject().put("role", "user").put("content", userContent))
            })
            // reasoning models burn 30s+ per request by default; keep them on
            // the lowest effort. Plain chat models ignore the field.
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

    private fun parseErrorMessage(body: String): String? = try {
        JSONObject(body).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    companion object {
        const val PROVIDER_GEMINI = "gemini"
        const val PROVIDER_OPENAI = "openai"

        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /**
         * Gemini models that accept generationConfig.thinkingConfig.
         * gemini-2.5* and 3.x think by default — the single biggest cause of
         * "translation is slow" (30-90s of hidden reasoning per batch).
         */
        private val THINKING_CAPABLE = Regex("""gemini-(2\.5|3|4)""")
        private val REASONING_OPENAI = Regex("""^(o\d|gpt-5|gpt-4\.1|deepseek-r)""")

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

            // numbering ignored entirely for a single paragraph → take raw content
            if (byIndex.isEmpty() && expected == 1) return listOf(content.trim())

            return (1..expected).map { i -> byIndex[i]?.toString()?.trim().orEmpty() }
        }
    }
}
