# Evidências da evolução 2.0

## Alpha.2

A revisão `b7a5c1b52186fd5ad014c74dafec4649d62c7bb5` passou no build GitHub, na suíte comum Android 10 e na prova separada com GGUF real. [Relatório e limites](../RELATORIO_ALPHA_2.md).

- [Resumo CI](alpha2-ci-final.json): 76 testes JVM, 44 Python de scripts, 44 Python de servidor; lint sem erros (38 avisos e 1 informação na CI).
- [JUnit Android 10](alpha2-final-android10-junit.xml): 13 aprovados, 0 falhas/erros, 1 ignorado sem fixture. Inclui quatro testes do leitor OCR e a tela Vision sem ativar sensores.
- [Observações da IA real](alpha2-final-ai-local-ai-observations.json), [instrumentação](alpha2-final-ai-instrumentation.txt) e [JUnit dedicado](alpha2-final-ai-junit.xml): um teste aprovado com quatro respostas geradas, incluindo contexto e UTF-8. O caso opcional ignorado na suíte comum foi executado aqui.
- [Inspeção do APK publicado](alpha2-published-apk.json): download público, digest GitHub e SHA256SUMS conferidos, assinatura v2, ZIP 16 KB, quatro ABIs, 40 ELFs e modelos OCR verificados. Sem GGUF no pacote.
- [Verificação local](alpha2-local-verification.json): build e APK locais, com hash próprio. As primeiras observações de IA da alpha.2 permanecem nos arquivos `alpha2-local-ai-observations.json` e `alpha2-ai-*`.

As imagens de teste são geradas no Android; as perguntas da IA são sintéticas. As provas não usam câmera ou microfone físicos, nem certificam qualidade geral do modelo, Libras completa ou desempenho no S9+.

## Alpha.1 preservada

Resultados locais de 24/10/2026 UTC. O JSON registra compilação, testes JVM e APK; a lista ELF registra os segmentos do pacote final. Os logs JNI e de qualidade usam modelos reais e prompts sintéticos no computador, com o upstream Linux correspondente; não são testes de inferência em um celular. As falhas de conteúdo do modelo menor permanecem nos registros.

Relatório e limites: [RELATORIO_NEXUS_2.md](../RELATORIO_NEXUS_2.md). Testes físicos, câmera, microfone e gestos exigem resultados separados. O APK de CI possui hash próprio, distinto do APK local.

O arquivo `android10-junit.xml` da alpha.1 registra oito testes aprovados no emulador do GitHub. Inclui execução do rastreador sobre quadro preto e rejeição de pesos ausentes pela ponte JNI; não inclui inferência de linguagem com GGUF dentro do Android nem detecção de mão física.
