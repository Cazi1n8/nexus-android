package br.com.nexus.assistant.data.local

import br.com.nexus.assistant.data.AiException
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Exercises the real HTTPS downloader and its files; no interceptor manufactures responses. */
class LocalModelDownloadTest {
    private lateinit var directory: File
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private val clients = mutableListOf<OkHttpClient>()
    private val payload = ByteArray(96 * 1024) { ((it * 31 + 7) % 256).toByte() }.apply {
        "GGUF".toByteArray(Charsets.US_ASCII).copyInto(this)
    }

    @Before fun prepareHttpsFixture() {
        directory = Files.createTempDirectory("nexus-model-download-").toFile()
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCertificates = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer().apply {
            protocols = listOf(Protocol.HTTP_1_1)
            useHttps(serverCertificates.sslSocketFactory(), false)
            start()
        }
        client = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(false)
            .build().also(clients::add)
    }

    @After fun closeFixture() {
        clients.forEach { connection ->
            connection.dispatcher.cancelAll()
            connection.connectionPool.evictAll()
            connection.dispatcher.executorService.shutdownNow()
        }
        server.shutdown()
        directory.deleteRecursively()
    }

    @Test(timeout = 15_000L)
    fun fixedLengthDownloadReturnsAfterVerifyingAndReleasesTheJob() = runBlocking<Unit> {
        server.enqueue(body(payload))
        val manager = manager()
        withTimeout(5_000) { manager.download() }
        assertReady(manager)
        assertEquals("identity", server.takeRequest(1, TimeUnit.SECONDS)?.getHeader("Accept-Encoding"))
        // READY must also be usable by the caller; an orphan cancellation watcher
        // would make download() fail to return before reaching this assertion.
        assertEquals(File(directory, "fixture.gguf"), manager.requireReadyFile())
    }

    @Test(timeout = 15_000L)
    fun completePinnedBytesDoNotWaitForAnUnknownLengthConnectionToClose() = runBlocking<Unit> {
        // A legal HTTP/1.1 unknown-length response whose server leaves the socket
        // open after sending every pinned byte. This reproduces a 100% download
        // waiting for EOF; the trusted length and SHA determine completeness.
        server.enqueue(body(payload).removeHeader("Content-Length").setSocketPolicy(SocketPolicy.KEEP_OPEN))
        val manager = manager()
        withTimeout(3_000) { manager.download() }
        assertReady(manager)
    }

