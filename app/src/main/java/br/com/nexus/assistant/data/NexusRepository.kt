package br.com.nexus.assistant.data

import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Only user-created memories enter the AI context. Nothing is inferred or memorized automatically. */
class NexusRepository(database: NexusDatabase, private val chatImagesDirectory: File? = null) {
    private val dao = database.dao()
    val conversations: Flow<List<ConversationEntity>> = dao.observeConversations()
    val memories: Flow<List<MemoryEntity>> = dao.observeMemories()
    fun messages(conversationId: String): Flow<List<MessageEntity>> = dao.observeMessages(conversationId)
    suspend fun conversationMessages(id: String): List<MessageEntity> = dao.messages(id)

    /** Search stays in the local Room database. Wildcards are treated as literal user text. */
    fun searchConversations(query: String): Flow<List<ConversationEntity>> =
        if (query.isBlank()) conversations else dao.searchConversations(conversationSearchPattern(query))


    suspend fun createConversation(title: String): String {
        val id = UUID.randomUUID().toString()
        dao.insertConversation(ConversationEntity(id, title.trim().take(80).ifBlank { "Nova conversa" }, System.currentTimeMillis()))
        return id
    }

    suspend fun addMessage(conversationId: String, role: String, content: String, imagePath: String? = null): MessageEntity {
        require(role in setOf("user", "assistant")) { "Papel de mensagem inválido." }
        require(content.isNotBlank() || imagePath != null) { "Mensagem vazia." }
        require(content.length <= 24_000) { "A mensagem ultrapassa 24.000 caracteres. Divida o texto em partes." }
        val message = MessageEntity(UUID.randomUUID().toString(), conversationId, role, content, System.currentTimeMillis(), imagePath)
        dao.insertMessage(message)
        return message
    }

    suspend fun renameConversation(id: String, title: String) = dao.renameConversation(id, title.trim().take(80).ifBlank { "Conversa" })

    suspend fun deleteConversation(id: String) {
        val attachments = dao.messages(id).mapNotNull { it.imagePath }
        dao.deleteConversation(id)
        deleteAttachments(attachments)
    }

    suspend fun clearConversations() {
        val attachments = dao.allMessages().mapNotNull { it.imagePath }
        dao.clearConversations()
        deleteAttachments(attachments)
    }

    suspend fun saveMemory(content: String, id: String? = null) {
        require(content.isNotBlank()) { "Escreva a informação que deseja guardar." }
        require(content.length <= 2_000) { "Cada memória pode ter até 2.000 caracteres." }
        dao.upsertMemory(MemoryEntity(id ?: UUID.randomUUID().toString(), content.trim(), System.currentTimeMillis()))
    }

    suspend fun deleteMemory(id: String) = dao.deleteMemory(id)
    suspend fun clearMemories() = dao.clearMemories()

    private suspend fun deleteAttachments(paths: List<String>) = withContext(Dispatchers.IO) {
        val root = chatImagesDirectory?.canonicalFile ?: return@withContext
        val stillUsed = dao.allMessages().mapNotNull { it.imagePath }.toSet()
        paths.forEach { path ->
            if (path !in stillUsed) {
                runCatching {
                    val file = File(path).canonicalFile
                    if (file.toPath().startsWith(root.toPath()) && file != root && file.isFile) file.delete()
                }
            }
        }
    }

    /** Plain JSON export is requested explicitly by the user. Credentials and image files are excluded. */
    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val snapshot = dao.snapshot()
        JSONObject()
            .put("formatVersion", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("imagesIncluded", false)
            .put("conversations", JSONArray().apply {
                snapshot.conversations.forEach { conversation ->
                    put(JSONObject().put("id", conversation.id).put("title", conversation.title).put("createdAt", conversation.createdAt))
                }
            })
            .put("messages", JSONArray().apply {
                snapshot.messages.forEach { message ->
                    put(JSONObject().put("id", message.id).put("conversationId", message.conversationId)
                        .put("role", message.role).put("content", message.content).put("createdAt", message.createdAt)
                        .put("hasImage", message.imagePath != null))
                }
            })
            .put("memories", JSONArray().apply {
                snapshot.memories.forEach { memory ->
                    put(JSONObject().put("id", memory.id).put("content", memory.content).put("createdAt", memory.createdAt))
                }
            })
            .toString(2)
    }
}

/** A literal substring pattern, not SQL or a user-controlled LIKE expression. */
internal fun conversationSearchPattern(query: String): String = "%" + query.trim().take(200)
    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
