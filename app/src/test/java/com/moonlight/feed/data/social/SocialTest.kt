package com.moonlight.feed.data.social

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SocialTest {
    private val now = 1_760_000_000_000L
    private fun demo() = DemoSocial.seed(SocialState.fresh(), now)

    @Test fun idsAreUuids() {
        val s = demo()
        val uuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        (s.contacts.map { it.id } + s.conversations.map { it.id } + s.messages.map { it.id } + s.moments.map { it.id } +
            s.moments.flatMap { m -> m.attachments.map { it.id } + m.comments.map { it.id } }).forEach { assertTrue(it, uuid.matches(it)) }
        assertEquals(s.messages.size, s.messages.map { it.id }.toSet().size)
    }

    @Test fun jsonRoundTripKeepsEverything() {
        val s0 = demo()
        val friend = s0.friends.first()
        val s = s0.copy(contacts = s0.contacts.map { if (it.id == friend.id) it.copy(avatarPath = "/a.jpg", remoteUserId = "r1", isVirtual = false, autoReplyOn = true) else it },
            messages = s0.messages + Message(conversationId = s0.conversations[0].id, senderId = s0.selfId, text = "x", serverAt = 42, status = MessageStatus.FAILED, error = "e",
                attachments = listOf(Attachment(kind = AttachmentKind.VIDEO, libraryEntryId = "lib1", localPath = "/v.mp4", remoteUrl = "https://u", remoteId = "up1", mime = "video/mp4", sizeBytes = 9))))
        val back = SocialJson.decode(SocialJson.encode(s))
        assertEquals(s, back)
    }

    @Test fun decodeRejectsNonBackups() {
        assertTrue(runCatching { SocialJson.decode("not json") }.isFailure)
        assertTrue(runCatching { SocialJson.decode("{\"foo\":1}") }.isFailure)
    }

    @Test fun deleteContactCascades() {
        val s = demo()
        val yue = s.friends.first { it.name == "阿月" }
        val lin = s.friends.first { it.name == "小林" }
        val d = SocialOps.deleteContact(s, lin.id)
        assertNull(d.contact(lin.id))
        assertTrue(d.moments.none { it.authorId == lin.id })
        assertTrue(d.moments.all { lin.id !in it.likes && it.comments.none { c -> c.authorId == lin.id } })
        assertTrue(d.conversations.none { lin.id in it.memberIds })                 // 小林↔Nova removed, group lost a member
        assertTrue(d.conversations.any { it.title == "夜跑小队" && it.memberIds.size == 3 })
        assertTrue(d.messages.none { it.senderId == lin.id })
        assertEquals(s.messagesOf(s.conversations.first { yue.id in it.memberIds }.id).size,
            d.messagesOf(d.conversations.first { yue.id in it.memberIds }.id).size)
        assertEquals(s, SocialOps.deleteContact(s, s.selfId))                        // self can't be deleted
    }

    @Test fun likesToggleAndComments() {
        val s = demo()
        val m = s.moments.first()
        val who = s.friends.last().id
        val liked = SocialOps.toggleLike(s, m.id, who)
        assertTrue(who in liked.moments.first { it.id == m.id }.likes)
        assertFalse(who in SocialOps.toggleLike(liked, m.id, who).moments.first { it.id == m.id }.likes)
        val c = Comment(authorId = who, text = "hi", replyToId = s.selfId)
        val withC = SocialOps.addComment(s, m.id, c)
        assertEquals(c, withC.moments.first { it.id == m.id }.comments.last())
        assertEquals(m.comments, SocialOps.deleteComment(withC, m.id, c.id).moments.first { it.id == m.id }.comments)
    }

    @Test fun conversationForReusesOneToOneButNotGroups() {
        val s = demo()
        val yue = s.friends.first { it.name == "阿月" }
        val (s1, c1) = SocialOps.conversationFor(s, listOf(yue.id, s.selfId))
        assertEquals(s, s1)
        assertTrue(c1.memberIds.toSet() == setOf(s.selfId, yue.id))
        val three = listOf(s.selfId, yue.id, s.friends.last().id)
        val (s2, g1) = SocialOps.conversationFor(s, three)
        val (_, g2) = SocialOps.conversationFor(s2, three)
        assertTrue(g1.isGroup); assertNotEquals(g1.id, g2.id)
        val (_, ff) = SocialOps.conversationFor(s, listOf(s.friends[1].id, s.friends[3].id))  // friend ↔ friend
        assertFalse(ff.isGroup); assertFalse(s.selfId in ff.memberIds)
    }

    @Test fun autoReplyOffByDefaultAndNoLoops() {
        assertFalse(Contact(name = "x").autoReplyOn)
        val s = demo()
        val yue = s.friends.first { it.name == "阿月" }
        val conv = s.conversations.first { yue.id in it.memberIds && !it.isGroup }
        val mine = Message(conversationId = conv.id, senderId = s.selfId, text = "在吗")
        assertTrue("seeded presets but toggle is off", AutoReply.replies(mine, conv, s.contacts).isEmpty())
        val on = s.contacts.map { if (it.id == yue.id) it.copy(autoReplyOn = true) else it }
        val r = AutoReply.replies(mine, conv, on, Random(1), now)
        assertEquals(1, r.size)
        assertEquals(yue.id, r[0].senderId); assertTrue(r[0].auto); assertTrue(r[0].text in yue.autoReplies)
        assertTrue(AutoReply.replies(r[0], conv, on).isEmpty())                     // auto never triggers auto
        assertTrue(AutoReply.replies(mine.copy(senderId = yue.id), conv, on).isEmpty()) // nobody answers themselves
        assertTrue(AutoReply.replies(mine, conv.copy(remote = true), on).isEmpty())  // never in server chats
    }

    @Test fun readReceiptsAndPutMessage() {
        val s = demo()
        val conv = s.conversations.first { it.memberIds.size == 2 && s.selfId in it.memberIds }
        val friend = conv.memberIds.first { it != s.selfId }
        val r = SocialOps.markReadBy(s, conv.id, friend, now)
        assertTrue(r.messagesOf(conv.id).filter { it.senderId == s.selfId }.all { it.status == MessageStatus.READ })
        val m = Message(conversationId = conv.id, senderId = friend, text = "新消息", createdAt = now + 5)
        val p = SocialOps.putMessage(s, m)
        assertEquals(m, p.lastMessage(conv.id))
        assertEquals(now + 5, p.conversations.first { it.id == conv.id }.updatedAt)
    }

    @Test fun mergeRemapsSelfAndDedupes() {
        val mine = SocialState.fresh()
        val other = demo()
        val merged = SocialOps.merge(mine, other)
        assertEquals(mine.selfId, merged.selfId)
        assertEquals(other.friends.size, merged.friends.size)
        assertTrue(merged.moments.none { it.authorId == other.selfId })
        assertTrue(merged.conversations.none { other.selfId in it.memberIds })
        assertEquals(merged, SocialOps.merge(merged, other).let { m2 -> m2 }.copy().let { SocialOps.merge(merged, SocialJson.decode(SocialJson.encode(other))) })
    }

    @Test fun transports() = runBlocking {
        val conv = Conversation(memberIds = listOf("a", "b"))
        val msg = Message(conversationId = conv.id, senderId = "a", text = "x")
        assertEquals(MessageStatus.SENT, LocalTransport().send(msg, conv).status)
        val remote = RemoteTransport(ChatServerConfig("https://chat.example.com"))
        assertFalse(remote.active)
        val r = remote.send(msg, conv)
        assertEquals(MessageStatus.FAILED, r.status); assertTrue(r.error!!.contains("即将推出"))
        assertEquals("wss://chat.example.com/v1/ws", remote.config.wsUrl())
        assertEquals("/v1/conversations/c1/messages", RemoteTransport.messages("c1"))
    }

    @Test fun demoIsClearlyFake() {
        val s = demo()
        assertTrue(s.friends.all { it.isVirtual && it.bio.contains("示例") && it.avatarPath == null })
        assertTrue(s.moments.flatMap { it.attachments }.all { it.coverUrl!!.startsWith("art:") && it.localPath == null })
    }
}
