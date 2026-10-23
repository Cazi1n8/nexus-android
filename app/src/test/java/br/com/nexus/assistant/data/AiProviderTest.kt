package br.com.nexus.assistant.data

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AiProviderTest {
    private val settings = NexusSettings(endpoint = "https://nexus.example/v1", model = "modelo-local", remoteConsent = true)

    @Test fun `request sends context and session credential only as header`() {
        val request = OpenAiCompatibleProvider(allowInsecureLocal = false).buildRequest(
            settings.copy(userName = "Ana", personality = "Professor"), "session-for-test",
            listOf(AiMessage("user", "Explique uma fração")), listOf("Prefiro exemplos curtos"),
        )
        val buffer = Buffer().also { request.body!!.writeTo(it) }
        val raw = buffer.readUtf8()
        val body = JSONObject(raw)
        assertEquals("https://nexus.example/v1/chat/completions", request.url.toString())
        assertEquals("Bearer session-for-test", request.header("Authorization"))
        assertEquals("modelo-local", body.getString("model"))
        assertFalse(body.getBoolean("stream"))
        assertEquals("Explique uma fração", body.getJSONArray("messages").getJSONObject(1).getString("content"))
        assertTrue(body.getJSONArray("messages").getJSONObject(0).getString("content").contains("Prefiro exemplos curtos"))
        assertFalse(raw.contains("session-for-test"))
    }

    @Test fun `request requires consent and configured model`() {
        expectAiError("Autorize") { OpenAiCompatibleProvider().buildRequest(settings.copy(remoteConsent = false), "", listOf(AiMessage("user", "Olá")), emptyList()) }
        expectAiError("modelo") { OpenAiCompatibleProvider().buildRequest(settings.copy(model = ""), "", listOf(AiMessage("user", "Olá")), emptyList()) }
    }

    @Test fun `release rejects cleartext and URL embedded credentials`() {
        listOf("http://localhost:8080", "http://10.0.2.2:8080", "http://example.org", "https://user:secret@example.org", "https://example.org/?key=secret", "https://example.org/#key").forEach { endpoint ->
            expectAiError { OpenAiCompatibleProvider.endpoint(endpoint, false) }
        }
        assertEquals("http://10.0.2.2:8080/v1/chat/completions", OpenAiCompatibleProvider.endpoint("http://10.0.2.2:8080", true).toString())
        expectAiError { OpenAiCompatibleProvider.endpoint("http://192.168.1.20:8080", true) }
        assertEquals("https://nexus.example/v1/chat/completions", OpenAiCompatibleProvider.endpoint("https://nexus.example/v1/chat/completions/", false).toString())
    }

    @Test fun `invalid session header is rejected without exposing credential`() {
        val credential = "private-session-\u0000-do-not-display"
        try {
            OpenAiCompatibleProvider().buildRequest(settings, credential, listOf(AiMessage("user", "Olá")), emptyList())
            fail("Esperava rejeição do token")
        } catch (error: AiException) {
            assertTrue(error.message!!.contains("Token de sessão inválido"))
            assertFalse(error.message!!.contains("private-session"))
        }
    }

    @Test fun `context keeps newest messages and limits text budget`() {
        val messages = (0 until 80).map { AiMessage(if (it % 2 == 0) "user" else "assistant", "$it:" + "a".repeat(2_000)) }
        val selected = OpenAiCompatibleProvider.selectContext(messages)
        assertTrue(selected.size <= 24)
        assertTrue(selected.sumOf { it.content.length } <= 30_000)
        assertEquals(messages.last(), selected.last())
        assertEquals("user", selected.first().role)
        assertFalse(selected.any { it.content.startsWith("0:") })
    }

    @Test fun `images are attached as actual data URLs and only two are sent`() {
        val file = File.createTempFile("nexus-image", ".jpg")
        try {
            file.writeBytes(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 1, 2, 3))
            val request = OpenAiCompatibleProvider().buildRequest(
                settings, "", (0..2).map { AiMessage("user", "imagem $it", file.absolutePath) }, emptyList(),
            )
            val raw = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
            val messages = JSONObject(raw).getJSONArray("messages")
            assertTrue(messages.getJSONObject(1).getString("content").contains("omitida"))
            assertTrue(messages.getJSONObject(3).getJSONArray("content").getJSONObject(1).getJSONObject("image_url").getString("url").startsWith("data:image/jpeg;base64,"))
            assertEquals(2, Regex("data:image/jpeg;base64,").findAll(raw).count())
        } finally { file.delete() }
    }

    @Test fun `unavailable image is reported instead of simulated analysis`() {
        expectAiError("não está mais disponível") { OpenAiCompatibleProvider.imageDataUrl("/nexus-missing-test-file.jpg") }
    }

    @Test fun `parser supports text and multimodal response formats`() {
        assertEquals("Olá", OpenAiCompatibleProvider.parseAnswer("""{"choices":[{"message":{"content":"Olá"}}]}"""))
        assertEquals("Texto observado", OpenAiCompatibleProvider.parseAnswer("""{"choices":[{"message":{"content":[{"type":"text","text":"Texto observado"}]}}]}"""))
        expectAiError("compatível") { OpenAiCompatibleProvider.parseAnswer("{\"unknown\":true}") }
        expectAiError("não devolveu texto") { OpenAiCompatibleProvider.parseAnswer("""{"choices":[{"message":{"content":""}}]}""") }
    }

    @Test fun `parser never invents text from null numeric or tool only responses`() {
        listOf(
            """{"choices":[{"message":{"content":null,"refusal":null}}]}""",
            """{"choices":[{"message":{"content":null,"refusal":123}}]}""",
            """{"choices":[{"message":{"content":[{"type":"text","text":null}]}}]}""",
            """{"choices":[{"message":{"content":[{"type":"text","text":123}]}}]}""",
            """{"choices":[{"message":{"tool_calls":[]}}]}""",
        ).forEach { raw -> expectAiError("não devolveu texto") { OpenAiCompatibleProvider.parseAnswer(raw) } }
        assertEquals("Não posso ajudar com isso.", OpenAiCompatibleProvider.parseAnswer(
            """{"choices":[{"message":{"content":null,"refusal":"Não posso ajudar com isso."}}]}""",
        ))
    }

    @Test fun `transport talks to compatible server and includes real message`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"Resposta recebida do servidor"}}]}"""))
            val result = OpenAiCompatibleProvider(allowInsecureLocal = true).complete(
                settings.copy(endpoint = server.url("/v1").toString()), "test-session", listOf(AiMessage("user", "Como está?")), emptyList(),
            )
            assertEquals("Resposta recebida do servidor", result)
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/v1/chat/completions", request.path)
            assertEquals("Bearer test-session", request.getHeader("Authorization"))
            assertTrue(request.body.readUtf8().contains("Como está?"))
        }
    }

    @Test fun `transport errors do not disclose upstream error or credentials`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401).setBody("private-provider-key-do-not-display"))
            try {
                OpenAiCompatibleProvider(allowInsecureLocal = true).complete(
                    settings.copy(endpoint = server.url("/v1").toString()), "test-session", listOf(AiMessage("user", "Olá")), emptyList(),
                )
                fail("Esperava erro de autenticação")
            } catch (error: AiException) {
                assertTrue(error.message!!.contains("não foi autorizada"))
                assertFalse(error.message!!.contains("private-provider-key"))
                assertFalse(error.message!!.contains("test-session"))
            }
        }
    }

    @Test fun `cancelled coroutine cancels network request promptly`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"atrasada"}}]}""").setHeadersDelay(2, TimeUnit.SECONDS))
            val call = async {
                OpenAiCompatibleProvider(allowInsecureLocal = true).complete(
                    settings.copy(endpoint = server.url("/v1").toString()), "", listOf(AiMessage("user", "Olá")), emptyList(),
                )
            }
            withTimeout(5_000) {
                while (server.requestCount == 0) delay(10)
                call.cancel()
                call.join()
            }
            assertTrue(call.isCancelled)
        }
    }

    private fun expectAiError(part: String? = null, block: () -> Any?) {
        try {
            block()
            fail("Esperava AiException")
        } catch (error: AiException) {
            if (part != null) assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(part))
        }
    }
}
