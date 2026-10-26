#include <jni.h>

#include "nexus_runtime.hpp"

#include <cstdint>
#include <exception>
#include <new>
#include <string>

namespace {

void throw_java(JNIEnv * env, const char * type, const char * message) {
    if (env->ExceptionCheck()) return;
    auto exception = env->FindClass(type);
    if (exception != nullptr) {
        env->ThrowNew(exception, message);
        env->DeleteLocalRef(exception);
    }
}

std::string read_bytes(JNIEnv * env, jbyteArray bytes, std::size_t maximum) {
    if (bytes == nullptr) throw std::invalid_argument("Uma entrada obrigatória da IA local está ausente.");
    const auto length = env->GetArrayLength(bytes);
    if (length <= 0 || static_cast<std::size_t>(length) > maximum) throw std::invalid_argument("A entrada da IA local ultrapassa o limite permitido.");
    std::string result(static_cast<std::size_t>(length), '\0');
    env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte *>(result.data()));
    return result;
}

void translate_exception(JNIEnv * env) {
    try { throw;
    } catch (const nexus::Cancelled & exception) {
        throw_java(env, "java/util/concurrent/CancellationException", exception.what());
    } catch (const std::bad_alloc &) {
        throw_java(env, "java/lang/OutOfMemoryError", "Memória insuficiente para a IA local. Feche outros aplicativos.");
    } catch (const std::invalid_argument & exception) {
        throw_java(env, "java/lang/IllegalArgumentException", exception.what());
    } catch (const std::runtime_error & exception) {
        throw_java(env, "java/lang/IllegalStateException", exception.what());
    } catch (...) {
        throw_java(env, "java/lang/IllegalStateException", "Ocorreu uma falha na IA local. Tente iniciar a sessão novamente.");
    }
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_br_com_nexus_assistant_data_local_NexusNativeBridge_nativeLoad(
    JNIEnv * env, jobject, jbyteArray path, jint context_tokens, jint threads) {
    try {
        auto decoded_path = read_bytes(env, path, 16 * 1024);
        if (env->ExceptionCheck()) return 0;
        return static_cast<jlong>(nexus::load(decoded_path, context_tokens, threads));
    } catch (...) { translate_exception(env); return 0; }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_br_com_nexus_assistant_data_local_NexusNativeBridge_nativeGenerate(
    JNIEnv * env, jobject, jlong handle, jbyteArray chat_json, jint maximum_tokens) {
    try {
        auto payload = read_bytes(env, chat_json, 1024 * 1024);
        if (env->ExceptionCheck()) return nullptr;
        auto answer = nexus::generate(static_cast<std::uint64_t>(handle), payload, maximum_tokens);
        auto bytes = env->NewByteArray(static_cast<jsize>(answer.size()));
        if (bytes != nullptr) env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(answer.size()), reinterpret_cast<const jbyte *>(answer.data()));
        return bytes;
    } catch (...) { translate_exception(env); return nullptr; }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_br_com_nexus_assistant_data_local_NexusNativeBridge_nativeGenerateResult(
    JNIEnv * env, jobject, jlong handle, jbyteArray chat_json, jint maximum_tokens) {
    try {
        auto payload = read_bytes(env, chat_json, 1024 * 1024);
        if (env->ExceptionCheck()) return nullptr;
        auto result = nexus::generate_result(static_cast<std::uint64_t>(handle), payload, maximum_tokens);
        auto bytes = env->NewByteArray(static_cast<jsize>(result.size()));
        if (bytes != nullptr) env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(result.size()), reinterpret_cast<const jbyte *>(result.data()));
        return bytes;
    } catch (...) { translate_exception(env); return nullptr; }
}

extern "C" JNIEXPORT void JNICALL
Java_br_com_nexus_assistant_data_local_NexusNativeBridge_nativeCancel(JNIEnv *, jobject, jlong handle) {
    nexus::cancel(static_cast<std::uint64_t>(handle));
}

extern "C" JNIEXPORT void JNICALL
Java_br_com_nexus_assistant_data_local_NexusNativeBridge_nativeClose(JNIEnv *, jobject, jlong handle) {
    nexus::close(static_cast<std::uint64_t>(handle));
}
