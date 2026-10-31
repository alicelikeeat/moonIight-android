package com.moonlight.feed.data.social

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialV30Test {
    private val now = 1_760_000_000_000L
    private fun demo() = DemoSocial.seed(SocialState.fresh(), now)

    @Test fun likeAndDislikeAreMutuallyExclusivePerPerson() {
        val s = demo(); val m = s.timeline.first(); val me = s.selfId; val other = s.friends.first().id
        var t = SocialOps.toggleLike(s, m.id, me)
        t = SocialOps.toggleDislike(t, m.id, me)
        var mm = t.moments.first { it.id == m.id }
        assertTrue(me in mm.dislikes); assertFalse("踩 removes 赞", me in mm.likes)
        t = SocialOps.toggleLike(t, m.id, me)
        mm = t.moments.first { it.id == m.id }
        assertTrue(me in mm.likes); assertFalse("赞 removes 踩", me in mm.dislikes)
        // other people are independent
        t = SocialOps.toggleDislike(t, m.id, other)
        mm = t.moments.first { it.id == m.id }
        assertTrue(me in mm.likes); assertTrue(other in mm.dislikes)
        // toggling off
        t = SocialOps.toggleDislike(t, m.id, other)
        assertFalse(other in t.moments.first { it.id == m.id }.dislikes)
    }

    @Test fun dislikesCoversAndReadMarkersSurviveBackup() {
        val s0 = demo()
        val f = s0.friends.first()
        val s = s0.copy(contacts = s0.contacts.map { if (it.id == f.id) it.copy(avatarPath = "/a.mp4", avatarVideo = true, coverPath = "/c.jpg", coverVideo = false) else it })
        val back = SocialJson.decode(SocialJson.encode(s))
        assertEquals(s, back)
        assertTrue(back.moments.any { it.dislikes.isNotEmpty() })
        assertEquals(2, JSONObject(SocialJson.encode(s)).getInt("schema"))
    }

    @Test fun schema1BackupImportsWithoutUnreadFlood() {
        val s = demo()
        val o = JSONObject(SocialJson.encode(s))
        o.put("schema", 1)
        val convs = o.getJSONArray("conversations"); for (i in 0 until convs.length()) convs.getJSONObject(i).remove("readAt")
        val ms = o.getJSONArray("moments"); for (i in 0 until ms.length()) ms.getJSONObject(i).remove("dislikes")
        val back = SocialJson.decode(o.toString())
        assertEquals(0, back.totalUnread)
        assertTrue(back.moments.all { it.dislikes.isEmpty() })
    }

    @Test fun unreadCountsAndMarkRead() {
        val s = demo()
        val group = s.conversations.first { it.isGroup }
        assertEquals(2, s.unread(group.id))
        assertEquals("friend↔friend chats never count", 0, s.unread(s.conversations.first { s.selfId !in it.memberIds }.id))
        assertEquals(2, s.totalUnread)
        val read = SocialOps.markRead(s, group.id, now)
        assertEquals(0, read.totalUnread)
        // own messages never count
        val mine = SocialOps.putMessage(read, Message(conversationId = group.id, senderId = s.selfId, text = "hi", createdAt = now + 5))
        assertEquals(0, mine.totalUnread)
        val theirs = SocialOps.putMessage(read, Message(conversationId = group.id, senderId = group.memberIds.last(), text = "yo", createdAt = now + 5))
        assertEquals(1, theirs.totalUnread)
    }

    @Test fun deletingAndMergingCarryDislikes() {
        val s = demo()
        val nova = s.friends.first { it.name == "Nova" }
        assertTrue(s.moments.any { nova.id in it.dislikes })
        assertTrue(SocialOps.deleteContact(s, nova.id).moments.none { nova.id in it.dislikes })
        val other = SocialState.fresh().let { f -> f.copy(moments = listOf(Moment(authorId = f.selfId, dislikes = listOf(f.selfId)))) }
        val merged = SocialOps.merge(s, other)
        assertEquals(listOf(s.selfId), merged.moments.first { it.id == other.moments[0].id }.dislikes)
    }
}
