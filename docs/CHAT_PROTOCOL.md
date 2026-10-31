# 月光 Chat Protocol (draft v0)

Status: **planned, not implemented.** The app today talks only to `LocalTransport`
(everything on-device). `RemoteTransport` in
`app/src/main/java/com/moonlight/feed/data/social/ChatTransport.kt` is an inactive stub with the
endpoint constants below. Settings > 聊天服务器 stores a server URL but "连接" is disabled (即将推出).
This document is the contract a backend should implement so the client can switch on.

## Principles

- **Client-generated UUIDs** (v4) for users, conversations, messages, attachments, moments, and comments.
  The server must accept them, so sends are **idempotent**: re-sending the same `id` returns the stored message.
- **Two clocks.** `createdAt` is the client wall clock (it can be back-dated by the user for virtual chats).
  `serverAt` is the server's monotonic ordering timestamp in ms, assigned on accept. Clients order by
  `serverAt ?: createdAt`.
- **Virtual and real people coexist.** `Contact.isVirtual = true` contacts never leave the device and
  are never sent to the server. A real account has `isVirtual = false` and `remoteUserId`.
  A conversation is server-backed only if `remote = true`, and then every member must be a real account.
- Everything is JSON over HTTPS, plus one WebSocket for realtime events. All times are epoch ms (UTC).

## Auth

| | |
|---|---|
| `POST /v1/auth/token` | body `{ "method": "password" \| "magic_link" \| "device", "login": "...", "secret": "...", "deviceId": "uuid", "deviceName": "Pixel 8" }` → `{ "accessToken", "refreshToken", "expiresIn", "user": User }` |
| `POST /v1/auth/refresh` | `{ "refreshToken" }` → same shape |
| `GET /v1/me` | → `User` |
| `PATCH /v1/me` | `{ "name"?, "bio"?, "avatarUploadId"? }` |

Use the `Authorization: Bearer <accessToken>` header on every request and on the WebSocket upgrade.

```json
User { "id": "uuid", "name": "string", "bio": "string", "avatarUrl": "https://… | null" }
```

## Conversations

