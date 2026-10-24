# Room and MediaPipe ship consumer rules. Keep JSON-facing model field names stable.
-keepattributes Signature,InnerClasses,EnclosingMethod

# JNI uses the stable class/method names declared by this bridge.
-keep class br.com.nexus.assistant.data.local.NexusNativeBridge { *; }

# Tesseract4Android JNI resolves these wrapper classes by their original names.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
