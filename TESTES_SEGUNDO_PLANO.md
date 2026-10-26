# Mouse global e voz em segundo plano — estado real

Atualizado em 26/10/2026. Estes resultados pertencem às alterações da versão 3.0 em desenvolvimento; não significam aprovação do Google Play Protect.

## O que foi implementado

- `GlobalMouseCameraService`: serviço em primeiro plano do tipo `camera`, iniciado manualmente a partir da tela visível, com notificação e controle PARAR. A análise CameraX/MediaPipe pertence ao serviço, sem depender da Activity ou de mostrar uma prévia.
- `BackgroundVoiceService`: serviço separado do tipo `microphone`, iniciado após autorização explícita. Compartilha o mesmo `NexusViewModel` e o mesmo modelo local do Chat, sem carregar uma segunda cópia da IA.
- Voz em português brasileiro conforme as vozes efetivamente instaladas. Motores que não enumeram as vozes podem usar o idioma oferecido pelo próprio Android; neste caso o aplicativo não declara que a voz é offline.
- O reconhecedor fica parado durante a geração e a fala sintetizada. OUVIR pode interromper a resposta da sessão; PARAR encerra o microfone; ENCERRAR TUDO solicita o encerramento dos dois serviços.
- O controlador de áudio da interface recusa escuta e fala enquanto a sessão em segundo plano é a dona do áudio. A checagem ocorre na operação real, inclusive em respostas enfileiradas e ações atrasadas, além dos botões desabilitados; o serviço tem seu próprio controlador autorizado.
- Sessão de conversa limitada a 15 minutos ou 30 perguntas, com no máximo duas novas tentativas após silêncio. O modo básico exige outro toque em OUVIR. Não existe detector permanente de “Ei, NEXUS” implementado nesta versão; não se usa um ciclo ilimitado de `SpeechRecognizer` para simular essa função.
- Mouse com suavização baseada no tempo transcorrido entre quadros, cadência de análise adaptativa de 50–500 ms, descarte de quadros antigos e calibração. O modo 3D mantém seu próprio controlador e a cadência padrão das demais telas.
- A acessibilidade executa gestos autorizados e desenha o cursor por `TYPE_ACCESSIBILITY_OVERLAY`; não solicita conteúdo das janelas nem registra eventos de outros aplicativos.
- Os dois serviços são `START_NOT_STICKY`, não iniciam no boot e liberam recursos ao encerrar. O usuário e o Android continuam podendo interrompê-los.

## Testes automatizados efetivamente executados nesta revisão

Execução local de classes Kotlin puras com JDK 17, compilador Kotlin 2.1.20 e JUnit 4.13.2: **17 testes aprovados**, sem Gradle paralelo. Saída: `/workspace/tooling/ai3-background-unit/junit-output.txt`.

| Grupo | Quantidade | O que foi verificado |
|---|---:|---|
| `HandGeometryTest` | 9 | Pinça curta, duplo clique, arrasto, cancelamento, calibração, projeção 3D e resposta temporal equivalente a diferentes taxas de quadros |
| `TrackingCadenceTest` | 5 | Descarte por intervalo, redução de frequência quando a inferência fica lenta, recuperação e limites de cadência |
| `VoiceConversationBudgetTest` | 3 | Duas tentativas após silêncio, ausência de laço automático em erros de rede/modo manual e encerramento por duração ou número de perguntas |

Essa execução não abre câmera, microfone, sintetizador ou outro aplicativo. Compilação Android, Lint e testes instrumentados devem ser consultados no relatório de compilação correspondente; não estão presumidos como aprovados aqui.

## Roteiro obrigatório em aparelho físico

**Todos os 13 testes abaixo estão PENDENTES DE EXECUÇÃO EM APARELHO FÍSICO nesta revisão.** O ambiente não tem acesso ao Samsung S9+ do usuário. Compilar um serviço ou testar sua lógica pura não comprova continuidade de câmera/microfone sob as políticas do fabricante.

Pré-condições: instalar a versão que contém estas alterações, manter o Play Protect ativado, preparar a IA local e testar a voz em Configurações. Autorizar câmera, microfone, notificações e acessibilidade somente nas funções em que são solicitados. Registrar Android, versão do NEXUS, condições de rede e o resultado observado, sem incluir dados pessoais.

