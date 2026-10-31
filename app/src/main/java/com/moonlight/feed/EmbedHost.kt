package com.moonlight.feed

import com.moonlight.feed.data.PlatformId
import com.moonlight.feed.data.PlatformResolver

/**
 * Official embedder identity for WebView players.
 *
 * YouTube Required Minimum Functionality → API Client Identity and Credentials:
 * https://developers.google.com/youtube/terms/required-minimum-functionality
 * Error 153 = missing HTTP Referer / embedder identity (IFrame API revision history, 2025-07-09).
 * Android: loadDataWithBaseURL(baseUrl=https://<packageName>) or loadUrl(..., Referer header).
 */
object EmbedHost {
    /**
     * Pause everything audible in the page: our YouTube IFrame-API player, plain <video>/<audio>,
     * and nested iframes via the postMessage pause commands YouTube / Vimeo / Dailymotion / Twitch understand.
     * Media we paused are tagged so [RESUME_JS] restarts only those.
     */
    const val PAUSE_JS = "(function(){" +
        "try{if(window.player&&window.player.getPlayerState){var s=window.player.getPlayerState();window.__moonYt=(s==1||s==3);window.player.pauseVideo();}}catch(e){}" +
        "document.querySelectorAll('video,audio').forEach(function(m){try{if(!m.paused){m.dataset.moonPaused='1';}m.pause();}catch(e){}});" +
        "document.querySelectorAll('iframe').forEach(function(f){try{var w=f.contentWindow;" +
        "w.postMessage('{\"event\":\"command\",\"func\":\"pauseVideo\",\"args\":\"\"}','*');" +
        "w.postMessage('{\"method\":\"pause\"}','*');w.postMessage('pause','*');" +
        "w.postMessage({eventName:'pause',params:{}},'*');}catch(e){}});})();"

    const val RESUME_JS = "(function(){" +
        "try{if(window.player&&window.player.playVideo&&window.__moonYt){window.player.playVideo();}}catch(e){}" +
        "document.querySelectorAll('video,audio').forEach(function(m){try{if(m.dataset.moonPaused){delete m.dataset.moonPaused;m.play();}}catch(e){}});" +
        "document.querySelectorAll('iframe').forEach(function(f){try{var w=f.contentWindow;" +
        "w.postMessage('{\"event\":\"command\",\"func\":\"playVideo\",\"args\":\"\"}','*');" +
        "w.postMessage('{\"method\":\"play\"}','*');w.postMessage('play','*');}catch(e){}});})();"

    /** Was the embed audibly playing? [apiState] is the YouTube IFrame-API state (-1 = no API bridge on this page → unknown, assume yes). */
    fun embedWasPlaying(attached: Boolean, apiState: Int): Boolean = attached && (apiState < 0 || apiState == 1 || apiState == 3)

    /**
     * Cross-origin embeds without an API bridge (B站, Vimeo, Twitch, generic pages) may ignore the script pause,
     * so when the app is fully hidden we unload them to guarantee silence, and reload on return.
     */
    fun needsHardStop(wasPlaying: Boolean, apiState: Int): Boolean = wasPlaying && apiState < 0

    /** App ID as HTTPS origin — matches YouTube docs' Android example. */
    const val BASE = "https://com.moonlight.feed"
    const val ORIGIN = "https://com.moonlight.feed"
    const val HOST = "com.moonlight.feed"

    fun refererHeaders(): Map<String, String> = mapOf(
        "Referer" to "$BASE/",
    )

