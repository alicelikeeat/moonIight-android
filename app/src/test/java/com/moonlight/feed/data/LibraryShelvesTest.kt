package com.moonlight.feed.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryShelvesTest {
    @Test fun createRejectsBlankAndDuplicates() {
        val book = ShelfBook()
        assertNull(book.create("  "))
        val (withOne, shelf) = book.create("夜景")!!
        assertEquals("夜景", shelf.name)
        assertNull(withOne.create("夜景"))
        assertNull(withOne.create(" 夜景 "))
    }

    @Test fun deleteKeepsMediaByDroppingOnlyTheGrouping() {
        val (book, shelf) = ShelfBook().create("片单")!!
        val moved = book.move(setOf("a", "b"), shelf.id)
        val cleared = moved.delete(shelf.id)
        assertTrue(cleared.shelves.isEmpty())
        assertTrue(cleared.assignments.isEmpty())
    }

    @Test fun moveAndFilter() {
        val (book, night) = ShelfBook().create("夜")!!
        val (withDay, day) = book.create("昼")!!
        val moved = withDay.move(setOf("a", "b"), night.id).move(setOf("b"), day.id).move(setOf("c"), "missing")
        assertEquals(night.id, moved.assignments["a"])
        assertEquals(day.id, moved.assignments["b"])
        assertEquals(null, moved.assignments["c"])
        val items = listOf("a", "b", "c", "d")
        assertEquals(listOf("b"), moved.matching(items, { it }, day.id))
        val ungrouped = moved.move(setOf("b"), null)
        assertEquals(emptyList<String>(), ungrouped.matching(items, { it }, day.id))
    }

    @Test fun jsonRoundTripDropsDanglingAssignments() {
        val (book, shelf) = ShelfBook().create("星")!!
        val raw = ShelfJson.encode(book.move(setOf("a"), shelf.id))
        val back = ShelfJson.decode(raw)
        assertEquals(listOf("星"), back.shelves.map { it.name })
        assertEquals(shelf.id, back.assignments["a"])
        val dangling = raw.replace(shelf.id, "gone", ignoreCase = false)
        // shelf id appears in both the shelf and the assignment; a broken assignment alone is dropped
        val broken = """{"shelves":[{"id":"s","name":"星"}],"assignments":{"a":"missing"}}"""
        assertTrue(ShelfJson.decode(broken).assignments.isEmpty())
        assertEquals("星", ShelfJson.decode(broken).shelves.single().name)
        assertEquals(ShelfBook(), ShelfJson.decode("not json"))
        assertTrue(dangling.isNotBlank())
    }

    @Test fun dockHideMatchesTheIosChoices() {
        assertEquals(listOf(0, 5, 15, 30), DockHide.CHOICES)
        assertEquals(5, DockHide.normalize(5))
        assertEquals(0, DockHide.normalize(0))
        assertEquals(5, DockHide.normalize(6))
        assertEquals("关闭", DockHide.label(0))
        assertEquals("15 秒", DockHide.label(15))
    }
}
