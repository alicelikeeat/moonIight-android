package com.moonlight.feed.data.social

import android.content.Context
import android.net.Uri
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.MediaKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


/** Library entry → moment/message attachment (keeps a snapshot so it still renders if the entry is removed). */
fun attachmentOf(e: MediaEntry) = Attachment(
    kind = when (e.kind) { MediaKind.IMAGE -> AttachmentKind.IMAGE; MediaKind.VIDEO -> AttachmentKind.VIDEO; else -> AttachmentKind.EMBED },
    libraryEntryId = e.id, localPath = e.localPath, coverUrl = e.coverUrl ?: if (e.kind == MediaKind.IMAGE) e.remoteUrl else null,
    remoteUrl = e.remoteUrl, title = e.title,
)

/** Pure state edits (unit-tested); [SocialRepository] applies + persists them. */
object SocialOps {
    fun upsertContact(s: SocialState, c: Contact) =
        s.copy(contacts = if (s.contacts.any { it.id == c.id }) s.contacts.map { if (it.id == c.id) c.copy(updatedAt = System.currentTimeMillis()) else it } else s.contacts + c)

    /** Removes a friend with everything they wrote; 1:1 chats with them go, groups just lose the member. */
    fun deleteContact(s: SocialState, id: String): SocialState {
        if (id == s.selfId) return s
        val convs = s.conversations.mapNotNull { c ->
            if (id !in c.memberIds) c else {
                val left = c.memberIds - id
                if (!c.isGroup || left.size < 2) null else c.copy(memberIds = left)
            }
        }
        val convIds = convs.map { it.id }.toSet()
        return s.copy(
            contacts = s.contacts.filter { it.id != id },
            moments = s.moments.filter { it.authorId != id }.map { m -> m.copy(likes = m.likes - id, dislikes = m.dislikes - id, comments = m.comments.filter { it.authorId != id }) },
            conversations = convs,
            messages = s.messages.filter { it.conversationId in convIds && it.senderId != id },
        )
    }

    fun upsertMoment(s: SocialState, m: Moment) =
        s.copy(moments = if (s.moments.any { it.id == m.id }) s.moments.map { if (it.id == m.id) m else it } else s.moments + m)
    fun deleteMoment(s: SocialState, id: String) = s.copy(moments = s.moments.filter { it.id != id })
    /** 赞 and 踩 are mutually exclusive per person: adding one removes the other. */
    fun toggleLike(s: SocialState, momentId: String, who: String) = s.copy(moments = s.moments.map { m ->
        if (m.id != momentId) m else if (who in m.likes) m.copy(likes = m.likes - who) else m.copy(likes = m.likes + who, dislikes = m.dislikes - who)
    })
    fun toggleDislike(s: SocialState, momentId: String, who: String) = s.copy(moments = s.moments.map { m ->
        if (m.id != momentId) m else if (who in m.dislikes) m.copy(dislikes = m.dislikes - who) else m.copy(dislikes = m.dislikes + who, likes = m.likes - who)
    })
    fun markRead(s: SocialState, convId: String, at: Long) =
        s.copy(conversations = s.conversations.map { if (it.id == convId && it.readAt < at) it.copy(readAt = at) else it })
    fun addComment(s: SocialState, momentId: String, c: Comment) = s.copy(moments = s.moments.map { if (it.id == momentId) it.copy(comments = it.comments + c) else it })
    fun deleteComment(s: SocialState, momentId: String, commentId: String) =
        s.copy(moments = s.moments.map { if (it.id == momentId) it.copy(comments = it.comments.filter { c -> c.id != commentId }) else it })

    /** Existing 1:1 (or same-member) conversation, or a new one. */
    fun conversationFor(s: SocialState, members: List<String>, title: String? = null, group: Boolean = members.distinct().size > 2): Pair<SocialState, Conversation> {
        val set = members.distinct()
        if (!group) s.conversations.firstOrNull { !it.isGroup && it.memberIds.toSet() == set.toSet() }?.let { return s to it }
        val c = Conversation(memberIds = set, title = title?.ifBlank { null }, isGroup = group)
        return s.copy(conversations = s.conversations + c) to c
    }

