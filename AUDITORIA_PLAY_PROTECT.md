# Auditoria de segurança, permissões e Play Protect do NEXUS

## Continuidade alpha.2 / código 28 — 29/10/2026

**Não existe aprovação nova do Play Protect.** A alpha.2 mantém o pacote e as oito permissões Android da alpha.1. A compilação final passou na auditoria do APK real, com a permissão interna AndroidX de nível assinatura, nove `uses-permission` no total, serviços privados e acessibilidade limitada a gestos. O arquivo é assinado com certificado de teste, não identidade de produção. Instalação física com proteção ativa continua pendente.

A revisão do ciclo de vida encontrou comportamentos concretos que precisavam de correção: um resultado de câmera podia repor a sessão como ativa depois de PARAR; a conclusão de um gesto podia disparar outro toque da fila depois de PAUSAR/PARAR; callbacks de escuta/resposta podiam sobreviver ao pedido de encerramento da voz. A fonte agora bloqueia trabalho novo imediatamente e libera os recursos do serviço. O mouse também revê câmera, acessibilidade e notificações durante a sessão. Essas correções diminuem ações indesejadas após uma parada, **sem afirmar que causaram ou resolveram o bloqueio específico do Google**. [Revisão detalhada](docs/NEXUS_GLOBAL_SEGURANCA_4_1_CONTINUIDADE.md) e [voz](docs/VOZ_NEXUS_4_1_REVISAO.md).

O novo transporte usa SSE real, limita eventos/texto, exige conclusão válida e cancela o socket ao interromper a solicitação. A pesquisa opcional aceita somente uma consulta para um SearXNG configurado pelo operador: não aceita URL arbitrária do cliente/modelo nem visita as páginas dos resultados. Imagens e contexto só podem ser enviados ao provedor autorizado; reduzir resolução não substitui consentimento. O gateway atual é para ambiente controlado e ainda não é serviço público multiusuário implantado. [Protocolo, autenticação e implantação](server/README.md).

Nenhuma permissão, capacidade de leitura de outros aplicativos, dependência grande ou modelo obrigatório foi adicionada por essas alterações. Os protótipos de modelos usados no computador são dados de avaliação, não código remoto carregado no Android. A publicação automática continua suspensa. Assinatura de produção estável, teste físico e eventual [revisão oficial](docs/PLAY_PROTECT_REVISAO.md) permanecem pendentes; não foi solicitado desativar a proteção.

A compilação final da alpha.2 passou em 7m09s com **298 testes JVM**, 173 testes Python e 56 testes do servidor aprovados. O Lint debug/coreTest não reportou erros, com 57 avisos e uma informação por variante. Isso não aprova o Play Protect nem dispensa os novos testes Android e físicos. A avaliação real de texto e visão **reprovou a qualidade dos candidatos**; nenhuma resposta do modelo é tomada como evidência de acesso a câmera ou conteúdo de outros aplicativos.

O APK CORE final local tem **108.635.674 bytes**, SHA-256 `556872b2ecc467237017ec5e7c43f177996f372cf8a6599559bdd30f388e773f`, certificado de teste `ffc6524eee21a185872e946e78649a977f4b82011ee573c20adf5ca6ba376f79`. As [verificações compiladas](docs/evidencias/n42-security-package-audit.json) confirmam HTTPS, capacidades/componentes e assinatura dentro da política auditada; o [relatório integrado](docs/evidencias/n42-build-verification.json) identifica os arquivos. Não é uma análise de todas as vulnerabilidades nem o veredito do Google. O release de 108.623.386 bytes continua sem assinatura e não instalável por falta da chave estável do responsável. A publicação pública permanece suspensa.

## Revisão prioritária NEXUS 4.1 — Galaxy A03, 28/10/2026

**O aparelho de teste informado é o Samsung Galaxy A03.** O usuário confirmou que o APK CORE continuou bloqueado com a mensagem sobre acesso a dados sensíveis. Não há acesso físico a esse aparelho neste ambiente. Não houve varredura do novo APK pelo Play Protect, resposta do Google ou confirmação de liberação. **A distribuição de uma versão considerada pronta permanece pendente desses requisitos.** O histórico abaixo conserva os identificadores das versões anteriores; referências antigas ao S9+ não representam o aparelho desta revisão.

### O que pode explicar o aviso — sem inventar um veredito

