package com.novelreader.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Lightweight Text-to-Speech manager for novel reader.
 * Reads paragraphs sequentially and tracks the active index.
 */
class ReaderTts(context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private var currentParagraphs: List<String> = emptyList()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                tts?.language = Locale("id", "ID")
                setupListener()
            } else {
                Log.w("ReaderTts", "TTS init failed with code $status")
            }
        }
    }

    private fun setupListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                val next = _currentIndex.value + 1
                if (next < currentParagraphs.size && _isPlaying.value) {
                    _currentIndex.value = next
                    speakIndex(next)
                } else {
                    _isPlaying.value = false
                }
            }

            override fun onError(utteranceId: String?) {
                _isPlaying.value = false
            }
        })
    }

    fun play(paragraphs: List<String>, startIndex: Int = 0) {
        if (!isInitialized || paragraphs.isEmpty()) return
        currentParagraphs = paragraphs
        _currentIndex.value = startIndex.coerceIn(0, paragraphs.size - 1)
        _isPlaying.value = true
        speakIndex(_currentIndex.value)
    }

    fun pause() {
        _isPlaying.value = false
        tts?.stop()
    }

    fun toggle(paragraphs: List<String>, startIndex: Int = 0) {
        if (_isPlaying.value) pause() else play(paragraphs, startIndex)
    }

    private fun speakIndex(index: Int) {
        if (index !in currentParagraphs.indices) return
        val text = currentParagraphs[index].trim()
        if (text.isEmpty()) {
            val next = index + 1
            if (next < currentParagraphs.size && _isPlaying.value) {
                _currentIndex.value = next
                speakIndex(next)
            }
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "par_$index")
    }

    fun shutdown() {
        pause()
        tts?.shutdown()
        tts = null
    }
}
