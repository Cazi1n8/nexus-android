# Room and MediaPipe ship consumer rules. Keep JSON-facing model field names stable.
-keepattributes Signature,InnerClasses,EnclosingMethod

# JNI uses the stable class/method names declared by this bridge.
-keep class br.com.nexus.assistant.data.local.NexusNativeBridge { *; }
