# Auditoria de segurança, permissões e Play Protect do NEXUS

Leitura de fonte e dependências atualizada em **26/10/2026, 02:06 UTC**. Este documento pertence ao projeto existente `br.com.nexus.assistant`; não é um certificado de segurança nem uma aprovação do Google.

**Conclusão atual:** o usuário relatou bloqueio de instalação da alpha.4 pelo Play Protect. A causa específica não foi fornecida pelo Google e não foi reproduzida por este ambiente. Compilar, assinar ou executar testes do aplicativo não demonstra que o bloqueio acabou. Não há solicitação enviada, número de atendimento ou aprovação a apresentar.

## Escopo e procedência das observações

Foram lidos o manifesto de fonte, o último manifesto debug mesclado disponível, o relatório de origem da mesclagem, configuração e código da acessibilidade, serviço da câmera, consentimento da sessão global, rede, armazenamento, scripts de preparação e fluxo de assinatura.

O manifesto intermediário observado em `app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml` ainda identifica **alpha.4, versão 23, Android mínimo 26/alvo 35**. Seu relatório de origem está em `app/build/intermediates/manifest_merge_blame_file/debug/processDebugMainManifest/manifest-merger-blame-debug-report.txt`. **Ele não prova o conteúdo do APK 3.0 em desenvolvimento.** Uma compilação nova deve regenerar os dois arquivos e comparar o resultado antes da entrega.

Mudanças de IA, voz em serviço e visão estão sendo desenvolvidas em paralelo. Uma funcionalidade planejada não é tratada abaixo como compilada ou testada. A seção final separa testes de fonte/artefato dos testes físicos que continuam pendentes.

## Permissões e componentes encontrados

