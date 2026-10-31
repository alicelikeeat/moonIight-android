package com.moonlight.feed.data.social

import org.json.JSONArray
import org.json.JSONObject

/** Stable JSON codec for local storage and export/backup (also the shape synced to a future server). */
object SocialJson {
    private fun JSONObject.str(k: String): String? = if (has(k) && !isNull(k)) optString(k).ifEmpty { null } else null
    private fun JSONObject.lng(k: String): Long? = if (has(k) && !isNull(k)) optLong(k) else null
    private fun strings(a: JSONArray?) = if (a == null) emptyList() else List(a.length()) { a.getString(it) }
    private fun <T> arr(a: JSONArray?, f: (JSONObject) -> T) = if (a == null) emptyList() else List(a.length()) { f(a.getJSONObject(it)) }

    private fun saved(m: SavedMedia?): Any = m?.let { JSONObject().apply { put("path", it.path); put("video", it.video); put("focus", it.focus?.encode() ?: JSONObject.NULL) } } ?: JSONObject.NULL
    private fun saved(o: JSONObject?): SavedMedia? = o?.str("path")?.let { SavedMedia(it, o.optBoolean("video"), com.moonlight.feed.data.MediaFocus.decode(o.str("focus"))) }

    fun contact(c: Contact) = JSONObject().apply {
        put("id", c.id); put("name", c.name); put("bio", c.bio); put("avatarPath", c.avatarPath ?: JSONObject.NULL)
        put("avatarColor", c.avatarColor); put("avatarVideo", c.avatarVideo)
        put("coverPath", c.coverPath ?: JSONObject.NULL); put("coverVideo", c.coverVideo)
        put("avatarFocus", c.avatarFocus?.encode() ?: JSONObject.NULL); put("coverFocus", c.coverFocus?.encode() ?: JSONObject.NULL); put("isVirtual", c.isVirtual); put("isSelf", c.isSelf)
        put("prevAvatar", saved(c.prevAvatar)); put("prevCover", saved(c.prevCover))
        put("remoteUserId", c.remoteUserId ?: JSONObject.NULL); put("autoReplyOn", c.autoReplyOn)
        put("autoReplies", JSONArray(c.autoReplies)); put("createdAt", c.createdAt); put("updatedAt", c.updatedAt)
    }
    fun contact(o: JSONObject) = Contact(
        id = o.getString("id"), name = o.optString("name"), bio = o.optString("bio"), avatarPath = o.str("avatarPath"),
        avatarColor = o.optInt("avatarColor"), avatarVideo = o.optBoolean("avatarVideo"),
        coverPath = o.str("coverPath"), coverVideo = o.optBoolean("coverVideo"),
        avatarFocus = com.moonlight.feed.data.MediaFocus.decode(o.str("avatarFocus")), coverFocus = com.moonlight.feed.data.MediaFocus.decode(o.str("coverFocus")), isVirtual = o.optBoolean("isVirtual", true), isSelf = o.optBoolean("isSelf"),
        prevAvatar = saved(o.optJSONObject("prevAvatar")), prevCover = saved(o.optJSONObject("prevCover")),
        remoteUserId = o.str("remoteUserId"), autoReplyOn = o.optBoolean("autoReplyOn"), autoReplies = strings(o.optJSONArray("autoReplies")),
        createdAt = o.optLong("createdAt"), updatedAt = o.optLong("updatedAt", o.optLong("createdAt")),
    )

    fun attachment(a: Attachment) = JSONObject().apply {
        put("id", a.id); put("kind", a.kind.name); put("libraryEntryId", a.libraryEntryId ?: JSONObject.NULL)
        put("localPath", a.localPath ?: JSONObject.NULL); put("coverUrl", a.coverUrl ?: JSONObject.NULL)
        put("remoteUrl", a.remoteUrl ?: JSONObject.NULL); put("remoteId", a.remoteId ?: JSONObject.NULL)
        put("mime", a.mime ?: JSONObject.NULL); put("sizeBytes", a.sizeBytes); put("title", a.title)
    }
    fun attachment(o: JSONObject) = Attachment(
        id = o.getString("id"), kind = runCatching { AttachmentKind.valueOf(o.getString("kind")) }.getOrDefault(AttachmentKind.IMAGE),
        libraryEntryId = o.str("libraryEntryId"), localPath = o.str("localPath"), coverUrl = o.str("coverUrl"),
        remoteUrl = o.str("remoteUrl"), remoteId = o.str("remoteId"), mime = o.str("mime"), sizeBytes = o.optLong("sizeBytes"), title = o.optString("title"),
    )

