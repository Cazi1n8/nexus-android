# Revisão do bloqueio pelo Google Play Protect

## Revisão atual — NEXUS CORE no Galaxy A03 (28/10/2026)

**Estado: minuta preparada, não enviada ao Google.** Não há resposta, número de protocolo ou aprovação. As seções históricas abaixo identificam APKs anteriores e não devem ser copiadas como se fossem o CORE atual.

Formulário oficial: https://support.google.com/googleplay/android-developer/contact/protectappeals

Antes de enviar, confirme que o arquivo efetivamente bloqueado é este:

- Pacote: `br.com.nexus.assistant`.
- CORE público: `3.0.0-alpha.3-core`, código **26**, release `v3.0.0-alpha.3-core-download-22`.
- Arquivo: `NEXUS-3.0.0-alpha.3-core.apk`, **108.210.081 bytes**.
- SHA-256 APK: `e76ff4e228206a11317000a238556d8c24693532c1527b05f8264f5ba6b50d15`.
- SHA-256 certificado: `0e72073647f9fd5b4bc27f6f8fbafa9100f4fadc391433ff6844fc2f96ec97fc`.
- Origem: https://github.com/Cazi1n8/nexus-android/releases/tag/v3.0.0-alpha.3-core-download-22
- Dispositivo informado: **Samsung Galaxy A03**. Versão do Android, região, data exata do bloqueio e versão do Play Protect: **a confirmar no dispositivo**.
- Mensagem relatada: “O app foi bloqueado para proteger seu dispositivo”, com aviso de possível acesso a dados sensíveis.

Se o bloqueio ocorreu com outro arquivo, substitua versão, hash e certificado pelos desse arquivo. Não atribua esses identificadores a uma captura sem confirmar a versão. O candidato local `4.1.0-alpha.1-core/27` tem outro hash/certificado e ainda não foi submetido a um teste físico de Play Protect.

Texto técnico para revisão pelo responsável:

> Solicito análise do NEXUS (`br.com.nexus.assistant`), aplicativo Android com controle por movimentos da mão. O APK identificado acima foi relatado como bloqueado no Galaxy A03 com aviso sobre dados sensíveis. A acessibilidade é opcional e usada para `dispatchGesture` e `TYPE_ACCESSIBILITY_OVERLAY`, com `canRetrieveWindowContent=false`, sem eventos de acessibilidade, captura de tela, filtragem de teclas, leitura de mensagens ou dados de outros aplicativos. Câmera e microfone têm sessões manuais independentes, notificações e parada visível. O arquivo usa certificado de teste; não alegamos falso positivo nem aprovação prévia. A revisão 4.1 atualiza dependências vulneráveis e reforça consentimento e validação do manifesto. Pedimos orientação sobre os achados concretos e os requisitos de distribuição aplicáveis.

Anexos a preparar: arquivo bloqueado ou URL verificável, captura do aviso, [auditoria de segurança](../AUDITORIA_PLAY_PROTECT.md), [inspeção atual do CORE](evidencias/nexus41-core-security.json) identificada como **outro candidato**, política de privacidade e descrição de consentimento/controles. Não publique credenciais, dados de conta, históricos pessoais ou chaves de assinatura no repositório ou no formulário.

A assinatura de produção estável e a distribuição de testes pela Google Play dependem da conta/chave do responsável e da revisão das declarações de acesso sensível. Não há garantia de aceitação. Mantenha o Play Protect ativo durante a validação; esta minuta não contém instruções para contorná-lo.

Atualizado em **25/10/2026, 14:06 UTC**.

**Registro histórico do APK bloqueado:** a minuta e os identificadores abaixo pertencem à alpha.4. A IA 3.0 alpha.2 tem uma [auditoria separada](RELATORIO_IA3_ALPHA_2.md), sem verificação de instalação pelo Play Protect. Se houver bloqueio de outra versão, identifique seu arquivo e certificado exatos; os hashes locais da alpha.2 não identificam automaticamente o APK do GitHub Actions. Não misture dados de versões na solicitação.

O usuário informou que a **alpha.4 foi bloqueada novamente**. Este é um relato do celular; o bloqueio não foi reproduzido pelo ambiente de desenvolvimento. A instalação e o funcionamento no Samsung Galaxy S9+ continuam sem aprovação. Os testes do aplicativo e a integridade do arquivo não substituem a análise do Google.

