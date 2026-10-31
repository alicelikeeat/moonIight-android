package com.moonlight.feed.data.social

/**
 * Clearly fake sample data ("示例"): monogram avatars, procedural `art:N` pictures only — no real people or photos.
 * Used by "载入示例" in the empty 朋友圈 and by screenshots.
 */
object DemoSocial {
    private const val MIN = 60_000L
    private const val HOUR = 60 * MIN

    fun seed(s: SocialState, now: Long = System.currentTimeMillis()): SocialState {
        val me = s.self
        fun f(name: String, bio: String, color: Int, auto: List<String> = emptyList()) =
            Contact(name = name, bio = "$bio · 示例好友", avatarColor = color, autoReplies = auto, createdAt = now - 30 * 24 * HOUR)
        val yue = f("阿月", "只在夜里出门拍月亮", 1, listOf("好呀～", "刚看到，等我一下", "哈哈哈哈", "今晚月亮好圆"))
        val lin = f("小林", "跑步 / 胶片 / 猫", 2)
        val nova = f("Nova", "Stargazer. Night owl.", 3)
        val ye = f("山野", "徒步中，信号时有时无", 4)
        val he = f("星河", "做一点点音乐", 5)
        fun img(n: Int) = Attachment(kind = AttachmentKind.IMAGE, coverUrl = "art:$n")
        fun vid(n: Int, t: String) = Attachment(kind = AttachmentKind.VIDEO, coverUrl = "art:$n", title = t)
        val moments = listOf(
            Moment(authorId = yue.id, text = "湖面上的月光，一次长曝光 30 秒。风停的那一下刚刚好。", attachments = listOf(img(0), img(4), img(2)), createdAt = now - 12 * MIN,
                likes = listOf(me.id, nova.id, lin.id), comments = listOf(
                    Comment(authorId = nova.id, text = "这个倒影绝了", createdAt = now - 9 * MIN),
                    Comment(authorId = me.id, text = "下次带上我", replyToId = null, createdAt = now - 6 * MIN),
                    Comment(authorId = yue.id, text = "好，周六见", replyToId = me.id, createdAt = now - 4 * MIN),
                )),
            Moment(authorId = lin.id, text = "夜跑 8 公里，路过山脊线的时候停下来录了一段。", attachments = listOf(vid(3, "山脊夜跑")), createdAt = now - 2 * HOUR,
                likes = listOf(ye.id, he.id), dislikes = listOf(nova.id)),
            Moment(authorId = me.id, text = "今天的月亮像一颗糖。", attachments = listOf(img(5)), createdAt = now - 5 * HOUR, likes = listOf(yue.id)),
            Moment(authorId = nova.id, text = "Milky Way season is back ✨ 一共拍了九张，挑几张。", attachments = listOf(img(1), img(4), img(0), img(2), img(3), img(5)), createdAt = now - 26 * HOUR,
                likes = listOf(me.id, yue.id, ye.id, he.id), comments = listOf(Comment(authorId = he.id, text = "可以做我新专辑封面吗", createdAt = now - 20 * HOUR))),
            Moment(authorId = ye.id, text = "营地没有信号，只有星星。", createdAt = now - 3 * 24 * HOUR),
        )
        val c1 = Conversation(memberIds = listOf(me.id, yue.id), createdAt = now - 3 * HOUR)
        val c2 = Conversation(memberIds = listOf(lin.id, nova.id), createdAt = now - 5 * HOUR)
        val c3 = Conversation(memberIds = listOf(me.id, lin.id, ye.id, he.id), title = "夜跑小队", isGroup = true, createdAt = now - 48 * HOUR)
        fun m(c: Conversation, who: Contact, text: String, ago: Long, st: MessageStatus = MessageStatus.SENT, att: List<Attachment> = emptyList()) =
            Message(conversationId = c.id, senderId = who.id, text = text, createdAt = now - ago, status = st, attachments = att)
        val messages = listOf(
            m(c1, yue, "今晚去湖边吗？月亮 21:40 升起", 58 * MIN),
            m(c1, me, "去！我带三脚架", 55 * MIN, MessageStatus.READ),
            m(c1, yue, "", 40 * MIN, att = listOf(img(0))),
            m(c1, yue, "上次拍的，这次争取更清楚", 39 * MIN),
            m(c1, me, "好看！那 21:15 湖边见", 12 * MIN, MessageStatus.READ),
            m(c1, me, "记得带外套，晚上冷", 2 * MIN, MessageStatus.SENT),
            m(c2, lin, "你那边看得到银河吗", 5 * HOUR),
            m(c2, nova, "看得到！刚拍完", 4 * HOUR),
            m(c3, ye, "周六老地方？", 30 * HOUR),
            m(c3, he, "+1", 29 * HOUR),
            m(c3, me, "我可能晚 10 分钟", 28 * HOUR, MessageStatus.READ),
        )
        return s.copy(
            contacts = s.contacts + listOf(yue, lin, nova, ye, he),
            moments = s.moments + moments,
            conversations = s.conversations + listOf(c1.copy(updatedAt = now - 2 * MIN, readAt = now - MIN), c2.copy(updatedAt = now - 4 * HOUR, readAt = now), c3.copy(updatedAt = now - 28 * HOUR, readAt = now - 31 * HOUR)),
            messages = s.messages + messages,
        )
    }
}