| Declaração | Origem observada | Uso e limite |
| --- | --- | --- |
| `INTERNET` | Manifesto do app | Provedor de IA opcional, preparação de modelos na variante sem pesos e possíveis transmissões dos SDKs. A inferência local não precisa de rede, mas isso não prova ausência de todo tráfego do processo. |
| `ACCESS_NETWORK_STATE` | Manifesto do app | Estado de conectividade; não permite ler mensagens de outros aplicativos. |
| `CAMERA` | Manifesto do app | Captura e rastreamento de mãos iniciados pelo usuário. Permissão de execução precisa ser concedida. |
| `RECORD_AUDIO` | Manifesto do app | Reconhecimento de voz por serviço Android iniciado pelo usuário. O mecanismo de fala escolhido pode usar rede. |
| `FOREGROUND_SERVICE` | Manifesto do app | Execução de sessão explícita com notificação. Não elimina limites de bateria, processo ou permissões. |
| `FOREGROUND_SERVICE_CAMERA` | Manifesto do app | Tipo `camera` do serviço global de mouse. Deve começar enquanto a atividade possui acesso válido à câmera. |
| `FOREGROUND_SERVICE_MICROPHONE` | Manifesto de fonte 3.0 | Tipo `microphone` da sessão de voz independente. Início manual enquanto o app está visível, com notificação e parada. Conferir declaração no novo manifesto mesclado. |
| `POST_NOTIFICATIONS` | Manifesto do app | Autorização de notificações no Android 13+. A sessão global exige aviso e ação de parada visíveis. |
| `br.com.nexus.assistant.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX Core 1.16.0, no manifesto mesclado | Permissão própria de nível `signature` para receptores protegidos; não é permissão de SMS, contatos ou monitoramento. |
| `BIND_ACCESSIBILITY_SERVICE` | Proteção declarada no componente de acessibilidade | Permite que o Android vincule o serviço quando o usuário o habilita. Não é um `uses-permission` solicitado pelo app para ler todos os dados do telefone. A capacidade de executar gestos continua sensível. |

Não foram encontradas no manifesto de fonte atual nem no último mesclado inspecionado: `READ_SMS`, `RECEIVE_SMS`, leitura de contatos/chamadas, localização, `REQUEST_INSTALL_PACKAGES`, `MANAGE_EXTERNAL_STORAGE`, `QUERY_ALL_PACKAGES`, `SYSTEM_ALERT_WINDOW`, receptor próprio de boot ou permissão para desativar o Play Protect. Isso é uma observação do escopo inspecionado; o manifesto final de cada variante precisa continuar sendo verificado.

O app consulta atalhos de aplicativos para seus comandos. Isso é diferente de uma permissão de visibilidade irrestrita ou de leitura do conteúdo de suas telas.

### Componentes inseridos por bibliotecas

O último build debug inclui `PreviewActivity` do Compose e `ComponentActivity` do manifesto de testes/UI, além dos componentes AndroidX Camera, Startup, Room e ProfileInstaller. Há atividades debug exportadas; elas não devem ser confundidas com componentes de produção revisados. **O release final deve demonstrar ausência das atividades de tooling/teste**, com `debuggable=false`. O receptor `ProfileInstallReceiver` é exportado sob a permissão de sistema `DUMP`; os serviços Room/Camera/Startup observados não são exportados.

O mesmo manifesto inclui serviços e receptor de **Google DataTransport**, originados de `transport-backend-cct:3.1.0` e `transport-runtime:3.1.0`. A presença deles levou à inspeção do SDK MediaPipe descrita abaixo; não foi presumido que fossem inativos.

## Acessibilidade e mouse global

Fontes: [XML do serviço](app/src/main/res/xml/nexus_accessibility_service.xml), [implementação](app/src/main/java/br/com/nexus/assistant/gestures/global/NexusAccessibilityService.kt), [consentimento](app/src/main/java/br/com/nexus/assistant/gestures/global/GlobalMousePanel.kt) e [serviço de câmera](app/src/main/java/br/com/nexus/assistant/gestures/global/GlobalMouseCameraService.kt).

O serviço informa `canPerformGestures=true`, `canRetrieveWindowContent=false` e `isAccessibilityTool=false`. Ao conectar, define `eventTypes=0` e `flags=0`; `onAccessibilityEvent` não coleta conteúdo. O código despacha gestos oficiais com `dispatchGesture`. O serviço é exportado para vinculação pelo sistema, protegido por `BIND_ACCESSIBILITY_SERVICE`; o serviço da câmera é `exported=false`.

O cursor e o botão **PARAR NEXUS** usam `TYPE_ACCESSIBILITY_OVERLAY`, API oficial disponível ao serviço autorizado. Não foi solicitada `SYSTEM_ALERT_WINDOW`. Isso não torna um overlay inofensivo por definição: o usuário precisa compreender que a sessão pode executar toques em outros aplicativos, e apps protegidos podem recusá-los.

A interface explica câmera, gestos em outros apps, ausência de leitura das janelas e limitação do controle. O início exige checkbox de consentimento da sessão, acessibilidade conectada, permissão de câmera e notificações. A abertura das configurações oficiais de acessibilidade é uma ação do usuário. Os controles de consentimento encontrados não equivalem à aprovação da política de acessibilidade da Google Play; a divulgação e o formulário de declaração aplicáveis ainda precisam de revisão para publicação.

O serviço de mouse inicia uma notificação de câmera e controle ativos, contém ação **PARAR**, mantém parada no overlay e não usa uma câmera de preview sobre outros aplicativos. Ocultar a prévia não oculta os indicadores oficiais de privacidade. A ausência de quadros cancela gestos mantidos e pode encerrar a sessão por falha. `START_NOT_STICKY` evita reinício automático depois que o processo morre. Não há promessa de execução permanente; a ação de parar do usuário, revogação, câmera ocupada, memória e decisões do sistema podem encerrá-lo.

Acessibilidade é uma capacidade potencialmente relevante à triagem de segurança; **não foi demonstrado que ela seja a causa do aviso específico**. Não foi alterado o nome/pacote, escondida a declaração ou removida divulgação para tentar contornar a análise.

## Câmera, microfone e segundo plano

Câmera e microfone exigem permissão de execução e respeito ao estado visível/permitido da atividade. Um serviço foreground precisa declarar seu tipo correto e iniciar de acordo com as restrições da versão Android. Ter uma notificação ou um `startForegroundService` não concede automaticamente acesso enquanto o app está em segundo plano.

O manifesto de fonte 3.0 declara dois serviços próprios independentes e não exportados: `GlobalMouseCameraService` de tipo `camera` e `BackgroundVoiceService` de tipo `microphone`. O último manifesto mesclado anterior ainda é alpha.4 e não comprova o empacotamento da nova voz. A nova fonte exige início pela interface visível, `RECORD_AUDIO`, notificações autorizadas e canal não bloqueado. Usa a mesma instância de IA da interface, pausa reconhecimento durante a geração/fala e encerra o reconhecedor/TTS na parada. `START_NOT_STICKY` evita reinício oculto.

A sessão de voz possui botão OUVIR e PARAR na notificação; o modo de conversa é limitado a 15 minutos/30 comandos e no máximo duas novas tentativas por silêncio. Não há detector local de palavra de ativação implementado nem `SpeechRecognizer` em reinício infinito. Esses limites são declarados na interface e não prometem escuta permanente. Continuidade em outro aplicativo, autorização/indicadores e parada ainda exigem teste físico com o mecanismo de fala instalado.

TextToSpeech e SpeechRecognizer são mecanismos Android reais, potencialmente fornecidos por outro aplicativo. Voz de rede e reconhecimento de rede podem enviar o texto/áudio ao mecanismo escolhido. Preferir modo local e dados de voz instalados não comprova processamento local em todo aparelho. Isso precisa aparecer na experiência e na política de privacidade.

## Achado confirmado: telemetria da distribuição MediaPipe

Foi inspecionado o AAR Maven **`com.google.mediapipe:tasks-core:0.10.35`**, 21.342.753 bytes, SHA-256 `4edf2f33c840d682c751b3ca951b1ae0465373734776d7fb37dcef936de28ad0`, obtido no cache de dependências da compilação. A análise de bytecode com `javap` constatou este caminho:

```text
TasksStatsLoggerFactory.create
  → TasksStatsProtoLogger.create
  → RemoteLoggingClient
  → Google DataTransport / CCTDestination / Transport.send