    @Test(timeout = 15_000L)
    fun httpsRedirectAndChunkedTransferReachTheSameVerifiedFile() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/actual-model")))
        server.enqueue(MockResponse().setChunkedBody(Buffer().write(payload), 4_096))
        val manager = manager()
        withTimeout(5_000) { manager.download() }
        assertReady(manager)
        assertEquals("/model", server.takeRequest(1, TimeUnit.SECONDS)?.path)
        assertEquals("/actual-model", server.takeRequest(1, TimeUnit.SECONDS)?.path)
    }

    @Test(timeout = 15_000L)
    fun refusesPlainHttpBeforeSendingARequest() = runBlocking<Unit> {
        val spec = spec().copy(downloadUrl = "http://localhost:${server.port}/model")
        val manager = LocalModelManager(directory, spec, client, responseTimeoutMillis = 1_000)
        failure(manager)
        assertEquals(LocalModelStatus.FAILED, manager.state.value.status)
        assertEquals(0, server.requestCount)
        assertFalse(destination.exists())
    }

    @Test(timeout = 15_000L)
    fun aSameLengthCorruptedFileIsNeverPromotedToReady() = runBlocking<Unit> {
        val corrupted = payload.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        server.enqueue(body(corrupted))
        val manager = manager()
        failure(manager)
        assertEquals(LocalModelStatus.FAILED, manager.state.value.status)
        assertFalse(destination.exists())
        assertFalse(partial.exists())
    }

    @Test(timeout = 15_000L)
    fun anOversizedDeclaredFileIsRejectedBeforeItCanBecomeAModel() = runBlocking<Unit> {
        server.enqueue(body(payload + byteArrayOf(1)))
        val manager = manager()
        failure(manager)
        assertEquals(LocalModelStatus.FAILED, manager.state.value.status)
        assertFalse(destination.exists())
        assertTrue(!partial.exists() || partial.length() == 0L)
    }

    @Test(timeout = 15_000L)
    fun aDisconnectedTransferPreservesItsPrefixWithoutMarkingItReady() = runBlocking<Unit> {
        val prefix = payload.copyOfRange(0, 16 * 1024)
        server.enqueue(body(prefix).setHeader("Content-Length", payload.size).setSocketPolicy(SocketPolicy.DISCONNECT_AT_END))
        val manager = manager()
        failure(manager)
        assertEquals(LocalModelStatus.PAUSED, manager.state.value.status)
        assertArrayEquals(prefix, partial.readBytes())
        assertFalse(destination.exists())
        assertThrows(AiException::class.java) { manager.requireReadyFile() }
    }

    @Test(timeout = 15_000L)
    fun noResponseHeadersEndsWithAnActionableFailureInsteadOfAnInfiniteSpinner() = runBlocking<Unit> {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val manager = manager(headerTimeoutMillis = 250)
        withTimeout(3_000) { failure(manager) }
        assertEquals(LocalModelStatus.FAILED, manager.state.value.status)
        assertFalse(manager.state.value.message.isNullOrBlank())
        assertFalse(destination.exists())
    }

    @Test(timeout = 15_000L)
    fun aStalledBodyPreservesTheBytesAlreadyReceivedAndStopsTheActiveState() = runBlocking<Unit> {
        val shortReadClient = client.newBuilder().readTimeout(250, TimeUnit.MILLISECONDS).build().also(clients::add)
        server.enqueue(body(payload).throttleBody(1_024, 2, TimeUnit.SECONDS))
        val manager = LocalModelManager(directory, spec(), shortReadClient, responseTimeoutMillis = 1_000)
        withTimeout(3_000) { failure(manager) }
        assertEquals(LocalModelStatus.PAUSED, manager.state.value.status)
        assertTrue(partial.length() in 1 until payload.size.toLong())
        assertFalse(destination.exists())
    }

    @Test(timeout = 20_000L)
    fun cancellationKeepsProgressAndANewManagerResumesWithTheExactRange() = runBlocking<Unit> {
        server.enqueue(body(payload).throttleBody(1_024, 50, TimeUnit.MILLISECONDS))
        val first = manager()
        val job = launch(Dispatchers.IO) { first.download() }
        withTimeout(3_000) {
            first.state.first { it.status == LocalModelStatus.DOWNLOADING && it.downloadedBytes > 0 }
        }
        job.cancelAndJoin()
        val offset = partial.length()
        assertTrue("Cancellation should preserve a useful partial file", offset in 1 until payload.size.toLong())
        assertEquals(LocalModelStatus.PAUSED, first.state.value.status)
        server.takeRequest(1, TimeUnit.SECONDS)
        val resumed = manager()
        resumed.refresh()
        assertEquals(LocalModelStatus.PAUSED, resumed.state.value.status)
        assertEquals(offset, resumed.state.value.downloadedBytes)
        server.enqueue(body(payload.copyOfRange(offset.toInt(), payload.size)).setResponseCode(206)
            .setHeader("Content-Range", "bytes $offset-${payload.lastIndex}/${payload.size}"))
        withTimeout(5_000) { resumed.download() }
        assertReady(resumed)
        assertEquals("bytes=$offset-", server.takeRequest(1, TimeUnit.SECONDS)?.getHeader("Range"))
    }

    @Test(timeout = 15_000L)
    fun aServerIgnoringRangeRestartsSafelyWithoutAppendingTheFileTwice() = runBlocking<Unit> {
        partial.writeBytes(payload.copyOfRange(0, 8_192))
        server.enqueue(body(payload))
        val manager = manager()
        manager.refresh()
        withTimeout(5_000) { manager.download() }
        assertReady(manager)
        assertEquals("bytes=8192-", server.takeRequest(1, TimeUnit.SECONDS)?.getHeader("Range"))
    }

    @Test(timeout = 15_000L)
    fun aMismatchedContentRangeCannotOverwriteOrAppendToTheExistingPrefix() = runBlocking<Unit> {
        val prefix = payload.copyOfRange(0, 8_192)
        partial.writeBytes(prefix)
        server.enqueue(body(payload.copyOfRange(prefix.size, payload.size)).setResponseCode(206)
            .setHeader("Content-Range", "bytes 0-${payload.size - prefix.size - 1}/${payload.size}"))
        val manager = manager()
        manager.refresh()
        failure(manager)
        assertEquals(LocalModelStatus.PAUSED, manager.state.value.status)
        assertArrayEquals(prefix, partial.readBytes())
        assertFalse(destination.exists())
    }

    @Test(timeout = 15_000L)
    fun completePartialFromAnInterruptedFinalizationIsVerifiedWithoutRedownloading() = runBlocking<Unit> {
        partial.writeBytes(payload)
        val manager = manager()
        manager.refresh()
        withTimeout(3_000) { manager.download() }
        assertReady(manager)
        assertEquals(0, server.requestCount)
    }

    @Test(timeout = 15_000L)
    fun deletingAnInterruptedDownloadPreservesOtherModelFiles() = runBlocking<Unit> {
        partial.writeBytes(payload.copyOfRange(0, 8_192))
        val other = File(directory, "other-profile.gguf").apply { writeText("keep") }
        val manager = manager()
        manager.refresh()
        manager.delete()
        assertEquals(LocalModelStatus.ABSENT, manager.state.value.status)
        assertFalse(partial.exists())
        assertEquals("keep", other.readText())
    }

    private fun manager(headerTimeoutMillis: Long = 2_000) =
        LocalModelManager(directory, spec(), client, responseTimeoutMillis = headerTimeoutMillis)

    private fun spec() = LocalModelSpec("fixture.gguf", "HTTPS transport fixture", server.url("/model").toString(),
        payload.size.toLong(), MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it.toInt() and 255) })

    private val destination get() = File(directory, "fixture.gguf")
    private val partial get() = File(directory, "fixture.gguf.part")
    private fun body(bytes: ByteArray) = MockResponse().setBody(Buffer().write(bytes))

    private suspend fun failure(manager: LocalModelManager) {
        try {
            withTimeout(5_000) { manager.download() }
            fail("The untrusted or stalled response must not be accepted")
        } catch (_: AiException) { }
    }

    private fun assertReady(manager: LocalModelManager) {
        assertEquals(LocalModelStatus.READY, manager.state.value.status)
        assertEquals(payload.size.toLong(), manager.state.value.downloadedBytes)
        assertArrayEquals(payload, manager.requireReadyFile().readBytes())
        assertFalse("A completed model must not leave a second partial copy", partial.exists())
    }
}
