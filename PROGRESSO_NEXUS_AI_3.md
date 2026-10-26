# NEXUS AI 3.0 — progresso verificável

Atualização de 26/10/2026. A base compilada e os APKs descritos abaixo são versionCode 24, 3.0.0-alpha.1. A retomada 3.0.0-alpha.2 (versionCode 25) está em implementação; sua nova compilação ainda não foi concluída neste registro.

## Retomada alpha.2 em andamento

- Reutilização segura do prefixo KV implementada para reduzir o reprocessamento do histórico. Vinte comparações reais com os dois modelos mantiveram o texto idêntico; testes de cancelamento, poda e recuperação passaram. A comparação no computador é uma prova separada; ainda faltam concluir a nova compilação Android e sua verificação. [Relatório da alpha.2](docs/RELATORIO_IA3_ALPHA_2.md). Isso não corrige, por si só, interpretação ou conhecimento do modelo.
- Correção dos próprios testes de qualidade: a resposta real de desculpas assinada “Att., NEXUS” passa a ser rejeitada; um “não” sem relação com câmera deixa de servir como prova de limitação visual. O caso 9 foi incluído no gate crítico da CI.
- O relatório distingue conclusão técnica, verificações automáticas e revisão humana obrigatória. Os testes de regressão passaram no computador: **9 Kotlin/JUnit e 12 Python**. Após ajustar também a validação do cache no executor nativo, a suíte completa registrou **116 aprovados**; os testes JVM da nova fonte registraram **190 aprovados**. [Evidência do avaliador](docs/evidencias/ai3-evaluation-checks-regressions.json). Não foi reexecutada a bateria real dos quinze casos após esta correção do avaliador.

## Base alpha.1 verificada

