package br.com.nexus.assistant.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LocalImageTextProviderTest {
    private class CapturingProvider : AiProvider {
        var calls = 0
        var input = emptyList<AiMessage>()
        override suspend fun complete(settings: NexusSettings, token: String, messages: List<AiMessage>, memories: List<String>): String {
            calls++; input = messages; assertEquals("", token); return "Explicação do texto"
        }
    }
    @Test fun `transcription uses actual reader output without fabricated image understanding`() = runBlocking<Unit> {
        val delegate = CapturingProvider()
        val provider = LocalImageTextProvider(delegate) { path -> assertEquals("photo.jpg", path); "Texto real da foto" }
        val answer = provider.complete(NexusSettings(), "never-forward", listOf(AiMessage("user", "leia esse texto", "photo.jpg")), emptyList())
        assertTrue(answer.contains("Texto real da foto")); assertTrue(answer.contains("OCR")); assertEquals(0, delegate.calls)
    }
    @Test fun `question passes bounded extracted text and no image to text model`() = runBlocking<Unit> {
        val delegate = CapturingProvider()
        var reads = 0
        val provider = LocalImageTextProvider(delegate) { reads++; "Enunciado real. ".repeat(500) }
        val input = listOf(AiMessage("user", "explique este exercício", "photo.jpg"))
        val answer = provider.complete(NexusSettings(), "", input, emptyList())
        assertTrue(answer.contains("Explicação do texto")); assertTrue(answer.contains("sem análise visual"))
        assertNull(delegate.input.single().imagePath)
        assertTrue(delegate.input.single().content.contains("Você não viu a imagem"))
        assertTrue(delegate.input.single().content.length < 1_800)
        provider.complete(NexusSettings(), "", input, emptyList())
        assertEquals(1, reads)
    }
    @Test fun `blank OCR cannot produce a scene description`() = runBlocking<Unit> {
        val delegate = CapturingProvider()
        val answer = LocalImageTextProvider(delegate) { "  " }.complete(NexusSettings(), "", listOf(AiMessage("user", "o que é isso?", "photo.jpg")), emptyList())
        assertTrue(answer.contains("Não encontrei texto legível")); assertTrue(answer.contains("não reconhece objetos")); assertEquals(0, delegate.calls)
    }
    @Test fun `follow up keeps cached transcription without rereading or image rejection`() = runBlocking<Unit> {
        val delegate = CapturingProvider()
        var reads = 0
        val provider = LocalImageTextProvider(delegate) { reads++; "Texto do exercício" }
        val image = AiMessage("user", "leia", "photo.jpg")
        provider.complete(NexusSettings(), "", listOf(image), emptyList())
        provider.complete(NexusSettings(), "", listOf(image, AiMessage("assistant", "Texto do exercício"), AiMessage("user", "Explique")), emptyList())
        assertTrue(delegate.input.first().content.contains("Texto do exercício"))
        assertTrue(delegate.input.all { it.imagePath == null }); assertEquals(1, reads)
    }
}
