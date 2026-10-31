package com.moonlight.feed.data.social

import kotlin.random.Random

/** Preset auto-replies for virtual friends (off by default). Pure — no network / AI. */
object AutoReply {
    /**
     * Who answers [msg] in [conv]: every other virtual member with auto-reply on and at least one preset.
     * Auto messages never trigger further replies, so two auto-replying friends cannot loop.
     */
    fun replies(msg: Message, conv: Conversation, contacts: List<Contact>, rnd: Random = Random.Default, now: Long = System.currentTimeMillis()): List<Message> {
        if (msg.auto || conv.remote) return emptyList()
        val byId = contacts.associateBy { it.id }
        return conv.memberIds.filter { it != msg.senderId }.mapNotNull { byId[it] }
            .filter { it.isVirtual && !it.isSelf && it.autoReplyOn && it.autoReplies.any { r -> r.isNotBlank() } }
            .mapIndexed { i, c ->
                val text = c.autoReplies.filter { it.isNotBlank() }.random(rnd)
                Message(conversationId = conv.id, senderId = c.id, text = text, createdAt = now + 1 + i, status = MessageStatus.SENT, auto = true)
            }
    }
}
