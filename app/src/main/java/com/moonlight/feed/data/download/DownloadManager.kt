package com.moonlight.feed.data.download

import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.MediaKind
import com.moonlight.feed.data.MediaProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Task list, persisted (latest-wins, off the main thread) in filesDir/downloads.json. */
class DownloadStore(file: File, scope: CoroutineScope, debounceMs: Long = 300) {
    private val writer = CoalescingWriter(file, scope, debounceMs, DownloadCodec::encode)
    private val _tasks = MutableStateFlow(DownloadCodec.decode(runCatching { if (file.exists()) file.readText() else null }.getOrNull()))
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    fun get(id: String): DownloadTask? = _tasks.value.firstOrNull { it.id == id }

    @Synchronized fun put(t: DownloadTask) {
        val cur = _tasks.value
        _tasks.value = if (cur.any { it.id == t.id }) cur.map { if (it.id == t.id) t else it } else listOf(t) + cur
        writer.submit(_tasks.value)
    }
    @Synchronized fun update(id: String, f: (DownloadTask) -> DownloadTask): DownloadTask? {
        val t = get(id) ?: return null
        return f(t).also { put(it) }
    }
    @Synchronized fun remove(id: String) { _tasks.value = _tasks.value.filter { it.id != id }; writer.submit(_tasks.value) }
    suspend fun flush() = writer.flush()
}

/** WorkManager in the app; a recording fake in tests. One unique work per item id. */
interface DownloadScheduler {
    /** [replace] = true → REPLACE (new / resumed task); false → KEEP (re-enqueue after a restart, no duplicate). */
    fun enqueue(id: String, replace: Boolean)
    fun cancel(id: String)
}

/** User-facing 主动下载 controls (库 rows, rings). */
class DownloadManager(
    val store: DownloadStore,
    private val scheduler: DownloadScheduler,
    val partDir: File,
    /** Does the finished library entry still exist? (deleted from 库 → downloading again starts over). */
    private val entryExists: (String) -> Boolean = { true },
) {
    enum class Request { STARTED, RESUMED, ALREADY_ACTIVE, ALREADY_DONE }

    val tasks get() = store.tasks
    fun partFile(id: String) = File(partDir, safeName(id) + ".part")

    fun request(src: DownloadSource): Request {
        val cur = store.get(src.id)
        when {
            cur == null -> Unit
            cur.state == DlState.DONE && cur.resultEntryId != null && entryExists(cur.resultEntryId) -> return Request.ALREADY_DONE
            cur.active -> return Request.ALREADY_ACTIVE
            cur.state == DlState.PAUSED || cur.state == DlState.FAILED -> { resume(src.id); return Request.RESUMED }
        }
        partFile(src.id).delete()
        store.put(DownloadTask(src.id, src.url, src.title, src.kind, src.coverUrl, src.platform, src.author))
        scheduler.enqueue(src.id, replace = true)
        return Request.STARTED
    }

    /** Stops the worker; the .part stays so 继续 resumes with a Range request. */
    fun pause(id: String) {
        val t = store.get(id) ?: return
        if (!t.active) return
        store.put(t.copy(state = DlState.PAUSED))
        scheduler.cancel(id)
    }

    fun resume(id: String) {
        val t = store.get(id) ?: return
        if (t.state != DlState.PAUSED && t.state != DlState.FAILED) return
        store.put(t.copy(state = DlState.QUEUED, error = null))
        scheduler.enqueue(id, replace = true)
    }

    fun retry(id: String) = resume(id)

    /** Cancels and forgets the task (and its partial file). Finished files in the 库 are not touched. */
    fun cancel(id: String) {
        scheduler.cancel(id)
        store.remove(id)
        partFile(id).delete()
    }

    /**
     * App start (also after a reboot / process death): a task that was RUNNING when the process died goes back to
     * QUEUED, and every unfinished task is re-enqueued with KEEP, so WorkManager's own persisted work is not duplicated.
     */
    fun restore(): List<String> {
        val ids = mutableListOf<String>()
        store.tasks.value.forEach { t ->
            if (t.state == DlState.RUNNING) store.put(t.copy(state = DlState.QUEUED))
            if (t.active) { scheduler.enqueue(t.id, replace = false); ids += t.id }
        }
        val known = store.tasks.value.map { partFile(it.id).name }.toSet()
        partDir.listFiles()?.filter { it.name.endsWith(".part") && it.name !in known }?.forEach { it.delete() }
        return ids
    }

    companion object {
        fun safeName(id: String) = id.replace(Regex("[^A-Za-z0-9._-]"), "_").take(96)
    }
}

