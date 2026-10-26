# NEXUS AI 3.0 — progresso verificável

Atualização de 26/10/2026. Projeto existente, versionCode 24, 3.0.0-alpha.1.

| Área | Implementação nesta etapa | Verificação / limite real |
| --- | --- | --- |
| Identidade | Campos separados e migração das preferências | Testes de prompt/configurações; avaliação real distingue Calebe e NEXUS |
| Geração | Sampling, condições de parada, metadados; nenhum rodapé automático | Avaliação real ainda encontra falhas semânticas; não declarar inteligência corrigida |
| Contexto | Orçamento real de tokens no JNI; trechos literais sob pressão | Testes automatizados e avaliação de 20 turnos em preparação |
| Histórico | Busca literal, renomear, continuar, excluir e novo contexto | Room local; testes de integração no emulador pendentes nesta versão |
| Ferramentas | Calculadora e propostas estruturadas de rotas/Configurações | Ações só após confirmação; não afirmar execução antes do resultado |
| Chat | Markdown, código, copiar, ouvir e interromper | Build/verificação visual em andamento |
| Imagens | OCR e classificação real de categorias de objetos | Não é um VLM geral; modelo multimodal remoto continua opcional |
| Vision ao vivo | Quadros locais periódicos, intervalo e prévia opcionais | Testes de classificador com imagem real preparados; câmera física pendente |
| Voz | Fila de TTS na inicialização e seleção pt-BR; serviço próprio | Testes JVM; ouvir/falar em S9+ pendente |
| Segundo plano | FGS independentes, notificações, parar tudo e interrupção | [13 cenários físicos](TESTES_SEGUNDO_PLANO.md) pendentes; sem wakeword permanente |
| Mouse | Filtro por tempo, cadência adaptativa e proteção de inicialização | 17 testes puros de geometria/cadência/orçamento passaram; fluidez física pendente |
| Libras | Janela temporal corrigida, alvo no canto e candidatas L/V/Y | Experimental; sem validação pedagógica ou alfabeto completo |
| Privacidade | MediaPipe com fábrica no-op upstream reproduzível | Integridade comprovada; manifesto/APK final ainda em conferência |
| Play Protect | Auditoria, justificativas, assinatura externa e revisão oficial | Causa exata do bloqueio desconhecida; sem aprovação do Google |
| Compatibilidade | Perfis selecionados por RAM/armazenamento reais | Android 8+; IA local 64 bits e memória mínima; sem garantia universal |
| Distribuição | CI prepara APK completo e artefatos de teste | Release assinada depende da chave protegida do responsável |

## Verificações executadas nesta retomada

- Scripts: `python3 -m unittest discover -s scripts/tests -v`: **113 aprovados**.
- Servidor opcional: `python3 -m unittest discover -s server/tests -v`: **44 aprovados**; não prova um backend público implantado.
- Testes JVM: **189 aprovados**, sem falhas. A compilação de teste inicial gerou APK debug e APK de instrumentação; lint encontrou uma leitura não observável de estado na tela de voz e ela foi corrigida. Nova compilação completa em andamento.
- Release completa: a primeira tentativa esgotou o armazenamento do ambiente. Cópias redundantes de artefatos foram liberadas e o empacotamento de pesos desnecessário nos testes JVM foi removido; nova tentativa em andamento.
- Estado salvo no GitHub: [5d8d141](https://github.com/Cazi1n8/nexus-android/commit/5d8d1418357fec0e2bdefaf91775d996b77091aa).
- Avaliações de linguagem reais: [TESTES_NEXUS_AI.md](TESTES_NEXUS_AI.md). Saída tecnicamente válida não equivale a resposta correta.
- Testes físicos no Samsung Galaxy S9+: não executados por este ambiente.

## Etapas seguintes verificáveis

1. Concluir testes JVM/lint e builds debug/release sem assinatura; inspecionar permissões e artefatos reais.
2. Executar CI com emulador, prova da IA incluída e avaliações críticas de linguagem; corrigir falhas encontradas.
3. Validar sessões globais, permissões, TTS, reconhecimento e aquecimento em aparelho físico.
4. Configurar a assinatura definitiva protegida, preparar revisão oficial e testes de distribuição com Play Protect ativado.
5. Evoluir reconhecimento validado de Libras, visão multimodal geral e eventual serviço online autenticado, sem apresentar esses recursos como concluídos.
