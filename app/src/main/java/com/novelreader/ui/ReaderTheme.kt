package com.novelreader.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

enum class ReaderBg {
    Dark,
    Sepia,
    Light,
}

data class ReaderPalette(
    val bg: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
)

fun palette(bg: ReaderBg): ReaderPalette = when (bg) {
    ReaderBg.Dark -> ReaderPalette(
        bg = Color(0xFF12141A),
        surface = Color(0xFF1C1F28),
        text = Color(0xFFE8E6E3),
        muted = Color(0xFF9A968F),
        accent = Color(0xFF8AB4F8),
    )
    ReaderBg.Sepia -> ReaderPalette(
        bg = Color(0xFFF4ECD8),
        surface = Color(0xFFE8DCC4),
        text = Color(0xFF3E3226),
        muted = Color(0xFF7A6A56),
        accent = Color(0xFF8B5E3C),
    )
    ReaderBg.Light -> ReaderPalette(
        bg = Color(0xFFFAFAF8),
        surface = Color(0xFFFFFFFF),
        text = Color(0xFF1A1A1A),
        muted = Color(0xFF6B6B6B),
        accent = Color(0xFF1A73E8),
    )
}

val ReaderSerif = FontFamily.Serif
