package com.novelreader.core

import com.novelreader.core.db.NovelDatabase
import com.novelreader.core.parser.NovelLoaderContext
import com.novelreader.source.BacaLightNovelParser
import com.novelreader.source.DummySource
import com.novelreader.source.LocalEpubSource
import com.novelreader.source.MistmintHavenParser
import com.novelreader.source.NovelSource
import com.novelreader.source.SakuraNovelParser
import com.novelreader.source.SonicMtlParser
import com.novelreader.source.WoopReadParser
import com.novelreader.source.WtrLabParser

/**
 * Declarative catalog of novel sources — the single place a site gets added.
 * Each entry pairs a source with whether it belongs to the user-facing catalog
 * (Explore grid + seeded `sources` table). Infrastructure sources (dummy, local
 * EPUB) are registered but not cataloged; ordering here is the display/seed order.
 */
class SourceRegistry(private val entries: List<Entry>) {

    /** Convenience wiring for the app: builds the standard catalog. */
    constructor(loaderContext: NovelLoaderContext, db: NovelDatabase) : this(
        defaultEntries(loaderContext, db),
    )

    data class Entry(val source: NovelSource, val catalog: Boolean)

    init {
        val dup = entries.groupBy { it.source.id }.filterValues { it.size > 1 }.keys
        require(dup.isEmpty()) { "Duplicate source ids: $dup" }
    }

    val sources: Map<String, NovelSource> = entries.associate { it.source.id to it.source }

    /** Ordered ids surfaced to users — Explore grid and seedSources input. */
    val catalogIds: List<String> = entries.filter { it.catalog }.map { it.source.id }

    companion object {
        fun defaultEntries(loaderContext: NovelLoaderContext, db: NovelDatabase): List<Entry> =
            listOf(
                Entry(DummySource(), catalog = false),
                Entry(BacaLightNovelParser(loaderContext), catalog = true),
                Entry(SakuraNovelParser(loaderContext), catalog = true),
                Entry(MistmintHavenParser(loaderContext), catalog = true),
                Entry(SonicMtlParser(loaderContext), catalog = true),
                Entry(WtrLabParser(loaderContext), catalog = true),
                Entry(WoopReadParser(loaderContext), catalog = true),
                // local EPUB import — offline, must stay out of Explore/seed (see SourcesRepository)
                Entry(LocalEpubSource(db), catalog = false),
            )
    }
}
