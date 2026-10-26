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
- **190 testes JVM** e **116 testes Python dos scripts** passaram nesta retomada. A compilação completa debug, instrumentação e release terminou com sucesso em **8 min 45 s**; lint debug/release: **0 erros, 47 avisos e 1 informação** em cada variante. [Resumo local](evidencias/ai3-alpha2-local-build.json).
- O avaliador de qualidade passou por nove regressões Kotlin e doze testes Python específicos. Ele agora rejeita os falsos positivos comprovados da desculpa assinada NEXUS e da alegação de acesso à câmera, mantendo as respostas originais. [Evidência](evidencias/ai3-evaluation-checks-regressions.json).

## Limites da correção

**Esta alteração corrige processamento desnecessário, não comprova uma IA mais inteligente.** As falhas semânticas da alpha.1 permanecem registradas em [TESTES_NEXUS_AI.md](../TESTES_NEXUS_AI.md). A nova avaliação Android ainda precisa executar a bateria completa com o novo cache.

Modelos alternativos foram investigados com inferência real, sem substituir silenciosamente os pesos incluídos. Nenhum foi aprovado como solução dos problemas de interpretação. [LFM2.5, Gemma3 e prompts curtos: 32 respostas reais](evidencias/ai3-alternative-models-quality.json), incluindo [cinco gerações com o sampler C++ de produção](evidencias/ai3-qwen25-pt-short-native.json). Os comparativos por servidor têm diferenças de amostragem explicitadas no relatório; não equivalem ao Android. Não existe backend NEXUS público implantado. A integração online opcional exige um serviço real configurado e autorizado.

Os testes físicos de mouse, voz, câmera, aquecimento e instalação com Play Protect continuam pendentes. A revisão de código e as verificações de assinatura não constituem aprovação do Google. A assinatura definitiva permanece dependente da chave protegida do responsável.

## Arquivos locais efetivamente gerados

Fonte: commit `62ec1e3a714cdcc577e04f659185808bd6c9ab3f`, árvore principal do app `5daf21ac5a99032f4b225c35cbad2e648f86b597`.

| Arquivo | Tamanho | SHA-256 | Estado |
| --- | --- | --- | --- |
| `app/build/outputs/apk/debug/app-debug.apk` | 1.738.201.604 bytes | `645f2df7eb851bf9f11b61471ef9cc44d0d62d9e3793c32ad7996ab41df24a9e` | Assinado com certificado de teste, v2 verificado |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | 1.715.971.484 bytes | `ee275b230119f1aa8df38ed5fe4e41df6c4fce86324e0950801e1c6595a3736d` | Sem assinatura; **não instalável** |

Ambos passaram pelo alinhamento ZIP 16 KB e inspeção dos 40 ELF das quatro ABIs. Tamanho e SHA dos dois GGUFs, OCR, classificador e avisos de licença foram conferidos. [APK debug](evidencias/ai3-alpha2-debug-apk.json), [release sem assinatura](evidencias/ai3-alpha2-release-unsigned.json) e [manifestos/certificado](evidencias/ai3-alpha2-manifest-signature-summary.json).

Certificado do debug **local**: `ffc6524eee21a185872e946e78649a977f4b82011ee573c20adf5ca6ba376f79`. A compilação do GitHub usa outro ambiente e pode ter certificado e hash diferentes. Não use os hashes locais para identificar o ZIP ou o APK do Actions.

## GitHub Actions e download da alpha.2

Cinco verificações da fonte `62ec1e3` concluíram com sucesso: [build 16](https://github.com/Cazi1n8/nexus-android/actions/runs/40389844327), [28 testes Android comuns e quatro opt-in ignorados](https://github.com/Cazi1n8/nexus-android/actions/runs/40389844498), [primeiro chat offline](https://github.com/Cazi1n8/nexus-android/actions/runs/40389844275), [seis inferências nativas/cache](https://github.com/Cazi1n8/nexus-android/actions/runs/40389844358) e [download real dos dois modelos](https://github.com/Cazi1n8/nexus-android/actions/runs/40389844276). [Resumo dos cinco resultados](evidencias/ai3-alpha2-ci-summary-five-complete.json).

A CI principal confirmou 190 testes JVM, 116 scripts e 44 do servidor; lint: zero erros, 48 avisos e uma informação. O chat offline preparou o modelo do APK em 3,453 s e gerou “Azul.” em 24,145 s, além de cálculo, OCR e Room. O teste nativo observou a repetição de `42` em 423 ms com 51/52 tokens reaproveitados, após 7.100 ms na entrada inicial. A recuperação após cancelar usou cache zero. Não são medições do S9+ nem prova de inteligência geral.

**[Baixar o artefato 16](https://github.com/Cazi1n8/nexus-android/actions/runs/40389844327/artifacts/12682941109)**: ZIP de **1.645.767.106 bytes**, com `app-debug.apk` e `SHA256SUMS.txt`; exige login e extração. Expira em 25/11/2026. O GitHub informa SHA-256 **do ZIP** `c9898979c7f301712970949c0cbf260c92e946127caeab0e980d43983f25ba36`. O ZIP grande não foi baixado ou inspecionado localmente. A publicação debug em Releases permaneceu desativada.

A [avaliação longa de qualidade](https://github.com/Cazi1n8/nexus-android/actions/runs/40389844392) continua em execução neste registro. Sua conclusão e revisão semântica serão documentadas separadamente; os cinco resultados acima não a substituem.
