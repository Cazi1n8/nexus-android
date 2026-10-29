# Voz NEXUS 4.1 — revisão de 29/10/2026

## Causa confirmada no código

`BackgroundVoiceService.tryListen()` esperava o modelo local ficar `READY` antes de abrir o reconhecimento de fala. No CORE sem pesos, a preparação do modelo não podia concluir esse requisito. A sessão permanecia em preparação mesmo para uma conta ou ação local que não precisa de um LLM.

A escuta agora depende do reconhecedor e da voz do Android. A solicitação reconhecida passa pelo mesmo `NexusViewModel.send()` do Chat: calculadora e ferramentas locais mantêm suas verificações; conversa geral informa a ausência de modelo/serviço. Não há resposta de IA simulada e não há promessa de conversa geral sem modelo operacional.

## Mudanças implementadas

- A prontidão da síntese considera as vozes realmente enumeradas pelo mecanismo. Uma voz instalada `pt-BR` pode ser selecionada mesmo se `isLanguageAvailable()` informar somente português genérico. Na ausência de metadados, o suporte ao país ainda é exigido. `pt-PT` não substitui silenciosamente `pt-BR`.
- O nome de voz salvo é transmitido ao controlador da tela e ao serviço; o idioma continua independente do nome da voz. Uma voz de rede exige seleção explícita quando só existem opções de rede enumeradas.
- Inicialização do TTS tem prazo de 12 segundos, inclusive sem texto aguardando. Gerações de inicialização rejeitam callbacks antigos após falha, nova tentativa ou fechamento.
- O progresso da leitura tem watchdog finito por trecho. O callback real do mecanismo conclui a fala; o watchdog apenas encerra uma fila que deixou de responder.
- A fala solicita foco de áudio oficial. Perda de foco interrompe a leitura e informa o motivo; não dispara uma nova escuta automaticamente.
- Nova ação manual de ouvir atualiza a disponibilidade do reconhecedor e das vozes instaladas.
- `PARAR` passa imediatamente para `STOPPING`, bloqueia trabalho pendente e mantém a propriedade do áudio até `onDestroy()` liberar os recursos. Respostas atrasadas não reativam a sessão nem renovam a notificação. Isso também evita uma segunda sessão durante o encerramento.

Arquivos principais: `device/VoiceController.kt`, `VoiceSelection.kt`, `VoiceInitialization.kt`, `device/voice/BackgroundVoiceService.kt`, `VoiceSessionRuntime.kt` e a sincronização de configurações em `ui/NexusApp.kt`.

## Verificação realizada

Quatro testes reais em JVM de `VoiceInitializationTest` passaram: callbacks atrasados, nova tentativa, fechamento e limites do watchdog. Evidência com hashes das fontes: [nexus41-voice-initialization-host.json](evidencias/nexus41-voice-initialization-host.json).

Esses testes não executam um mecanismo Android de fala, foco de áudio, permissões, notificação ou microfone físico. Os novos testes de seleção/normalização de idioma devem ser incluídos na compilação/teste geral pelo Gradle; este relatório não lhes atribui aprovação antecipada.

## Teste pendente no Galaxy A03

1. Instalar/ativar um mecanismo TTS com voz brasileira nas configurações do Android. Selecionar e testar essa voz no NEXUS.
2. No CORE sem modelo generativo, iniciar a sessão manual e dizer `quanto é 25 vezes 18`. Verificar se o texto reconhecido chega à calculadora e se a resposta real é falada.
3. Fazer uma pergunta geral sem modelo: deve informar o requisito real, sem permanecer esperando indefinidamente.
4. Com IA configurada, fazer uma pergunta; sair para outro aplicativo; confirmar a notificação e a resposta falada. Verificar que a fala do NEXUS não se transforma em nova pergunta.
5. Interromper pelo botão e pela notificação, inclusive durante geração/leitura. Confirmar que áudio e notificação encerram, que não existe reinício automático e que nova sessão só começa após liberar os recursos.
6. Testar chamadas, outro áudio, rede indisponível, permissão revogada e ausência de voz brasileira.

Não há detector local de palavra de ativação implementado nesta revisão. A sessão manual contínua continua limitada a 15 minutos ou 30 perguntas, com no máximo duas novas tentativas após silêncio. Fabricante, Android, memória e serviço de fala podem interrompê-la. Não há validação física de segundo plano neste ambiente.
