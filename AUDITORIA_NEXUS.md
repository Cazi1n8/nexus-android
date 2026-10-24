# Auditoria do NEXUS — início da evolução 2.0

Data: 23/10/2026 (São Paulo). Base examinada: `dec1afade67b0f048ac5004596a947b11ed3b769`, no mesmo repositório `Cazi1n8/nexus-android`. O usuário confirmou instalação no Samsung Galaxy S9+. A versão do Android, os mecanismos de fala instalados e a memória livre ainda não foram medidos nesse aparelho.

Esta auditoria foi feita antes de alterar o código do aplicativo. As referências abaixo são da base examinada. Relatos de uso e hipóteses de hardware não são apresentados como testes executados.

## Estrutura e recursos preservados

O aplicativo é Android nativo: Kotlin, Jetpack Compose, MVVM, Coroutines, Room, DataStore, CameraX e MediaPipe Tasks Vision. O build usa AGP 8.9.2, Kotlin 2.1.20, Gradle 8.11.1, Java 17, SDK 35 e Android mínimo 26. Há uma Activity, sem serviços próprios declarados no manifesto.

Módulos existentes: `data/AiProvider.kt` (HTTP real), `NexusViewModel.kt` (conversas), `device/VoiceController.kt` (TTS/STT), `device/CameraPanel.kt` (captura), `gestures/HandCamera.kt` (MediaPipe), `gestures/HandGeometry.kt` (heurísticas), `gestures/GestureFeatureScreen.kt` (mouse/mãos/cubo), `commands/PhoneCommands.kt` e `ui/NexusApp.kt` (navegação, configurações e Libras).

Preservar: banco Room v1 e histórico, memórias manuais, exclusão em cascata, exportação sem credenciais, token protegido por Keystore, consentimento HTTP, cancelamento de chamadas, permissões sob demanda, comandos Android por intents oficiais, tema e identidade do app. A base tinha build aprovado, 27 testes JVM e 44 testes Python; isso não certificou câmera, fala ou gestos no aparelho.

## Problemas confirmados no código

| Área | Evidência na base | Consequência |
| --- | --- | --- |
| IA padrão ausente | `data/NexusSettings.kt:5-6`, `NexusViewModel.kt:27,245-248` | Somente cliente HTTP; todo envio exige endpoint, modelo e consentimento remoto. Não existe inferência local Android. |
| Fluxo comum exige servidor | `ui/NexusApp.kt:203-215,252-253,497-513` | Home/chat enviam o usuário às configurações de URL e token. |
| Provedores sem isolamento | `data/NexusSettings.kt:4-15`, `SettingsRepository.kt:23-44` | Não há modo NEXUS AI separado de um perfil personalizado opcional. |
| Sem ferramentas da IA | `AiProvider.kt:25-34,87-92,212-225`; `PhoneCommands.kt:56-81` | Resposta é apenas texto; comandos exatos são um caminho independente. Não há argumentos estruturados validados nem confirmação de ferramentas. |
| Contexto apenas recortado | `AiProvider.kt:147-150,170-184` | Limite de 24 mensagens/30 mil caracteres/duas imagens; não há resumo persistente nem recuperação por relevância. |
| Voz estrangeira pode sobrescrever pt-BR | `VoiceController.kt:256-276,322-324`; `ui/NexusApp.kt:520-523` | Todas as vozes são listadas; uma voz escolhida em inglês/japonês é aceita depois de `setLanguage`, substituindo o idioma solicitado. |
| Preferência de leitura sem efeito | `NexusSettings.kt:13`, `SettingsRepository.kt:43,58` | `autoSpeak` é persistido, mas não há consumidor para leitura automática opcional do chat. Tom de voz não é configurável. |
| Mouse limitado ao laboratório | `GestureFeatureScreen.kt:144-156` | Pinça altera contadores, um alvo e um quadrado desenhado. Não dispara toque em botões reais do app ou do Android. |
| Mouse encerra na navegação | `GestureFeatureScreen.kt:70-93,131`; `HandCamera.kt:138,145-150` | Rastreador/câmera pertencem ao Composable e ao LifecycleOwner da tela, que são descartados ao navegar. |
| Rolagem não implementada | `HandGeometry.kt:61-98`, `GestureFeatureScreen.kt:144-156` | Máquina de pinça possui clique/arrasto, mas não há modo/evento de rolagem nem despacho de toque. |
| Controle global ausente | `AndroidManifest.xml:3-5,30-38` | Sem AccessibilityService, sobreposição, serviço de câmera, notificação de sessão ou parada de emergência. Não é uma simples permissão esquecida. |
| Modos acoplados estruturalmente | `GestureFeatureScreen.kt:44-93,139-184` | Um Composable declara estado/interpretação dos três modos; a visão não está isolada dos controladores. O `enum` impede executar o ramo de clique do mouse no ramo do cubo: competição simultânea não foi comprovada. |
| Rastreamento sem identidade persistente | `HandCamera.kt:84,110-115` | Usa `RunningMode.IMAGE`, redetecta quadros e descarta handedness; `firstOrNull()` pode trocar a mão que controla o cursor. |
| Interação 3D insuficiente | `GestureFeatureScreen.kt:168-184`, `HandGeometry.kt:119-137` | Projeção matemática de cubo em Canvas; sem seleção espacial do objeto e a escala muda com duas mãos mesmo sem ambas segurarem o cubo. Não há renderer OpenGL/Filament/SceneView. |
| Mãos livres restritas | `GestureFeatureScreen.kt:46-47,158-165` | Apenas nenhuma ação, contador, voltar e abrir comandos; não há associação a aplicativos ou navegação global. |
| Libras sem exercícios | `ui/NexusApp.kt:627-638` | Apenas textos, link externo e atalho para mãos; não há biblioteca, referência visual própria, avaliação ou progresso. |
| Diagnóstico incompleto | `ui/NexusApp.kt:641-669` | Mostra disponibilidade e atalhos; não testa inferência, pt-BR, controlador persistente, duas mãos ou permissões globais. |

