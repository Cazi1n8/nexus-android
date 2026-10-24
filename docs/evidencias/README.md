# Evidências da evolução 2.0

Resultados locais de 24/10/2026 UTC. O JSON registra compilação, testes JVM e APK; a lista ELF registra os segmentos do pacote final. Os logs JNI e de qualidade usam modelos reais e prompts sintéticos no computador, com o upstream Linux correspondente; não são testes de inferência em um celular. As falhas de conteúdo do modelo menor permanecem nos registros.

Relatório e limites: [RELATORIO_NEXUS_2.md](../RELATORIO_NEXUS_2.md). Testes físicos, câmera, microfone e gestos exigem resultados separados. O APK de CI possui hash próprio, distinto do APK local.

O XML Android 10 registra oito testes aprovados no emulador do GitHub. Inclui execução do rastreador sobre quadro preto e rejeição de pesos ausentes pela ponte JNI; não inclui inferência de linguagem com GGUF dentro do Android nem detecção de mão física.
