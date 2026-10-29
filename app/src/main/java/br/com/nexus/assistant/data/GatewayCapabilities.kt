package br.com.nexus.assistant.data

import java.security.MessageDigest
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Only our authenticated, versioned declaration may change provider behavior. */
internal data class GatewayCapabilities(val tools: Boolean, val search: Boolean)

internal class GatewayCapabilityDiscovery(private val client: OkHttpClient) {
    private data class Entry(val capabilities: GatewayCapabilities?, val expiresAt: Long)
    private val cache = LinkedHashMap<String, Entry>(8, .75f, true)

    suspend fun discover(chatRequest: Request): GatewayCapabilities? {
        val credential = chatRequest.header("Authorization").orEmpty()
        val digest = MessageDigest.getInstance("SHA-256").digest(credential.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val url = chatRequest.url.newBuilder().encodedPath(
            chatRequest.url.encodedPath.removeSuffix("/chat/completions") + "/capabilities",
        ).build()
        val key = "$url:$digest"
        synchronized(cache) {
            cache[key]?.takeIf { it.expiresAt > System.nanoTime() }?.let { return it.capabilities }
        }
        val request = chatRequest.newBuilder().url(url).get().header("Accept", "application/json").build()
        val capabilities = readDeclaration(client.newCall(request))
        synchronized(cache) {
            cache[key] = Entry(capabilities, System.nanoTime() + 60_000_000_000L)
            while (cache.size > 8) cache.remove(cache.keys.first())
        }
        return capabilities
    }

    private suspend fun readDeclaration(call: Call): GatewayCapabilities? = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Discovery is optional for direct providers. The actual chat
                // call still reports its own failures; search requires a declaration.
                if (continuation.isActive) continuation.resume(null)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val body = response.body
                        val declaration = if (!response.isSuccessful || body == null || body.contentLength() > 65_536) null else {
                            val source = body.source()
                            if (source.request(65_537)) null else runCatching { JSONObject(source.readUtf8()) }.getOrNull()
                        }
                        val result = if (declaration?.optString("service") == "nexus-gateway") {
                            if (declaration.optInt("protocol_version") != 1 || declaration.opt("tools") !is Boolean || declaration.opt("search") !is Boolean) {
                                throw AiException("A declaração de capacidades do servidor NEXUS é incompatível. Atualize o servidor ou aplicativo.")
                            }
                            GatewayCapabilities(declaration.getBoolean("tools"), declaration.getBoolean("search"))
                        } else null
                        if (continuation.isActive) continuation.resume(result)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(
                            if (error is AiException) error else AiException("Não foi possível ler as capacidades do servidor NEXUS.", error),
                        )
                    }
                }
            }
        })
    }
}
