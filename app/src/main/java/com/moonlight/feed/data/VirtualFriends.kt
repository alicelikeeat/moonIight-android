package com.moonlight.feed.data

import com.moonlight.feed.MoonApp
import com.moonlight.feed.data.social.Attachment
import com.moonlight.feed.data.social.AttachmentKind
import com.moonlight.feed.data.social.Comment
import com.moonlight.feed.data.social.Moment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Opt-in local moments from virtual friends. Off until the user enables it.
 * An API key, if the user types one, stays in app storage and is sent only to the provider they chose.
 * Missing key or a failed request falls back to a local sentence. Nothing is posted to an external social network.
 */
object VirtualFriends {
    suspend fun tick(app: MoonApp, s: SettingsRepository.Snapshot): Boolean {
        if (!s.virtualFriends) return false
        val friends = app.social.state.value.friends.filter { it.isVirtual }
        val author = friends.randomOrNull() ?: return false
        val entry = ContentImport.fetch(app, 10).firstOrNull()
        if (entry != null) app.library.upsert(entry)
        val text = caption(entry?.title.orEmpty(), entry?.author.orEmpty(), s.virtualProvider, s.virtualKey, app)
        val moment = Moment(
            authorId = author.id,
            text = text,
            attachments = listOfNotNull(entry?.let { attachment(it) }),
        )
        app.social.upsertMoment(moment)
        val liker = friends.filter { it.id != author.id }.randomOrNull()
        if (liker != null) app.social.toggleLike(moment.id, liker.id)
        val commenter = friends.randomOrNull()
        if (commenter != null) {
            app.social.addComment(moment.id, Comment(authorId = commenter.id, text = VirtualCopy.comment()))
        }
        return true
    }

    internal suspend fun caption(title: String, author: String, provider: String, key: String, app: MoonApp): String {
        val local = VirtualCopy.local(title, author)
        if (key.isBlank()) return local
        return withContext(Dispatchers.IO) {
            runCatching { remote(title, author, provider, key, app) }.getOrDefault(local).ifBlank { local }
        }
    }

    private fun remote(title: String, author: String, provider: String, key: String, app: MoonApp): String {
        val grok = provider.equals("grok", true)
        val url = if (grok) "https://api.x.ai/v1/chat/completions" else "https://api.openai.com/v1/chat/completions"
        val model = if (grok) "grok-2-latest" else "gpt-4o-mini"
        val prompt = "为本地虚拟好友写一条自然、简短的中文朋友圈。只根据标题和作者，不要编造看过视频的事实。只返回一句文案。标题：${title.take(160)}；作者：${author.take(80)}。"
        val payload = JSONObject()
            .put("model", model)
            .put("max_tokens", 80)
            .put("messages", org.json.JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            .toString()
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $key")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        app.http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return VirtualCopy.local(title, author)
            val root = JSONObject(resp.body?.string().orEmpty())
            val text = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            return text.trim().take(180)
        }
    }

    private fun attachment(e: MediaEntry) = Attachment(
        kind = when (e.kind) {
            MediaKind.IMAGE -> AttachmentKind.IMAGE
            MediaKind.VIDEO -> AttachmentKind.VIDEO
            else -> AttachmentKind.EMBED
        },
        libraryEntryId = e.id,
        localPath = e.localPath,
        coverUrl = e.coverUrl,
        remoteUrl = e.remoteUrl ?: e.embedUrl,
        title = e.title,
    )
}