    fun putMessage(s: SocialState, m: Message) = s.copy(
        messages = if (s.messages.any { it.id == m.id }) s.messages.map { if (it.id == m.id) m else it } else s.messages + m,
        conversations = s.conversations.map { if (it.id == m.conversationId) it.copy(updatedAt = maxOf(it.updatedAt, m.createdAt)) else it },
    )

    /** Marks earlier messages not sent by [reader] as read (virtual read receipt when a friend answers). */
    fun markReadBy(s: SocialState, convId: String, reader: String, upTo: Long) = s.copy(messages = s.messages.map {
        if (it.conversationId == convId && it.senderId != reader && it.createdAt <= upTo && (it.status == MessageStatus.SENT || it.status == MessageStatus.DELIVERED)) it.copy(status = MessageStatus.READ) else it
    })

    /** Merge an imported backup: contacts/moments/conversations/messages by id; local self stays. */
    fun merge(s: SocialState, other: SocialState): SocialState {
        fun <T> mergeBy(a: List<T>, b: List<T>, id: (T) -> String): List<T> { val ids = a.map(id).toSet(); return a + b.filter { id(it) !in ids } }
        val remap: (String) -> String = { if (it == other.selfId) s.selfId else it }
        val o = other.copy(
            contacts = other.contacts.filter { it.id != other.selfId },
            moments = other.moments.map { m -> m.copy(authorId = remap(m.authorId), likes = m.likes.map(remap), dislikes = m.dislikes.map(remap), comments = m.comments.map { it.copy(authorId = remap(it.authorId)) }) },
            conversations = other.conversations.map { it.copy(memberIds = it.memberIds.map(remap)) },
            messages = other.messages.map { it.copy(senderId = remap(it.senderId)) },
        )
        return s.copy(
            contacts = mergeBy(s.contacts, o.contacts) { it.id }, moments = mergeBy(s.moments, o.moments) { it.id },
            conversations = mergeBy(s.conversations, o.conversations) { it.id }, messages = mergeBy(s.messages, o.messages) { it.id },
        )
    }
}

class SocialRepository(private val ctx: Context, private val serverConfig: () -> ChatServerConfig = { ChatServerConfig() }) {
    private val file = File(ctx.filesDir, "social.json")
    private val avatarDir = File(ctx.filesDir, "avatars").also { it.mkdirs() }
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writer = com.moonlight.feed.data.download.CoalescingWriter(file, io, debounceMs = 250, encode = SocialJson::encode)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<SocialState> = _state.asStateFlow()
    val local = LocalTransport()
    fun transportFor(c: Conversation): ChatTransport = if (c.remote) RemoteTransport(serverConfig()) else local

    private fun load(): SocialState = runCatching { if (file.exists()) SocialJson.decode(file.readText()) else null }.getOrNull()
        ?: SocialState.fresh().also { writer.submit(it) }

    /**
     * v4.4: the caller only swaps the in-memory state. Encoding + the tmp/fsync/rename write run on IO, debounced,
     * latest-wins — a burst of edits (auto-replies, typing receipts, likes) is one write of the final state.
     */
    @Synchronized private fun edit(f: (SocialState) -> SocialState): SocialState {
        val n = f(_state.value); _state.value = n
        writer.submit(n)
        return n
    }
    /** Writes the pending state now (app going to the background). */
    suspend fun flush() = writer.flush()
    val value get() = _state.value

