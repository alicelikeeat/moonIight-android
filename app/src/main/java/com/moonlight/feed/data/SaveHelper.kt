package com.moonlight.feed.data

import com.moonlight.feed.MoonApp
import kotlinx.coroutines.flow.first

object SaveHelper {
    suspend fun maybeSaveEntry(entry: MediaEntry): GallerySaver.Result? {
        val app = MoonApp.instance
        if (!app.settings.flow.first().autoSaveGallery) return null
        return when (entry.kind) {
            MediaKind.BILI, MediaKind.EMBED -> {
                if (entry.isDownloadable() && !entry.remoteUrl.isNullOrBlank() &&
                    entry.kind != MediaKind.BILI &&
                    entry.resolvedPlatform() in setOf(
                        PlatformId.ARCHIVE, PlatformId.NASA, PlatformId.COMMONS,
                        PlatformId.PEERTUBE, PlatformId.PEXELS, PlatformId.PIXABAY, PlatformId.URL,
                    )
                ) {
                    // EMBED with file shouldn't reach here often; VIDEO path preferred
                    GallerySaver.saveEntry(app, app.http, entry.copy(kind = MediaKind.VIDEO))
                } else if (!entry.coverUrl.isNullOrBlank()) {
                    GallerySaver.saveFromUrl(
                        app, app.http, entry.coverUrl!!, isVideo = false,
                        displayName = "${entry.biliBvid ?: entry.platform ?: entry.id}_cover.jpg",
                    )
                } else null
            }
            MediaKind.IMAGE, MediaKind.VIDEO -> GallerySaver.saveEntry(app, app.http, entry)
        }
    }
}
