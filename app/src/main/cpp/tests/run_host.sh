#!/usr/bin/env sh
# CPU/JNI smoke test using the same bridge sources and the verified, matching
# official upstream Linux llama.cpp binary. This is not an Android device test.
set -eu
NEXUS_CPP=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
NEXUS_TEST_BUILD=${NEXUS_TEST_BUILD:-/workspace/tooling/nexus-native-harness}
NEXUS_UPSTREAM_SOURCES=${NEXUS_UPSTREAM_SOURCES:-/workspace/tooling/llama.cpp}
NEXUS_UPSTREAM_LIBS=${NEXUS_UPSTREAM_LIBS:-/workspace/tooling/ai-smoke/runtime/llama-b11838}
NEXUS_MODEL_PATH=${1:?Provide the absolute path of the verified Qwen GGUF model}
shift
: "${JAVA_HOME:?Set JAVA_HOME to a full JDK}"
mkdir -p "$NEXUS_TEST_BUILD/classes"
g++ -std=c++17 -O3 -fPIC -shared -pthread \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
  -I"$NEXUS_UPSTREAM_SOURCES/include" -I"$NEXUS_UPSTREAM_SOURCES/ggml/include" \
  -I"$NEXUS_UPSTREAM_SOURCES/vendor" \
  "$NEXUS_CPP/nexus_jni.cpp" "$NEXUS_CPP/nexus_runtime.cpp" \
  -L"$NEXUS_UPSTREAM_LIBS" -Wl,-rpath,"$NEXUS_UPSTREAM_LIBS" -lllama \
  -o "$NEXUS_TEST_BUILD/libnexus_llama.so"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -d "$NEXUS_TEST_BUILD/classes" "$NEXUS_CPP/tests/NexusNativeBridge.java"
# Upstream Linux releases load CPU variants from the executable directory or
# current directory, not LD_LIBRARY_PATH. Java's executable lives in the JDK.
# Android's build links the CPU backend statically and does not need this.
cd "$NEXUS_UPSTREAM_LIBS"
LD_LIBRARY_PATH="$NEXUS_UPSTREAM_LIBS${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" \
  "$JAVA_HOME/bin/java" -cp "$NEXUS_TEST_BUILD/classes" \
  br.com.nexus.assistant.data.local.NexusNativeBridge \
  "$NEXUS_TEST_BUILD/libnexus_llama.so" "$NEXUS_MODEL_PATH" "$@"
