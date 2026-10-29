package br.com.nexus.assistant.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class RemoteStreamingTest {
    private fun delta(text: String) = """{"choices":[{"index":0,"delta":{"content":${org.json.JSONObject.quote(text)}},"finish_reason":null}]}"""
    private val stop = """{"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}"""
    private val settings = NexusSettings(remoteConsent = true, endpoint = "https://nexus.example/v1", model = "test-model")

    @Test fun `decoder appends incremental fragments once and preserves paragraphs`() {
        val decoder = OpenAiStreamDecoder()
        val fragments = listOf("Olá", ", Calebe!\n\n", "Como posso ajudar?")
        assertEquals(fragments, fragments.map { decoder.accept(delta(it)) })
        decoder.accept(stop)
        decoder.accept("[DONE]")
        assertEquals(fragments.joinToString(""), decoder.finish())
        assertEquals("stop", decoder.finishReason)
    }

    @Test fun `decoder rejects unfinished empty private errors and invalid events`() {
        val incomplete = OpenAiStreamDecoder()
        incomplete.accept(delta("parcial"))
        expectError("antes da resposta") { incomplete.finish() }
        val empty = OpenAiStreamDecoder()
        empty.accept(stop)
        empty.accept("[DONE]")
        expectError("não devolveu") { empty.finish() }
        expectError("incompatível") { OpenAiStreamDecoder().accept("not-json") }
        val error = expectError("interrompeu") {
            OpenAiStreamDecoder().accept("""{"error":{"message":"private-upstream-key"}}""")
        }
        assertFalse(error.message.orEmpty().contains("private-upstream-key"))
    }

    @Test fun `length finish is reported rather than pretending an unlimited complete answer`() {
        val decoder = OpenAiStreamDecoder()
        decoder.accept(delta("Resposta parcial"))
        decoder.accept("""{"choices":[{"index":0,"delta":{},"finish_reason":"length"}]}""")
        decoder.accept("[DONE]")
        assertEquals("Resposta parcial", decoder.finish())
        assertEquals("length", decoder.finishReason)
    }

    @Test fun `finish reason without actual SSE completion is a connection failure`() {
        val decoder = OpenAiStreamDecoder()
        decoder.accept(delta("Texto recebido"))
        decoder.accept(stop)
        expectError("resposta completa") { decoder.finish() }
        val missingReason = OpenAiStreamDecoder()
        missingReason.accept(delta("Texto"))
        missingReason.accept("[DONE]")
        expectError("resposta completa") { missingReason.finish() }
    }

    @Test fun `tool arguments are assembled then validated before a proposal exists`() {
        val decoder = OpenAiStreamDecoder()
        decoder.accept("""{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"type":"function","function":{"name":"navigate","arguments":"{\"route\":"}}]}}]}""")
        decoder.accept("""{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"settings\"}"}}]},"finish_reason":"tool_calls"}]}""")
        decoder.accept("[DONE]")
        val completion = AiCompletion.fromText(decoder.finish())
        assertEquals("route:settings", completion.proposal?.action)
        val invalid = OpenAiStreamDecoder()
        invalid.accept("""{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"type":"function","function":{"name":"run_shell","arguments":"{}"}}]},"finish_reason":"tool_calls"}]}""")
        invalid.accept("[DONE]")
        expectError("não é permitida") { invalid.finish() }
    }

    @Test fun `SSE reader handles comments CRLF multiline and final line without newline`() {
        val body = ":heartbeat\r\nevent:message\r\ndata: first\r\ndata: second\r\n\r\ndata: [DONE]"
            .toResponseBody("text/event-stream".toMediaType())
        body.use {
            val reader = SseEventReader(it)
            assertEquals("first\nsecond", reader.nextData())
            assertEquals("[DONE]", reader.nextData())
            assertNull(reader.nextData())
        }
    }

    @Test fun `SSE reader rejects oversized lines and malformed UTF8`() {
        ("data: " + "a".repeat(66_000) + "\n\n").toResponseBody().use { body ->
            expectError("limite") { SseEventReader(body).nextData() }
        }
        byteArrayOf(0x64, 0x61, 0x74, 0x61, 0x3a, 0x20, 0xc3.toByte(), 0x28, 0x0a, 0x0a)
            .toResponseBody().use { body -> expectError("codificação") { SseEventReader(body).nextData() } }
    }

    @Test fun `search sources remain structured metadata and never answer deltas`() {
        val decoder = OpenAiStreamDecoder()
        assertEquals("", decoder.accept("""{"choices":[],"nexus_sources":[{"title":"Fonte","url":"https://source.example/item","snippet":"Trecho encontrado"}]}"""))
        assertEquals(listOf(AiSource("Fonte", "https://source.example/item", "Trecho encontrado")), decoder.sources)
        expectError("URL de fonte") {
            decoder.accept("""{"choices":[],"nexus_sources":[{"title":"Fonte","url":"javascript:alert(1)","snippet":"Trecho"}]}""")
        }
    }

    @Test fun `remote context preserves whole turns and never cuts the current request`() {
        val old = listOf(AiMessage("user", "old".repeat(4_000)), AiMessage("assistant", "answer".repeat(3_000)))
        val current = listOf(AiMessage("user", "Crie uma história"), AiMessage("assistant", "História de Max"), AiMessage("user", "Agora faça um final feliz"))
        assertEquals(current, OpenAiCompatibleProvider.selectContext(old + current))
        expectError("não foi cortado") {
            OpenAiCompatibleProvider.selectContext(listOf(AiMessage("user", "a".repeat(30_001))))
        }
        val twentyTurns = (0 until 20).flatMap { listOf(AiMessage("user", "Item $it"), AiMessage("assistant", "Resposta $it")) } + AiMessage("user", "Qual era o primeiro item?")
        assertEquals(twentyTurns, OpenAiCompatibleProvider.selectContext(twentyTurns))
    }

    @Test fun `visual followups retain actual photo turn when intermediate history must be omitted`() {
        val anchor = listOf(AiMessage("user", "O que é isso?", "/actual-photo.jpg"), AiMessage("assistant", "Descrição anterior"))
        val middle = (0 until 12).flatMap { listOf(AiMessage("user", "Pergunta $it"), AiMessage("assistant", "x".repeat(3_000))) }
        val question = AiMessage("user", "Para que serve?")
        val selected = OpenAiCompatibleProvider.selectContext(anchor + middle + question)
        assertEquals(anchor, selected.take(2))
        assertEquals(question, selected.last())
        assertTrue(selected.sumOf { it.content.length } <= OpenAiCompatibleProvider.MAX_CONTEXT_CHARACTERS)
        expectError("sem os pixels") {
            OpenAiCompatibleProvider.selectContext(listOf(AiMessage("user", "Foto", "/actual-photo.jpg"),
                AiMessage("assistant", "x".repeat(20_000)), AiMessage("user", "y".repeat(20_000))))
        }
    }

    @Test fun `search is only sent by the explicit method and cannot choose a destination`() {
        val provider = OpenAiCompatibleProvider()
        val normal = provider.buildRequest(settings, "", listOf(AiMessage("user", "Preço atual?")), emptyList())
        val searched = provider.buildRequest(settings, "", listOf(AiMessage("user", "Preço atual?")), emptyList(), searchQuery = "Preço do objeto identificado")
        fun json(request: okhttp3.Request) = org.json.JSONObject(okio.Buffer().also { request.body!!.writeTo(it) }.readUtf8())
        assertFalse(json(normal).has("nexus_search"))
        assertEquals(setOf("query"), json(searched).getJSONObject("nexus_search").keys().asSequence().toSet())
        expectError("pesquisa") { provider.buildRequest(settings, "", listOf(AiMessage("user", "Olá")), emptyList(), searchQuery = "") }
    }

    @Test fun `real HTTP SSE emits before completion and records timing`() = runBlocking {
        MockWebServer().use { server ->
            val first = "data: ${delta("Olá")}\n\n"
            val body = first + "data: ${delta(", mundo")}\n\ndata: $stop\n\ndata: [DONE]\n\n"
            streamDispatcher(server, body, first.toByteArray().size.toLong(), 200)
            val firstReceived = CompletableDeferred<Unit>()
            val fragments = mutableListOf<String>()
            val generation = async {
                OpenAiCompatibleProvider(allowInsecureLocal = true).stream(settings.copy(endpoint = server.url("/v1").toString()),
                    "test-session", listOf(AiMessage("user", "Olá")), emptyList()) { fragment ->
                    fragments += fragment
                    firstReceived.complete(Unit)
                }
            }
            try { withTimeout(5_000) { firstReceived.await() } } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                val paths = generateSequence { server.takeRequest(1, TimeUnit.MILLISECONDS)?.path }.toList()
                throw AssertionError("No SSE text; HTTP requests=${server.requestCount}; paths=$paths", timeout)
            }
            assertTrue("First delta must arrive while the response is still being transmitted", generation.isActive)
            val result = withTimeout(5_000) { generation.await() }
            assertEquals("Olá, mundo", result.text)
            assertEquals(result.text, fragments.joinToString(""))
            assertNotNull(result.firstTokenMs)
            assertTrue(result.totalMs >= result.firstTokenMs!!)
            assertEquals("/v1/capabilities", server.takeRequest(2, TimeUnit.SECONDS)!!.path)
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("text/event-stream", request.getHeader("Accept"))
            assertTrue(org.json.JSONObject(request.body.readUtf8()).getBoolean("stream"))
        }
    }

    @Test fun `cancelling while waiting for another SSE event closes the active call promptly`() = runBlocking {
        MockWebServer().use { server ->
            val first = "data: ${delta("Primeira parte")}\n\n"
            val body = first + "data: ${delta("restante")}\n\ndata: $stop\n\ndata: [DONE]\n\n"
            streamDispatcher(server, body, first.toByteArray().size.toLong(), 2_000)
            val firstReceived = CompletableDeferred<Unit>()
            val generation = async {
                OpenAiCompatibleProvider(allowInsecureLocal = true).stream(settings.copy(endpoint = server.url("/v1").toString()),
                    "", listOf(AiMessage("user", "Olá")), emptyList()) { firstReceived.complete(Unit) }
            }
            try { withTimeout(5_000) { firstReceived.await() } } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                val paths = generateSequence { server.takeRequest(1, TimeUnit.MILLISECONDS)?.path }.toList()
                throw AssertionError("No SSE text; HTTP requests=${server.requestCount}; paths=$paths", timeout)
            }
            withTimeout(1_000) { generation.cancelAndJoin() }
            assertTrue(generation.isCancelled)
        }
    }

    private fun streamDispatcher(server: MockWebServer, body: String, bytes: Long, periodMs: Long) {
        // QueueDispatcher.peek() also throttles reading the POST request body.
        // Use the default unthrottled peek; dispatch only selects the response
        // after the complete request has arrived, so only SSE delivery is slow.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/v1/capabilities" -> MockResponse().setResponseCode(404)
                "/v1/chat/completions" -> MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body)
                    .throttleBody(bytes, periodMs, TimeUnit.MILLISECONDS)
                else -> MockResponse().setResponseCode(404)
            }
        }
    }

    private fun expectError(part: String, block: () -> Any?): AiException {
        try { block(); fail("Expected an explicit AI transport error") } catch (error: AiException) {
            assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(part))
            return error
        }
        error("unreachable")
    }
}