    fun restore(s: SocialState) { edit { s } }
    fun upsertContact(c: Contact) = edit { SocialOps.upsertContact(it, c) }
    fun deleteContact(id: String) = edit { SocialOps.deleteContact(it, id) }
    fun upsertMoment(m: Moment) = edit { SocialOps.upsertMoment(it, m) }
    fun deleteMoment(id: String) = edit { SocialOps.deleteMoment(it, id) }
    fun toggleLike(momentId: String, who: String) = edit { SocialOps.toggleLike(it, momentId, who) }
    fun toggleDislike(momentId: String, who: String) = edit { SocialOps.toggleDislike(it, momentId, who) }
    fun markRead(convId: String, at: Long = System.currentTimeMillis()) {
        if ((value.conversations.firstOrNull { it.id == convId }?.readAt ?: Long.MAX_VALUE) < at) edit { SocialOps.markRead(it, convId, at) }
    }
    fun addComment(momentId: String, c: Comment) = edit { SocialOps.addComment(it, momentId, c) }
    fun deleteComment(momentId: String, commentId: String) = edit { SocialOps.deleteComment(it, momentId, commentId) }
    fun conversationFor(members: List<String>, title: String? = null, group: Boolean = members.distinct().size > 2): Conversation {
        var conv: Conversation? = null
        edit { s -> SocialOps.conversationFor(s, members, title, group).also { conv = it.second }.first }
        return conv!!
    }
    fun updateConversation(c: Conversation) = edit { s -> s.copy(conversations = s.conversations.map { if (it.id == c.id) c else it }) }
    fun deleteConversation(id: String) = edit { s -> s.copy(conversations = s.conversations.filter { it.id != id }, messages = s.messages.filter { it.conversationId != id }) }
    fun clearConversation(id: String) = edit { s -> s.copy(messages = s.messages.filter { it.conversationId != id }) }
    fun deleteMessage(id: String) = edit { s -> s.copy(messages = s.messages.filter { it.id != id }) }
    fun updateMessage(m: Message) = edit { SocialOps.putMessage(it, m) }

    /** Store → hand to the conversation's transport → update status; then virtual auto-replies (if enabled). */
    fun send(convId: String, senderId: String, text: String, attachments: List<Attachment> = emptyList()) {
        val conv = value.conversations.firstOrNull { it.id == convId } ?: return
        val msg = Message(conversationId = convId, senderId = senderId, text = text.trim(), attachments = attachments, status = MessageStatus.SENDING)
        edit { SocialOps.putMessage(it, msg) }
        io.launch {
            val r = transportFor(conv).send(msg, conv)
            edit { SocialOps.putMessage(it, msg.copy(status = r.status, serverAt = r.serverAt, error = r.error)) }
            if (r.status == MessageStatus.FAILED) return@launch
            AutoReply.replies(msg, conv, value.contacts).forEachIndexed { i, reply ->
                delay(900L + i * 700L + (0..600).random())
                val at = System.currentTimeMillis()
                edit { s -> SocialOps.putMessage(SocialOps.markReadBy(s, convId, reply.senderId, at), reply.copy(createdAt = at)) }
            }
        }
    }

    suspend fun saveAvatar(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val out = File(avatarDir, "${newId()}.jpg")
            ctx.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } } ?: return@runCatching null
            if (out.length() == 0L) { out.delete(); null } else out.absolutePath
        }.getOrNull()
    }

    /**
     * Copies a picked photo/video (avatar, 朋友圈 cover, profile background) into app storage so it survives
     * gallery deletion. Returns (path, isVideo).
     */
    suspend fun saveProfileMedia(uri: Uri): Pair<String, Boolean>? = withContext(Dispatchers.IO) {
        runCatching {
            val mime = ctx.contentResolver.getType(uri).orEmpty()
            val video = mime.startsWith("video/")
            val ext = if (video) (android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "mp4") else "jpg"
            val out = File(avatarDir, "${newId()}.$ext")
            ctx.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } } ?: return@runCatching null
            if (out.length() == 0L) { out.delete(); null } else out.absolutePath to video
        }.getOrNull()
    }

    fun exportJson(): String = SocialJson.encode(value)
    /** Imports a backup. [replace] = true overwrites; false merges by id. Returns counts. */
    fun importJson(text: String, replace: Boolean): SocialState {
        val incoming = SocialJson.decode(text)
        return edit { if (replace) incoming else SocialOps.merge(it, incoming) }
    }

    fun seedDemo() = edit { DemoSocial.seed(it) }
}
