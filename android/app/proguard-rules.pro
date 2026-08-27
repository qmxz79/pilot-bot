# AMap 3DMap, Navi, Location, Search SDK ProGuard Keep Rules
-dontwarn com.amap.**
-dontwarn com.autonavi.**
-dontwarn com.loc.**
-keep class com.amap.** { *; }
-keep class com.autonavi.** { *; }
-keep class com.loc.** { *; }

# PilotBot Models, Reflection, Config and JS Interface
-keepclassmembers class com.qmxz.pilotbot.map.GoogleMapEngine$* {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.qmxz.pilotbot.memory.** { *; }
-keep class com.qmxz.pilotbot.search.** { *; }
-keep class com.qmxz.pilotbot.map.** { *; }
-keep class com.qmxz.pilotbot.llm.** { *; }
-keep class com.qmxz.pilotbot.voice.** { *; }
-keep class com.qmxz.pilotbot.config.** { *; }
-keep class com.qmxz.pilotbot.tts.** { *; }
-keep class com.qmxz.pilotbot.asr.** { *; }
-keep class com.qmxz.pilotbot.navi.** { *; }
-keep class com.qmxz.pilotbot.enroute.** { *; }

# OkHttp & Coroutines
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature
