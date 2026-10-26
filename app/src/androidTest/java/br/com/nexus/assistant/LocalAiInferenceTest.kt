package br.com.nexus.assistant

import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.nexus.assistant.data.local.LocalModelCatalog
import br.com.nexus.assistant.data.local.NexusNativeBridge
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in, real GGUF inference. Default instrumented runs explicitly skip this large fixture. */
@RunWith(AndroidJUnit4::class)
class LocalAiInferenceTest {
    @Test(timeout = 600_000L)
    fun realModelProducesUtf8TextOnAndroidAndRecordsObservations() {
        val arguments = InstrumentationRegistry.getArguments()
        val relativePath = arguments.getString("nexusModelPath")
        assumeTrue("Optional real-model test requires the verified nexusModelPath fixture.", !relativePath.isNullOrBlank())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reportFile = File(context.filesDir, "ci-reports/local-ai-observations.json")
        val observations = JSONArray()
        val report = JSONObject()
            .put("schemaVersion", 1)
            .put("executionStatus", "started")
            .put("androidApi", Build.VERSION.SDK_INT)
            .put("abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
            .put("runtime", "llama.cpp b11838 packaged JNI")
            .put("contextTokens", 512).put("threads", 1)
            .put("cases", observations)
            .put("scope", "Real text inference in Android; no camera, microphone or claim of general model quality.")
        var handle = 0L
        try {
            assertTrue("The fixture test requires a 64-bit Android process", NexusNativeBridge.supports64BitProcess)
            assertTrue("The real packaged runtime must load", NexusNativeBridge.available)
            val relative = requireNotNull(relativePath)
            require(!File(relative).isAbsolute) { "The fixture must be relative to the application's private files." }
            val model = File(context.filesDir, relative).canonicalFile
            require(model.toPath().startsWith(context.filesDir.canonicalFile.toPath())) { "The fixture must stay in private files." }
            val spec = LocalModelCatalog.compact
            assertEquals("The exact pinned GGUF must be transferred", spec.bytes, model.length())
            val digest = MessageDigest.getInstance("SHA-256")
            model.inputStream().buffered().use { input ->
                val bytes = ByteArray(1024 * 1024)
                while (true) {
                    val read = input.read(bytes)
                    if (read < 0) break
                    digest.update(bytes, 0, read)
                }
            }
            val actualHash = digest.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
            assertEquals("The GGUF was changed or corrupted in transfer", spec.sha256, actualHash)
            report.put("model", spec.displayName).put("modelBytes", model.length()).put("modelSha256", actualHash)
            val loadStarted = SystemClock.elapsedRealtime()
            handle = NexusNativeBridge.load(model.absolutePath, nCtx = 512, threads = 1)
            report.put("loadMilliseconds", SystemClock.elapsedRealtime() - loadStarted)
            assertTrue(handle != 0L)

            fun messages(vararg turns: Pair<String, String>): JSONArray = JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", "Você é NEXUS. Responda em português brasileiro. Seja breve."))
                turns.forEach { (role, text) -> put(JSONObject().put("role", role).put("content", text)) }
            }
            var lastCachedPromptTokens = 0
            fun infer(name: String, conversation: JSONArray, limit: Int): String {
                val started = SystemClock.elapsedRealtime()
                // generateResult strictly decodes UTF-8 before parsing metadata.
                val result = NexusNativeBridge.generateResult(handle, conversation.toString(), limit)
                val text = result.text.trim()
                assertTrue("The runtime must return generated text for $name", text.isNotBlank())
                lastCachedPromptTokens = result.cachedPromptTokens
                assertEquals(result.promptTokens - result.cachedPromptTokens, result.evaluatedPromptTokens)
                assertTrue("At least the final input token must be evaluated for fresh logits", result.evaluatedPromptTokens > 0)
                observations.put(JSONObject().put("name", name).put("answer", text)
                    .put("milliseconds", SystemClock.elapsedRealtime() - started).put("outputTokenLimit", limit)
                    .put("promptTokens", result.promptTokens).put("cachedPromptTokens", result.cachedPromptTokens)
                    .put("evaluatedPromptTokens", result.evaluatedPromptTokens))
                return text
            }

            val arithmeticHistory = messages("user" to "Quanto é 17 mais 25? Responda só com o número.")
            val arithmetic = infer("arithmetic", arithmeticHistory, 16)
            report.put("arithmeticMatchedExpected42", arithmetic.trim().trimEnd('.', '!') == "42")
            val repeated = infer("same_prompt_warm_cache", arithmeticHistory, 16)
            assertEquals("A deterministic retry must preserve the exact model text", arithmetic, repeated)
            assertTrue("The pinned Qwen fixture must reuse its exact prompt prefix", lastCachedPromptTokens > 0)
            NexusNativeBridge.cancel(handle)
            assertEquals(arithmetic, infer("after_idle_cancellation", arithmeticHistory, 16))
            assertEquals("A cancelled epoch must not reuse earlier context", 0, lastCachedPromptTokens)
            val introduction = "Meu objeto preferido é um cubo ciano. Lembre apenas nesta conversa."
            val acknowledgement = infer("context_acknowledgement", messages("user" to introduction), 32)
            val color = infer("context_follow_up", messages("user" to introduction,
                "assistant" to acknowledgement, "user" to "Qual é a cor do meu objeto preferido? Responda em uma palavra."), 16)
            report.put("contextMatchedExpectedCiano", color.trim().trimEnd('.', '!').equals("ciano", ignoreCase = true))
            val unicode = infer("unicode", messages("user" to "Repita exatamente: Olá, coração! 🚀"), 32)
            report.put("unicodeMatchedRequestedText", unicode == "Olá, coração! 🚀")
            // Quality flags are honest observations, not a replacement for the generated answers.
            // Incorrect math/context is retained in the report rather than faked or overwritten.
            report.put("executionStatus", "passed").put("semanticAssessment", "observations_only_not_general_quality_certification")
        } catch (failure: Throwable) {
            report.put("executionStatus", "failed").put("failureType", failure.javaClass.simpleName)
            throw failure
        } finally {
            if (handle != 0L) NexusNativeBridge.close(handle)
            reportFile.parentFile?.mkdirs()
            reportFile.writeText(report.toString(2), Charsets.UTF_8)
        }
    }
}
