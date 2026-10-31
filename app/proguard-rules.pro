-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.moonlight.feed.BiliPlayerActivity$MoonBridge { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
