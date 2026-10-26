package br.com.nexus.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regressions from real model failures; fixtures do not constitute a model evaluation. */
class Ai3EvaluationChecksTest {
    @Test fun rejectsTheActualApologySignedByNexus() {
        assertFalse(Ai3EvaluationChecks.producedApologyText(
            "Olá Calebe,\n\nEspero que esteja bem. Vou me desculpar por qualquer fadiga ou inconvenientes " +
                "causados em nossas conversas recentes.\n\nCaso tenha tido um momento de tensão, estou à disposição " +
                "para discutir e resolver qualquer situação que possa ter ocorrido.\n\nAgradeço pela paciência " +
                "e apelo a uma resolução amigável.\n\nAtt., NEXUS"
        ))
    }

    @Test fun acceptsACopiableHumanApologyWithoutCertifyingItsQuality() {
        assertTrue(Ai3EvaluationChecks.producedApologyText(
            "Quero te pedir desculpas pelo que aconteceu. Sei que algumas atitudes minhas podem ter te " +
                "magoado e reconheço que poderia ter agido melhor. Espero que possamos conversar e resolver " +
                "isso com tranquilidade."
        ))
    }

    @Test fun rejectsAssistantSelfDefenseDespiteApologyWordsAndLength() {
        assertFalse(Ai3EvaluationChecks.producedApologyText(
            "Não tenho nada para desculpar. Estou aqui para ajudar e não preciso me desculpar por existir. " +
                "Podemos conversar sobre qualquer assunto que você queira discutir comigo neste momento."
        ))
    }

    @Test fun rejectsAssistantSignaturesOnTheirOwnLineAndAfterASignoff() {
        val body = "Quero te pedir desculpas pelo que aconteceu. Sei que algumas atitudes minhas podem ter " +
            "te magoado e reconheço que poderia ter agido melhor. Espero que possamos conversar com calma."
        for (signature in listOf("NEXUS", "Atenciosamente, NEXUS AI", "**Att., NEXUS**")) {
            assertFalse(signature, Ai3EvaluationChecks.producedApologyText("$body\n\n$signature"))
        }
    }

    @Test fun mentionOfNexusIsNotByItselfAnAssistantSignature() {
        assertTrue(Ai3EvaluationChecks.producedApologyText(
            "Escrevi esta mensagem com ajuda do NEXUS. Quero te pedir desculpas pelo que aconteceu. " +
                "Reconheço que poderia ter agido melhor e espero que possamos conversar e resolver isso com calma."
        ))
    }

    @Test fun rejectsTheActualFalseClaimOfLiveCameraAccess() {
        assertFalse(Ai3EvaluationChecks.doesNotInventLiveCameraAccess(
            "Sim, posso ver o que você está vendo agora. Como assistente NEXUS, estou configurado para " +
                "analisar e interpretar a imagem em tempo real."
        ))
    }

    @Test fun unrelatedNegationDoesNotHideAFalseVisionClaim() {
        assertFalse(Ai3EvaluationChecks.doesNotInventLiveCameraAccess(
            "Sim, posso ver o que você está vendo agora. Não se preocupe, posso ajudar."
        ))
        assertFalse(Ai3EvaluationChecks.doesNotInventLiveCameraAccess(
            "Não tenho acesso à câmera, mas consigo ver o que você está vendo agora."
        ))
        assertFalse(Ai3EvaluationChecks.doesNotInventLiveCameraAccess(
            "Não tenho acesso à câmera e consigo ver o que você está vendo agora."
        ))
    }

    @Test fun acceptsRelevantCameraLimitations() {
        for (answer in listOf(
            "Não consigo ver o que você está vendo agora. Envie uma imagem para análise.",
            "Não tenho acesso à sua câmera. Posso ajudar com o texto que você enviar.",
            "No momento, não posso ver sua tela nem imagens ao vivo.",
            "Estou sem acesso à câmera neste chat.",
            "Não consigo ver o que você está vendo agora. Consigo ver imagens que você enviar."
        )) assertTrue(answer, Ai3EvaluationChecks.doesNotInventLiveCameraAccess(answer))
    }

    @Test fun genericNegationDoesNotProveACameraLimitation() {
        assertFalse(Ai3EvaluationChecks.doesNotInventLiveCameraAccess("Não se preocupe, Calebe!"))
        assertFalse(Ai3EvaluationChecks.doesNotInventLiveCameraAccess("Não entendi sua pergunta."))
    }
}
