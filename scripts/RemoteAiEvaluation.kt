import br.com.nexus.assistant.data.AiCompletion
import br.com.nexus.assistant.data.AiMessage
import br.com.nexus.assistant.data.AiMode
import br.com.nexus.assistant.data.NexusSettings
import br.com.nexus.assistant.data.OpenAiCompatibleProvider
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/** Developer host evaluation: production transport, actual endpoint, no generated-answer fixtures. */
fun main(args: Array<String>) = runBlocking {
    require(args.size == 4) { "Expected loopback endpoint, model, prompt fixture and private report path" }
    val endpoint = URI(args[0])
    require(endpoint.scheme in setOf("http", "https") && endpoint.host in setOf("127.0.0.1", "localhost", "[::1]", "::1") &&
        endpoint.rawUserInfo == null && endpoint.rawQuery == null && endpoint.rawFragment == null) { "Developer evaluations accept loopback endpoints only" }
    val token = System.getenv("NEXUS_EVAL_SESSION_TOKEN").orEmpty()
    require(token.isNotEmpty() && token.length <= 8_192 && token.all { it.code in 0x20..0x7e }) { "Set NEXUS_EVAL_SESSION_TOKEN privately in the process environment" }
    val fixtureFile = File(args[2])
    require(fixtureFile.length() in 1..128_000) { "Prompt fixture is missing or too large" }
    val fixture = JSONObject(fixtureFile.readText())
    val cases = fixture.getJSONArray("cases")
    require(cases.length() in 1..32)
    val settings = NexusSettings(aiMode = AiMode.CUSTOM, endpoint = args[0], model = args[1],
        userPreferredName = fixture.optString("userPreferredName", "Calebe"), remoteConsent = true,
        assistantPersonality = fixture.optString("personality", "Amigável"), detail = fixture.optString("detail", "Equilibrado"))
    val memories = fixture.optJSONArray("memories")?.let { values ->
        require(values.length() <= 32)
        (0 until values.length()).map { values.getString(it).also { text -> require(text.length <= 2_000) } }
    }.orEmpty()
    val provider = OpenAiCompatibleProvider(allowInsecureLocal = true)
    val results = JSONArray()
    val output = Path.of(args[3]).toAbsolutePath()
    Files.createDirectories(output.parent)
    fun sha(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    val report = JSONObject().put("kind", "real_remote_model_host_evaluation")
        .put("androidHardwareTest", false).put("endpoint", endpoint.toString()).put("model", args[1])
        .put("fixtureSha256", sha(fixtureFile)).put("modelIdentity", "operator_configured_not_verified_by_client")
        .put("semanticApproval", "not_assessed; inspect actual responses against an independent rubric")
        .put("cases", results)
    fun save() {
        val temporary = Files.createTempFile(output.parent, "nexus-remote-eval-", ".json",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        try {
            Files.writeString(temporary, report.toString(2) + "\n")
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally { Files.deleteIfExists(temporary) }
    }
    var completedTurns = 0
    var failures = 0
    for (caseIndex in 0 until cases.length()) {
        val case = cases.getJSONObject(caseIndex)
        require(case.keySet().all { it in setOf("id", "turns") }) { "Cases contain prompts only" }
        val turns = case.getJSONArray("turns")
        require(turns.length() in 1..24)
        val history = mutableListOf<AiMessage>()
        val responses = JSONArray()
        results.put(JSONObject().put("id", case.getString("id")).put("turns", responses))
        for (turnIndex in 0 until turns.length()) {
            val turn = turns.getJSONObject(turnIndex)
            require(turn.keySet().all { it in setOf("text", "imagePath", "searchQuery") }) { "A turn may not contain a prepared answer" }
            val query = turn.getString("text")
            require(query.length in 1..16_000 && '\u0000' !in query)
            val image = turn.optString("imagePath").takeIf { it.isNotBlank() }?.let { path ->
                val file = File(path).let { if (it.isAbsolute) it else File(fixtureFile.absoluteFile.parentFile, path) }
                require(file.isFile && file.length() in 1..OpenAiCompatibleProvider.MAX_IMAGE_BYTES.toLong())
                file
            }
            val searchQuery = turn.optString("searchQuery").takeIf { it.isNotBlank() }
            history += AiMessage("user", query, image?.absolutePath)
            val row = JSONObject().put("query", query).put("turnIndex", turnIndex)
                .put("hasImage", image != null).put("imageBytes", image?.length() ?: 0)
                .put("imageSha256", image?.let(::sha) ?: JSONObject.NULL)
                .put("searchRequested", searchQuery != null)
            responses.put(row)
            val chunks = mutableListOf<String>()
            val started = System.nanoTime()
            var firstMs: Long? = null
            try {
                val onDelta: suspend (String) -> Unit = { delta ->
                    if (firstMs == null) firstMs = (System.nanoTime() - started) / 1_000_000L
                    chunks += delta
                }
                val response = if (searchQuery == null) provider.stream(settings, token, history, memories, onDelta)
                    else provider.streamWithSearch(settings, token, history, memories, searchQuery, onDelta)
                val parsed = AiCompletion.fromText(response.text)
                row.put("status", "received").put("text", response.text).put("deltas", JSONArray(chunks))
                    .put("exactDeltaText", chunks.joinToString(""))
                    .put("exactDeltasEqualFinal", chunks.joinToString("") == response.text)
                    .put("trimmedDeltasEqualFinal", chunks.joinToString("").trim() == response.text)
                    .put("hasPendingActionProposal", parsed.proposal != null)
                    .put("actionExecuted", false).put("finishReason", response.finishReason).put("truncated", response.truncated)
                    .put("firstTextMsWall", firstMs ?: JSONObject.NULL).put("totalMsWall", (System.nanoTime() - started) / 1_000_000L)
                    .put("providerFirstTokenMs", response.firstTokenMs ?: JSONObject.NULL).put("providerTotalMs", response.totalMs)
                    .put("inputTokensReported", response.inputTokens ?: JSONObject.NULL).put("outputTokensReported", response.outputTokens ?: JSONObject.NULL)
                    .put("sources", JSONArray(response.sources.map { source -> JSONObject().put("title", source.title).put("url", source.url).put("snippet", source.snippet) }))
                history += AiMessage("assistant", parsed.text)
                completedTurns++
            } catch (failure: Exception) {
                // Exceptions may hold request metadata. Persist safe app messages,
                // never a stack trace, headers, auth token or upstream error body.
                failures++
                val safeMessage = if (failure is br.com.nexus.assistant.data.AiException) failure.message else "Falha técnica no avaliador host."
                row.put("status", "technical_failure").put("errorClass", failure.javaClass.simpleName).put("message", safeMessage)
                    .put("deltas", JSONArray(chunks)).put("totalMsWall", (System.nanoTime() - started) / 1_000_000L)
                save()
                break // Never continue a conversation using an invented assistant answer.
            }
            save()
        }
    }
    report.put("completedTurns", completedTurns).put("technicalFailures", failures)
    save()
    println("Saved private host report: $output; turns=$completedTurns; technicalFailures=$failures; semanticApproval=not_assessed")
    check(failures == 0) { "Remote evaluation had technical failures; inspect the private report" }
}
