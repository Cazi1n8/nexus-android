package br.com.nexus.assistant.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GatewayNegotiationTest {
    private val declaration = """{"service":"nexus-gateway","protocol_version":1,"tools":false,"search":true}"""
    private val answer = """{"choices":[{"message":{"content":"Resposta do transporte controlado"}}]}"""
    private fun settings(server: MockWebServer) = NexusSettings(endpoint = server.url("/custom/v1").toString(), model = "test-model", remoteConsent = true)

    @Test fun `real app request negotiates gateway before omitting action schemas and keeps conversation history`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(declaration))
            server.enqueue(MockResponse().setBody(answer))
            server.enqueue(MockResponse().setBody(answer))
            val provider = OpenAiCompatibleProvider(allowInsecureLocal = true)
            val messages = listOf(AiMessage("user", "Crie uma história"), AiMessage("assistant", "Era um robô"), AiMessage("user", "Agora deixe o final feliz"))
            provider.complete(settings(server), "test-session", messages, emptyList())
            val capability = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/custom/v1/capabilities", capability.path)
            assertEquals("GET", capability.method)
            assertEquals("Bearer test-session", capability.getHeader("Authorization"))
            val chat = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/custom/v1/chat/completions", chat.path)
            val body = JSONObject(chat.body.readUtf8())
            assertFalse(body.has("tools"))
            assertFalse(body.has("tool_choice"))
            assertEquals("Agora deixe o final feliz", body.getJSONArray("messages").getJSONObject(3).getString("content"))
            provider.complete(settings(server), "test-session", listOf(AiMessage("user", "Continua")), emptyList())
            assertEquals("/custom/v1/chat/completions", server.takeRequest(2, TimeUnit.SECONDS)!!.path)
            assertEquals(3, server.requestCount) // Authenticated capability cache.
        }
    }

    @Test fun `direct providers keep tools and a generic tools false declaration cannot disable them`() = runBlocking {
        for (capability in listOf(MockResponse().setResponseCode(404), MockResponse().setBody("""{"tools":false,"search":true}"""))) {
            MockWebServer().use { server ->
                server.enqueue(capability)
                server.enqueue(MockResponse().setBody(answer))
                OpenAiCompatibleProvider(allowInsecureLocal = true).complete(settings(server), "test-session", listOf(AiMessage("user", "Abra configurações")), emptyList())
                server.takeRequest(2, TimeUnit.SECONDS)
                val body = JSONObject(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8())
                assertEquals(3, body.getJSONArray("tools").length())
                assertEquals("auto", body.getString("tool_choice"))
            }
        }
    }

    @Test fun `search requires an actual gateway capability and never silently degrades to ordinary chat`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404))
            try {
                OpenAiCompatibleProvider(allowInsecureLocal = true).completeWithSearch(settings(server), "test-session", listOf(AiMessage("user", "Preço atual?")), emptyList(), "preço objeto")
                fail("Search must not be sent to a provider that would ignore nexus_search")
            } catch (error: AiException) {
                assertTrue(error.message.orEmpty().contains("pesquisa"))
            }
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun `stream timing includes capability discovery latency`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(declaration).setBodyDelay(150, TimeUnit.MILLISECONDS))
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Olá\"},\"finish_reason\":null}]}\n\n" +
                    "data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"))
            val result = OpenAiCompatibleProvider(allowInsecureLocal = true).stream(settings(server), "test-session",
                listOf(AiMessage("user", "Olá")), emptyList()) { }
            assertTrue("TTFT must include gateway discovery, not only the final POST", result.firstTokenMs!! >= 100)
            assertTrue(result.totalMs >= result.firstTokenMs!!)
        }
    }

    @Test fun `explicit gateway search sends query after capability and no unsupported schemas`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(declaration))
            server.enqueue(MockResponse().setBody(answer))
            OpenAiCompatibleProvider(allowInsecureLocal = true).completeWithSearch(settings(server), "test-session", listOf(AiMessage("user", "Preço atual?")), emptyList(), "preço objeto")
            server.takeRequest(2, TimeUnit.SECONDS)
            val body = JSONObject(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8())
            assertEquals("preço objeto", body.getJSONObject("nexus_search").getString("query"))
            assertFalse(body.has("tools"))
        }
    }
}
