# Room ships consumer rules. Preserve generic metadata used across the app.
-keepattributes Signature,InnerClasses,EnclosingMethod

# The upstream MediaPipe AARs do not ship consumer rules for their JNI callbacks.
# Native code resolves PacketListCallback.process, Packet.create/getNativeHandle,
# ProtoUtil.SerializedMessage fields and other SDK entry points by their names.
# Keep the SDK's public/interoperability contract in the shipped optimized APK;
# this also lets instrumentation exercise the same native SDK API as production.
# Large model DATA remains optional and is unaffected by these bytecode rules.
-keep class com.google.mediapipe.** { *; }

# JNI uses the stable class/method names declared by this bridge.
-keep class br.com.nexus.assistant.data.local.NexusNativeBridge { *; }

# Tesseract4Android JNI resolves these wrapper classes by their original names.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
