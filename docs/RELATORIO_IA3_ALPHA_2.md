# NEXUS AI 3.0 alpha.2 — correção do reprocessamento de contexto

Versão 25, `3.0.0-alpha.2`, no mesmo aplicativo e repositório. Relatório de 26/10/2026.

## Problema confirmado e alteração

O runtime nativo limpava o cache KV antes e depois de cada geração. Cada pergunta voltava a processar todo o histórico selecionado. Na avaliação Android da alpha.1, respostas curtas chegaram a levar mais de dois minutos conforme a conversa crescia; a bateria atingiu o timeout de 40 minutos.

A alpha.2 compara os tokens de entrada com os tokens efetivamente processados e reaproveita somente o prefixo idêntico. Reprocessa ao menos o último token de entrada para obter os logits corretos. Se a memória não representar uma sequência contígua ou não permitir remoção parcial, processa novamente a entrada completa. As regras de geração e o texto do modelo não são substituídos por respostas prontas.

Erros e cancelamento invalidam o cache. Trocar de conversa libera o engine. Alterar nome, instruções, preferências ou memórias cancela a geração antiga e aguarda sua liberação antes de salvar o novo contexto. Apagar conversas também libera a memória de inferência; os controles existentes de exclusão dos dados persistentes foram preservados.

## Verificações já executadas

- **20 comparações reais** entre baseline e protótipo: dez com Qwen2.5 0.5B e dez com 1.5B. Todos os textos ficaram idênticos byte a byte. Incluem repetição, novo turno, mudança de sistema/pedido, poda de histórico, entrada inválida e recuperação após cancelamento.
- Exemplo no host com 1.5B: repetição da mesma entrada passou de **3.224 ms para 110 ms**, com 246 de 247 tokens reaproveitados. É uma observação de CPU Linux, não um benchmark do S9+. A primeira pergunta ainda precisa processar a entrada inteira; sem prefixo comum não há esse ganho.
- JNI da fonte de produção: geração real, UTF-8, histórico, reutilização, cancelamento, recuperação e fechamento concorrente passaram. [Log](evidencias/ai3-kv-prefix-cache-production-jni.txt) e [comparações completas](evidencias/ai3-kv-prefix-cache-host.json).
- **190 testes JVM** e **116 testes Python dos scripts** passaram nesta retomada. A compilação Android completa ainda estava em andamento ao iniciar este relatório; os resultados finais e hashes serão acrescentados após sua conclusão.
- O avaliador de qualidade passou por nove regressões Kotlin e doze testes Python específicos. Ele agora rejeita os falsos positivos comprovados da desculpa assinada NEXUS e da alegação de acesso à câmera, mantendo as respostas originais. [Evidência](evidencias/ai3-evaluation-checks-regressions.json).

## Limites da correção

**Esta alteração corrige processamento desnecessário, não comprova uma IA mais inteligente.** As falhas semânticas da alpha.1 permanecem registradas em [TESTES_NEXUS_AI.md](../TESTES_NEXUS_AI.md). A nova avaliação Android ainda precisa executar a bateria completa com o novo cache.

Modelos alternativos foram investigados com inferência real, sem substituir silenciosamente os pesos incluídos. Nenhum foi aprovado como solução dos problemas de interpretação. [LFM2.5, Gemma3 e prompts curtos: 32 respostas reais](evidencias/ai3-alternative-models-quality.json), incluindo [cinco gerações com o sampler C++ de produção](evidencias/ai3-qwen25-pt-short-native.json). Os comparativos por servidor têm diferenças de amostragem explicitadas no relatório; não equivalem ao Android. Não existe backend NEXUS público implantado. A integração online opcional exige um serviço real configurado e autorizado.

Os testes físicos de mouse, voz, câmera, aquecimento e instalação com Play Protect continuam pendentes. A revisão de código e as verificações de assinatura não constituem aprovação do Google. A assinatura definitiva permanece dependente da chave protegida do responsável.
