package com.novelreader.translate

import com.novelreader.core.db.TranslationEntity
import com.novelreader.core.db.TranslationsDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Translates reader paragraphs via [AiTranslationApi].
 * One chapter = ONE streaming request: translated paragraphs are delivered
 * live as they stream in ([onLive]), and the final aligned list is cached in
 * Room (per novel+chapter+lang+model). Paragraphs the model skipped fall back
 * to the original text so the chapter stays readable.
 */
class AiTranslationRepository(private val dao: TranslationsDao) {

    companion object {
        const val MAX_RETRIES = 2
    }

    sealed class Progress {
        data object Idle : Progress()
        data class Done(val fromCache: Boolean) : Progress()
        data class Error(val message: String) : Progress()
    }

    suspend fun getCached(
        novelId: String,
        chapterId: String,
        lang: String,
        model: String,
    ): TranslationEntity? = withContext(Dispatchers.IO) {
        runCatching { dao.find(novelId, chapterId, lang, model) }.getOrNull()
    }

    suspend fun invalidate(novelId: String, chapterId: String) = withContext(Dispatchers.IO) {
        runCatching { dao.deleteChapter(novelId, chapterId) }
    }

    /**
     * Translate ALL [paragraphs] of a chapter in a single streaming request.
     * [onLive] fires with (index, translatedText) as each paragraph completes
     * mid-stream, so the UI can render progressively. Returns the final
     * aligned list, same order and size (missing paragraphs = original text).
     * Throws on final failure.
     */
    suspend fun translateChapter(
        api: AiTranslationApi,
        paragraphs: List<String>,
        targetLang: String,
        onLive: (Int, String) -> Unit,
        onProgress: (Progress) -> Unit,
    ): List<String> {
        if (paragraphs.isEmpty()) return emptyList()
        // parser created per attempt so a failed attempt can't poison the
        // marker state of the retry
        val raw = withRetry {
            val parser = StreamParser(paragraphs.size, onLive)
            val out = api.streamTranslateBatch(paragraphs, targetLang) { parser.feed(it) }
            parser.flush()
            out
        }
        onProgress(Progress.Done(fromCache = false))
        return finalize(raw, paragraphs)
    }

    /** Align raw output to [paragraphs]; blank slots keep the original text. */
    internal fun finalize(raw: String, paragraphs: List<String>): List<String> {
        val aligned = AiTranslationApi.alignResults(raw, paragraphs.size)
        return aligned.mapIndexed { i, t ->
            t.trim().ifEmpty { paragraphs[i] }
        }
    }

    suspend fun store(
        novelId: String,
        chapterId: String,
        lang: String,
        model: String,
        paragraphs: List<String>,
    ) = withContext(Dispatchers.IO) {
        val html = paragraphs.joinToString("") { "<p>${it}</p>" }
        runCatching {
            dao.upsert(
                TranslationEntity(
                    id = "$novelId|$chapterId|$lang|$model",
                    novelId = novelId,
                    chapterId = chapterId,
                    targetLang = lang,
                    model = model,
                    translatedHtml = html,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var lastError: Exception? = null
        repeat(MAX_RETRIES + 1) { attempt ->
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (attempt < MAX_RETRIES) delay(1000L * (1 shl attempt)) // 1s, 2s
            }
        }
        throw lastError ?: IllegalStateException("Unknown translation error")
    }
}
