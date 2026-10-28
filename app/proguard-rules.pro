# Room ships consumer rules. Preserve generic metadata used across the app.
-keepattributes Signature,InnerClasses,EnclosingMethod

# Native code resolves these Java callbacks, entry points and fields by name.
# R8 is disabled in this alpha until optimized instrumentation is validated.
# Keep the known JNI boundary explicit for future, separately tested optimization.
-keep class com.google.mediapipe.framework.Packet { *; }
-keep interface com.google.mediapipe.framework.PacketCallback { *; }
-keep interface com.google.mediapipe.framework.PacketListCallback { *; }
-keep class com.google.mediapipe.framework.ProtoUtil$SerializedMessage { *; }
-keep class com.google.mediapipe.framework.MediaPipeException { *; }

# JNI uses the stable class/method names declared by this bridge.
-keep class br.com.nexus.assistant.data.local.NexusNativeBridge { *; }

# Tesseract4Android JNI resolves these wrapper classes by their original names.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
