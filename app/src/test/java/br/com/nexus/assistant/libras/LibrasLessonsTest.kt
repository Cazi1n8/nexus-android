package br.com.nexus.assistant.libras

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.junit.Assert.*
import org.junit.Test

class LibrasLessonsTest {
    @Test fun invalidOrWrongConceptAnswersNeverCountAsCorrect() {
        LibrasLessons.entries.forEach { lesson ->
            assertFalse(lesson.acceptsAnswer(-1))
            assertFalse(lesson.acceptsAnswer(lesson.answers.size))
            assertFalse(lesson.acceptsAnswer(Int.MAX_VALUE))
            assertTrue(lesson.acceptsAnswer(lesson.correctAnswer))
            lesson.answers.indices.filter { it != lesson.correctAnswer }.forEach {
                assertFalse(lesson.acceptsAnswer(it))
            }
        }
    }

    @Test fun unknownStoredNamesDoNotCompleteLessonsOrSkipAReview() {
        val unknown = setOf("all_signs_mastered", "LANGUAGE ", "../PARAMETERS")
        assertTrue(LibrasLessons.validStudied(unknown).isEmpty())
        assertEquals(LibrasLessonId.LANGUAGE, LibrasLessons.nextToStudy(unknown)?.id)
        val study = unknown + LibrasLessonId.LANGUAGE.name
        assertEquals(setOf("LANGUAGE"), LibrasLessons.validStudied(study))
        assertEquals(LibrasLessonId.PARAMETERS, LibrasLessons.nextToStudy(study)?.id)
        assertNull(LibrasLessons.nextToStudy(LibrasLessonId.entries.map { it.name }.toSet()))
    }

    @Test fun addingLessonsPreservesThePreviousCameraProgress() {
        val old = mutablePreferencesOf(
            stringSetPreferencesKey("completed") to setOf("EXTENDED", "FLEXED"),
            intPreferencesKey("sessions") to 8,
            intPreferencesKey("completions") to 5,
            longPreferencesKey("last_completed_at") to 123_456L,
        )
        val decoded = decodeLibrasProgress(old)
        assertEquals(setOf("EXTENDED", "FLEXED"), decoded.completed)
        assertEquals(8, decoded.sessions)
        assertEquals(5, decoded.completions)
        assertEquals(123_456L, decoded.lastCompletedAt)
        assertTrue(decoded.studiedLessons.isEmpty())
        old[stringSetPreferencesKey("studied_lessons")] = setOf("LANGUAGE", "invalid")
        val withLesson = decodeLibrasProgress(old)
        assertEquals(decoded.copy(studiedLessons = setOf("LANGUAGE")), withLesson)
    }

    @Test fun missingAllProgressRemainsAnUnstudiedUnpracticedState() {
        assertEquals(LibrasProgress(), decodeLibrasProgress(emptyPreferences()))
    }
}