    fun comment(c: Comment) = JSONObject().apply {
        put("id", c.id); put("authorId", c.authorId); put("text", c.text); put("replyToId", c.replyToId ?: JSONObject.NULL); put("createdAt", c.createdAt)
    }
    fun comment(o: JSONObject) = Comment(o.getString("id"), o.getString("authorId"), o.optString("text"), o.str("replyToId"), o.optLong("createdAt"))

    fun moment(m: Moment) = JSONObject().apply {
        put("id", m.id); put("authorId", m.authorId); put("text", m.text)
        put("attachments", JSONArray(m.attachments.map(::attachment))); put("createdAt", m.createdAt)
        put("editedAt", m.editedAt ?: JSONObject.NULL); put("likes", JSONArray(m.likes)); put("dislikes", JSONArray(m.dislikes)); put("comments", JSONArray(m.comments.map(::comment)))
        if (m.autoplay) put("autoplay", true)
    }
    fun moment(o: JSONObject) = Moment(
        id = o.getString("id"), authorId = o.getString("authorId"), text = o.optString("text"),
        attachments = arr(o.optJSONArray("attachments"), ::attachment), createdAt = o.optLong("createdAt"), editedAt = o.lng("editedAt"),
        likes = strings(o.optJSONArray("likes")), dislikes = strings(o.optJSONArray("dislikes")), comments = arr(o.optJSONArray("comments"), ::comment),
        autoplay = o.optBoolean("autoplay", false),
    )

    fun conversation(c: Conversation) = JSONObject().apply {
        put("id", c.id); put("memberIds", JSONArray(c.memberIds)); put("title", c.title ?: JSONObject.NULL); put("isGroup", c.isGroup)
        put("remote", c.remote); put("remoteId", c.remoteId ?: JSONObject.NULL); put("pinned", c.pinned)
        put("createdAt", c.createdAt); put("updatedAt", c.updatedAt); put("readAt", c.readAt)
    }
    fun conversation(o: JSONObject) = Conversation(
        id = o.getString("id"), memberIds = strings(o.optJSONArray("memberIds")), title = o.str("title"), isGroup = o.optBoolean("isGroup"),
        remote = o.optBoolean("remote"), remoteId = o.str("remoteId"), pinned = o.optBoolean("pinned"),
        createdAt = o.optLong("createdAt"), updatedAt = o.optLong("updatedAt", o.optLong("createdAt")),
        // schema-1 files have no read marker: treat what was already there as read
        readAt = if (o.has("readAt")) o.optLong("readAt") else Long.MAX_VALUE / 2,
    )

    fun message(m: Message) = JSONObject().apply {
        put("id", m.id); put("conversationId", m.conversationId); put("senderId", m.senderId); put("text", m.text)
        put("attachments", JSONArray(m.attachments.map(::attachment))); put("createdAt", m.createdAt)
        put("serverAt", m.serverAt ?: JSONObject.NULL); put("status", m.status.name); put("auto", m.auto); put("error", m.error ?: JSONObject.NULL)
    }
    fun message(o: JSONObject) = Message(
        id = o.getString("id"), conversationId = o.getString("conversationId"), senderId = o.getString("senderId"), text = o.optString("text"),
        attachments = arr(o.optJSONArray("attachments"), ::attachment), createdAt = o.optLong("createdAt"), serverAt = o.lng("serverAt"),
        status = runCatching { MessageStatus.valueOf(o.getString("status")) }.getOrDefault(MessageStatus.LOCAL),
        auto = o.optBoolean("auto"), error = o.str("error"),
    )

    fun encode(s: SocialState): String = JSONObject().apply {
        put("schema", s.schema); put("app", "moonlight"); put("selfId", s.selfId)
        put("contacts", JSONArray(s.contacts.map(::contact))); put("moments", JSONArray(s.moments.map(::moment)))
        put("conversations", JSONArray(s.conversations.map(::conversation))); put("messages", JSONArray(s.messages.map(::message)))
    }.toString()

    /** Decodes; throws IllegalArgumentException on data that isn't a 月光 social backup. */
    fun decode(text: String): SocialState {
        val o = runCatching { JSONObject(text) }.getOrElse { throw IllegalArgumentException("不是有效的 JSON") }
        val selfId = o.optString("selfId").ifEmpty { throw IllegalArgumentException("缺少 selfId，不是月光备份") }
        var contacts = arr(o.optJSONArray("contacts"), ::contact)
        if (contacts.none { it.id == selfId }) contacts = listOf(Contact(id = selfId, name = "我", isSelf = true, isVirtual = false)) + contacts
        return SocialState(
            selfId = selfId, contacts = contacts,
            moments = arr(o.optJSONArray("moments"), ::moment),
            conversations = arr(o.optJSONArray("conversations"), ::conversation),
            messages = arr(o.optJSONArray("messages"), ::message),
            schema = o.optInt("schema", SocialState.SCHEMA),
        )
    }
}