| Área | Implementação nesta etapa | Verificação / limite real |
| --- | --- | --- |
| Identidade | Campos separados e migração das preferências | Modelo identificou NEXUS no caso 2, mas ainda assinou o texto de desculpas como NEXUS; separação de campos não comprova comportamento correto |
| Geração | Sampling, condições de parada, metadados; nenhum rodapé automático | Avaliação real ainda encontra falhas semânticas; não declarar inteligência corrigida |
| Contexto | Orçamento real de tokens no JNI; trechos literais sob pressão | Avaliação interrompida após 16 dos 20 turnos relacionados; latência cresceu com o histórico; memória final não foi verificada |
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
- Código e relatórios anteriores salvos no GitHub: [e98027c](https://github.com/Cazi1n8/nexus-android/commit/e98027c2aa0b07fdaaf366db6d94a42fd09c3f2b). A fonte principal do aplicativo tem árvore `8dde24ce23b4b7b6d545bdd350fb4acc4b774390`; o ajuste do teste e os relatórios não mudaram essa fonte.
- Avaliações de linguagem reais: [TESTES_NEXUS_AI.md](TESTES_NEXUS_AI.md). Saída tecnicamente válida não equivale a resposta correta.
- Testes físicos no Samsung Galaxy S9+: não executados por este ambiente.

## Etapas seguintes verificáveis

1. Corrigir a causa das falhas de interpretação, escrita e alegações indevidas do modelo. A avaliação real ainda não comprova a inteligência solicitada; filtros de exibição ou respostas fixas não substituem essa correção.
2. Concluir a otimização do contexto em implementação, compilar e validar a reutilização segura do prefixo KV no Android; repetir a bateria de quinze casos e os vinte turnos até obter resultados completos, sem marcar o timeout como aprovação.
3. Validar sessões globais, permissões, TTS, reconhecimento e aquecimento em aparelho físico.
4. Configurar a assinatura definitiva protegida, preparar revisão oficial e testes de distribuição com Play Protect ativado.
5. Evoluir reconhecimento validado de Libras, visão multimodal geral e eventual serviço online autenticado, sem apresentar esses recursos como concluídos.

## Compilação e artefatos reais — 26/10/2026

- Build local: **189 testes JVM aprovados**, lint debug/release **0 erros, 47 avisos e 1 informação**. `testDebugUnitTest lintDebug lintRelease assembleDebug assembleDebugAndroidTest assembleRelease` concluiu em **7 min 37 s**.
- APK debug local: **1.738.344.072 bytes**, SHA-256 `987cd6dcee18c6c23efb648b11893ef54c27c7ecd0f6778920626c60aaa50bf3`. Assinatura v2, alinhamento ZIP 16 KB, quatro ABIs, ELF e os dois GGUF/OCR/classificador verificados.
- Release local: **1.715.955.100 bytes**, SHA-256 `a109fb45fb31d6cc6abb0e5e2cc6fd850e557e7e5971417e6feb4af3eee9fd8e`. R8 e alinhamento passaram; ausência de assinatura confirmada. **Não instalável**.
- [CI principal e98027c](https://github.com/Cazi1n8/nexus-android/actions/runs/40305428821): **aprovada**, 189 testes JVM, 113 scripts e 44 servidor; lint **0 erros, 48 avisos e 1 informação**. Artefato de teste disponível; publicação pública debug permaneceu pausada.
- [Regressão Android](https://github.com/Cazi1n8/nexus-android/actions/runs/40305428792): **19 aprovados, 4 opt-in ignorados, 0 falhas/erros**. Inclui foto real no classificador, quadro vazio, fábrica MediaPipe no-op e busca/renomeação/continuação Room.
- [Inferência nativa Android](https://github.com/Cazi1n8/nexus-android/actions/runs/40303339754): **1 aprovado**, gerações reais de 42/Ciano/UTF-8; não certifica qualidade geral.
- [Download real Android](https://github.com/Cazi1n8/nexus-android/actions/runs/40303339485): **aprovado**. Ambos os modelos chegaram a READY com hashes corretos, refresh preservou READY e houve geração real de 42. Não substitui validação no S9+.
- [Avaliação semântica Android](https://github.com/Cazi1n8/nexus-android/actions/runs/40303339463): **falhou por timeout de 40 minutos**. Casos 1–10 concluíram geração/ferramenta, caso 11 registrou 16 turnos, caso 12 iniciou sem resposta e 13–15 não rodaram. Foram 28 respostas registradas (26 do modelo e duas de ferramentas), sem reabertura final do Room. A revisão humana confirmou o texto de desculpas indevidamente assinado NEXUS, uma alegação falsa de acesso à câmera, erros factuais, mudança para inglês e saídas cortadas. [Respostas originais e revisão](TESTES_NEXUS_AI.md); **a inteligência permanece sem correção comprovada**.
- A prova inicial do APK incluído encontrou uma expectativa antiga no teste (sempre compacto após escolha manual). O contrato foi atualizado para verificar escolha manual e depois ativar o ajuste automático real. A [execução corrigida](https://github.com/Cazi1n8/nexus-android/actions/runs/40305428815) **passou**: preparação do modelo em 4,726 s, resposta real “Azul.” em 36,250 s, cálculo `10+10÷10×10 = 20`, OCR “OLA NEXUS 2026”, três respostas persistidas e perfil estável após recriar a Activity, sem rede antes ou depois. Isso valida o fluxo técnico, não a qualidade geral do modelo.

[APK de teste no GitHub Actions](https://github.com/Cazi1n8/nexus-android/actions/runs/40305428821/artifacts/12642117269): ZIP que contém `app-debug.apk`, **exige login no GitHub**. O hash do ZIP informado pelo GitHub não é o hash do APK local. As assinaturas debug locais e do GitHub podem diferir.

As evidências de CI estão em [ai3-ci-verification-summary.json](docs/evidencias/ai3-ci-verification-summary.json) e a prova do primeiro chat em [ai3-bundled-fixed-observations.json](docs/evidencias/ai3-bundled-fixed-observations.json). O ZIP do APK no GitHub não foi baixado nem inspecionado localmente; os hashes locais acima são de outro artefato, verificado neste ambiente.

Não houve instalação física nem aprovação do Play Protect. A qualidade da IA ainda não atende a todos os pedidos e os serviços globais precisam dos 13 testes físicos documentados.