/** Runs one worker attempt for a task: Range fetch → verify → move into the 库 as a 我的下载 entry. */
class DownloadEngine(
    private val store: DownloadStore,
    private val fetch: RangeFetch,
    private val manager: DownloadManager,
    private val mediaDir: File,
    private val finish: (MediaEntry) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    enum class Step { DONE, RETRY, FAILED, STOPPED, SKIPPED }
    private val locks = ConcurrentHashMap<String, Any>()

    fun execute(id: String, attempt: Int, maxAttempts: Int, isStopped: () -> Boolean, onProgress: (DownloadTask) -> Unit = {}): Step =
        synchronized(locks.getOrPut(id) { Any() }) { run(id, attempt, maxAttempts, isStopped, onProgress) }

    private fun run(id: String, attempt: Int, maxAttempts: Int, isStopped: () -> Boolean, onProgress: (DownloadTask) -> Unit): Step {
        val t0 = store.get(id) ?: return Step.SKIPPED
        if (!t0.active) return Step.SKIPPED
        store.put(t0.copy(state = DlState.RUNNING, error = null))
        val part = manager.partFile(id)
        var lastTick = 0L
        val out = fetch.run(
            t0.url, part, RangeFetch.Meta(t0.etag, t0.lastModified, t0.total),
            isStopped = { isStopped() || store.get(id)?.state != DlState.RUNNING },
        ) { b, total ->
            val n = now()
            if (n - lastTick >= 400 || (total > 0 && b >= total)) {
                lastTick = n
                store.update(id) { if (it.state == DlState.RUNNING) it.copy(bytes = b, total = total) else it }?.let(onProgress)
            }
        }
        return when (out) {
            is RangeFetch.Outcome.Done -> {
                val t = store.get(id) ?: return Step.SKIPPED
                val file = File(mediaDir, "dl_${DownloadManager.safeName(id)}.${ext(out.probe, t)}")
                if (!part.renameTo(file)) { part.copyTo(file, overwrite = true); part.delete() }
                val entry = entryFor(t, file, out.probe)
                finish(entry)
                store.put(t.copy(state = DlState.DONE, bytes = out.bytes, total = out.bytes, etag = out.meta.etag, lastModified = out.meta.lastModified, resultEntryId = entry.id, error = null))
                Step.DONE
            }
            is RangeFetch.Outcome.Stopped -> {
                // user pause / cancel already changed the state; a system stop (constraints, quota) re-queues
                store.update(id) { it.copy(bytes = out.bytes, etag = out.meta.etag, lastModified = out.meta.lastModified, total = out.meta.total,
                    state = if (it.state == DlState.RUNNING) DlState.QUEUED else it.state) }
                Step.STOPPED
            }
            is RangeFetch.Outcome.Retry -> {
                val last = attempt + 1 >= maxAttempts
                store.update(id) { it.copy(bytes = out.bytes, etag = out.meta.etag, lastModified = out.meta.lastModified, total = out.meta.total,
                    error = out.reason, state = if (it.state != DlState.RUNNING) it.state else if (last) DlState.FAILED else DlState.QUEUED) }
                if (last) Step.FAILED else Step.RETRY
            }
            is RangeFetch.Outcome.Fatal -> {
                store.update(id) { it.copy(state = DlState.FAILED, error = out.reason, bytes = 0) }
                Step.FAILED
            }
        }
    }

    companion object {
        fun ext(p: MediaProbe.Probe, t: DownloadTask): String = when (p.kind) {
            MediaProbe.Kind.MP4 -> "mp4"; MediaProbe.Kind.WEBM, MediaProbe.Kind.MKV -> "webm"
            MediaProbe.Kind.PNG -> "png"; MediaProbe.Kind.WEBP -> "webp"; MediaProbe.Kind.GIF -> "gif"; MediaProbe.Kind.JPEG -> "jpg"
            else -> t.url.substringBefore('?').substringAfterLast('.', "").lowercase().takeIf { it.length in 2..4 } ?: if (t.kind == MediaKind.IMAGE) "jpg" else "mp4"
        }

        /** Finished file → a local 库 entry in 我的下载 (also a 本地 item on 首页). */
        fun entryFor(t: DownloadTask, file: File, p: MediaProbe.Probe): MediaEntry {
            val video = if (p.isVideo || p.isImage) p.isVideo else t.kind == MediaKind.VIDEO
            return MediaEntry(
                id = DownloadPolicy.entryId(t.id),
                kind = if (video) MediaKind.VIDEO else MediaKind.IMAGE,
                title = t.title, localPath = file.absolutePath, remoteUrl = t.url,
                coverUrl = if (video) t.coverUrl else null,
                platform = t.platform, author = t.author, source = DownloadPolicy.SOURCE,
            )
        }
    }
}

/**
 * 自动 (best-effort) caching: Coil's thumbnail/image cache under cacheDir (the OS may clear it any time) and the
 * players' in-memory buffers. Nothing here ever creates a 库 entry, survives as a download, or resumes.
 * 主动下载 parts live in filesDir/downloads, finished files in filesDir/media — never in cacheDir.
 */
object AutoCache {
    const val IMAGE_DIR = "coil"
    fun imageDir(cacheDir: File) = File(cacheDir, IMAGE_DIR)
    fun downloadPartDir(filesDir: File) = File(filesDir, "downloads")
}
