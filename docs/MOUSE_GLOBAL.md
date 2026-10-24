# Mouse global experimental

Estado inicial: implementado, aguardando compilação integrada e teste em dispositivo. Não foi demonstrado controle de outros aplicativos no Galaxy S9+ nesta etapa de desenvolvimento.

## Como iniciar e parar

1. Abra Mouse Virtual no NEXUS, leia a explicação de uso e marque a autorização da sessão.
2. Use **Abrir acessibilidade do Android** e habilite **NEXUS — controle por gestos**. Esse serviço recebe apenas a capacidade oficial de executar gestos; não recupera conteúdo das janelas.
3. Volte ao NEXUS e toque em **Iniciar mouse global**. Autorize câmera e, no Android 13 ou posterior, notificações.
4. Mantenha o aparelho apoiado e mostre a mão à câmera frontal. O cursor é um único overlay de acessibilidade; não captura os toques físicos.
5. Use **PARAR NEXUS** no overlay, **PARAR** na notificação ou o botão da tela Mouse para encerrar câmera e controle. Remover a tarefa do NEXUS também encerra a sessão.

Indicador move o cursor, pinça curta executa um toque, duas pinças rápidas geram dois toques, pinça mantida inicia arrasto e dois dedos executam um arrasto para rolar. A continuidade usa `GestureDescription.StrokeDescription.continueStroke` (Android 8 ou posterior). Ao parar ou perder a mão, o dispatcher termina a continuação para soltar o toque; a API de acessibilidade não oferece o mesmo envio direto de `ACTION_CANCEL` usado pelo mouse interno. O Android pode recusar gestos em superfícies protegidas, e um toque físico pode interromper um gesto sintético.

## Recursos e privacidade

- Serviço de câmera em primeiro plano, iniciado somente pela tela visível, com `START_NOT_STICKY`; não reinicia no boot nem após ser encerrado pelo sistema.
- Notificação obrigatória e botão de parada visível. O início é recusado se as notificações do aplicativo/canal estiverem desativadas.
- Câmera frontal, MediaPipe CPU em modo VIDEO, até cerca de nove quadros por segundo, entrada desejada de 640×480; a resolução efetiva depende do aparelho.
- Sem acesso ao microfone, gravação, envio de imagens, captura de telas ou coleta de eventos de acessibilidade.
- `canRetrieveWindowContent=false`, `canPerformGestures=true`, `eventTypes=0`. O serviço não procura textos, senhas ou elementos da interface de outros aplicativos.
- Rastreador e cursor param ao perder a sessão. Perda de quadros solta um toque mantido; falha prolongada da câmera encerra o serviço.
- Câmeras internas e global compartilham uma exclusão explícita: pare uma sessão antes de abrir a outra.

O serviço é declarado com `isAccessibilityTool=false`, pois o NEXUS é um assistente geral. Antes de publicar na Play Store, revisar a declaração de uso de AccessibilityService, a divulgação de dados e os requisitos de serviço em primeiro plano. Esta documentação não constitui aprovação da loja.

## Testes físicos necessários

No S9+: testar permissão negada/revogação, início/encerramento por cada botão, notificação, troca entre apps, toque e duplo toque, arrasto/rolagem, interrupção por toque físico, orientação da tela, mão perdida, câmera ocupada por outro app, retorno a Vision/3D/Libras, retirada da tarefa e consumo de bateria. Os testes automatizados de geometria não substituem esses testes de integração Android.
