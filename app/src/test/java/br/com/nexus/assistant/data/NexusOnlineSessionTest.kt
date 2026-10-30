package br.com.nexus.assistant.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NexusOnlineSessionTest {
    private class MemoryStore(var value: StoredOnlineSession? = null) : OnlineSessionStorage {
        override fun read() = value
        override fun write(value: StoredOnlineSession?) { this.value = value }
    }

    private val access = "access-token-that-is-only-a-test-1"
    private val refresh = "refresh-token-that-is-only-a-test-1"
    private fun response(access: String = this.access, refresh: String = this.refresh, seconds: Int = 900, model: String = "nexus-ai") =
        MockResponse().setHeader("Content-Type", "application/json").setBody(JSONObject()
            .put("access_token", access).put("refresh_token", refresh).put("expires_in", seconds).put("model", model).toString())

    @Test fun `service path is fixed and credentials never belong in its URL`() {
        assertEquals("https://nexus.example/v1", NexusOnlineSessionManager.canonicalServiceUrl("https://nexus.example", false))
        assertEquals("https://nexus.example/api/v1", NexusOnlineSessionManager.canonicalServiceUrl("https://nexus.example/api/v1/", false))
        assertEquals("https://nexus.example/api/v1/sessions/refresh", NexusOnlineSessionManager.sessionEndpoint("https://nexus.example/api/v1", "refresh", false).toString())
        for (url in listOf("http://nexus.example/v1", "https://secret@nexus.example/v1", "https://nexus.example/v1?key=secret", "https://nexus.example/v1#token")) {
            try { NexusOnlineSessionManager.canonicalServiceUrl(url, false); fail(url) } catch (_: AiException) { }
        }
        try { NexusOnlineSessionManager.canonicalServiceUrl("http://localhost/v1", false); fail() } catch (_: AiException) { }
        assertEquals("http://localhost/v1", NexusOnlineSessionManager.canonicalServiceUrl("http://localhost/v1", true))
    }

    @Test fun `not deployed reports actual unavailability without making a request`() = runBlocking {
        val manager = NexusOnlineSessionManager("", MemoryStore())
        assertFalse(manager.configured)
        assertFalse(manager.hasSession())
        try { manager.accessToken(); fail() } catch (error: AiException) { assertTrue(error.message!!.contains("não foi implantado")) }
    }

    @Test fun `invitation redemption stores a service-bound individual session and redacts string output`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(response())
            val store = MemoryStore()
            val manager = NexusOnlineSessionManager(server.url("/nexus/v1").toString(), store, allowInsecureLocal = true, nowEpochSeconds = { 1_000 })
            manager.redeem("one-time-invitation-test-123456")
            val req = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/nexus/v1/sessions/redeem", req.path)
            assertEquals("one-time-invitation-test-123456", JSONObject(req.body.readUtf8()).getString("invitation_code"))
            assertNull(req.getHeader("Authorization"))
            assertEquals(1_900L, store.value!!.expiresAtEpochSeconds)
            assertEquals(access, manager.accessToken())
            assertEquals(1, server.requestCount)
            assertTrue(manager.hasSession())
            assertFalse(store.value.toString().contains(access))
            assertFalse(store.value.toString().contains(refresh))
        }
    }

    @Test fun `refresh rotates once across concurrent clients sharing the same credentials`() = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/v1").toString()
            val store = MemoryStore(StoredOnlineSession(base, access, refresh, 1_010))
            server.enqueue(response("next-access-token-that-is-only-a-test", "next-refresh-token-that-is-only-a-test").setBodyDelay(100, TimeUnit.MILLISECONDS))
            val one = NexusOnlineSessionManager(base, store, allowInsecureLocal = true, nowEpochSeconds = { 1_000 })
            val two = NexusOnlineSessionManager(base, store, allowInsecureLocal = true, nowEpochSeconds = { 1_000 })
            val results = coroutineScope { val a = async { one.accessToken() }; val b = async { two.accessToken() }; listOf(a.await(), b.await()) }
            assertEquals(listOf(store.value!!.accessToken, store.value!!.accessToken), results)
            assertEquals(1, server.requestCount)
            val req = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/v1/sessions/refresh", req.path)
            assertEquals(refresh, JSONObject(req.body.readUtf8()).getString("refresh_token"))
            assertNotEquals(refresh, store.value!!.refreshToken)
        }
    }

    @Test fun `rejected refresh clears local credentials and does not retry`() = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/v1").toString()
            val store = MemoryStore(StoredOnlineSession(base, access, refresh, 1_010))
            server.enqueue(MockResponse().setResponseCode(401).setBody("private upstream body must not be shown"))
            val manager = NexusOnlineSessionManager(base, store, allowInsecureLocal = true, nowEpochSeconds = { 1_000 })
            try { manager.accessToken(); fail() } catch (error: AiException) {
                assertTrue(error.message!!.contains("revogada"))
                assertFalse(error.message!!.contains("private"))
            }
            assertNull(store.value)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun `session cannot be sent to a different deployment`() = runBlocking {
        MockWebServer().use { server ->
            val store = MemoryStore(StoredOnlineSession("https://old.example/v1", access, refresh, 9_999))
            val manager = NexusOnlineSessionManager(server.url("/v1").toString(), store, allowInsecureLocal = true, nowEpochSeconds = { 1_000 })
            assertFalse(manager.hasSession())
            try { manager.accessToken(); fail() } catch (_: AiException) { }
            assertNull(store.value)
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun `logout clears local state even when remote revocation fails`() = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/v1").toString()
            val store = MemoryStore(StoredOnlineSession(base, access, refresh, 9_999))
            server.enqueue(MockResponse().setResponseCode(503))
            val manager = NexusOnlineSessionManager(base, store, allowInsecureLocal = true)
            try { manager.revoke(); fail() } catch (_: AiException) { }
            assertNull(store.value)
            assertEquals("/v1/sessions/revoke", server.takeRequest(2, TimeUnit.SECONDS)!!.path)
        }
    }

    @Test fun `logout clears a corrupt encrypted record even if reading credentials fails`() = runBlocking {
        var cleared = false
        val corrupted = object : OnlineSessionStorage {
            override fun read(): StoredOnlineSession? = error("Corrupt encrypted record")
            override fun write(value: StoredOnlineSession?) { assertNull(value); cleared = true }
        }
        val manager = NexusOnlineSessionManager("https://nexus.example/v1", corrupted)
        try { manager.revoke(); fail() } catch (_: IllegalStateException) { }
        assertTrue(cleared)
    }

    @Test fun `redirects are refused instead of forwarding a refresh token`() = runBlocking {
        MockWebServer().use { server ->
            MockWebServer().use { unrelated ->
                val base = server.url("/v1").toString()
                val store = MemoryStore(StoredOnlineSession(base, access, refresh, 1_010))
                server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", unrelated.url("/steal")))
                val manager = NexusOnlineSessionManager(base, store, allowInsecureLocal = true, nowEpochSeconds = { 1_000 })
                try { manager.accessToken(); fail() } catch (error: AiException) { assertTrue(error.message!!.contains("redirecionar")) }
                assertEquals(1, server.requestCount)
                assertEquals(0, unrelated.requestCount)
            }
        }
    }

    @Test fun `malformed model validity and oversized responses never become stored credentials`() = runBlocking {
        for (bad in listOf(response(model = "unapproved-model"), response(seconds = 1), response(seconds = 9_999),
            response(access = "short"), MockResponse().setHeader("Content-Type", "application/json").setBody("x".repeat(32_769)))) {
            MockWebServer().use { server ->
                server.enqueue(bad)
                val store = MemoryStore()
                val manager = NexusOnlineSessionManager(server.url("/v1").toString(), store, allowInsecureLocal = true)
                try { manager.redeem("one-time-invitation-test-123456"); fail() } catch (_: AiException) { }
                assertNull(store.value)
            }
        }
    }

    @Test fun `clear waits for an in-flight refresh so a callback cannot resurrect logout`() = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/v1").toString()
            val store = MemoryStore(StoredOnlineSession(base, access, refresh, 1_010))
            server.enqueue(response("next-access-token-that-is-only-a-test", "next-refresh-token-that-is-only-a-test").setBodyDelay(100, TimeUnit.MILLISECONDS))
            val manager = NexusOnlineSessionManager(base, store, allowInsecureLocal = true, nowEpochSeconds = { 1_000 })
            val fresh = launch { manager.accessToken() }
            while (server.requestCount == 0) delay(5)
            manager.clearLocal()
            fresh.join()
            assertNull(store.value)
        }
    }
}