    fun youtubeHtml(videoId: String, mute: Boolean): String {
        val muteJs = if (mute) "true" else "false"
        // IFrame Player API with origin + widget_referrer; meta referrer policy per docs.
        return """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"/>
<meta name="referrer" content="strict-origin-when-cross-origin"/>
<style>
  html,body{margin:0;padding:0;background:#000;height:100%;overflow:hidden}
  #player{position:fixed;inset:0;width:100%;height:100%}
  #err{display:none;position:fixed;inset:0;z-index:5;background:rgba(0,0,0,.88);
    color:#fff;font-family:sans-serif;align-items:center;justify-content:center;
    flex-direction:column;text-align:center;padding:24px;box-sizing:border-box}
  #err.show{display:flex}
  #err h1{font-size:18px;font-weight:600;margin:0 0 8px}
  #err p{font-size:13px;opacity:.7;margin:0 0 20px}
  #err button{border:0;border-radius:22px;padding:12px 22px;font-size:14px;font-weight:600;margin:6px}
  #open{background:#fff;color:#000}
  #skip{background:#333;color:#fff}
</style>
</head>
<body>
<div id="player"></div>
<div id="err">
  <h1 id="errTitle">播放出错</h1>
  <p id="errDetail"></p>
  <button id="open" type="button">在 YouTube 中打开</button>
  <button id="skip" type="button">下一条</button>
</div>
<script>
  var VIDEO_ID = ${jsonStr(videoId)};
  var ORIGIN = ${jsonStr(ORIGIN)};
  var MUTE = $muteJs;
  var player;
  function showErr(code, msg) {
    document.getElementById('errTitle').textContent = msg || ('错误 ' + code);
    document.getElementById('errDetail').textContent = '错误 ' + code;
    document.getElementById('err').classList.add('show');
    try { MoonBridge && MoonBridge.onPlayerError(String(code), VIDEO_ID); } catch(e) {}
  }
  document.getElementById('open').onclick = function() {
    try { MoonBridge && MoonBridge.openYoutube(VIDEO_ID); } catch(e) {}
  };
  document.getElementById('skip').onclick = function() {
    try { MoonBridge && MoonBridge.skipNext(); } catch(e) {}
  };
  var tag = document.createElement('script');
  tag.src = 'https://www.youtube.com/iframe_api';
  document.head.appendChild(tag);
  function onYouTubeIframeAPIReady() {
    player = new YT.Player('player', {
      width: '100%',
      height: '100%',
      videoId: VIDEO_ID,
      host: 'https://www.youtube.com',
      playerVars: {
        autoplay: 1,
        mute: MUTE ? 1 : 0,
        playsinline: 1,
        rel: 0,
        modestbranding: 1,
        controls: 1,
        fs: 1,
        origin: ORIGIN,
        widget_referrer: ORIGIN,
        enablejsapi: 1
      },
      events: {
        onReady: function(ev) {
          try {
            if (MUTE) { ev.target.mute(); } else { ev.target.unMute(); }
            ev.target.playVideo();
          } catch(e) {}
          try { MoonBridge && MoonBridge.onPlayerReady(VIDEO_ID); } catch(e) {}
        },
        onStateChange: function(ev) {
          try { MoonBridge && MoonBridge.onPlayerState(String(ev.data)); } catch(e) {}
        },
        onError: function(ev) {
          var c = ev.data;
          var map = {
            2: '无效的视频参数',
            5: 'HTML5 播放器错误',
            100: '视频不存在或已删除',
            101: '嵌入已被视频所有者禁用',
            150: '嵌入已被视频所有者禁用',
            153: '视频播放器配置错误'
          };
          showErr(c, map[c] || ('播放错误 ' + c));
        }
      }
    });
  }
  // expose for older YT loader
  window.onYouTubeIframeAPIReady = onYouTubeIframeAPIReady;
</script>
</body>
</html>
        """.trimIndent()
    }

    /** Simple iframe shell with referrer meta — Vimeo / DM / PeerTube / Twitch / generic. */
    fun iframeHtml(embedSrc: String, title: String = "embed"): String {
        val src = embedSrc
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
        return """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"/>
<meta name="referrer" content="strict-origin-when-cross-origin"/>
<style>
html,body{margin:0;padding:0;background:#000;height:100%;overflow:hidden}
iframe{position:fixed;inset:0;width:100%;height:100%;border:0}
</style>
</head>
<body>
<iframe
  src="$src"
  title="$title"
  allow="autoplay; fullscreen; encrypted-media; picture-in-picture"
  allowfullscreen
  referrerpolicy="strict-origin-when-cross-origin"
></iframe>
</body>
</html>
        """.trimIndent()
    }

    fun twitchEmbedUrl(slug: String, mute: Boolean = true): String {
        val m = if (mute) "true" else "false"
        // parent must match the base host used in loadDataWithBaseURL
        return "https://clips.twitch.tv/embed?clip=$slug&parent=$HOST&parent=www.$HOST&autoplay=true&muted=$m"
    }

    fun youtubeWatchUrl(id: String) = "https://www.youtube.com/watch?v=$id"

    fun youtubeEmbedUrl(id: String, mute: Boolean): String {
        val m = if (mute) "1" else "0"
        return "https://www.youtube.com/embed/$id?autoplay=1&playsinline=1&mute=$m&rel=0&modestbranding=1&enablejsapi=1&origin=${ORIGIN}"
    }

    private fun jsonStr(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
