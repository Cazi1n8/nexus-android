package br.com.nexus.assistant.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NexusOnlineProviderTest {
    private class MemoryStore(var value: StoredOnlineSession?) : OnlineSessionStorage {
        override fun read() = value
        override fun write(value: StoredOnlineSession?) { this.value = value }
    }

    private val credential = "individual-session-access-only-a-test"
    private val declaration = """{"service":"nexus-gateway","protocol_version":1,"tools":false,"search":false}"""

    @Test fun `online consent is required before even sending authentication traffic`() = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/v1").toString()
            val store = MemoryStore(StoredOnlineSession(base, credential, "individual-refresh-only-for-a-test", 9_999))
            val manager = NexusOnlineSessionManager(base, store, allowInsecureLocal = true, nowEpochSeconds = { 1_000 })
            val provider = NexusOnlineProvider(manager, OpenAiCompatibleProvider(allowInsecureLocal = true))
            try { provider.complete(NexusSettings(onlineConsent = false), "advanced-private-token", listOf(AiMessage("user", "Olá")), emptyList()); fail() }
            catch (error: AiException) { assertTrue(error.message!!.contains("Autorize")) }
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun `online requests use the packaged deployment and individual token instead of advanced settings`() = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/v1").toString()
            val store = MemoryStore(StoredOnlineSession(base, credential, "individual-refresh-only-for-a-test", 9_999))
            val manager = NexusOnlineSessionManager(base, store, allowInsecureLocal = true, nowEpochSeconds = { 1_000 })
            val provider = NexusOnlineProvider(manager, OpenAiCompatibleProvider(allowInsecureLocal = true))
            server.enqueue(MockResponse().setBody(declaration))
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"choices":[{"message":{"content":"Saída controlada do transporte"}}]}"""))
            val settings = NexusSettings(onlineConsent = true, endpoint = "https://unrelated.example/v1", model = "advanced-model", remoteConsent = false)
            assertEquals("Saída controlada do transporte", provider.complete(settings, "advanced-private-token", listOf(AiMessage("user", "Escreva um texto")), emptyList()))
            server.takeRequest(2, TimeUnit.SECONDS)
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/v1/chat/completions", request.path)
            assertEquals("Bearer $credential", request.getHeader("Authorization"))
            assertEquals("nexus-ai", JSONObject(request.body.readUtf8()).getString("model"))
        }
    }

    @Test fun `online preserves real SSE deltas and does not retry a rejected generation`() = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/v1").toString()
            val store = MemoryStore(StoredOnlineSession(base, credential, "individual-refresh-only-for-a-test", 9_999))
            val manager = NexusOnlineSessionManager(base, store, allowInsecureLocal = true, nowEpochSeconds = { 1_000 })
            val provider = NexusOnlineProvider(manager, OpenAiCompatibleProvider(allowInsecureLocal = true))
            server.enqueue(MockResponse().setBody(declaration))
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                "data: {\"choices\":[{\"delta\":{\"content\":\"Olá, \"}}]}\n\n" +
                "data: {\"choices\":[{\"delta\":{\"content\":\"ação\"},\"finish_reason\":\"stop\"}]}\n\n" +
                "data: [DONE]\n\n"))
            val deltas = mutableListOf<String>()
            val result = provider.stream(NexusSettings(onlineConsent = true), "ignored-custom-token", listOf(AiMessage("user", "Olá")), emptyList(), deltas::add)
            assertEquals(listOf("Olá, ", "ação"), deltas)
            assertEquals("Olá, ação", result.text)
            server.enqueue(MockResponse().setResponseCode(401))
            try { provider.complete(NexusSettings(onlineConsent = true), "ignored", listOf(AiMessage("user", "Continua")), emptyList()); fail() } catch (_: AiException) { }
            assertEquals(3, server.requestCount) // capability, stream, one rejected generation; no replay.
            assertNull(store.value)
        }
    }

    @Test fun `rejected account clears cached credentials for each request path without replaying inference`() = runBlocking {
        for (status in listOf(401, 403)) for (path in listOf("complete", "stream", "search")) {
            MockWebServer().use { server ->
                val base = server.url("/v1").toString()
                val store = MemoryStore(StoredOnlineSession(base, credential, "individual-refresh-only-for-a-test", 9_999))
                val manager = NexusOnlineSessionManager(base, store, allowInsecureLocal = true, nowEpochSeconds = { 1_000 })
                val provider = NexusOnlineProvider(manager, OpenAiCompatibleProvider(allowInsecureLocal = true))
                server.enqueue(MockResponse().setBody(declaration.replace("\"search\":false", "\"search\":true")))
                server.enqueue(MockResponse().setResponseCode(status))
                val settings = NexusSettings(onlineConsent = true)
                val messages = listOf(AiMessage("user", "Mensagem para testar somente o transporte"))
                try {
                    when (path) {
                        "complete" -> provider.complete(settings, "ignored", messages, emptyList())
                        "stream" -> provider.stream(settings, "ignored", messages, emptyList()) { fail("No generated delta expected") }
                        else -> provider.streamWithSearch(settings, messages, emptyList(), "consulta") { fail("No generated delta expected") }
                    }
                    fail("The rejected account must not generate a successful response")
                } catch (error: AiException) {
                    assertTrue(error.message!!.contains("revogada"))
                }
                assertNull(store.value)
                assertFalse(manager.hasSession())
                assertEquals(2, server.requestCount)
                assertEquals("GET", server.takeRequest(2, TimeUnit.SECONDS)!!.method)
                val onlyPost = server.takeRequest(2, TimeUnit.SECONDS)!!
                assertEquals("POST", onlyPost.method)
                assertEquals("/v1/chat/completions", onlyPost.path)
            }
        }
    }

    @Test fun `provider unavailable status preserves the individual session without replaying inference`() = runBlocking {
        for (path in listOf("complete", "stream", "search")) {
            MockWebServer().use { server ->
                val base = server.url("/v1").toString()
                val existing = StoredOnlineSession(base, credential, "individual-refresh-only-for-a-test", 9_999)
                val store = MemoryStore(existing)
                val manager = NexusOnlineSessionManager(base, store, allowInsecureLocal = true, nowEpochSeconds = { 1_000 })
                val provider = NexusOnlineProvider(manager, OpenAiCompatibleProvider(allowInsecureLocal = true))
                server.enqueue(MockResponse().setBody(declaration.replace("\"search\":false", "\"search\":true")))
                server.enqueue(MockResponse().setResponseCode(502))
                val settings = NexusSettings(onlineConsent = true)
                val messages = listOf(AiMessage("user", "Mensagem para testar somente o transporte"))
                try {
                    when (path) {
                        "complete" -> provider.complete(settings, "ignored", messages, emptyList())
                        "stream" -> provider.stream(settings, "ignored", messages, emptyList()) { fail("No generated delta expected") }
                        else -> provider.streamWithSearch(settings, messages, emptyList(), "consulta") { fail("No generated delta expected") }
                    }
                    fail()
                } catch (error: AiHttpException) { assertEquals(502, error.statusCode) }
                assertEquals(existing, store.value)
                assertTrue(manager.hasSession())
                assertEquals(2, server.requestCount)
                assertEquals("GET", server.takeRequest(2, TimeUnit.SECONDS)!!.method)
                assertEquals("POST", server.takeRequest(2, TimeUnit.SECONDS)!!.method)
            }
        }
    }
}
