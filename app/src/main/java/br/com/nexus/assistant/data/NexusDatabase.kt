package br.com.nexus.assistant.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("conversationId")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    val imagePath: String? = null,
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val content: String,
    val createdAt: Long,
)

@Dao
interface NexusDao {
    @Query("SELECT * FROM conversations ORDER BY createdAt DESC, id DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, rowid ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query("""
        SELECT c.* FROM conversations AS c
        WHERE c.title LIKE :pattern ESCAPE '\'
           OR EXISTS (SELECT 1 FROM messages AS m
                      WHERE m.conversationId = c.id AND m.content LIKE :pattern ESCAPE '\')
        ORDER BY c.createdAt DESC, c.id DESC
    """)
    fun searchConversations(pattern: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM memories ORDER BY createdAt DESC, id DESC")
    fun observeMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, rowid ASC")
    suspend fun messages(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM conversations ORDER BY createdAt ASC, id ASC")
    suspend fun allConversations(): List<ConversationEntity>

    @Query("SELECT * FROM messages ORDER BY createdAt ASC, rowid ASC")
    suspend fun allMessages(): List<MessageEntity>

    @Query("SELECT * FROM memories ORDER BY createdAt ASC, id ASC")
    suspend fun allMemories(): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemory(memory: MemoryEntity)

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun renameConversation(id: String, title: String)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("DELETE FROM conversations")
    suspend fun clearConversations()

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemory(id: String)

    @Query("DELETE FROM memories")
    suspend fun clearMemories()

    @Transaction
    suspend fun snapshot(): DataSnapshot = DataSnapshot(allConversations(), allMessages(), allMemories())
}

data class DataSnapshot(
    val conversations: List<ConversationEntity>,
    val messages: List<MessageEntity>,
    val memories: List<MemoryEntity>,
)

@Database(entities = [ConversationEntity::class, MessageEntity::class, MemoryEntity::class], version = 1, exportSchema = true)
abstract class NexusDatabase : RoomDatabase() {
    abstract fun dao(): NexusDao

    companion object {
        @Volatile private var instance: NexusDatabase? = null

        fun getInstance(context: Context): NexusDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, NexusDatabase::class.java, "nexus.db")
                .build().also { instance = it }
        }
    }
}
