package com.moonlight.feed.data.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicReference

/** tmp + fsync + atomic rename: a reader (or a crash) only ever sees the old file or the complete new one. */
object AtomicFiles {
    fun write(file: File, text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(tmp).use { out -> out.write(text.toByteArray(Charsets.UTF_8)); out.flush(); runCatching { out.fd.sync() } }
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Throwable) {
            if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
        }
    }
}

/**
 * Latest-wins background writer. [submit] is cheap and safe on the main thread: it only stores the newest value and
 * pokes a conflated channel. Encoding and the atomic write both run on [scope] (Dispatchers.IO), after [debounceMs] of
 * quiet, so a burst of edits (typing, auto-replies, progress ticks) becomes one write of the final state.
 */
class CoalescingWriter<T>(
    private val file: File,
    private val scope: CoroutineScope,
    private val debounceMs: Long = 250,
    private val encode: (T) -> String,
) {
    private object None
    private val pending = AtomicReference<Any?>(None)
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private val lock = Mutex()
    @Volatile var writes = 0; private set

    init { scope.launch { for (s in signal) { if (debounceMs > 0) delay(debounceMs); drain() } } }

    fun submit(value: T) { pending.set(value); signal.trySend(Unit) }

    /** Writes whatever is pending now (app going to background, tests). */
    suspend fun flush() = drain()

    @Suppress("UNCHECKED_CAST")
    private suspend fun drain() = lock.withLock {
        val v = pending.getAndSet(None)
        if (v === None) return@withLock
        AtomicFiles.write(file, encode(v as T))
        writes++
    }
}