```

Há registro de ID e versão do aplicativo, sessões, erros, contagens de invocações e latência. A inspeção realizada **não demonstrou envio de quadros, conversas ou áudio por esse logger**, nem fornece uma captura completa do tráfego. Ainda assim, ela demonstra uma capacidade concreta de telemetria de uso: não é correto afirmar que o app inteiro não pode transmitir nada apenas porque o LLM e o OCR são locais.

A [configuração oficial upstream v0.10.35](https://github.com/google-ai-edge/mediapipe/blob/v0.10.35/mediapipe/tasks/java/com/google/mediapipe/tasks/core/BUILD) documenta `ENABLE_TASKS_USAGE_LOGGING=1` e a geração padrão de uma fábrica que usa `TasksStatsDummyLogger` sem operação. A variante Maven inspecionada escolhe o logger remoto. Não foi encontrada uma opção pública de `BaseOptions`/`TaskRunner` para desativá-lo no AAR já compilado. **Adicionar uma propriedade arbitrária ao Gradle não desliga a telemetria do AAR.**

Foi implementada uma **variante derivada e documentada**, preparada por [scripts/prepare_mediapipe_runtime.py](scripts/prepare_mediapipe_runtime.py). O script verifica o AAR Maven e o BUILD upstream por tamanho/hash, compila a fábrica padrão no-op em Java 8 usando JDK 17 e substitui **somente `TasksStatsLoggerFactory.class`**. Usa `TasksStatsDummyLogger.create`, exatamente a escolha da geração padrão upstream; não usa reflexão em campos privados nem altera o código nativo. O resultado não é apresentado como um AAR Google original.

A derivação foi executada com entradas locais verificadas, repetida e executada novamente pelo CLI padrão com downloads HTTPS. O resultado reproduzível tem **50.686.496 bytes**, SHA-256 **`25ac823c37f77d2870c32bfbe0dfae30b2b1a921b7fce395e743399c3b638bf3`**. A descompilação `javap -c` mostra `create → TasksStatsDummyLogger.create`. As outras **818 entradas de `classes.jar`**, todos os quatro `.so`, o manifesto e recursos conservam exatamente seus payloads originais. ZIPs são reempacotados sem compressão para reprodução independente da versão do zlib. Onze testes automatizados da preparação passaram. Os hashes por entrada e a proveniência estão em [docs/evidencias/ai3-mediapipe-runtime.json](docs/evidencias/ai3-mediapipe-runtime.json); licença e aviso de modificação acompanham os assets.

As classes remotas e dependências CCT permanecem para preservar compatibilidade do restante do SDK; a fábrica deixa de selecioná-las. Isso é uma correção verificável do caminho de logging de uso encontrado, **não uma certificação de ausência de todo tráfego nem aprovação do Play Protect**. A integração Gradle, inspeção do APK final, testes de mãos/classificador na variante e análise dinâmica de rede ainda devem ser registrados após a compilação. A verificação do arquivo final continua pendente nesta leitura.

## Dependências, modelos e carregamento

As dependências têm versões declaradas no Gradle; llama.cpp é preparado por script com revisão e SHA-256 fixados, e não é compilado/fetchado silenciosamente durante o uso do app. Os modelos GGUF e OCR passam por tamanho/hash fixados. Não foram encontradas nas rotas de IA/câmera inspecionadas importação remota de DEX, execução de scripts baixados ou instalação de outro APK; `System.loadLibrary` carrega a biblioteca JNI empacotada.

A auditoria identificou uma lacuna no script anterior de mãos: ele validava URL oficial versionada, tamanho amplo e ZIP, mas o SHA-256 era opcional. **Nesta etapa, `scripts/fetch_hand_model.py` foi corrigido para exigir por padrão o tamanho exato de 7.819.105 bytes e SHA-256 `fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1`.** Foram adicionados redirecionamento/final HTTPS, recusa de symlink, promoção exclusiva e preservação de arquivos concorrentes. Nove testes Python dessa preparação passaram; o asset local oficial existente também foi conferido. Isso corrige integridade da cadeia de preparação, não comprova a causa do bloqueio do Google.

Todo modelo e SDK novo precisa do mesmo controle de origem, licença, revisão, integridade e inclusão no artefato final. Ter código aberto não elimina vulnerabilidades ou a necessidade de acompanhar correções. Esta revisão não executou um scanner completo de CVEs nem substitui análise dinâmica.

## Rede, backend e dados

O perfil remoto exige consentimento de envio e usa HTTPS. A configuração de produção bloqueia tráfego HTTP sem TLS; o build debug permite apenas loopback/host do emulador para desenvolvimento. O servidor opcional possui token, limites e validações, mas sua fonte não equivale a um backend público implantado e seguro. Ele deve operar atrás de HTTPS e controles de acesso quando exposto fora da máquina local.

O token de sessão é separado do DataStore e protegido por Android Keystore. Nome, preferências e histórico são dados pessoais locais; Room não foi identificado como um banco com criptografia adicional própria. O app desabilita backup e exclui dados na política de extração, mas isso não é proteção contra root, aparelho comprometido ou exportação escolhida pelo usuário. O JSON exportado contém textos pessoais e precisa de cuidado ao compartilhar. Não registrar conteúdo pessoal, credenciais ou imagens em logs técnicos sem consentimento apropriado.

## Assinatura, APK auditado e distribuição

O APK histórico **alpha.4 build 13**, 615.085.340 bytes, tem SHA-256 `0c7059c77d07bc7eed40d5d7701a36e98ca3495a1d77a74c2500f7c7e85286df` e certificado SHA-256 `65297709dfb7a0149397313c1249cf40b3392913a4050f0af0c8a5951c394c1f`. É assinatura Android Debug, e os certificados de testes anteriores mudaram. Essa instabilidade impede atualizações diretas entre alguns APKs; **não foi demonstrado que seja a causa do Play Protect**.

O projeto tem configuração de assinatura release por ambiente, preparação temporária protegida da chave e workflow manual restrito à main, sem fallback para debug. A configuração validada com chave temporária de teste não é uma chave definitiva, um APK release testado nem uma assinatura da Google Play. Não incluir chaves/keystores/senhas no Git ou no APK; manter recuperação segura da chave do responsável. Publicar um AAB ou obter sucesso no Gradle não significa que a revisão de políticas ou a instalação passou.

A [minuta de revisão](docs/PLAY_PROTECT_REVISAO.md) identifica o APK exato bloqueado. O formulário oficial requer o responsável e sua conta; nenhum envio ou resposta do Google foi realizado pelo ambiente. Não desativar o Play Protect, não orientar bypass da verificação e não remover uma instalação existente enquanto a nova está bloqueada.

## Riscos confirmados, hipóteses e estado

| Item | Classificação nesta auditoria | Próximo requisito |
| --- | --- | --- |
| Usuário relata bloqueio da alpha.4 | Relato confirmado; causa técnica desconhecida | Revisão oficial do arquivo exato e resposta do Google |
| Acessibilidade executa gestos em outros apps | Capacidade sensível confirmada, com controles de sessão encontrados | Divulgação/política e testes físicos; não chamar de causa comprovada do bloqueio |
| Manifesto debug inclui tooling exportado e `debuggable=true` | Confirmado no último manifesto intermediário alpha.4 | Conferir variante release nova e sua assinatura |
| MediaPipe Maven possui logger remoto de uso | Confirmado por bytecode; derivação no-op preparada e bytecode verificado nesta etapa | Integrar/inspecionar APK novo e testar mãos/classificador e rede; não prova liberação pelo Google |
| Script anterior de mãos não exigia SHA por padrão | Confirmado e corrigido nesta etapa | Regredir pipeline completo e conferir asset no novo APK |
| Falta assinatura definitiva configurada | Pendente da identidade/segredos do responsável | Chave estável e build release/testes; não promete liberação do Google |
| Novo microfone em serviço e tipos/permissões | Fonte implementada, leitura de controles e limites realizada | Manifesto mesclado novo, notificações, permissões e testes físicos de ciclo de vida |
| Ausência de código malicioso ou de toda vulnerabilidade | Não certificada por esta leitura | Revisão contínua, análise de dependências/binário e testes dinâmicos |

## Protocolo físico proposto: 13 cenários ainda pendentes

Os cenários abaixo são um plano de validação em aparelho. **Nenhum foi marcado como aprovado por esta auditoria.** Instrumentação com emulador sem Google Play não reproduz a classificação do Play Protect nem garante o comportamento de um Samsung Galaxy S9+.

| Caso | Critério de aceitação | Estado |
| --- | --- | --- |
| 1. Instalação com Play Protect ativo | Instalação aceita pelo sistema, sem desativação da proteção; registrar versão/hash/certificado e texto do resultado | Pendente |
| 2. Primeiro uso sem consentimentos | Câmera, microfone e acessibilidade não iniciam por conta própria | Pendente |
| 3. Negar câmera | Mensagem compreensível; demais funções seguem; nenhum sensor ativo | Pendente |
| 4. Negar microfone | Texto continua disponível; nenhuma escuta ativa ou repetição automática de permissão | Pendente |
| 5. Acessibilidade desabilitada | Mouse global não executa gestos nem afirma estar controlando outro app | Pendente |
| 6. Início explícito do mouse global | Consentimento, indicador/notificação, cursor único e ausência de leitura de conteúdo | Pendente |
| 7. Parada pela notificação | Câmera, cursor e gestos mantidos encerram; não reiniciam sozinhos | Pendente |
| 8. Parada pelo overlay | Mesma liberação imediata e verificável da sessão global | Pendente |
| 9. Sair do app e remover recentes | Somente a sessão foreground autorizada permanece quando o sistema permitir; aviso/PARAR disponíveis | Pendente |
| 10. Bloqueio de tela e economia de bateria | Sem prometer continuidade; falha/parada segura, sem gesto preso ou reinício oculto | Pendente |
| 11. Câmera ocupada, permissão revogada ou mão perdida | Cancelamento seguro, erro claro e liberação de recursos | Pendente |
| 12. Voz foreground e interrupção | Início explícito, tipo correto, aviso, resposta audível, interrupção e parada do microfone; testar serviço instalado real | Pendente |
| 13. Dados e rede | Verificar tráfego por capacidade, telemetria de SDK, consentimento remoto, exportação/exclusão e ausência de conteúdo privado nos logs | Pendente |

## Fontes oficiais consultadas

As oito páginas abaixo retornaram HTTP 200 em nova consulta em 26/10/2026, 02:05 UTC; isso comprova acesso à documentação, não aprovação do NEXUS.

- [Google Play Protect](https://developers.google.com/android/play-protect)
- [Formulário oficial de revisão](https://support.google.com/googleplay/android-developer/contact/protectappeals)
- [Política de AccessibilityService da Google Play](https://support.google.com/googleplay/android-developer/answer/10964491)
- [Serviços de acessibilidade no Android](https://developer.android.com/guide/topics/ui/accessibility/service)
- [Tipos de serviço foreground, câmera e microfone](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Restrições para iniciar serviços em segundo plano](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Assinatura de aplicativos Android](https://developer.android.com/studio/publish/app-signing)
- [BUILD upstream MediaPipe v0.10.35 e logging de uso](https://github.com/google-ai-edge/mediapipe/blob/v0.10.35/mediapipe/tasks/java/com/google/mediapipe/tasks/core/BUILD)
