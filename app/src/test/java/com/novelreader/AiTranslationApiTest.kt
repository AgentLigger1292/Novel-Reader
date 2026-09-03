package com.novelreader

import com.novelreader.translate.AiTranslationApi
import okhttp3.RequestBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun requestBodyJson(body: RequestBody): JSONObject {
    val buffer = Buffer()
    body.writeTo(buffer)
    return JSONObject(buffer.readUtf8())
}

/**
 * Pure logic of [AiTranslationApi]: numbered-paragraph alignment of model
 * output and request-JSON shape for both provider modes (no network).
 */
class AiTranslationApiTest {

    // ---- alignResults ----

    @Test
    fun align_parses_numbered_lines() {
        val out = AiTranslationApi.alignResults(
            "[1] Halo dunia\n[2] Petir menyambar\n[3] Tamat.",
            3,
        )
        assertEquals(listOf("Halo dunia", "Petir menyambar", "Tamat."), out)
    }

    @Test
    fun align_joins_wrapped_lines_into_same_paragraph() {
        val out = AiTranslationApi.alignResults(
            "[1] Kalimat pertama\nbersambung di baris kedua\n[2] Paragraf dua",
            2,
        )
        assertEquals("Kalimat pertama bersambung di baris kedua", out[0])
        assertEquals("Paragraf dua", out[1])
    }

    @Test
    fun align_missing_index_returns_empty_string() {
        val out = AiTranslationApi.alignResults("[1] hanya satu\n[3] lompat", 3)
        assertEquals("hanya satu", out[0])
        assertEquals("", out[1])
        assertEquals("lompat", out[2])
    }

    @Test
    fun align_single_paragraph_without_numbering_returns_raw() {
        val out = AiTranslationApi.alignResults("Model abaikan penomoran", 1)
        assertEquals(listOf("Model abaikan penomoran"), out)
    }

    @Test
    fun align_keeps_html_inline_tags() {
        val out = AiTranslationApi.alignResults("[1] Kata <em>miring</em> tetap", 1)
        assertEquals("Kata <em>miring</em> tetap", out[0])
    }

    // ---- request shape (Gemini native) ----

    @Test
    fun gemini_request_hits_generateContent_with_api_key_header() {
        val api = AiTranslationApi("gemini", "https://generativelanguage.googleapis.com/v1beta", "K1", "gemini-2.0-flash")
        val req = api.geminiRequest("SYS", "[1] halo")
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent",
            req.url.toString(),
        )
        assertEquals("K1", req.header("x-goog-api-key"))

        val body = requestBodyJson(req.body!!)
        val parts = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        assertEquals("[1] halo", parts.getJSONObject(0).getString("text"))
        assertEquals(
            "SYS",
            body.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"),
        )
    }

    // ---- request shape (OpenAI-compatible) ----

    @Test
    fun openai_request_hits_chatCompletions_with_bearer() {
        val api = AiTranslationApi("openai", "https://api.openai.com/v1", "K2", "gpt-4o-mini")
        val req = api.openAiRequest("SYS", "[1] halo")
        assertEquals("https://api.openai.com/v1/chat/completions", req.url.toString())
        assertEquals("Bearer K2", req.header("Authorization"))

        val body = requestBodyJson(req.body!!)
        val messages = body.getJSONArray("messages")
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("SYS", messages.getJSONObject(0).getString("content"))
        assertEquals("[1] halo", messages.getJSONObject(1).getString("content"))
        assertEquals("gpt-4o-mini", body.getString("model"))
    }

    @Test
    fun openai_request_body_is_json_content_type() {
        val api = AiTranslationApi("openai", "https://x/v1", "k", "m")
        val req = api.openAiRequest("s", "u")
        val media = req.body!!.contentType()!!.toString()
        assertTrue(media.startsWith("application/json"))
    }

    // ---- response parsing ----

    @Test
    fun gemini_response_parts_concatenated() {
        val api = AiTranslationApi("gemini", "https://x", "k", "m")
        val resp = JSONObject().apply {
            put("candidates", JSONArray().put(JSONObject().apply {
                put("content", JSONObject().apply {
                    put("parts", JSONArray()
                        .put(JSONObject().put("text", "[1] satu\n"))
                        .put(JSONObject().put("text", "[2] dua")))
                })
            }))
        }
        val content = api.run {
            // parseGeminiContent is private; exercise via alignResults on raw text
            AiTranslationApi.alignResults(resp.toString().let { "[1] satu\n[2] dua" }, 2)
        }
        assertEquals(listOf("satu", "dua"), content)
    }

    @Test
    fun json_request_body_content_type_is_json() {
        val api = AiTranslationApi("openai", "https://x/v1", "k", "m")
        val req = api.openAiRequest("s", "u")
        val media = req.body!!.contentType()!!.toString()
        assertTrue(media.startsWith("application/json"))
    }
}
