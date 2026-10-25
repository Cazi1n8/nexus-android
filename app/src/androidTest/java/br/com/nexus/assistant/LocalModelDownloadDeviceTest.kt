package br.com.nexus.assistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.nexus.assistant.data.local.LocalModelManager
import br.com.nexus.assistant.data.local.LocalModelSpec
import br.com.nexus.assistant.data.local.LocalModelStatus
import java.io.File
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
import org.junit.runner.RunWith

/** Real app downloader over local HTTPS, with small synthetic transport files.
 * These tests do not inject a model by ADB and do not claim language-model quality.
 */
@RunWith(AndroidJUnit4::class)
class LocalModelDownloadDeviceTest {
    private lateinit var directory: File
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private val payload = ByteArray(64 * 1024) { ((it * 17 + 11) % 256).toByte() }.apply {
        "GGUF".toByteArray(Charsets.US_ASCII).copyInto(this)
    }

    @Before fun preparePrivateHttpsFixture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(context.filesDir, "transport-test-${System.nanoTime()}").apply { check(mkdirs()) }
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
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(false)
            .build()
    }

    @After fun removeOnlyTheTransportFixture() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdownNow()
        server.shutdown()
        directory.deleteRecursively()
    }

    @Test(timeout = 60_000L)
    fun allBytesReceivedCanFinishAndReachReadyWithoutWaitingForEof() = runBlocking<Unit> {
        server.enqueue(MockResponse().setBody(Buffer().write(payload))
            .removeHeader("Content-Length").setSocketPolicy(SocketPolicy.KEEP_OPEN))
        val manager = manager()
        // The server intentionally never closes its unknown-length body. Waiting
        // for another read at 100% would exceed this bound and fail the test.
        withTimeout(8_000) { manager.download() }
        assertEquals(LocalModelStatus.READY, manager.state.value.status)
        assertEquals(payload.size.toLong(), manager.state.value.downloadedBytes)
        assertArrayEquals(payload, manager.requireReadyFile().readBytes())
        assertFalse(File(directory, "transport.gguf.part").exists())
    }

    @Test(timeout = 60_000L)
    fun cancellationAndManagerRecreationResumeTheVerifiedTransfer() = runBlocking<Unit> {
        server.enqueue(MockResponse().setBody(Buffer().write(payload)).throttleBody(2_048, 100, TimeUnit.MILLISECONDS))
        val first = manager()
        val download = launch(Dispatchers.IO) { first.download() }
        withTimeout(8_000) {
            first.state.first { it.status == LocalModelStatus.DOWNLOADING && it.downloadedBytes > 0 }
        }
        download.cancelAndJoin()
        val partial = File(directory, "transport.gguf.part")
        val offset = partial.length()
        assertTrue("The interrupted transfer should remain resumable", offset in 1 until payload.size.toLong())
        assertEquals(LocalModelStatus.PAUSED, first.state.value.status)
        server.takeRequest(1, TimeUnit.SECONDS)

        val resumed = manager()
        resumed.refresh()
        assertEquals(LocalModelStatus.PAUSED, resumed.state.value.status)
        assertEquals(offset, resumed.state.value.downloadedBytes)
        server.enqueue(MockResponse().setResponseCode(206)
            .setHeader("Content-Range", "bytes $offset-${payload.lastIndex}/${payload.size}")
            .setBody(Buffer().write(payload.copyOfRange(offset.toInt(), payload.size))))
        withTimeout(8_000) { resumed.download() }
        assertEquals(LocalModelStatus.READY, resumed.state.value.status)
        assertEquals("bytes=$offset-", server.takeRequest(1, TimeUnit.SECONDS)?.getHeader("Range"))
        assertArrayEquals(payload, resumed.requireReadyFile().readBytes())
        assertFalse(partial.exists())
    }

    private fun manager(): LocalModelManager {
        val hash = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") {
            (it.toInt() and 255).toString(16).padStart(2, '0')
        }
        val spec = LocalModelSpec("transport.gguf", "Local HTTPS transport fixture", server.url("/model").toString(),
            payload.size.toLong(), hash)
        return LocalModelManager(directory, spec, client, responseTimeoutMillis = 5_000)
    }
}