Este documento reúne o APK exato e uma minuta para a revisão oficial. **Nenhuma solicitação foi enviada ao Google por esta sessão.** Não há protocolo de atendimento nem aprovação. A decisão e a causa exata do bloqueio permanecem desconhecidas.

## APK a identificar na solicitação

| Campo | Valor |
| --- | --- |
| Nome | NEXUS |
| Identificador do pacote | `br.com.nexus.assistant` |
| Versão | `2.0.0-alpha.4` |
| Código da versão | `23` |
| Distribuição | APK de teste fora da Google Play, publicado no GitHub |
| Arquivo | `NEXUS-2.0.0-alpha.4.apk` |
| Tamanho | `615085340` bytes |
| SHA-256 do arquivo | `0c7059c77d07bc7eed40d5d7701a36e98ca3495a1d77a74c2500f7c7e85286df` |
| SHA-256 do certificado | `65297709dfb7a0149397313c1249cf40b3392913a4050f0af0c8a5951c394c1f` |
| Tipo de assinatura atual | Certificado Android Debug; assinatura v2 verificada |
| Código-fonte do APK | Commit `48938e7205b329565766e3e4342aff016d801c03` |

- [APK exato para download](https://github.com/Cazi1n8/nexus-android/releases/download/v2.0.0-alpha.4-build-13/NEXUS-2.0.0-alpha.4.apk)
- [Release e checksum público](https://github.com/Cazi1n8/nexus-android/releases/tag/v2.0.0-alpha.4-build-13)
- [Manifesto da revisão publicada](https://github.com/Cazi1n8/nexus-android/blob/48938e7205b329565766e3e4342aff016d801c03/app/src/main/AndroidManifest.xml)
- [Inspeção do arquivo publicado](evidencias/alpha4-published-apk.json)
- [Estado do relato de bloqueio](evidencias/alpha4-play-protect-status.json)

As versões de teste usaram certificados diferentes. Ainda falta configurar uma assinatura de lançamento estável para a distribuição futura. Corrigir a assinatura não demonstra a causa deste bloqueio nem garante a liberação. Não gere outro APK com o mesmo nome para apresentá-lo como o arquivo acima: uma compilação ou assinatura diferente precisa de novos identificadores de arquivo/certificado na revisão.

## Capacidades que precisam ser declaradas com clareza

O mouse global usa **acessibilidade para executar gestos em outros aplicativos**. Essa capacidade é sensível e deve permanecer explícita. Não foi removida ou disfarçada para tentar passar pela verificação.

- O serviço `NexusAccessibilityService` é protegido por `BIND_ACCESSIBILITY_SERVICE` e depende de ativação pelo usuário nas configurações oficiais do Android.
- Sua configuração declara `canPerformGestures=true`, `canRetrieveWindowContent=false` e `isAccessibilityTool=false`. No código, `eventTypes=0`, `flags=0` e o callback de eventos não coleta conteúdo. Não há leitura da árvore de janelas de outros apps.
- A sessão global exige consentimento próprio, permissões e início pela tela do NEXUS. O serviço de câmera não é exportado. Há notificação, botão **PARAR NEXUS** e ação de parada; não há reinício após encerramento do processo ou inicialização automática no boot.
- A prévia do vídeo pode ser ocultada; isso não esconde os indicadores de privacidade do Android. A câmera permanece ativa apenas durante a sessão solicitada. Falta de quadros persistente, perda de autorização ou comando de parada encerram o controle.
- O microfone atende ao reconhecimento de fala na atividade. A alpha.4 não oferece escuta permanente nem serviço de voz fora do app.

| Permissão declarada | Uso no projeto desta revisão |
| --- | --- |
| `INTERNET` | Provedor de IA opcional autorizado e download de modelos na variante de regressão; a IA do APK completo e o OCR funcionaram sem rede no teste dedicado |
| `ACCESS_NETWORK_STATE` | Consulta da conectividade; não fornece conteúdo de comunicação de outros apps |
| `CAMERA` | Foto escolhida pelo usuário e rastreamento local de mãos; solicitada quando a função é iniciada |
| `RECORD_AUDIO` | Reconhecimento de fala pelo serviço Android, iniciado pelo usuário; esse serviço pode usar rede |
| `FOREGROUND_SERVICE` e `FOREGROUND_SERVICE_CAMERA` | Manutenção da sessão explícita do mouse global, com notificação |
| `POST_NOTIFICATIONS` | Aviso visível e parada da sessão global em versões Android que exigem essa autorização |

O manifesto não solicita SMS, contatos, chamadas, localização, instalação de outros pacotes ou acesso amplo aos arquivos. O seletor de aplicativos dos comandos consulta atalhos de inicialização disponíveis; não é um mecanismo de leitura de telas. As conversas e memórias ficam localmente; o provedor remoto é separado e requer consentimento. Não há chave privada de serviço de IA incluída no APK.

Fontes: [serviço de acessibilidade](../app/src/main/java/br/com/nexus/assistant/gestures/global/NexusAccessibilityService.kt), [configuração XML](../app/src/main/res/xml/nexus_accessibility_service.xml), [serviço da câmera](../app/src/main/java/br/com/nexus/assistant/gestures/global/GlobalMouseCameraService.kt) e [divulgação da sessão](../app/src/main/java/br/com/nexus/assistant/gestures/global/GlobalMousePanel.kt).

## Minuta para copiar no formulário oficial

Revise o texto e preencha somente os dados que o Google solicitar. Nome, e-mail e credenciais da conta Google não devem ser colocados no repositório.

> Solicito a análise do bloqueio de instalação do aplicativo NEXUS, pacote `br.com.nexus.assistant`, versão `2.0.0-alpha.4`, código `23`. O usuário relatou novamente o bloqueio pelo Play Protect em um Samsung Galaxy S9+. A captura fornecida anteriormente informa possível acesso a dados sensíveis; não identifica a causa técnica específica. Não temos confirmação de falso positivo e gostaríamos de conhecer e corrigir os achados da análise.
>
> É um assistente Android experimental com IA de texto incluída no APK, leitura local de texto de imagens, voz e rastreamento de mãos. O controle global por mão usa um serviço de acessibilidade para executar toques/arrastos autorizados em outros aplicativos. O serviço não recupera conteúdo das janelas e não coleta eventos de acessibilidade. Essa função é divulgada ao usuário e exige ativação nas configurações do Android, consentimento por sessão e início explícito no aplicativo. A câmera global tem notificação e botão de parada. O serviço não reinicia a câmera ocultamente após o processo ser encerrado.
>
> Câmera e microfone são solicitados para funções iniciadas pelo usuário. O manifesto não solicita SMS, contatos, chamadas, localização ou instalação de outros pacotes. O provedor externo de IA é opcional e exige autorização; o modo local não encaminha as conversas a um servidor. O código-fonte, manifesto e relatório de testes estão públicos para revisão.
>
> O APK ainda usa certificado Android Debug; a assinatura de lançamento estável é uma pendência da distribuição. Não afirmamos que isso seja a causa do bloqueio ou que os testes funcionais comprovem a segurança integral do pacote.
>
> APK: https://github.com/Cazi1n8/nexus-android/releases/download/v2.0.0-alpha.4-build-13/NEXUS-2.0.0-alpha.4.apk
>
> SHA-256 do arquivo: `0c7059c77d07bc7eed40d5d7701a36e98ca3495a1d77a74c2500f7c7e85286df`
>
> SHA-256 do certificado: `65297709dfb7a0149397313c1249cf40b3392913a4050f0af0c8a5951c394c1f`
>
> Código-fonte: https://github.com/Cazi1n8/nexus-android/tree/48938e7205b329565766e3e4342aff016d801c03
>
> Pedimos a revisão deste arquivo específico e orientações sobre eventuais violações ou comportamentos que precisem ser corrigidos. Não solicitamos que o usuário desative o Play Protect.

## Enviar pelo celular

1. Abra o [formulário oficial de revisão do Play Protect](https://support.google.com/googleplay/android-developer/contact/protectappeals). Faça login na conta Google responsável, se solicitado.
2. Preencha seus dados diretamente no formulário. Use os identificadores e a minuta acima e anexe o aviso de bloqueio quando o formulário permitir. Confirme que o arquivo enviado ou indicado é o APK identificado nesta página.
3. Guarde o protocolo/resposta recebido do Google. O envio e a resposta precisam ser registrados separadamente deste material preparado; nenhuma aprovação foi obtida até agora.

Não desative o Play Protect, não tente ocultar as capacidades do APK e não remova uma instalação existente enquanto a instalação nova estiver bloqueada. É necessário preservar uma cópia exportada dos dados antes de qualquer remoção futura, porque os certificados publicados são diferentes.
