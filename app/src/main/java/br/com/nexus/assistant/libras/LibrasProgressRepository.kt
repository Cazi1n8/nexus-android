package br.com.nexus.assistant.libras

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import java.io.IOException

private val Context.librasDataStore by preferencesDataStore(name = "nexus_libras_preparation")

data class LibrasProgress(
    val completed: Set<String> = emptySet(),
    val sessions: Int = 0,
    val completions: Int = 0,
    val lastCompletedAt: Long = 0L
)

/** Stores exercise counters only; no camera frames, voice recordings or biometric identity. */
class LibrasProgressRepository(context: Context) {
    private val store = context.applicationContext.librasDataStore
    private val completedKey = stringSetPreferencesKey("completed")
    private val sessionsKey = intPreferencesKey("sessions")
    private val completionsKey = intPreferencesKey("completions")
    private val lastAtKey = longPreferencesKey("last_completed_at")

    val progress = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { preferences -> LibrasProgress(
        completed = preferences[completedKey].orEmpty(), sessions = preferences[sessionsKey] ?: 0,
        completions = preferences[completionsKey] ?: 0, lastCompletedAt = preferences[lastAtKey] ?: 0L
    ) }

    suspend fun recordSession() { store.edit { it[sessionsKey] = ((it[sessionsKey] ?: 0).coerceAtMost(Int.MAX_VALUE - 1)) + 1 } }

    suspend fun recordCompletion(exercise: PreparationExercise) {
        store.edit { values ->
            values[completedKey] = values[completedKey].orEmpty() + exercise.name
            values[completionsKey] = (values[completionsKey] ?: 0).coerceAtMost(Int.MAX_VALUE - 1) + 1
            values[lastAtKey] = System.currentTimeMillis()
        }
    }

    suspend fun clear() { store.edit { it.clear() } }
}
