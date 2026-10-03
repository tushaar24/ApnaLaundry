# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ---- Razorpay Standard Checkout (UPI AutoPay) ----
-keep class com.razorpay.** { *; }
-dontwarn com.razorpay.**
-keepattributes JavascriptInterface
-keepattributes *Annotation*
-optimizations !method/inlining/*
# Keep the Activity's payment result callbacks (invoked reflectively by the SDK).
-keepclasseswithmembers class * {
    public void onPayment*(...);
}