| Nº | Procedimento | Resultado a conferir | Estado |
|---|---|---|---|
| 1 | Mouse Virtual → autorizar sessão → Iniciar mouse global → Home do Android | Notificação e botão PARAR continuam visíveis; cursor acompanha o indicador | Pendente |
| 2 | Com o mouse ativo, abrir outro aplicativo | Cursor continua recebendo posições reais, sem abrir uma prévia sobre o aplicativo | Pendente |
| 3 | Apontar para um botão não sensível e fazer uma pinça curta | Um toque real é executado no local indicado; duas pinças rápidas não produzem um terceiro toque | Pendente |
| 4 | Em uma página rolável, levantar dois dedos e deslocar a mão | Há rolagem na direção do deslocamento; manter os dedos parados não inicia um toque prolongado | Pendente |
| 5 | Tocar PARAR na notificação do mouse | Cursor e botão de sobreposição desaparecem, câmera é liberada e a notificação é removida | Pendente |
| 6 | Com o NEXUS visível, autorizar e iniciar sessão de voz | Notificação da sessão aparece; o microfone só começa após voz e IA estarem prontas | Pendente |
| 7 | Sair do NEXUS e abrir outro aplicativo durante a sessão | A sessão autorizada permanece ativa nas condições permitidas pelo Android | Pendente |
| 8 | Dar uma pergunta em pt-BR e aguardar | Pergunta entra na conversa, IA gera uma resposta real e TTS a lê em voz pt-BR; não reconhece a própria resposta | Pendente |
| 9 | Tocar PARAR na notificação de voz | Reconhecedor e TTS param; não ocorre reinício automático; a sessão termina | Pendente |
| 10 | Iniciar mouse e voz separadamente, depois alternar aplicativos | As duas notificações permanecem independentes e ENCERRAR TUDO libera ambos os recursos | Pendente |
| 11 | Encerrar mouse e abrir o ambiente 3D | O 3D funciona com seu controlador próprio; não reutiliza um arrasto antigo do mouse | Pendente |
| 12 | Revogar uma permissão, desligar microfone/câmera pelo Android ou abrir outra câmera | Aplicativo informa indisponibilidade e encerra/pausa de forma clara; não tenta contornar o bloqueio | Pendente |
| 13 | Conferir indicadores de privacidade antes, durante e após cada sessão | Indicadores correspondem ao recurso efetivamente ativo; após PARAR não há uso restante nem reinício oculto | Pendente |

Testes complementares: pinça mantida/soltura para arrastar; interrupção da resposta por OUVIR; parar durante a inicialização; rotação da tela; negar notificações; indisponibilidade do TTS; 15 minutos de sessão; duas tentativas após silêncio; bateria baixa, aquecimento e pressão de memória. Também pendentes em aparelho físico.

## Limitações conhecidas e funções ainda pendentes

- Serviços em primeiro plano não garantem execução ilimitada. Android, One UI, uso concorrente da câmera, revogação de permissões, aquecimento e falta de memória podem encerrar ou suspender recursos. O NEXUS informa a falha e não reinicia uma captura escondida.
- Câmera e microfone precisam ser iniciados enquanto o aplicativo está visível, conforme as restrições de permissões durante uso. Não há captura automática ao ligar o celular.
- A câmera global é exclusiva da sessão do mouse. Vision, Libras e 3D precisam liberar/disputar essa câmera de maneira explícita; não existe garantia de múltiplas sessões de câmera simultâneas.
- Aplicativos e superfícies protegidos podem recusar gestos. Não são lidos senhas, mensagens, dados bancários ou conteúdo das outras telas.
- Reconhecimento de palavra de ativação local, gesto configurável de voltar, espelhamento manual e limite de FPS configurável pelo usuário ainda precisam de implementação/validação específica. A cadência adaptativa e o espelhamento automático da câmera frontal já existem, mas não substituem esses controles.
- A instalação legítima com Play Protect ativado e a análise oficial pelo Google permanecem verificações separadas. Este relatório não certifica aprovação.
