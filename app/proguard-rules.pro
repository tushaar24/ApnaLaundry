# R8 rules for the release build (isMinifyEnabled + isShrinkResources).
# Most libraries ship their own consumer rules; the ones below cover SDKs that
# load classes reflectively or by name, plus our own wire models, so nothing a
# feature needs at runtime gets stripped or renamed.
#
# Upload app/build/outputs/mapping/release/mapping.txt with every bundle in the
# Play Console so crash traces are deobfuscated.

# ---- Crash traces: keep line numbers, hide the source file name ----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
# Generic signatures + annotations: needed by kotlinx.serialization / Ktor
# type info and by SDKs that read annotations at runtime.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,RuntimeVisibleAnnotations,AnnotationDefault

# ---- Razorpay Standard Checkout (UPI AutoPay) ----
-keep class com.razorpay.** { *; }
-dontwarn com.razorpay.**
-keepattributes JavascriptInterface
-optimizations !method/inlining/*
# The Activity's payment result callbacks are invoked reflectively by the SDK.
-keepclasseswithmembers class * {
    public void onPayment*(...);
}
# Razorpay's jar references these annotations without shipping them; without
# the dontwarn R8 stops the release build with "Missing class".
-keep class proguard.annotation.Keep
-keep class proguard.annotation.KeepClassMembers
-dontwarn proguard.annotation.**
# Its checkout WebView talks to JS through @JavascriptInterface methods.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ---- CleverTap (analytics) ----
# Loads optional modules (push, in-app, product config) by class name.
-keep class com.clevertap.android.sdk.** { *; }
-dontwarn com.clevertap.android.sdk.**
# Optional integrations CleverTap references but this app doesn't ship.
-dontwarn com.google.firebase.**
-dontwarn com.huawei.**
-dontwarn com.bumptech.glide.**
-dontwarn com.google.android.exoplayer2.**
-dontwarn com.android.installreferrer.**
-dontwarn com.xiaomi.**

# ---- Meta (Facebook) SDK app events ----
-keep class com.facebook.** { *; }
-keep interface com.facebook.** { *; }
-dontwarn com.facebook.**
# Advertising id lookup is reflective.
-keep class com.google.android.gms.ads.identifier.** { *; }
-dontwarn com.google.android.gms.**

# ---- Our own app entry points referenced by name ----
-keep class com.dailyworks.apnalaundry.ApnaLaundryApp { *; }
-keep class com.dailyworks.apnalaundry.MainActivity { *; }
# WorkManager creates the sync worker reflectively from its class name.
-keep class com.dailyworks.apnalaundry.data.sync.SyncWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ---- kotlinx.serialization (API DTOs, sync payloads, stored JSON) ----
# The library ships rules; these make our @Serializable models safe under R8
# full mode too (companion serializer() lookups and generated $serializer).
-keepclassmembers @kotlinx.serialization.Serializable class com.dailyworks.apnalaundry.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.dailyworks.apnalaundry.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.dailyworks.apnalaundry.**$$serializer { *; }
-keepclassmembers class com.dailyworks.apnalaundry.** {
    *** Companion;
}
# The wire models themselves: keep names so server field mapping and any
# error messages stay readable.
-keep @kotlinx.serialization.Serializable class com.dailyworks.apnalaundry.** { *; }
-dontnote kotlinx.serialization.**

# ---- Ktor / OkHttp: optional JVM-only deps referenced but absent on Android ----
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn io.ktor.util.debug.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# Ktor and coroutines update @Volatile fields through atomic field updaters
# (looked up by field name); renaming them breaks requests at runtime.
-keepclassmembers class io.ktor.** { volatile <fields>; }
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ---- DataStore (login tokens, setup flag, subscription cache) ----
# Preferences are stored as protobuf-lite messages whose fields are read by name.
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# ---- Room ----
# Room ships rules for the generated _Impl classes; keep entities' fields so
# column mapping never depends on R8's choices.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }

# ---- Enums (statuses / pay methods are stored and synced by name) ----
-keepclassmembers enum com.dailyworks.apnalaundry.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
