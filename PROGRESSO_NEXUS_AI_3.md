# NEXUS AI 3.0 — progresso verificável

Atualização de 26/10/2026. Projeto existente, versionCode 24, 3.0.0-alpha.1.

| Área | Implementação nesta etapa | Verificação / limite real |
| --- | --- | --- |
| Identidade | Campos separados e migração das preferências | Campos e prompts testados; resultados semânticos reais estão registrados separadamente |
| Geração | Sampling, condições de parada, metadados; nenhum rodapé automático | Avaliação real ainda encontra falhas semânticas; não declarar inteligência corrigida |
| Contexto | Orçamento real de tokens no JNI; trechos literais sob pressão | Testes automatizados; avaliação Android de 20 turnos em execução |
| Histórico | Busca literal, renomear, continuar, excluir e novo contexto | Room: renomear, pesquisar e continuar passaram no emulador |
| Ferramentas | Calculadora e propostas estruturadas de rotas/Configurações | Ações só após confirmação; não afirmar execução antes do resultado |
| Chat | Markdown, código, copiar, ouvir e interromper | Build aprovado; testes Android comuns passaram; validação física pendente |
| Imagens | OCR e classificação real de categorias de objetos | Não é um VLM geral; modelo multimodal remoto continua opcional |
| Vision ao vivo | Quadros locais periódicos, intervalo e prévia opcionais | Classificador com foto real e quadro vazio passou no Android; câmera física pendente |
| Voz | Fila de TTS na inicialização e seleção pt-BR; serviço próprio | Testes JVM; ouvir/falar em S9+ pendente |
| Segundo plano | FGS independentes, notificações, parar tudo e interrupção | [13 cenários físicos](TESTES_SEGUNDO_PLANO.md) pendentes; sem wakeword permanente |
| Mouse | Filtro por tempo, cadência adaptativa e proteção de inicialização | 17 testes puros de geometria/cadência/orçamento passaram; fluidez física pendente |
| Libras | Janela temporal corrigida, alvo no canto e candidatas L/V/Y | Experimental; sem validação pedagógica ou alfabeto completo |
| Privacidade | MediaPipe com fábrica no-op upstream reproduzível | Manifestos e APKs debug/release inspecionados; fábrica no-op confirmada no Android |
| Play Protect | Auditoria, justificativas, assinatura externa e revisão oficial | Causa exata do bloqueio desconhecida; sem aprovação do Google |
| Compatibilidade | Perfis selecionados por RAM/armazenamento reais | Android 8+; IA local 64 bits e memória mínima; sem garantia universal |
| Distribuição | CI prepara APK completo e artefatos de teste | Release assinada depende da chave protegida do responsável |

## Verificações executadas nesta retomada

- Scripts: `python3 -m unittest discover -s scripts/tests -v`: **113 aprovados**.
- Servidor opcional: `python3 -m unittest discover -s server/tests -v`: **44 aprovados**; não prova um backend público implantado.
- Testes JVM: **189 aprovados**, sem falhas. A compilação de teste inicial gerou APK debug e APK de instrumentação; lint encontrou uma leitura não observável de estado na tela de voz e ela foi corrigida. Nova compilação completa **aprovada em 7 min 37 s**.
- Release completa: a primeira tentativa esgotou o armazenamento do ambiente. Cópias redundantes de artefatos foram liberadas e o empacotamento de pesos desnecessário nos testes JVM foi removido; nova tentativa **aprovada**. O release é sem assinatura e não instalável.
- Estado salvo no GitHub: [5d8d141](https://github.com/Cazi1n8/nexus-android/commit/5d8d1418357fec0e2bdefaf91775d996b77091aa).
- Avaliações de linguagem reais: [TESTES_NEXUS_AI.md](TESTES_NEXUS_AI.md). Saída tecnicamente válida não equivale a resposta correta.
- Testes físicos no Samsung Galaxy S9+: não executados por este ambiente.

## Etapas seguintes verificáveis

1. Concluir a nova execução do teste de preparação/Chat após atualizar seu contrato de seleção de modelo e registrar a avaliação semântica da IA.
2. Executar CI com emulador, prova da IA incluída e avaliações críticas de linguagem; corrigir falhas encontradas.
3. Validar sessões globais, permissões, TTS, reconhecimento e aquecimento em aparelho físico.
4. Configurar a assinatura definitiva protegida, preparar revisão oficial e testes de distribuição com Play Protect ativado.
5. Evoluir reconhecimento validado de Libras, visão multimodal geral e eventual serviço online autenticado, sem apresentar esses recursos como concluídos.

## Compilação e artefatos reais — 26/10/2026

- Build local: **189 testes JVM aprovados**, lint debug/release **0 erros, 47 avisos e 1 informação**. `testDebugUnitTest lintDebug lintRelease assembleDebug assembleDebugAndroidTest assembleRelease` concluiu em **7 min 37 s**.
- APK debug local: **1.738.344.072 bytes**, SHA-256 `987cd6dcee18c6c23efb648b11893ef54c27c7ecd0f6778920626c60aaa50bf3`. Assinatura v2, alinhamento ZIP 16 KB, quatro ABIs, ELF e os dois GGUF/OCR/classificador verificados.
- Release local: **1.715.955.100 bytes**, SHA-256 `a109fb45fb31d6cc6abb0e5e2cc6fd850e557e7e5971417e6feb4af3eee9fd8e`. R8 e alinhamento passaram; ausência de assinatura confirmada. **Não instalável**.
- [CI principal 4228a54](https://github.com/Cazi1n8/nexus-android/actions/runs/40303339535): **aprovada**, 189 testes JVM, 113 scripts e 44 servidor; lint **0 erros, 48 avisos e 1 informação**. Artefato de teste disponível; publicação pública debug permaneceu pausada.
- [Regressão Android](https://github.com/Cazi1n8/nexus-android/actions/runs/40303339780): **19 aprovados, 4 opt-in ignorados, 0 falhas/erros**. Inclui foto real no classificador, quadro vazio, fábrica MediaPipe no-op e busca/renomeação/continuação Room.
- [Inferência nativa Android](https://github.com/Cazi1n8/nexus-android/actions/runs/40303339754): **1 aprovado**, gerações reais de 42/Ciano/UTF-8; não certifica qualidade geral.
- [Download real Android](https://github.com/Cazi1n8/nexus-android/actions/runs/40303339485): **aprovado**. Ambos os modelos chegaram a READY com hashes corretos, refresh preservou READY e houve geração real de 42. Não substitui validação no S9+.
- [Avaliação semântica Android](https://github.com/Cazi1n8/nexus-android/actions/runs/40303339463): em execução. O modelo tem falhas conhecidas em linguagem informal; não marcar os 15 casos como aprovados antes dos resultados.
- A prova do APK incluído encontrou uma expectativa antiga no teste (sempre compacto após escolha manual). O contrato foi atualizado para verificar escolha manual e depois ativar o ajuste automático real; o teste corrigido **compilou em 31 s**, mantendo as provas de Chat, OCR, cálculo, rede desligada e histórico. A nova execução ainda está pendente.

[APK de teste no GitHub Actions](https://github.com/Cazi1n8/nexus-android/actions/runs/40303339535/artifacts/12641159108): ZIP que contém `app-debug.apk`, **exige login no GitHub**. O hash do ZIP informado pelo GitHub não é o hash do APK local. As assinaturas debug locais e do GitHub podem diferir.

Não houve instalação física nem aprovação do Play Protect. A qualidade da IA ainda não atende a todos os pedidos e os serviços globais precisam dos 13 testes físicos documentados.
