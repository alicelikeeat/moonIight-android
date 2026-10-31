package com.moonlight.feed.data

import com.moonlight.feed.MoonApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Pulls a counted batch from the already-enabled public sources. No playurl / WBI / stream ripping. */
object ContentImport {
    suspend fun fetch(app: MoonApp, count: Int): List<MediaEntry> = withContext(Dispatchers.IO) {
        val s = app.settings.flow.first()
        val found = runCatching {
            RemoteApis.multiRandomDiscovery(
                keywords = s.keywords.toList(),
                exclude = s.exclude,
                enabled = s.enabledPlatforms,
                youtubeKey = s.youtubeKey,
                pexelsKey = s.pexelsKey,
                pixabayKey = s.pixabayKey,
                limitPer = 8,
                userSources = app.sources.items.value,
            )
        }.getOrDefault(emptyList())
        RandomBatch.pick(found, count)
    }
}
