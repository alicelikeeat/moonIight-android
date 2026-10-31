package com.moonlight.feed.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A user folder in the library. Deleting one keeps the media and only drops the grouping. */
data class Shelf(val id: String = UUID.randomUUID().toString(), val name: String)

data class ShelfBook(
    val shelves: List<Shelf> = emptyList(),
    val assignments: Map<String, String> = emptyMap(),
) {
    fun create(name: String): Pair<ShelfBook, Shelf>? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        if (shelves.any { it.name.equals(trimmed, ignoreCase = true) }) return null
        val shelf = Shelf(name = trimmed)
        return copy(shelves = shelves + shelf) to shelf
    }

    fun rename(id: String, name: String): ShelfBook? {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || shelves.none { it.id == id }) return null
        if (shelves.any { it.id != id && it.name.equals(trimmed, ignoreCase = true) }) return null
        return copy(shelves = shelves.map { if (it.id == id) it.copy(name = trimmed) else it })
    }

    fun delete(id: String) = copy(
        shelves = shelves.filter { it.id != id },
        assignments = assignments.filterValues { it != id },
    )

    /** null shelf moves the items back to the ungrouped library. Unknown shelf ids are ignored. */
    fun move(ids: Set<String>, shelfId: String?): ShelfBook {
        if (shelfId != null && shelves.none { it.id == shelfId }) return this
        val next = assignments.toMutableMap()
        ids.forEach { id ->
            if (shelfId == null) next.remove(id) else next[id] = shelfId
        }
        return copy(assignments = next)
    }

    fun <T> matching(items: List<T>, idOf: (T) -> String, shelfId: String): List<T> =
        items.filter { assignments[idOf(it)] == shelfId }
}

object ShelfJson {
    fun encode(book: ShelfBook): String {
        val shelves = JSONArray()
        book.shelves.forEach { shelves.put(JSONObject().put("id", it.id).put("name", it.name)) }
        val assigned = JSONObject()
        book.assignments.forEach { (item, shelf) -> assigned.put(item, shelf) }
        return JSONObject().put("shelves", shelves).put("assignments", assigned).toString()
    }

    fun decode(raw: String?): ShelfBook {
        if (raw.isNullOrBlank()) return ShelfBook()
        return runCatching {
            val root = JSONObject(raw)
            val shelves = root.optJSONArray("shelves") ?: JSONArray()
            val parsed = buildList {
                for (i in 0 until shelves.length()) {
                    val o = shelves.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    val name = o.optString("name").trim()
                    if (id.isNotBlank() && name.isNotEmpty()) add(Shelf(id, name))
                }
            }
            val known = parsed.map { it.id }.toSet()
            val assigned = root.optJSONObject("assignments") ?: JSONObject()
            val map = buildMap {
                assigned.keys().forEach { key ->
                    val shelf = assigned.optString(key)
                    if (key.isNotBlank() && shelf in known) put(key, shelf)
                }
            }
            ShelfBook(parsed, map)
        }.getOrDefault(ShelfBook())
    }

    fun read(file: File) = decode(runCatching { if (file.exists()) file.readText() else null }.getOrNull())

    fun write(file: File, book: ShelfBook) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(encode(book))
            if (!tmp.renameTo(file)) {
                file.writeText(encode(book))
                tmp.delete()
            }
        }
    }
}

/** How long the bottom dock stays up. 0 keeps it visible. Matches the iOS 关闭 / 5 / 15 / 30 choices. */
object DockHide {
    val CHOICES = listOf(0, 5, 15, 30)
    fun normalize(v: Int) = CHOICES.minBy { kotlin.math.abs(it - v) }
    fun label(v: Int) = if (normalize(v) <= 0) "关闭" else "${normalize(v)} 秒"
}
