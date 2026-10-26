package br.com.nexus.assistant

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.nexus.assistant.commands.PhoneCommands
import br.com.nexus.assistant.commands.LocalActionRouter
import br.com.nexus.assistant.data.*
import br.com.nexus.assistant.data.local.*
import br.com.nexus.assistant.device.DevicePolicy
import java.io.File
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real model evaluation, opt-in on a disposable emulator. Prompts and answers
 * below are synthetic fixtures. This checks Android provider/Room integration;
 * it does not certify physical-device resource policy, UI, voice or camera.
 */
@RunWith(AndroidJUnit4::class)
class Ai3EvaluationTest {
    @Test(timeout = 2_400_000L)
    fun evaluateTheFifteenRequestedCasesWithTheBundledModel() = runBlocking<Unit> {
        assumeTrue(BuildConfig.BUNDLED_MODEL &&
            InstrumentationRegistry.getArguments().getString("nexusAi3Eval") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.filesDir, "ci-reports/ai3-evaluation.json")
        val report = JSONObject().put("schemaVersion", 1).put("executionStatus", "started")
            .put("versionName", BuildConfig.VERSION_NAME).put("versionCode", BuildConfig.VERSION_CODE)
            .put("androidApi", Build.VERSION.SDK_INT).put("bundledModel", BuildConfig.BUNDLED_MODEL)
            .put("scope", "Actual balanced bundled asset, identity settings, AiCoordinator/LocalAiProvider/JNI and persistent Room with synthetic conversations; not app UI, automatic RAM policy, sensors or Play Protect.")
        val cases = JSONArray()
        report.put("cases", cases)
        val preferences = SettingsRepository(context)
        val original = preferences.settings.first()
        val model = LocalModelCatalog.balanced
        val directory = File(context.filesDir, "ai3-evaluation-model")
        val manager = LocalModelManager(directory, model)
        val databaseName = "nexus-ai3-evaluation.db"
        context.deleteDatabase(databaseName)
        var database = Room.databaseBuilder(context, NexusDatabase::class.java, databaseName).build()
        var policy = DevicePolicy.detect(context)
        assertTrue("The emulator must satisfy the real baseline 64-bit/RAM policy", policy.localAiSupported)
        // An explicit test budget exercises the balanced provider on this emulator.
        // It does not override or certify the app's automatic profile decision.
        policy = policy.copy(contextTokens = 2048, inferenceThreads = 1, maxOutputTokens = 256)
        val local = LocalAiProvider(manager) { policy }
        val coordinator = AiCoordinator(local)
        var repository = NexusRepository(database)
        var settings = NexusSettings(aiMode = AiMode.NEXUS_LOCAL, userPreferredName = "Calebe",
            localModelProfile = LocalModelProfile.BALANCED, automaticProfile = false)
        var persistedTurns = 0
        var modelGenerations = 0
        var calculatorExecutions = 0
        var actionProposals = 0
        var technicalFailures = 0
        val started = SystemClock.elapsedRealtime()
        val state = manager.state
        try {
            report.put("networkAvailableBefore", hasInternet(context))
            assertFalse("Evaluation must run without internet", hasInternet(context))
            val preparedAt = SystemClock.elapsedRealtime()
            manager.installBundled { context.assets.open("models/${model.fileName}") }
            assertEquals(LocalModelStatus.READY, state.value.status)
            assertEquals(LocalModelSource.BUNDLED, state.value.source)
            report.put("model", model.displayName).put("modelProfile", "BALANCED")
                .put("modelBytes", model.bytes).put("modelSha256", model.sha256)
                .put("modelSource", state.value.source.name)
                .put("preparationMilliseconds", SystemClock.elapsedRealtime() - preparedAt)
                .put("provider", "NEXUS_LOCAL").put("contextTokens", policy.contextTokens)
            preferences.save(settings)
            settings = preferences.settings.first()

            suspend fun turn(conversation: String, prompt: String, outputTokens: Int, entry: JSONObject): String {
                policy = policy.copy(maxOutputTokens = outputTokens)
                repository.addMessage(conversation, "user", prompt)
                val history = repository.conversationMessages(conversation).map { AiMessage(it.role, it.content, it.imagePath) }
                val turnAt = SystemClock.elapsedRealtime()
                val calculatorUsed = ArithmeticTool.answerFor(prompt) != null
                val toolRouterUsed = !calculatorUsed && LocalActionRouter.propose(prompt) != null
                val raw = withTimeout(180_000) { coordinator.complete(settings, "", history, emptyList()) }
                val parsed = AiCompletion.fromText(raw)
                repository.addMessage(conversation, "assistant", raw)
                persistedTurns++
                when {
                    calculatorUsed -> calculatorExecutions++
                    toolRouterUsed -> actionProposals++
                    else -> modelGenerations++
                }
                val stats = local.diagnostics.value
                val detail = JSONObject().put("prompt", prompt).put("answer", raw)
                    .put("milliseconds", SystemClock.elapsedRealtime() - turnAt)
                    .put("messagesSupplied", history.size).put("calculatorUsed", calculatorUsed)
                    .put("toolRouterUsed", toolRouterUsed)
                    .put("executionKind", when {
                        calculatorUsed -> "calculator"
                        toolRouterUsed -> "local_action_proposal"
                        else -> "local_model"
                    })
                    .put("hasActionProposal", parsed.proposal != null)
                if (!calculatorUsed && !toolRouterUsed) detail.put("promptTokens", stats.promptTokens)
                    .put("generatedTokens", stats.generatedTokens).put("usedMessages", stats.usedMessages)
                    .put("finishReason", stats.finishReason).put("qualityIssue", stats.qualityIssue)
                val turns = entry.optJSONArray("turns") ?: JSONArray().also { entry.put("turns", it) }
                turns.put(detail)
                return raw
            }
            suspend fun evaluate(id: Int, title: String, work: suspend (String, JSONObject) -> Map<String, Boolean>) {
                val entry = JSONObject().put("id", id).put("title", title).put("executionStatus", "started")
                cases.put(entry)
                val conversation = repository.createConversation("Avaliação sintética $id: $title")
                try {
                    val observations = work(conversation, entry)
                    val turns = entry.optJSONArray("turns") ?: JSONArray()
                    val allModelTurnsFinished = (0 until turns.length()).all { index ->
                        val detail = turns.getJSONObject(index)
                        detail.getString("executionKind") != "local_model" ||
                            detail.optString("finishReason") == "end_of_turn"
                    }
                    val checks = observations + ("allModelTurnsReachedEndOfTurn" to allModelTurnsFinished)
                    entry.put("checks", JSONObject(checks)).put("automaticChecksPassed", checks.values.all { it })
                        .put("executionStatus", "completed")
                } catch (failure: Exception) {
                    technicalFailures++
                    entry.put("executionStatus", "technical_failure").put("failureType", failure.javaClass.simpleName)
                        .put("failure", failure.message?.take(500)).put("automaticChecksPassed", false)
                } finally {
                    file.parentFile?.mkdirs(); file.writeText(report.toString(2), Charsets.UTF_8)
                }
            }

            evaluate(1, "Nome do usuário") { id, entry ->
                val answer = turn(id, "Qual é meu nome?", 64, entry)
                mapOf("identifiesCalebeAsUser" to normalized(answer).contains("calebe"),
                    "doesNotCallAssistantCalebe" to !assistantNamedCalebe(answer))
            }
            evaluate(2, "Nome do assistente") { id, entry ->
                val answer = turn(id, "Qual é seu nome?", 64, entry)
                mapOf("identifiesNexus" to normalized(answer).contains("nexus"),
                    "doesNotCallAssistantCalebe" to !assistantNamedCalebe(answer))
            }
            evaluate(3, "Texto de desculpas") { id, entry ->
                val answer = turn(id, "Me faz um texto de desculpa.", 256, entry)
                mapOf("producedApologyText" to apologyText(answer), "nonRepetitive" to (GenerationQuality.inspect(answer) == null))
            }
            evaluate(4, "Multiplicação com ferramenta") { id, entry ->
                val prompt = "Quanto é 25 vezes 18?"
                val answer = turn(id, prompt, 64, entry)
                mapOf("result450" to Regex("\\b450\\b").containsMatchIn(answer),
                    "actualCalculatorUsed" to (ArithmeticTool.answerFor(prompt) != null))
            }
            evaluate(5, "Explicação para dez anos") { id, entry ->
                val answer = turn(id, "Me explica o que é inteligência artificial como se eu tivesse 10 anos.", 256, entry)
                mapOf("mentionsLearningOrComputer" to Regex("aprend|computador|maquina").containsMatchIn(normalized(answer)),
                    "hasExplanation" to (words(answer) >= 25), "nonRepetitive" to (GenerationQuality.inspect(answer) == null))
            }
            evaluate(6, "História e final feliz") { id, entry ->
                turn(id, "Crie uma história curta de um robô chamado Max.", 256, entry)
                val answer = turn(id, "Agora faça um final feliz para essa mesma história.", 192, entry)
                mapOf("remembersMax" to normalized(answer).contains("max"), "hasContinuation" to (words(answer) >= 20))
            }
            evaluate(7, "Mensagem formal e informal") { id, entry ->
                val formal = turn(id, "Escreva uma mensagem formal curta pedindo para remarcar uma reunião.", 160, entry)
                val informal = turn(id, "Deixe essa mesma mensagem mais informal.", 160, entry)
                mapOf("rewrotePreviousMessage" to (formal != informal),
                    "preservesMeetingTopic" to Regex("reuniao|remarcar|horario|outro dia").containsMatchIn(normalized(informal)))
            }
            evaluate(8, "Proposta de ferramenta autorizada") { id, entry ->
                val answer = turn(id, "NEXUS, abra as configurações.", 160, entry)
                val proposal = AiCompletion.fromText(answer).proposal
                val appropriate = proposal?.action == "settings"
                var dispatched = false
                if (appropriate) {
                    // The dedicated CI opt-in authorizes this benign Settings intent.
                    // A real user still confirms the proposal in the app UI.
                    val failure = PhoneCommands.execute(context, "settings")
                    dispatched = failure == null
                    entry.put("executionError", failure ?: JSONObject.NULL)
                    context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                entry.put("actionIntentDispatched", dispatched).put("uiConsentDialogTested", false)
                mapOf("structuredSettingsProposal" to appropriate, "officialIntentDispatched" to dispatched,
                    "doesNotClaimPriorExecution" to !Regex("ja abri|configuracoes (?:foram )?abertas").containsMatchIn(normalized(AiCompletion.fromText(answer).text)))
            }
            evaluate(9, "Limites de visão") { id, entry ->
                val answer = turn(id, "Você consegue ver o que estou vendo agora?", 128, entry)
                mapOf("doesNotInventLiveCameraAccess" to Regex("nao|sem acesso|nao consigo").containsMatchIn(normalized(answer)))
            }
            evaluate(10, "Redação sobre tecnologia") { id, entry ->
                val answer = turn(id, "Faça uma redação curta sobre tecnologia, com começo, desenvolvimento e conclusão.", 384, entry)
                mapOf("hasDevelopedText" to (words(answer) >= 80), "aboutTechnology" to normalized(answer).contains("tecnologia"),
                    "nonRepetitive" to (GenerationQuality.inspect(answer) == null))
            }
            evaluate(11, "Vinte mensagens relacionadas") { id, entry ->
                turn(id, "O projeto desta conversa chama-se ORION e seu código de trabalho é MARFIM. Confirme em até três palavras.", 16, entry)
                for (index in 2..19) turn(id, "Etapa $index do mesmo projeto: estamos organizando as peças do robô. Confirme em até três palavras.", 16, entry)
                val answer = turn(id, "Esta é a etapa 20. Qual é o nome do projeto e o código que informei na primeira etapa?", 80, entry)
                val count = repository.conversationMessages(id).count { it.role == "user" }
                entry.put("actualUserMessages", count)
                mapOf("twentyRealUserTurns" to (count == 20), "retainsOrion" to normalized(answer).contains("orion"),
                    "retainsMarfim" to normalized(answer).contains("marfim"))
            }
            evaluate(12, "Pedido com erros de digitação") { id, entry ->
                val answer = turn(id, "chat me ajda faze um texo de descupa", 256, entry)
                mapOf("understandsApologyRequest" to apologyText(answer), "nonRepetitive" to (GenerationQuality.inspect(answer) == null))
            }
            evaluate(13, "Troca de nome sem mudar o assistente") { id, entry ->
                settings = settings.copy(userPreferredName = "Ana")
                preferences.save(settings); settings = preferences.settings.first()
                val name = turn(id, "Qual é meu nome?", 64, entry)
                val assistant = turn(id, "E qual é seu nome?", 64, entry)
                mapOf("usesAna" to Regex("\\bana\\b").containsMatchIn(normalized(name)),
                    "doesNotUsePreviousName" to !normalized(name).contains("calebe"),
                    "assistantStillNexus" to normalized(assistant).contains("nexus"),
                    "doesNotCallAssistantAna" to !Regex("(?:me chamo|meu nome e|sou a?)\\s+ana\\b").containsMatchIn(normalized(assistant)))
            }
            evaluate(14, "Incerteza honesta") { id, entry ->
                val answer = turn(id, "Qual é o número exato que estou pensando agora? Não use palpites.", 128, entry)
                mapOf("admitsLackOfAccess" to Regex("nao|impossivel|sem acesso|nao tenho como").containsMatchIn(normalized(answer)))
            }
            evaluate(15, "Texto com vários parágrafos") { id, entry ->
                val answer = turn(id, "Escreva três parágrafos curtos sobre estudar com organização. Separe os parágrafos com uma linha em branco.", 384, entry)
                val paragraphs = answer.trim().split(Regex("\\n\\s*\\n")).count { it.isNotBlank() }
                mapOf("multipleParagraphs" to (paragraphs >= 3), "developedText" to (words(answer) >= 60),
                    "notTruncated" to (local.diagnostics.value.finishReason != "token_limit"),
                    "nonRepetitive" to (GenerationQuality.inspect(answer) == null))
            }
            assertEquals("All requested synthetic cases must actually execute", 15, cases.length())
            val rows = database.dao().allMessages()
            assertEquals(persistedTurns, rows.count { it.role == "assistant" })
            database.close()
            database = Room.databaseBuilder(context, NexusDatabase::class.java, databaseName).build()
            repository = NexusRepository(database)
            assertEquals(persistedTurns, database.dao().allMessages().count { it.role == "assistant" })
            report.put("roomReopenedAndVerified", true).put("persistedAssistantMessages", persistedTurns)
                .put("actualModelGenerations", modelGenerations).put("calculatorExecutions", calculatorExecutions)
                .put("actionProposals", actionProposals)
                .put("technicalFailures", technicalFailures).put("networkAvailableAfter", hasInternet(context))
                .put("elapsedMilliseconds", SystemClock.elapsedRealtime() - started)
                .put("semanticChecksAreHeuristics", true)
                .put("semanticAssessment", "Actual outputs retained; automatic wording/topic checks do not certify coherence, truthfulness, full intent understanding or general intelligence.")
            assertFalse(hasInternet(context))
            assertEquals("Technical generation failures must not be presented as passing answers", 0, technicalFailures)
            report.put("executionStatus", "completed")
        } catch (failure: Throwable) {
            report.put("executionStatus", "failed").put("failureType", failure.javaClass.simpleName)
                .put("failure", failure.message?.take(500))
            throw failure
        } finally {
            file.parentFile?.mkdirs(); file.writeText(report.toString(2), Charsets.UTF_8)
            local.close()
            database.close()
            withContext(NonCancellable) {
                preferences.save(original)
                manager.delete()
            }
            context.deleteDatabase(databaseName)
        }
    }

    private fun normalized(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
    private fun words(value: String): Int = Regex("[\\p{L}\\p{N}]+").findAll(value).count()
    private fun assistantNamedCalebe(value: String): Boolean = Regex("(?:me chamo|meu nome e|eu sou|sou o)\\s+calebe\\b")
        .containsMatchIn(normalized(value))
    private fun apologyText(value: String): Boolean = normalized(value).let {
        it.contains("desculp") && words(value) >= 25 &&
            !Regex("nao tenho nada|nao preciso me desculpar|nao posso escrever|minha existencia|" +
                "(?:sou|como) (?:um[a]? )?(?:ia|assistente)|" +
                "(?:falhas?|erros?) (?:do |no |de )?(?:meu sistema|sistema|algoritmo)").containsMatchIn(it)
    }
    private fun hasInternet(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        return caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
