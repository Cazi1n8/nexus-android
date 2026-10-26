package br.com.nexus.assistant.data

import org.junit.Assert.*
import org.junit.Test

class ConversationSearchPatternTest {
    @Test fun userWildcardsAreLiteralAndCannotExpandTheSearch() {
        assertEquals("%100\\%\\_\\\\%", conversationSearchPattern("100%_\\"))
        assertEquals("%Max%", conversationSearchPattern("  Max  "))
    }

    @Test fun inputIsBoundedWithoutSqlConcatenation() {
        assertEquals(202, conversationSearchPattern("a".repeat(1000)).length)
        assertEquals("%O'Brien%", conversationSearchPattern("O'Brien"))
    }
}
