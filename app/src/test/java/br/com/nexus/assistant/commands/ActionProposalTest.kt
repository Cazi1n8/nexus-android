package br.com.nexus.assistant.commands

import br.com.nexus.assistant.data.AiCompletion
import br.com.nexus.assistant.data.AiException
import br.com.nexus.assistant.data.OpenAiCompatibleProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ActionProposalTest {
    @Test fun proseNeverBecomesAction() {
        val result = AiCompletion.fromText("Vou abrir as configurações agora. settings")
        assertNull(result.proposal)
    }
    @Test fun knownStructuredActionIsOnlyAProposal() {
        val result = AiCompletion.fromText("""{"nexus_action":{"name":"open_settings","arguments":{}},"message":"Posso abrir?"}""")
        assertEquals("settings", result.proposal?.action)
        assertEquals("Posso abrir?", result.text)
        assertFalse(result.proposal?.id.isNullOrBlank())
    }
    @Test fun unsupportedActionsAndExtraArgumentsAreRejected() {
        for ((name, args) in listOf("shell" to "{}", "open_settings" to "{\"command\":\"rm\"}",
            "open_app" to "{\"packageName\":\"https://example.com\"}", "navigate" to "{\"route\":\"secret\"}")) {
            try { ActionProposal.parse(name, JSONObject(args)); fail(name) } catch (_: AiException) { }
        }
    }
    @Test fun customToolResponseUsesSameValidation() {
        val raw = """{"choices":[{"message":{"content":null,"tool_calls":[{"type":"function","function":{"name":"open_app","arguments":"{\"packageName\":\"com.whatsapp\"}"}}]}}]}"""
        val proposal = AiCompletion.fromText(OpenAiCompatibleProvider.parseAnswer(raw)).proposal
        assertEquals("app:com.whatsapp", proposal?.action)
    }
    @Test fun multipleToolCallsCannotRunInOneConfirmation() {
        val function = """{"type":"function","function":{"name":"open_settings","arguments":"{}"}}"""
        try {
            OpenAiCompatibleProvider.parseAnswer("""{"choices":[{"message":{"tool_calls":[$function,$function]}}]}""")
            fail("must reject multiple proposals")
        } catch (_: AiException) { }
    }
}
