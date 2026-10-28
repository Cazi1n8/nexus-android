package br.com.nexus.assistant

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.nexus.assistant.data.AiMode
import br.com.nexus.assistant.data.NexusDatabase
import br.com.nexus.assistant.data.NexusRepository
import br.com.nexus.assistant.data.NexusSettings
import br.com.nexus.assistant.data.SettingsRepository
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in on a disposable CORE emulator; does not grant permissions or contact a server. */
@RunWith(AndroidJUnit4::class)
class CoreArithmeticToolsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var store: ViewModelStore? = null
    private lateinit var model: NexusViewModel
    private lateinit var repository: NexusRepository
    private lateinit var settings: SettingsRepository
    private var previousSettings: NexusSettings? = null
    private val createdConversations = mutableSetOf<String>()

    @Before fun initialize() {
        assumeTrue("Opt in on a fresh CORE emulator with nexusCoreSmoke=true", BuildConfig.CORE_INSTALLATION &&
            InstrumentationRegistry.getArguments().getString("nexusCoreSmoke") == "true")
        val application = instrumentation.targetContext.applicationContext as Application
        assumeTrue("This checks operation without any downloaded LLM", File(application.filesDir, "models").listFiles()
            .orEmpty().none { it.extension == "gguf" })
        repository = NexusRepository(NexusDatabase.getInstance(application))
        settings = SettingsRepository(application)
        runBlocking {
            previousSettings = settings.settings.first()
            settings.save(NexusSettings(aiMode = AiMode.NEXUS_LOCAL, userPreferredName = "Calebe"))
        }
        instrumentation.runOnMainSync {
            store = ViewModelStore()
            model = ViewModelProvider(requireNotNull(store), ViewModelProvider.AndroidViewModelFactory.getInstance(application))[NexusViewModel::class.java]
        }
        eventually { model.settings.value.userPreferredName == "Calebe" }
    }

    @After fun cleanUp() {
        instrumentation.runOnMainSync { store?.clear() }
        previousSettings?.let { previous -> runBlocking {
            createdConversations.forEach { repository.deleteConversation(it) }
            settings.save(previous)
        } }
    }

    @Test fun calculatorSavesExactResponseInRoomWithoutModelOrRemoteConfiguration() {
        instrumentation.runOnMainSync { assertTrue(model.send("Quanto é 50 + 50 + 50 × 50?")) }
        eventually { !model.busy.value && model.selected.value != null }
        val conversation = requireNotNull(model.selected.value).also(createdConversations::add)
        val messages = runBlocking { repository.conversationMessages(conversation) }
        assertEquals(listOf("user", "assistant"), messages.map { it.role })
        assertTrue(messages.last().content.endsWith(" = 2600"))
        assertFalse(model.localModelDownloadActive.value)
        assertNull(model.pendingAction.value)
        assertEquals("NEXUS", model.settings.value.assistantName)
        assertEquals("Calebe", model.settings.value.userPreferredName)
    }

    @Test fun localActionIsOnlyProposedAndGeneralConversationStillRequiresRealModel() {
        instrumentation.runOnMainSync { assertTrue(model.send("NEXUS, abra as configurações")) }
        eventually { !model.busy.value && model.selected.value != null }
        val conversation = requireNotNull(model.selected.value).also(createdConversations::add)
        val messages = runBlocking { repository.conversationMessages(conversation) }
        assertTrue(messages.last().content.contains("A ação ainda não foi executada"))
        assertNotNull(model.pendingAction.value)
        assertFalse(model.localModelDownloadActive.value)
        instrumentation.runOnMainSync {
            model.newConversation()
            assertFalse("General conversation must never pretend to work without a model", model.send("Crie uma história de um robô"))
        }
        assertNull(model.selected.value)
    }

    private fun eventually(condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 10_000
        while (!condition() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(10)
        assertTrue("Expected CORE tool state was not reached in 10 seconds", condition())
    }
}
