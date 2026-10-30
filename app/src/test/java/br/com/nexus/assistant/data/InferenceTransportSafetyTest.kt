package br.com.nexus.assistant.data

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test

class InferenceTransportSafetyTest {
    private val history = listOf(AiMessage("user", "Escreva uma explicação curta"))
    private val declaration = """{"service":"nexus-gateway","protocol_version":1,"tools":false,"search":false}"""

    @Test fun connectionLostAfterRequestDoesNotReplayTheInferenceEvenWithInjectedRetryClient() = runBlocking {
        MockWebServer().use { server ->
            val inferenceRequests = AtomicInteger()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                    "/v1/capabilities" -> MockResponse().setHeader("Content-Type", "application/json").setBody(declaration)
                    "/v1/chat/completions" -> if (inferenceRequests.incrementAndGet() == 1) {
                        MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                    } else {
                        MockResponse().setHeader("Content-Type", "application/json")
                            .setBody("""{"choices":[{"message":{"content":"Unexpected replay fixture"}}]}""")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
            val provider = OpenAiCompatibleProvider(OkHttpClient.Builder().retryOnConnectionFailure(true).build(), allowInsecureLocal = true)
            try {
                provider.complete(NexusSettings(remoteConsent = true, endpoint = server.url("/v1").toString(), model = "fixture"), "fixture-token", history, emptyList())
                fail("The failed inference must require a new explicit user attempt")
            } catch (_: AiException) { }
            assertEquals(1, inferenceRequests.get())
            assertEquals(2, server.requestCount) // One discovery GET and exactly one generation POST.
        }
    }

    @Test fun injectedRedirectClientCannotForwardTheSessionToAnotherOrigin() = runBlocking {
        MockWebServer().use { destination ->
            MockWebServer().use { server ->
                server.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                        "/v1/capabilities" -> MockResponse().setHeader("Content-Type", "application/json").setBody(declaration)
                        else -> MockResponse().setResponseCode(307).setHeader("Location", destination.url("/capture"))
                    }
                }
                val provider = OpenAiCompatibleProvider(OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build(), allowInsecureLocal = true)
                try {
                    provider.complete(NexusSettings(remoteConsent = true, endpoint = server.url("/v1").toString(), model = "fixture"), "fixture-token", history, emptyList())
                    fail("A redirected inference must be rejected")
                } catch (failure: AiHttpException) {
                    assertEquals(307, failure.statusCode)
                }
                assertEquals(0, destination.requestCount)
                assertEquals(2, server.requestCount)
            }
        }
    }
}
