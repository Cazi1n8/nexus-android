package br.com.nexus.assistant

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.nexus.assistant.data.NexusDatabase
import br.com.nexus.assistant.data.NexusRepository
import br.com.nexus.assistant.data.NexusSettings
import br.com.nexus.assistant.data.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room/ViewModel checks; cancellation happens before any network request. */
@RunWith(AndroidJUnit4::class)
class ConversationCancellationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var store: ViewModelStore
    private lateinit var model: NexusViewModel
    private lateinit var repository: NexusRepository

    @Before fun initialize() {
        val application = instrumentation.targetContext.applicationContext as Application
        repository = NexusRepository(NexusDatabase.getInstance(application))
        runBlocking {
            repository.clearConversations()
            repository.clearMemories()
            SettingsRepository(application).save(NexusSettings(
                endpoint = "https://nexus-instrumentation.invalid/v1", model = "test-model", remoteConsent = true,
            ))
        }
        instrumentation.runOnMainSync {
            store = ViewModelStore()
            model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(application))[NexusViewModel::class.java]
        }
        eventually { model.settings.value.model == "test-model" }
    }

    @After fun cleanUp() {
        instrumentation.runOnMainSync { store.clear() }
        runBlocking {
            repository.clearConversations()
            repository.clearMemories()
            SettingsRepository(instrumentation.targetContext).save(NexusSettings())
        }
    }

    @Test fun interruptingTheFirstAnswerKeepsItsMessageAndNextSendInTheSameConversation() {
        instrumentation.runOnMainSync {
            assertTrue(model.send("Primeira pergunta"))
            model.cancelResponse()
        }
        eventually { !model.busy.value && model.selected.value != null }
        val conversation = requireNotNull(model.selected.value)
        instrumentation.runOnMainSync {
            assertTrue(model.send("Segunda pergunta"))
            model.cancelResponse()
        }
        eventually { !model.busy.value }
        assertEquals(conversation, model.selected.value)
        runBlocking {
            assertEquals(listOf("Primeira pergunta", "Segunda pergunta"), repository.conversationMessages(conversation).map { it.content })
            assertEquals(1, NexusDatabase.getInstance(instrumentation.targetContext).dao().allConversations().size)
            assertTrue(repository.conversationMessages(conversation).all { it.role == "user" })
        }
    }

    @Test fun deletingDataWaitsForAnExistingMemorySaveAndRejectsEditsDuringDeletion() {
        lateinit var deletion: kotlinx.coroutines.Job
        instrumentation.runOnMainSync {
            model.saveMemory("Informação anterior à exclusão")
            deletion = model.deleteAllData()
            model.saveMemory("Esta edição não deve ressurgir após excluir")
        }
        runBlocking { deletion.join() }
        runBlocking {
            assertTrue(NexusDatabase.getInstance(instrumentation.targetContext).dao().allMemories().isEmpty())
            assertTrue(NexusDatabase.getInstance(instrumentation.targetContext).dao().allConversations().isEmpty())
        }
        eventually { model.settings.value.endpoint.isBlank() }
    }

    private fun eventually(condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 10_000
        while (!condition() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(10)
        assertTrue("O estado esperado não foi atingido dentro de 10 segundos.", condition())
    }
}