## Limites e pontos que exigem aparelho

- Não foram reproduzidos no S9+ atrasos, perda de mão, quedas ou falhas de permissões. São relatos a investigar com diagnóstico e testes físicos.
- STT já solicita `pt-BR`, possui fallback e encerra a sessão; não é correto afirmar que o idioma errado no reconhecimento foi confirmado. A disponibilidade de serviços e pacotes no aparelho precisa ser checada.
- Não há credencial nem infraestrutura de IA online do desenvolvedor configurada. Não será inventado um endpoint gratuito permanente. A primeira estratégia será inferência local com modelo público, identificado e baixado com consentimento.
- Um modelo compacto em um celular antigo não equivale à capacidade de serviços grandes. Informar tamanho, uso de memória, velocidade variável e limitações; não prometer multimodalidade em um modelo apenas de texto.
- Câmera em segundo plano exige arquitetura oficial: iniciar sessão visível, serviço de câmera em primeiro plano quando necessário, permissões adequadas, indicador e parada. Android e fabricantes podem restringir sessões e gestos globais.
- Acessibilidade deverá ser opcional, com divulgação explícita, sem coleta de árvore/conteúdo privado de outros apps. Ações da IA não podem ser executadas a partir de texto arbitrário.
- Uma configuração de dedos reconhecida não certifica um sinal completo de Libras. Conteúdo sem referência ou sem avaliação confiável permanece indisponível ou recebe limites expressos.

## Plano de correção e critérios verificáveis

1. **IA e voz:** adicionar NEXUS AI local como padrão, catálogo de modelos com download/checagem/cancelamento, JNI de runtime real, contexto e memória preservados; manter HTTP personalizado separado. Filtrar e validar voz por idioma, padrão pt-BR, velocidade/tom/leitura automática e ajuda de instalação. Testar transporte, inferência real possível e build antes de avançar.
2. **Mouse:** separar tracker e controlador; sessão persistente durante navegação, cursor único, histerese/calibração/perda de mão, eventos de toque e rolagem reais. Depois integrar controle global opcional com APIs oficiais, notificação e parada de emergência; não marcar como testado sem dispositivo.
3. **Mãos livres:** controlador e configuração independentes; ações em lista permitida e abertura de apps; estabilidade e prevenção de disparos repetidos.
4. **3D:** identidade das mãos, renderer 3D e máquina de estados de seleção, duas pinças, movimento/rotação/escala/liberação; sem gerar cliques do mouse.
5. **Libras:** exercícios pequenos e fundamentados, referência visual, câmera, avaliação limitada e progresso; não inventar validação pedagógica ou linguística.
6. **Qualidade:** testes de regressão, diagnóstico por capacidade, instruções para S9+, changelog e APK de teste. Cada sistema recebe um dos estados definidos em `PROGRESSO_NEXUS_2.md`.

As mudanças serão incrementais e versionadas no mesmo repositório. A compilação e testes automatizados são evidências distintas dos critérios que dependem de câmera, microfone e interação no celular.
