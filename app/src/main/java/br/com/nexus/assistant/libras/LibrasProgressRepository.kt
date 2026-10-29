package br.com.nexus.assistant.libras

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.Preferences
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
    val lastCompletedAt: Long = 0L,
    /** Read/quiz progress, not a recognized sign or measured language proficiency. */
    val studiedLessons: Set<String> = emptySet(),
)

private object LibrasProgressKeys {
    val completed = stringSetPreferencesKey("completed")
    val sessions = intPreferencesKey("sessions")
    val completions = intPreferencesKey("completions")
    val lastAt = longPreferencesKey("last_completed_at")
    val studiedLessons = stringSetPreferencesKey("studied_lessons")
}

/** Existing exercise counters survive adding the independent lesson-reading field. */
internal fun decodeLibrasProgress(preferences: Preferences): LibrasProgress = LibrasProgress(
    completed = preferences[LibrasProgressKeys.completed].orEmpty(),
    sessions = preferences[LibrasProgressKeys.sessions] ?: 0,
    completions = preferences[LibrasProgressKeys.completions] ?: 0,
    lastCompletedAt = preferences[LibrasProgressKeys.lastAt] ?: 0L,
    studiedLessons = LibrasLessons.validStudied(preferences[LibrasProgressKeys.studiedLessons].orEmpty()),
)

/** Stores exercise counters and lesson IDs; no frames, recordings or biometric identity. */
class LibrasProgressRepository(context: Context) {
    private val store = context.applicationContext.librasDataStore
    private val completedKey = LibrasProgressKeys.completed
    private val sessionsKey = LibrasProgressKeys.sessions
    private val completionsKey = LibrasProgressKeys.completions
    private val lastAtKey = LibrasProgressKeys.lastAt
    private val studiedLessonsKey = LibrasProgressKeys.studiedLessons

    val progress = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map(::decodeLibrasProgress)

    suspend fun recordSession() { store.edit { it[sessionsKey] = ((it[sessionsKey] ?: 0).coerceAtMost(Int.MAX_VALUE - 1)) + 1 } }

    suspend fun recordCompletion(exercise: PreparationExercise) {
        store.edit { values ->
            values[completedKey] = values[completedKey].orEmpty() + exercise.name
            values[completionsKey] = (values[completionsKey] ?: 0).coerceAtMost(Int.MAX_VALUE - 1) + 1
            values[lastAtKey] = System.currentTimeMillis()
        }
    }

    suspend fun recordLessonStudied(lesson: LibrasLessonId) {
        store.edit { values -> values[studiedLessonsKey] = LibrasLessons.validStudied(values[studiedLessonsKey].orEmpty()) + lesson.name }
    }

    suspend fun clear() { store.edit { it.clear() } }
}
