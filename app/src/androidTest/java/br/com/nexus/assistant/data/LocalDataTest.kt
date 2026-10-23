package br.com.nexus.assistant.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Requires an emulator/device; not part of testDebugUnitTest. */
@RunWith(AndroidJUnit4::class)
class LocalDataTest {
    private lateinit var database: NexusDatabase
    private lateinit var repository: NexusRepository

    @Before fun createDatabase() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, NexusDatabase::class.java).build()
        repository = NexusRepository(database)
    }
    @After fun closeDatabase() { database.close() }

    @Test fun deletingConversationRemovesMessagesButPreservesAuthorizedMemories() = runBlocking {
        val id = repository.createConversation("Exercício")
        repository.addMessage(id, "user", "Quanto é 2 + 2?")
        repository.saveMemory("Prefiro explicações curtas.")
        assertEquals(1, repository.conversationMessages(id).size)
        repository.deleteConversation(id)
        assertTrue(repository.conversationMessages(id).isEmpty())
        assertTrue(database.dao().allConversations().isEmpty())
        assertEquals(1, database.dao().allMemories().size)
    }

    @Test fun exportContainsConversationAndMemoriesButOmitsPrivateImagePaths() = runBlocking {
        val id = repository.createConversation("Imagem")
        repository.addMessage(id, "user", "Descreva a imagem", "/private/files/foto.jpg")
        repository.saveMemory("Aprendo programação.", "memory-1")
        val exported = repository.exportJson()
        val json = JSONObject(exported)
        assertEquals(1, json.getJSONArray("conversations").length())
        assertEquals(1, json.getJSONArray("messages").length())
        assertEquals(1, json.getJSONArray("memories").length())
        assertTrue(json.getJSONArray("messages").getJSONObject(0).getBoolean("hasImage"))
        assertFalse(exported.contains("/private/files"))
        assertFalse(exported.contains("sessionToken"))
    }
}