| | |
|---|---|
| `GET /v1/conversations?cursor=` | → `{ "items": [Conversation], "cursor": "opaque|null" }` |
| `POST /v1/conversations` | `{ "id": "uuid", "memberIds": ["uuid"], "title": "string|null", "isGroup": bool }` → `Conversation` (idempotent on `id`; for 1:1 the server may return an existing conversation's id) |
| `PATCH /v1/conversations/{id}` | `{ "title"?, "addMembers"?, "removeMembers"? }` |

```json
Conversation { "id", "memberIds": [], "title": null, "isGroup": false, "createdAt": 0, "updatedAt": 0, "readAt": 0 }
```

`readAt` is the reader's own marker (drives the unread badge): messages from others with
`createdAt > readAt` are unread. It syncs per user via `POST /v1/conversations/{id}/read { "readAt": 0 }`
and is what produces `read` receipts for the other members.

## Messages

| | |
|---|---|
| `POST /v1/conversations/{id}/messages` | body `Message` with `status` omitted → `201 { "id", "serverAt", "status": "sent" }`; replay of a known id → `200` with the same body |
| `GET /v1/conversations/{id}/messages?before=serverAt&limit=50` | history page, newest first |

```json
Message {
  "id": "uuid", "conversationId": "uuid", "senderId": "uuid",
  "text": "string", "attachments": [Attachment],
  "createdAt": 0, "serverAt": 0,
  "status": "sending|sent|delivered|read|failed"
}
Attachment { "id": "uuid", "kind": "image|video", "uploadId": "uuid", "url": "https://…", "mime": "image/jpeg", "sizeBytes": 0, "width": 0, "height": 0, "durationMs": 0 }
```

### Client status machine (`MessageStatus`)

`LOCAL` (stored only on device; virtual chats) · `SENDING` → `SENT` (server accepted, has `serverAt`) →
`DELIVERED` (on ≥1 recipient device) → `READ` (recipient opened the conversation) · `FAILED` (retryable;
the client keeps the same `id` when retrying).

The UI shows this subtly under the user's own bubbles: ⏱ · ✓ · ✓✓ · ✓✓ in the accent colour · "!".

## Sync

`GET /v1/sync?cursor=<opaque>&limit=500` → `{ "messages": [Message], "conversations": [Conversation], "receipts": [Receipt], "cursor": "opaque", "hasMore": bool }`

The client stores the cursor and calls sync on app start, on reconnect, and whenever the WebSocket
reports `gap`. Merging is by `id` (last `serverAt` wins).

## Receipts

`POST /v1/conversations/{id}/receipts` `{ "type": "delivered|read", "upToMessageId": "uuid" }`

```json
Receipt { "conversationId", "userId", "type": "delivered|read", "upToMessageId", "at": 0 }
```

## Realtime: WebSocket `GET /v1/ws` (`wss://`)

Server → client frames: `{ "type": "message", "data": Message }`, `{ "type": "receipt", "data": Receipt }`,
`{ "type": "conversation", "data": Conversation }`, `{ "type": "typing", "data": { "conversationId", "userId" } }`,
`{ "type": "gap" }` (the client should call `/v1/sync`), and `{ "type": "ping" }`.
Client → server frames: `{ "type": "pong" }` and `{ "type": "typing", "conversationId" }`.
Sending messages always goes over HTTP POST (it's idempotent and retryable); the WebSocket is only for push.
On reconnect, use exponential backoff of 1 s → 60 s.

## Media upload

1. `POST /v1/uploads` `{ "id": "uuid", "mime": "video/mp4", "sizeBytes": 123, "sha256": "hex" }` → `{ "uploadId", "putUrl", "headers": {}, "expiresAt" }` (a pre-signed PUT; resumable/chunked uploads are optional for large videos)
2. `PUT putUrl` (raw bytes)
3. `POST /v1/uploads/{uploadId}/complete` → `{ "url", "width", "height", "durationMs" }`
4. Reference `uploadId` from `Attachment` when sending the message.

The client keeps `Attachment.localPath` (and `libraryEntryId`) and fills in `remoteId`/`remoteUrl` afterwards,
so local playback never waits on the network.

## Errors

`{ "error": { "code": "unauthorized|forbidden|not_found|conflict|rate_limited|invalid", "message": "…" } }`
with matching HTTP status. `429` carries `Retry-After`.

## Users / profile media (schema 2)

```json
User { "id": "uuid", "name": "string", "bio": "string",
       "avatar": { "url": "https://…", "kind": "image|video" } | null,
       "cover":  { "url": "https://…", "kind": "image|video" } | null }
```

Locally a contact stores `avatarPath` + `avatarVideo` and `coverPath` + `coverVideo` (files copied into
app storage). Video avatars/covers are short muted loops; clients may show the first frame instead.
On upload they go through *Media upload* like any attachment.

## Moments reactions (local now; server later)

Moments stay local in v0, but the backup / sync shape is fixed:

```json
Moment { "id", "authorId", "text", "attachments": [Attachment], "createdAt", "editedAt",
         "likes": ["userId"], "dislikes": ["userId"], "comments": [Comment] }
```

`likes` (赞) and `dislikes` (踩) are sets of user ids and are **mutually exclusive per user**: adding a
user to one removes them from the other. A future endpoint would be
`PUT /v1/moments/{id}/reaction { "reaction": "like|dislike|none" }`.

## Backup file (`social.json`, export/import)

`{ "schema": 2, "app": "moonlight", "selfId", "contacts", "moments", "conversations", "messages" }`.
Schema 1 files import fine: missing `dislikes` = `[]`, missing cover/video flags = none, and missing
`readAt` = "already read" (so upgrading never floods the unread badge).

## Out of scope for v0

End-to-end encryption, server-side 朋友圈 (moments stay local), push notifications (FCM token
registration would be `POST /v1/devices`), and moderation tooling. Virtual friends, their auto-replies,
and back-dated messages are **never** uploaded.
