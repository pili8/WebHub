# WebView 相关
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keepattributes JavascriptInterface
-keepattributes *Annotation*

# 金山文档相关
-keep class com.kingsoft.** { *; }
-keep class com.wps.** { *; }