A [documentação oficial do Play Protect](https://support.google.com/googleplay/answer/2812853?hl=pt-BR), consultada novamente com HTTP 200 em 28/10/2026, informa que a proteção contra fraude pode impedir a instalação de aplicativos não verificados com permissões sensíveis. O [artigo oficial sobre a proteção reforçada contra fraude](https://security.googleblog.com/2024/02/piloting-new-ways-to-protect-Android-users-from%20financial-fraud.html) descreve análise de aplicativos instalados por navegador, mensagens ou gerenciadores de arquivos e inclui **acessibilidade** entre as capacidades sensíveis consideradas. O espaço codificado no segundo URL faz parte do endereço publicado pelo Google.

Isso é **compatível com a distribuição do NEXUS por APK no GitHub, com controle global por acessibilidade e com o texto do aviso**. É uma hipótese sustentada pela política oficial, **não a causa individual confirmada do APK**. O artigo descreve inicialmente um piloto; não permite inferir a configuração regional ou o algoritmo ativo no A03. A recusa também não permite concluir que o aplicativo seja um falso positivo. Assinatura válida, `debuggable=false`, ausência de SMS e `canRetrieveWindowContent=false` não garantem que a classificação desapareça.

Não foram removidas, renomeadas ou escondidas as declarações necessárias ao mouse para evitar a inspeção. Não é proposto desativar o Play Protect, alterar o instalador para evitar a análise, disfarçar o aplicativo ou capturar dados de outros apps. A alternativa de distribuição é o canal oficial de testes da Google Play, com identidade estável e revisão das declarações aplicáveis; ele também depende da análise do Google e não garante aprovação.

### Inspeção do APK público CORE que o usuário recebeu

O arquivo público `NEXUS-3.0.0-alpha.3-core.apk` foi inspecionado **como APK**, com `apkanalyzer` para o manifesto e os XMLs compilados efetivos, e `apksigner verify --verbose --print-certs` para a assinatura. A extração resolve os nomes reduzidos dos recursos (`res/…xml`), em vez de pressupor que o caminho de fonte foi mantido no ZIP.

| Campo | Resultado observado |
| --- | --- |
| Origem | Release `v3.0.0-alpha.3-core-download-22`, build 22 |
| Pacote / versão | `br.com.nexus.assistant`, `3.0.0-alpha.3-core`, código 26 |
| Tamanho | 108.210.081 bytes |
| SHA-256 APK | `e76ff4e228206a11317000a238556d8c24693532c1527b05f8264f5ba6b50d15` |
| SHA-256 certificado | `0e72073647f9fd5b4bc27f6f8fbafa9100f4fadc391433ff6844fc2f96ec97fc` |
| Assinatura | Verificada por ferramenta oficial; certificado **Android Debug** |
| SDK | Mínimo 26, alvo 35 |
| Depuração / backup | `debuggable` efetivo false; `allowBackup=false`, `fullBackupContent=false` |
| Permissões | 8 Android + permissão interna AndroidX `signature` = 9 |
| Componentes exportados | MainActivity; acessibilidade protegida por `BIND_ACCESSIBILITY_SERVICE`; ProfileInstallReceiver protegido por `DUMP` |
| Acessibilidade | `canPerformGestures=true`, `canRetrieveWindowContent=false`, `isAccessibilityTool=false`; nenhuma capacidade declarada de captura de tela ou filtragem de teclas |
| Rede | XML compilado base `cleartextTrafficPermitted=false` |
| Serviços próprios | Câmera e microfone privados, tipos foreground separados |
| Resultado de instalação física / Play Protect | Relato do usuário: bloqueado; verificação independente do arquivo neste A03 pendente |

Um certificado de teste não é uma identidade de produção estável. **Não foi demonstrado que ele tenha causado o bloqueio.** As chaves efêmeras antigas da CI não foram recuperadas; não foi criada uma chave definitiva nova que prometesse atualizar uma instalação com outro certificado. Não incluir keystore, senha ou chave privada no Git. O workflow assinado existente só pode produzir uma distribuição quando o responsável configurar os segredos protegidos; sem isso, release continua sem assinatura.

### Auditoria de permissões e dependências desta etapa

Continuam necessárias: `INTERNET` e `ACCESS_NETWORK_STATE` para integrações/downloads opcionais; `CAMERA` para mãos/visão iniciadas pelo usuário; `RECORD_AUDIO` para voz; `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CAMERA` e `FOREGROUND_SERVICE_MICROPHONE` para sessões explícitas; `POST_NOTIFICATIONS` para controles visíveis em versões aplicáveis. A permissão AndroidX de nível assinatura protege receptores internos. `BIND_ACCESSIBILITY_SERVICE` protege o vínculo do componente pelo Android; o app não a pede como permissão de leitura de dados.

Não foram encontradas no manifesto do CORE inspecionado permissões de SMS, contatos, chamadas, localização, instalação de pacotes, armazenamento amplo, `QUERY_ALL_PACKAGES`, `PACKAGE_USAGE_STATS`, `SYSTEM_ALERT_WINDOW` ou boot. A sobreposição usa `TYPE_ACCESSIBILITY_OVERLAY` somente com o serviço autorizado. O chat e a Central de Extensões não exigem ativação da acessibilidade. Câmera e microfone devem ser solicitados no início da função, não na abertura do aplicativo.

A consulta **real à API OSV** em 28/10/2026 encontrou correspondências para duas versões declaradas no projeto:

| Dependência anterior | Correspondência por versão | Correção selecionada e verificada |
| --- | --- | --- |
| `protobuf-javalite:4.26.1` | [GHSA-735f-pc8j-v9w8 / CVE-2024-7254](https://osv.dev/vulnerability/GHSA-735f-pc8j-v9w8): recursão não limitada ao interpretar campos desconhecidos pode causar StackOverflow/indisponibilidade | `4.28.3`; consulta OSV dessa versão sem correspondências reportadas |
| `guava:27.0.1-android` | [CVE-2020-8908](https://osv.dev/vulnerability/GHSA-5mg8-w23w-74h3), [CVE-2023-2976](https://osv.dev/vulnerability/GHSA-7g45-4rm6-3mm3): diretórios temporários; [CVE-2026-102554](https://osv.dev/vulnerability/GHSA-xxph-c9ww-hj94): alocação não controlada na desserialização | `33.7.2-android`; POM existente no Maven Central e consulta OSV dessa versão sem correspondências reportadas |

**A presença dessas versões é confirmada; a exploração no NEXUS não foi demonstrada.** A consulta não executou cargas maliciosas nem prova ausência de vulnerabilidades nas novas versões. A investigação de fonte não encontrou desserialização Java de objetos não confiáveis ou uso direto de `Files.createTempDir` pelo app. As bibliotecas podem ter caminhos próprios e requerem regressão. Esses avisos não são atribuídos como causa do Play Protect. Consultas limitadas de OkHttp 4.12.0, MediaPipe tasks-vision 0.10.35, Room 2.7.1 e DataTransport runtime 3.1.0 não retornaram advisories; isso não é uma varredura completa das dependências transitivas ou do código nativo.

O derivado MediaPipe sem logging de uso, os pesos por HTTPS/tamanho/SHA e o modelo de mãos obrigatório permanecem como documentados abaixo. Atualizar Guava/Protobuf no Gradle não altera o AAR derivado nem justifica modificar sua proveniência. O restante do processo precisa conferir as **versões resolvidas**, executar testes e medir o APK final novamente.

### Alterações de segurança implementadas na fonte 4.1

- O manifesto agora explicita `usesCleartextTraffic=false` além do XML de rede existente. A alteração é defesa declarativa; a distribuição anterior já tinha política base HTTPS.
- O XML da acessibilidade omite eventos/flags (padrão zero) e explicita `canTakeScreenshot=false` e `canRequestFilterKeyEvents=false`. A primeira tentativa de explicitar o valor `0` foi rejeitada pelo AAPT2 e corrigida; o serviço também impõe zero em tempo de execução. São restrições que já eram padrão na versão anterior; não são apresentadas como remoção de uma coleta que existia.
- A descrição exibida pelo Android especifica toques/arrastos/rolagem, ausência de leitura de telas/senhas/mensagens/notificações e que habilitar o serviço não inicia a câmera.
- A navegação às configurações de acessibilidade passa a depender do consentimento destacado na tela do mouse global. A permissão de câmera do mouse é pedida no início escolhido pelo usuário.
- Foi adicionado [`scripts/audit_android_security.py`](scripts/audit_android_security.py). Ele confere o manifesto de fonte/mesclado ou o **manifesto e recursos compilados de um APK**, permissões permitidas, limites dos componentes exportados, tipos separados dos serviços, capacidades da acessibilidade, HTTPS, backup e assinatura. `--production` recusa certificado Android Debug. O relatório diz explicitamente que não é um veredito do Google.

### Verificações já executadas e próximos passos

**14 testes Python** do novo auditor passaram, incluindo regressões que inserem SMS/overlay/visibilidade ampla, exportam serviços, habilitam leitura/captura/teclas, misturam tipos foreground, permitem HTTP e fornecem assinatura de teste. A inspeção regular do APK público código 26 passou: 9 permissões, componentes e XMLs dentro da política declarada, assinatura verificável. O mesmo APK com `--production` foi **recusado, como esperado, exclusivamente por `debug_signing_identity`**. Isso separa teste de distribuição de produção e não reproduz o bloqueio do Google.

A nova compilação local **4.1.0-alpha.1-core/27** passou: 235 testes JVM, 162 testes de scripts, Lint sem erros e inspeção do APK real com 9 permissões. CORE medido em **108.520.986 bytes**; Guava **33.7.2-android** e Protobuf **4.28.3** foram confirmados no classpath resolvido. Assinatura local válida é de teste, diferente do CORE público; o gate `--production` a recusou como esperado. O release é sem assinatura. Nenhum desses resultados comprova instalação/Play Protect.

O resultado de compilação, hashes e testes da nova fonte 4.1 devem ser conferidos em [PROGRESSO_NEXUS_4_1.md](PROGRESSO_NEXUS_4_1.md). As medições acima identificam estritamente o arquivo anterior, não um APK futuro. Os testes físicos do A03 permanecem pendentes: instalação com proteção ativa, divulgação antes das permissões, câmera/voz só após início manual, cursor em outro app, recusa de conteúdo protegido, pausa/parada pela notificação e liberação dos sensores. Não remover uma instalação que contém dados antes de exportar o histórico e verificar compatibilidade de certificado.

Para gerar a inspeção do APK compilado, use os caminhos oficiais de ferramentas instalados no seu SDK:

```bash
python3 scripts/audit_android_security.py \
  --apk app/build/outputs/apk/coreTest/app-coreTest.apk \
  --apkanalyzer "$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer" \
  --apksigner "$ANDROID_HOME/build-tools/35.0.0/apksigner" \
  --output security-core.json
```

Para um release de produção, acrescente `--production` e aponte para o APK realmente assinado. Um APK sem assinatura falha na verificação; não é instalável. A inspeção de fonte usa `--manifest`, `--accessibility-xml` e `--network-xml`, sem `--apk`; acrescente `--final` ao manifesto mesclado. Os XMLs de fonte não substituem a inspeção do artefato.

### Caminho oficial de revisão do CORE bloqueado

O [formulário oficial de revisão do Play Protect](https://support.google.com/googleplay/android-developer/contact/protectappeals) retornou HTTP 200. Ainda não foi enviado por esta sessão. O responsável deve informar **o APK exato bloqueado**, pacote, versão/código, URL de origem, SHA-256 do APK/certificado, screenshot, aparelho **Galaxy A03**, versão Android efetivamente observada e resultado com proteção ativa. Para o arquivo público código 26, use os identificadores da tabela acima; não reutilize os da alpha.4 na minuta histórica de [docs/PLAY_PROTECT_REVISAO.md](docs/PLAY_PROTECT_REVISAO.md).

Texto técnico sugerido: “Solicito análise do NEXUS `br.com.nexus.assistant`, código 26, CORE 3.0.0-alpha.3-core, bloqueado no Galaxy A03 com aviso de possível acesso a dados sensíveis. O controle por mãos usa AccessibilityService somente para dispatchGesture autorizado e overlay, sem recuperação de janelas, leitura de eventos ou captura de tela. Câmera/microfone são opcionais e iniciados pelo usuário com notificação e parada. O APK foi distribuído fora da Play com certificado de teste; não afirmamos falso positivo e solicitamos orientação sobre os achados concretos.” Anexar a tabela e auditoria, sem dados de conta/chaves no repositório. Se o Google solicitar a nova compilação, registrar seus próprios identificadores e não trocar silenciosamente o arquivo citado.

Revisar também a [política de AccessibilityService](https://support.google.com/googleplay/android-developer/answer/10964491?hl=pt-BR): o NEXUS não se declara `isAccessibilityTool=true`; precisa de divulgação/consentimento e declaração compatíveis com o caso de uso. Uma ação de IA não pode executar controle oculto e não autorizado. A implementação legítima do mouse é preservada, sujeita às restrições do Android e aplicativos protegidos. Assinatura estável, política de privacidade e formulários são pré-requisitos de distribuição, não atalhos para aprovação.

## Histórico das auditorias anteriores

Leitura de fonte e dependências atualizada em **26/10/2026**. Os manifestos mesclados e os APKs finais locais debug e release da versão 24 foram conferidos após a compilação completa. O APK release é sem assinatura e não é instalável. Este documento pertence ao projeto existente `br.com.nexus.assistant`; não é um certificado de segurança nem uma aprovação do Google.

**Conclusão atual:** o usuário relatou bloqueio de instalação da alpha.4 pelo Play Protect. A causa específica não foi fornecida pelo Google e não foi reproduzida por este ambiente. Compilar, assinar ou executar testes do aplicativo não demonstra que o bloqueio acabou. Não há solicitação enviada, número de atendimento ou aprovação a apresentar.

## Escopo e procedência das observações

Foram lidos o manifesto de fonte, os manifestos debug/release mesclados versão 24, os relatórios de origem da mesclagem, configuração e código da acessibilidade, serviço da câmera, consentimento da sessão global, rede, armazenamento, scripts de preparação e fluxo de assinatura.

Foram regenerados e inspecionados os manifestos intermediários **debug e release de `3.0.0-alpha.1`, versão 24, Android mínimo 26/alvo 35**. Arquivos: `app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml` e `app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml`, com os respectivos relatórios `manifest_merge_blame_file`. Ambos contêm **oito permissões Android e uma permissão própria AndroidX de nível signature: nove `uses-permission` no total**. A evidência estruturada registra hashes, componentes, flags, tipos de serviços e diferenças: [docs/evidencias/ai3-manifest-audit.json](docs/evidencias/ai3-manifest-audit.json). Essa inspeção dos manifestos foi complementada pela leitura dos dois APKs locais descrita abaixo. **O APK release gerado é sem assinatura; não foi instalado nem aprovado.**

A compilação completa das mudanças de IA, voz em serviço e visão terminou com sucesso nesta etapa. A geração dos manifestos e a estrutura dos APKs foram verificadas; uma funcionalidade não é tratada abaixo como executada no aparelho por ter sido declarada ou compilada. A seção final separa testes de fonte/artefato dos testes físicos que continuam pendentes.

## Permissões e componentes encontrados

| Declaração | Origem observada | Uso e limite |
| --- | --- | --- |
| `INTERNET` | Manifesto do app | Provedor de IA opcional, preparação de modelos na variante sem pesos e possíveis transmissões dos SDKs. A inferência local não precisa de rede, mas isso não prova ausência de todo tráfego do processo. |
| `ACCESS_NETWORK_STATE` | Manifesto do app | Estado de conectividade; não permite ler mensagens de outros aplicativos. |
| `CAMERA` | Manifesto do app | Captura e rastreamento de mãos iniciados pelo usuário. Permissão de execução precisa ser concedida. |
| `RECORD_AUDIO` | Manifesto do app | Reconhecimento de voz por serviço Android iniciado pelo usuário. O mecanismo de fala escolhido pode usar rede. |
| `FOREGROUND_SERVICE` | Manifesto do app | Execução de sessão explícita com notificação. Não elimina limites de bateria, processo ou permissões. |
| `FOREGROUND_SERVICE_CAMERA` | Manifesto do app | Tipo `camera` do serviço global de mouse. Deve começar enquanto a atividade possui acesso válido à câmera. |
| `FOREGROUND_SERVICE_MICROPHONE` | Fonte e ambos os manifestos mesclados 3.0 | Tipo `microphone` da sessão de voz independente. Início manual enquanto o app está visível, com notificação e parada. |
| `POST_NOTIFICATIONS` | Manifesto do app | Autorização de notificações no Android 13+. A sessão global exige aviso e ação de parada visíveis. |
| `br.com.nexus.assistant.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX Core 1.16.0, no manifesto mesclado | Permissão própria de nível `signature` para receptores protegidos; não é permissão de SMS, contatos ou monitoramento. |
| `BIND_ACCESSIBILITY_SERVICE` | Proteção declarada no componente de acessibilidade | Permite que o Android vincule o serviço quando o usuário o habilita. Não é um `uses-permission` solicitado pelo app para ler todos os dados do telefone. A capacidade de executar gestos continua sensível. |

Não foram encontradas no manifesto de fonte atual nem nos manifestos debug/release 3.0 inspecionados: `READ_SMS`, `RECEIVE_SMS`, leitura de contatos/chamadas, localização, `REQUEST_INSTALL_PACKAGES`, `MANAGE_EXTERNAL_STORAGE`, `QUERY_ALL_PACKAGES`, `SYSTEM_ALERT_WINDOW`, receptor próprio de boot ou permissão para desativar o Play Protect. Isso é uma observação do escopo inspecionado; o manifesto final de cada variante precisa continuar sendo verificado.

O app consulta atalhos de aplicativos para seus comandos. Isso é diferente de uma permissão de visibilidade irrestrita ou de leitura do conteúdo de suas telas.

### Componentes inseridos por bibliotecas

O manifesto debug 3.0 inclui `PreviewActivity` do Compose e `ComponentActivity` do manifesto de testes/UI, ambas exportadas. **O manifesto release 3.0 não contém essas duas atividades e seu `debuggable` efetivo é false** (atributo ausente, padrão Android); debug declara `debuggable=true`. As duas variantes incluem AndroidX Camera, Startup, Room e ProfileInstaller. No release, os componentes exportados são somente a atividade inicial `MainActivity`, o serviço de acessibilidade protegido por `BIND_ACCESSIBILITY_SERVICE` e `ProfileInstallReceiver` protegido pela permissão de sistema `DUMP`. Os serviços Room/Camera e o provider Startup observados não são exportados. Os manifestos compilados dos dois APKs também foram inspecionados: debug contém as atividades de tooling e `debuggable=true`; release não contém essas atividades, tem `debuggable` efetivo false e mantém somente os três componentes exportados descritos acima.

Ambos os manifestos incluem serviços e receptor de **Google DataTransport**, originados de `transport-backend-cct:3.1.0` e `transport-runtime:3.1.0`. A presença deles levou à inspeção do SDK MediaPipe descrita abaixo; não foi presumido que fossem inativos.

## Acessibilidade e mouse global

Fontes: [XML do serviço](app/src/main/res/xml/nexus_accessibility_service.xml), [implementação](app/src/main/java/br/com/nexus/assistant/gestures/global/NexusAccessibilityService.kt), [consentimento](app/src/main/java/br/com/nexus/assistant/gestures/global/GlobalMousePanel.kt) e [serviço de câmera](app/src/main/java/br/com/nexus/assistant/gestures/global/GlobalMouseCameraService.kt).

O serviço informa `canPerformGestures=true`, `canRetrieveWindowContent=false` e `isAccessibilityTool=false`. Ao conectar, define `eventTypes=0` e `flags=0`; `onAccessibilityEvent` não coleta conteúdo. O código despacha gestos oficiais com `dispatchGesture`. O serviço é exportado para vinculação pelo sistema, protegido por `BIND_ACCESSIBILITY_SERVICE`; o serviço da câmera é `exported=false`.

O cursor e o botão **PARAR NEXUS** usam `TYPE_ACCESSIBILITY_OVERLAY`, API oficial disponível ao serviço autorizado. Não foi solicitada `SYSTEM_ALERT_WINDOW`. Isso não torna um overlay inofensivo por definição: o usuário precisa compreender que a sessão pode executar toques em outros aplicativos, e apps protegidos podem recusá-los.

A interface explica câmera, gestos em outros apps, ausência de leitura das janelas e limitação do controle. O início exige checkbox de consentimento da sessão, acessibilidade conectada, permissão de câmera e notificações. A abertura das configurações oficiais de acessibilidade é uma ação do usuário. Os controles de consentimento encontrados não equivalem à aprovação da política de acessibilidade da Google Play; a divulgação e o formulário de declaração aplicáveis ainda precisam de revisão para publicação.

O serviço de mouse inicia uma notificação de câmera e controle ativos, contém ação **PARAR**, mantém parada no overlay e não usa uma câmera de preview sobre outros aplicativos. Ocultar a prévia não oculta os indicadores oficiais de privacidade. A ausência de quadros cancela gestos mantidos e pode encerrar a sessão por falha. `START_NOT_STICKY` evita reinício automático depois que o processo morre. Não há promessa de execução permanente; a ação de parar do usuário, revogação, câmera ocupada, memória e decisões do sistema podem encerrá-lo.

Acessibilidade é uma capacidade potencialmente relevante à triagem de segurança; **não foi demonstrado que ela seja a causa do aviso específico**. Não foi alterado o nome/pacote, escondida a declaração ou removida divulgação para tentar contornar a análise.

## Câmera, microfone e segundo plano

Câmera e microfone exigem permissão de execução e respeito ao estado visível/permitido da atividade. Um serviço foreground precisa declarar seu tipo correto e iniciar de acordo com as restrições da versão Android. Ter uma notificação ou um `startForegroundService` não concede automaticamente acesso enquanto o app está em segundo plano.

O manifesto de fonte 3.0 e **ambos os manifestos mesclados versão 24** declaram dois serviços próprios independentes e não exportados: `GlobalMouseCameraService` de tipo `camera` e `BackgroundVoiceService` de tipo `microphone`, com `stopWithTask=false`. A declaração foi confirmada nesta compilação; a continuidade real da captura/voz ainda exige execução. A nova fonte exige início pela interface visível, `RECORD_AUDIO`, notificações autorizadas e canal não bloqueado. Usa a mesma instância de IA da interface, pausa reconhecimento durante a geração/fala e encerra o reconhecedor/TTS na parada. `START_NOT_STICKY` evita reinício oculto.

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

As classes remotas e dependências CCT permanecem para preservar compatibilidade do restante do SDK; a fábrica deixa de selecioná-las. Isso é uma correção verificável do caminho de logging de uso encontrado, **não uma certificação de ausência de todo tráfego nem aprovação do Play Protect**. O Gradle já integra o AAR derivado com tamanho/hash obrigatórios, exclui o `tasks-core` Maven transitivo de `tasks-vision` e declara explicitamente as dependências do POM original. O APK debug local foi inspecionado com `apkanalyzer dex code`: a fábrica empacotada realmente chama `TasksStatsDummyLogger.create`, sem referência ao logger Proto nessa classe. A [saída DEX observada](docs/evidencias/ai3-mediapipe-dex-factory.txt) e o [relatório do APK](docs/evidencias/ai3-debug-apk-security.json) registram isso. O derivado preserva os nativos do AAR; durante o build, a etapa oficial AGP/NDK `stripDebugDebugSymbols` transforma seus bytes. Foram comparados os quatro arquivos: os `merged_native_libs` coincidem com os nativos upstream e os `.so` do APK coincidem com os `stripped_native_libs`. Portanto, não se afirma que os `.so` finais têm o mesmo hash do AAR original. O APK release otimizado por R8 também foi inspecionado: a fábrica e `TasksStatsDummyLogger.create` foram incorporadas a `TaskRunner.create`, conforme o [mapeamento R8](docs/evidencias/ai3-release-mediapipe-r8-mapping.txt); o [DEX otimizado correspondente](docs/evidencias/ai3-release-mediapipe-taskrunner-dex.txt) foi preservado. Isso não é uma observação de ausência de tráfego em execução. Testes de mãos/classificador, execução do teste Android da fábrica e análise dinâmica de rede ainda precisam de resultados próprios.

## Dependências, modelos e carregamento

As dependências têm versões declaradas no Gradle; llama.cpp é preparado por script com revisão e SHA-256 fixados, e não é compilado/fetchado silenciosamente durante o uso do app. Os modelos GGUF e OCR passam por tamanho/hash fixados. Não foram encontradas nas rotas de IA/câmera inspecionadas importação remota de DEX, execução de scripts baixados ou instalação de outro APK; `System.loadLibrary` carrega a biblioteca JNI empacotada.

A auditoria identificou uma lacuna no script anterior de mãos: ele validava URL oficial versionada, tamanho amplo e ZIP, mas o SHA-256 era opcional. **Nesta etapa, `scripts/fetch_hand_model.py` foi corrigido para exigir por padrão o tamanho exato de 7.819.105 bytes e SHA-256 `fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1`.** Foram adicionados redirecionamento/final HTTPS, recusa de symlink, promoção exclusiva e preservação de arquivos concorrentes. Nove testes Python dessa preparação passaram; o asset local oficial existente também foi conferido. Isso corrige integridade da cadeia de preparação, não comprova a causa do bloqueio do Google.

Todo modelo e SDK novo precisa do mesmo controle de origem, licença, revisão, integridade e inclusão no artefato final. Ter código aberto não elimina vulnerabilidades ou a necessidade de acompanhar correções. Esta revisão não executou um scanner completo de CVEs nem substitui análise dinâmica.

## Rede, backend e dados

O perfil remoto exige consentimento de envio e usa HTTPS. A configuração de produção bloqueia tráfego HTTP sem TLS; o build debug permite apenas loopback/host do emulador para desenvolvimento. O servidor opcional possui token, limites e validações, mas sua fonte não equivale a um backend público implantado e seguro. Ele deve operar atrás de HTTPS e controles de acesso quando exposto fora da máquina local.

O token de sessão é separado do DataStore e protegido por Android Keystore. Nome, preferências e histórico são dados pessoais locais; Room não foi identificado como um banco com criptografia adicional própria. O app desabilita backup e exclui dados na política de extração, mas isso não é proteção contra root, aparelho comprometido ou exportação escolhida pelo usuário. O JSON exportado contém textos pessoais e precisa de cuidado ao compartilhar. Não registrar conteúdo pessoal, credenciais ou imagens em logs técnicos sem consentimento apropriado.

## Assinatura, APK auditado e distribuição

O APK histórico **alpha.4 build 13**, 615.085.340 bytes, tem SHA-256 `0c7059c77d07bc7eed40d5d7701a36e98ca3495a1d77a74c2500f7c7e85286df` e certificado SHA-256 `65297709dfb7a0149397313c1249cf40b3392913a4050f0af0c8a5951c394c1f`. É assinatura Android Debug, e os certificados de testes anteriores mudaram. Essa instabilidade impede atualizações diretas entre alguns APKs; **não foi demonstrado que seja a causa do Play Protect**.

Foi criado e inspecionado um **APK debug local versão 24 / 3.0.0-alpha.1**, em `app/build/outputs/apk/debug/app-debug.apk`: **1.738.344.072 bytes**, SHA-256 **`987cd6dcee18c6c23efb648b11893ef54c27c7ecd0f6778920626c60aaa50bf3`**. `aapt2 dump badging` confirmou pacote, versão, mínimo 26/alvo 35, as nove permissões e quatro ABIs. `apkanalyzer manifest print` confirmou o manifesto compilado e os dois serviços não exportados, tipos `0x40` câmera e `0x80` microfone. `zipalign -c -P 16 4` passou e `apksigner verify --verbose --print-certs` aprovou a assinatura v2.

O certificado desse arquivo é **Android Debug**, SHA-256 **`ffc6524eee21a185872e946e78649a977f4b82011ee573c20adf5ca6ba376f79`**. É diferente do certificado da alpha.4 pública; não garante atualização direta daquela instalação. O APK debug tem `debuggable=true` e as atividades de tooling esperadas de teste. **Não é uma distribuição de produção, não foi instalado neste aparelho físico e não foi avaliado pelo Play Protect neste ambiente.** As evidências pertencem a esse arquivo local exato; uma compilação posterior ou da CI pode ter outro hash/certificado e deve ser verificada novamente.

A compilação **`assembleRelease` também terminou com sucesso**, produzindo `app/build/outputs/apk/release/app-release-unsigned.apk`: **1.715.955.100 bytes**, SHA-256 **`a109fb45fb31d6cc6abb0e5e2cc6fd850e557e7e5971417e6feb4af3eee9fd8e`**. A inspeção confirmou versão 24, mínimo 26/alvo 35, nove permissões, os dois serviços foreground não exportados, ausência das atividades de tooling e `debuggable` efetivo false. `zipalign -c -P 16 4` passou. **`apksigner verify` falhou como esperado (`DOES NOT VERIFY`, `Missing META-INF/MANIFEST.MF`), pois nenhuma chave release foi configurada: esse arquivo não é instalável e não constitui uma distribuição assinada.** O [relatório release](docs/evidencias/ai3-release-apk-security.json) preserva resultados e limites; o [manifesto compilado release](docs/evidencias/ai3-release-compiled-manifest.xml) foi extraído do arquivo real.

Os relatórios de ambos os APKs foram atualizados depois do build completo que inclui as correções de posse de áudio, explicação aritmética e licença Markwon. O hash anterior `1e336cc0…` pertencia a um APK intermediário e não identifica mais o arquivo debug final. A cadeia de nativos AAR → `merged_native_libs` → `stripped_native_libs` → APK foi conferida nas quatro ABIs de ambas as variantes, sem alterar o binário empacotado para a inspeção.

O projeto tem configuração de assinatura release por ambiente, preparação temporária protegida da chave e workflow manual restrito à main, sem fallback para debug. A configuração validada com chave temporária de teste não é uma chave definitiva, um APK release testado nem uma assinatura da Google Play. Não incluir chaves/keystores/senhas no Git ou no APK; manter recuperação segura da chave do responsável. Publicar um AAB ou obter sucesso no Gradle não significa que a revisão de políticas ou a instalação passou.

A [minuta de revisão](docs/PLAY_PROTECT_REVISAO.md) identifica o APK exato bloqueado. O formulário oficial requer o responsável e sua conta; nenhum envio ou resposta do Google foi realizado pelo ambiente. Não desativar o Play Protect, não orientar bypass da verificação e não remover uma instalação existente enquanto a nova está bloqueada.

## Riscos confirmados, hipóteses e estado

| Item | Classificação nesta auditoria | Próximo requisito |
| --- | --- | --- |
| Usuário relata bloqueio da alpha.4 | Relato confirmado; causa técnica desconhecida | Revisão oficial do arquivo exato e resposta do Google |
| Acessibilidade executa gestos em outros apps | Capacidade sensível confirmada, com controles de sessão encontrados | Divulgação/política e testes físicos; não chamar de causa comprovada do bloqueio |
| Manifesto debug inclui tooling exportado e `debuggable=true` | Confirmado na versão 24; release 24 sem essas duas atividades e com `debuggable` efetivo false | Ambos os APKs conferidos; debug com v2 válida, release sem assinatura e não instalável; instalação e revisão do Google continuam pendentes |
| MediaPipe Maven possui logger remoto de uso | Confirmado por bytecode; derivação no-op preparada e bytecode verificado nesta etapa | Gradle, DEX debug e transformação R8 release conferidos; testar mãos/classificador e rede; não prova liberação pelo Google |
| Script anterior de mãos não exigia SHA por padrão | Confirmado e corrigido nesta etapa | Regredir pipeline completo e conferir asset no novo APK |
| Falta assinatura definitiva configurada | Pendente da identidade/segredos do responsável | Chave estável e build release/testes; não promete liberação do Google |
| Novo microfone em serviço e tipos/permissões | Fonte e manifestos mesclados debug/release 24 confirmados, controles/limites lidos | Notificações, permissões e testes físicos de ciclo de vida |
| Ausência de código malicioso ou de toda vulnerabilidade | Não certificada por esta leitura | Revisão contínua, análise de dependências/binário e testes dinâmicos |

## Protocolo físico proposto: 13 cenários ainda pendentes

Os cenários abaixo são um plano de validação em aparelho. **Nenhum foi marcado como aprovado por esta auditoria.** Instrumentação com emulador sem Google Play não reproduz a classificação do Play Protect nem garante o comportamento do Samsung Galaxy A03 informado nesta revisão.

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

## Verificação adicional da IA 3.0 alpha.2

Em 26/10/2026, o mesmo aplicativo foi compilado como `3.0.0-alpha.2`/25, fonte `62ec1e3a714cdcc577e04f659185808bd6c9ab3f`. A alteração reaproveita o contexto de inferência local e o descarta nas mudanças de dados pessoais; não adiciona permissões ou serviços.

Os manifestos finais debug/release foram extraídos dos novos APKs e conferidos: **9 declarações de permissão em cada variante**, mesmas permissões entre elas, release sem `debuggable`, sem `SYSTEM_ALERT_WINDOW`, `QUERY_ALL_PACKAGES`, Usage Stats, SMS ou contatos. [Manifestos e certificado](docs/evidencias/ai3-alpha2-manifest-signature-summary.json). O debug local possui assinatura v2 válida; o release continua sem assinatura e não instalável. Modelos, licenças, quatro ABIs e alinhamento foram inspecionados: [relatório da alpha.2](docs/RELATORIO_IA3_ALPHA_2.md).

Não foi feita instalação física ou consulta do novo arquivo ao Play Protect. A causa do bloqueio relatado continua desconhecida. O certificado e o hash do APK local não identificam a compilação separada do GitHub.

## Download direto de teste em 27/10/2026

A pedido do responsável, o APK existente do build 16 foi disponibilizado por link direto público, sem login ou extração de ZIP. Não houve recompilação, nova assinatura, mudança de permissões ou alteração da variável global que mantém pausadas as publicações automáticas. Origem, checksum, versão e assinatura foram conferidos no workflow; acesso HTTP sem autenticação também foi testado. Identificadores do arquivo publicado e limites da verificação estão no [relatório da alpha.2](docs/RELATORIO_IA3_ALPHA_2.md) e na [evidência de download](docs/evidencias/ai3-alpha2-direct-download.json). Isso não constitui aprovação do Play Protect ou teste físico.

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
