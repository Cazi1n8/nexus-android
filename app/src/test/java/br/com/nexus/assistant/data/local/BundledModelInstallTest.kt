package br.com.nexus.assistant.data.local

import br.com.nexus.assistant.data.AiException
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Real private-file preparation from an APK-like stream; no HTTP response or AI is fabricated. */
class BundledModelInstallTest {
    private lateinit var directory: File
    private lateinit var client: OkHttpClient
    private val requests = AtomicInteger()
    private val payload = ByteArray(160 * 1024) { ((it * 29 + 3) % 256).toByte() }
        .apply { "GGUF".toByteArray(Charsets.US_ASCII).copyInto(this) }
    private val destination get() = File(directory, "included.gguf")
    private val partial get() = File(directory, "included.gguf.part")

    @Before fun prepare() {
        directory = Files.createTempDirectory("nexus-bundled-model-").toFile()
        client = OkHttpClient.Builder().addInterceptor {
            requests.incrementAndGet()
            throw AssertionError("Preparing a bundled model must not create an HTTP request")
        }.build()
    }

    @After fun removeFixture() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdownNow()
        directory.deleteRecursively()
        assertEquals("No bundled preparation may contact the network", 0, requests.get())
    }

    @Test(timeout = 15_000L)
    fun exactIncludedBytesBecomeAReadyPrivateModelAndTheJobReturns() = runBlocking<Unit> {
        val manager = manager()
        val closed = AtomicBoolean(false)
        withTimeout(3_000) {
            manager.installBundled { tracked(payload, closed) }
        }
        assertReady(manager)
        assertTrue(closed.get())
    }

    @Test(timeout = 15_000L)
    fun anExistingVerifiedModelIsReusedWithoutOpeningTheApkAsset() = runBlocking<Unit> {
        destination.writeBytes(payload)
        val manager = manager()
        withTimeout(3_000) { manager.installBundled { error("The verified copy must be reused") } }
        assertReady(manager)
    }

    @Test(timeout = 15_000L)
    fun coreUpgradeReusesAnEarlierBundledCopyAndCanRemoveItWithoutDownloading() = runBlocking<Unit> {
        val oldApplication = manager()
        oldApplication.installBundled { ByteArrayInputStream(payload) }
        val otherProfile = File(directory, "other-profile.gguf").apply { writeText("another installed pack") }
        val history = File(directory, "history-to-preserve.json").apply { writeText("authorized conversation fixture") }

        // The CORE build no longer includes the asset, but the app's private
        // verified copy is already present after an update with the same signer.
        val core = manager()
        core.refresh()
        assertReady(core, LocalModelSource.NETWORK)
        core.download()
        assertReady(core, LocalModelSource.NETWORK)
        assertEquals(payload.size.toLong(), destination.length())
        assertFalse(partial.exists())
        assertEquals(0, requests.get())

        core.delete()
        assertEquals(LocalModelStatus.ABSENT, core.state.value.status)
        assertFalse(destination.exists())
        assertFalse(partial.exists())
        assertEquals("another installed pack", otherProfile.readText())
        assertEquals("authorized conversation fixture", history.readText())
        assertEquals(0, requests.get())
    }

    @Test(timeout = 15_000L)
    fun aCompleteVerifiedPartialIsPromotedWithoutOpeningTheApkAsset() = runBlocking<Unit> {
        partial.writeBytes(payload)
        val manager = manager()
        withTimeout(3_000) { manager.installBundled { error("The complete valid partial needs no copy") } }
        assertReady(manager)
    }

    @Test(timeout = 15_000L)
    fun aCorruptInstalledCopyIsRepairedFromTheBundledAsset() = runBlocking<Unit> {
        destination.writeBytes(payload.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() })
        val other = File(directory, "other-model.gguf").apply { writeText("keep") }
        val manager = manager()
        withTimeout(3_000) { manager.installBundled { ByteArrayInputStream(payload) } }
        assertReady(manager)
        assertEquals("keep", other.readText())
    }

    @Test(timeout = 15_000L)
    fun aCompleteCorruptPartialIsRepairedLocallyInsteadOfRequestingADownload() = runBlocking<Unit> {
        partial.writeBytes(payload.copyOf().apply { this[12] = (this[12].toInt() xor 1).toByte() })
        val manager = manager()
        withTimeout(3_000) { manager.installBundled { ByteArrayInputStream(payload) } }
        assertReady(manager)
    }

    @Test(timeout = 15_000L)
    fun aValidInterruptedPrefixIsComparedThenResumedWithoutDuplicatingIt() = runBlocking<Unit> {
        partial.writeBytes(payload.copyOfRange(0, 37_000))
        val opens = AtomicInteger()
        val manager = manager()
        withTimeout(3_000) {
            manager.installBundled { opens.incrementAndGet(); ByteArrayInputStream(payload) }
        }
        assertReady(manager)
        assertEquals(1, opens.get())
    }

    @Test(timeout = 15_000L)
    fun aMismatchingInterruptedPrefixRestartsFromTheApkSafely() = runBlocking<Unit> {
        partial.writeBytes(payload.copyOfRange(0, 37_000).apply { this[2] = (this[2].toInt() xor 1).toByte() })
        val opens = AtomicInteger()
        val manager = manager()
        withTimeout(3_000) {
            manager.installBundled { opens.incrementAndGet(); ByteArrayInputStream(payload) }
        }
        assertReady(manager)
        assertEquals("A bad prefix requires reopening the immutable asset from byte zero", 2, opens.get())
    }

    @Test(timeout = 15_000L)
    fun anOversizedOldPartialCanBeRepairedFromTheBundledAsset() = runBlocking<Unit> {
        partial.writeBytes(payload + byteArrayOf(1, 2, 3))
        val manager = manager()
        withTimeout(3_000) { manager.installBundled { ByteArrayInputStream(payload) } }
        assertReady(manager)
    }

    @Test(timeout = 15_000L)
    fun aTruncatedApkStreamCannotBePromotedAndClosesItsResources() = runBlocking<Unit> {
        val manager = manager()
        val closed = AtomicBoolean(false)
        expectFailure { manager.installBundled { tracked(payload.copyOfRange(0, 80_000), closed) } }
        assertEquals(LocalModelStatus.PAUSED, manager.state.value.status)
        assertEquals(LocalModelSource.BUNDLED, manager.state.value.source)
        assertFalse(destination.exists())
        assertTrue(partial.length() in 1 until payload.size.toLong())
        assertTrue(closed.get())
    }

    @Test(timeout = 15_000L)
    fun aSameLengthCorruptApkStreamIsDiscardedWithoutUnlockingChat() = runBlocking<Unit> {
        val manager = manager()
        val corrupted = payload.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        expectFailure { manager.installBundled { ByteArrayInputStream(corrupted) } }
        assertEquals(LocalModelStatus.FAILED, manager.state.value.status)
        assertFalse(destination.exists())
        assertFalse(partial.exists())
        assertTrue(manager.state.value.message.orEmpty().contains("IA incluída"))
        assertThrows(AiException::class.java) { manager.requireReadyFile() }
    }

    @Test(timeout = 15_000L)
    fun cancellationClosesTheAssetAndANewManagerResumesThePrivateCopy() = runBlocking<Unit> {
        val closed = AtomicBoolean(false)
        val first = manager()
        val work = launch(Dispatchers.IO) {
            first.installBundled { object : ByteArrayInputStream(payload) {
                override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                    if (closed.get()) throw IOException("Closed controlled asset stream")
                    Thread.sleep(5)
                    return super.read(bytes, offset, minOf(length, 4_096))
                }
                override fun close() { closed.set(true); super.close() }
            } }
        }
        withTimeout(3_000) {
            first.state.first { it.status == LocalModelStatus.COPYING && it.downloadedBytes > 0 }
        }
        work.cancelAndJoin()
        assertTrue(closed.get())
        assertEquals(LocalModelStatus.PAUSED, first.state.value.status)
        assertTrue(partial.length() in 1 until payload.size.toLong())
        val resumed = manager()
        withTimeout(3_000) { resumed.installBundled { ByteArrayInputStream(payload) } }
        assertReady(resumed)
    }

    @Test(timeout = 15_000L)
    fun preparationDoesNotNeedToReadPastTheExactPinnedByteBudget() = runBlocking<Unit> {
        val read = AtomicInteger()
        val manager = manager()
        withTimeout(3_000) {
            manager.installBundled { object : ByteArrayInputStream(payload + byteArrayOf(99, 100)) {
                override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                    check(read.get() < payload.size) { "No read for an EOF or trailer is needed" }
                    val count = super.read(bytes, offset, length)
                    if (count > 0) read.addAndGet(count)
                    return count
                }
            } }
        }
        assertReady(manager)
        assertEquals(payload.size, read.get())
    }

    @Test(timeout = 15_000L)
    fun zeroLengthBulkReadsDoNotCreateAnInfinitePreparationLoop() = runBlocking<Unit> {
        val manager = manager()
        withTimeout(3_000) {
            manager.installBundled { object : ByteArrayInputStream(payload) {
                var zero = true
                override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                    zero = !zero
                    return if (zero) 0 else super.read(bytes, offset, length)
                }
            } }
        }
        assertReady(manager)
    }

    @Test(timeout = 15_000L)
    fun anAssetOpenFailureLeavesChatLockedAndCanBeRetriedLocally() = runBlocking<Unit> {
        val manager = manager()
        expectFailure { manager.installBundled { throw IOException("Controlled APK asset open failure") } }
        assertEquals(LocalModelStatus.FAILED, manager.state.value.status)
        assertEquals(LocalModelSource.BUNDLED, manager.state.value.source)
        assertFalse(destination.exists())
        assertThrows(AiException::class.java) { manager.requireReadyFile() }
        withTimeout(3_000) { manager.installBundled { ByteArrayInputStream(payload) } }
        assertReady(manager)
    }

    @Test(timeout = 15_000L)
    fun simultaneousPreparationCallsSerializeAndReuseTheFirstVerifiedCopy() = runBlocking<Unit> {
        val firstRead = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val opens = AtomicInteger()
        val manager = manager()
        val first = launch(Dispatchers.IO) {
            manager.installBundled {
                opens.incrementAndGet()
                object : ByteArrayInputStream(payload) {
                    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                        firstRead.countDown()
                        check(releaseRead.await(3, TimeUnit.SECONDS)) { "The test did not release the asset stream" }
                        return super.read(bytes, offset, length)
                    }
                }
            }
        }
        assertTrue(firstRead.await(3, TimeUnit.SECONDS))
        // Enter the suspended mutex acquisition before releasing the first call.
        val second = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            manager.installBundled { opens.incrementAndGet(); ByteArrayInputStream(payload) }
        }
        releaseRead.countDown()
        withTimeout(3_000) { first.join(); second.join() }
        assertReady(manager)
        assertEquals("Only the first serialized preparation needs to open the APK asset", 1, opens.get())
    }

    @Test(timeout = 15_000L)
    fun cancellingABlockedAssetReadClosesItAndPreservesOnlyCompletedChunks() = runBlocking<Unit> {
        val blockedRead = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val closed = AtomicBoolean()
        val manager = manager()
        val work = launch(Dispatchers.IO) {
            manager.installBundled {
                object : ByteArrayInputStream(payload) {
                    private var reads = 0
                    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                        if (++reads == 2) {
                            blockedRead.countDown()
                            check(releaseRead.await(3, TimeUnit.SECONDS)) { "Cancellation did not close the blocked asset" }
                            if (closed.get()) throw IOException("Controlled read closed by cancellation")
                        }
                        return super.read(bytes, offset, length)
                    }
                    override fun close() { closed.set(true); releaseRead.countDown(); super.close() }
                }
            }
        }
        assertTrue(blockedRead.await(3, TimeUnit.SECONDS))
        withTimeout(3_000) { work.cancelAndJoin() }
        assertTrue(closed.get())
        assertEquals(LocalModelStatus.PAUSED, manager.state.value.status)
        assertEquals(64 * 1024L, partial.length())
        assertFalse(destination.exists())
        withTimeout(3_000) { manager.installBundled { ByteArrayInputStream(payload) } }
        assertReady(manager)
    }

    @Test(timeout = 15_000L)
    fun deletingThePrivateCopyDoesNotRequireANetworkDownloadToPrepareItAgain() = runBlocking<Unit> {
        val manager = manager()
        withTimeout(3_000) { manager.installBundled { ByteArrayInputStream(payload) } }
        manager.delete()
        assertEquals(LocalModelStatus.ABSENT, manager.state.value.status)
        assertEquals(LocalModelSource.BUNDLED, manager.state.value.source)
        assertFalse(destination.exists())
        assertThrows(AiException::class.java) { manager.requireReadyFile() }
        withTimeout(3_000) { manager.installBundled { ByteArrayInputStream(payload) } }
        assertReady(manager)
    }

    private fun manager(): LocalModelManager {
        val sha = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") {
            (it.toInt() and 255).toString(16).padStart(2, '0')
        }
        return LocalModelManager(directory,
            LocalModelSpec("included.gguf", "Bundled stream fixture", "https://must-not-contact.invalid/model", payload.size.toLong(), sha), client)
    }

    private fun tracked(bytes: ByteArray, closed: AtomicBoolean): InputStream = object : ByteArrayInputStream(bytes) {
        override fun close() { closed.set(true); super.close() }
    }

    private suspend fun expectFailure(action: suspend () -> Unit) {
        try { withTimeout(3_000) { action() }; fail("An incomplete or corrupt included model must fail") }
        catch (_: AiException) { }
    }

    private fun assertReady(manager: LocalModelManager, source: LocalModelSource = LocalModelSource.BUNDLED) {
        assertEquals(LocalModelStatus.READY, manager.state.value.status)
        assertEquals(source, manager.state.value.source)
        assertEquals(payload.size.toLong(), manager.state.value.downloadedBytes)
        assertEquals(payload.size.toLong(), manager.state.value.verifiedBytes)
        assertArrayEquals(payload, manager.requireReadyFile().readBytes())
        assertFalse(partial.exists())
    }
}
