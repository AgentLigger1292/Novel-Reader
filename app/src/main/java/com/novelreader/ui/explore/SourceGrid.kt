package com.novelreader.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import java.net.URI
import kotlin.math.abs

/** Minimal source info the picker grid needs. */
data class SourceUiItem(
    val id: String,
    val name: String,
    val siteUrl: String?,
)

private val LETTER_COLORS = listOf(
    Color(0xFFF2C14E), // yellow
    Color(0xFF64B5F6), // blue
    Color(0xFF66BB6A), // green
    Color(0xFF4DD0E1), // cyan
    Color(0xFFFF8A65), // orange
    Color(0xFFBA68C8), // purple
    Color(0xFFF06292), // pink
)

private fun letterColor(id: String): Color =
    LETTER_COLORS[abs(id.hashCode() % LETTER_COLORS.size)]

private fun letterOf(name: String): String =
    name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

private fun faviconUrl(siteUrl: String?): String? {
    val host = siteUrl?.let { runCatching { URI(it).host }.getOrNull() } ?: return null
    return "https://www.google.com/s2/favicons?domain=$host&sz=128"
}

/** Kotatsu-style source picker: grid of icon tiles with the name below, 4 per row. */
@Composable
fun SourceGrid(
    sources: List<SourceUiItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        sources.chunked(4).forEach { rowSources ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowSources.forEach { s ->
                    SourceTile(
                        source = s,
                        selected = s.id == selectedId,
                        onClick = { onSelect(s.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // keep tiles evenly sized on the last, shorter row
                repeat(4 - rowSources.size) {
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SourceTile(
    source: SourceUiItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val favicon = remember(source.siteUrl) { faviconUrl(source.siteUrl) }

    Column(
        modifier.clickable(onClickLabel = "Pilih ${source.name}", onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = RoundedCornerShape(20),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (favicon != null) {
                // plain Coil fetch — favicons must NOT go through CoverLoader's
                // WebView download queue (that is for CF-protected covers only)
                SubcomposeAsyncImage(
                    model = favicon,
                    contentDescription = source.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    loading = { LetterAvatar(source.name, source.id) },
                    error = { LetterAvatar(source.name, source.id) },
                )
            } else {
                LetterAvatar(source.name, source.id)
            }
        }
        Text(
            text = source.name,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}

@Composable
private fun LetterAvatar(name: String, id: String) {
    Text(
        text = letterOf(name),
        color = letterColor(id),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
}

@Preview(name = "SourceGrid (dark)", showBackground = true, widthDp = 380)
@Composable
private fun SourceGridPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        SourceGrid(
            sources = listOf(
                SourceUiItem("bacalightnovel", "Baca Light Novel", "https://bacalightnovel.co"),
                SourceUiItem("sakuranovel", "Sakura Novel", "https://sakuranovel.id"),
                SourceUiItem("mistminthaven", "Mistmint Haven", "https://mistminthaven.com"),
                SourceUiItem("sonicmtl", "SonicMTL", "https://sonicmtl.com"),
                SourceUiItem("dummy", "Dummy", null),
            ),
            selectedId = "sonicmtl",
            onSelect = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
