# NEXUS 4.1 — mouse interno e global

Data da revisão: 28/10/2026. Aparelho físico de referência informado pelo usuário: **Samsung Galaxy A03**. Esta revisão não utiliza o Galaxy S9 como referência e não atribui RAM, versão de Android ou processador a um nome comercial.

## O que sabemos e o que ainda precisa de confirmação

O usuário informou que clique e rolagem funcionam no NEXUS, mas há atraso e o mouse não continua fora do aplicativo. Esse resultado é uma observação do usuário; ainda não houve reprodução física por esta equipe no A03.

O código anterior já possuía um `GlobalMouseCameraService` com ciclo de vida próprio, CameraX, MediaPipe, foreground service de câmera, notificação e `START_NOT_STICKY`. O `AccessibilityService` já executava gestos por `dispatchGesture`, com sobreposição de acessibilidade e sem leitura do conteúdo de outras aplicações. Portanto, não há evidência para afirmar que simplesmente sair da Activity encerrava esse serviço global.

Havia também um **mouse interno**, cujo encerramento ao sair do NEXUS é deliberado. Sua ativação aparecia antes do painel global, que ficava abaixo de vários controles. Confundir esses dois botões é uma hipótese plausível para a falha relatada, mas não uma causa comprovada no telefone. O painel global agora aparece antes do modo interno, com instruções distintas.

## Problemas confirmados na implementação e correções

| Problema identificado no código | Correção | Como verificar |
| --- | --- | --- |
| A janela inteira do cursor era limitada às bordas da tela, enquanto `dispatchGesture` utilizava a coordenada do indicador. Perto das bordas, o centro visível e o toque podiam ficar separados por até metade do tamanho do cursor. | `GlobalPointerGeometry` define a mesma coordenada física para o centro e o toque. Apenas o halo decorativo pode ultrapassar a tela. A origem física não depende de RTL. | Testes JVM com diversas resoluções, tamanhos de ponteiro e coordenadas, incluindo bordas e valores inválidos; teste físico de botões nas bordas pendente. |
| Cada quadro removia e recolocava a entrega no fim da fila principal. Sob carga, a entrega podia ser adiada continuamente. | Uma única entrega pendente troca apenas o resultado mais recente por `LatestFrameDelivery`. | Testes do helper e inspeção do uso no serviço; comportamento sob carga no A03 pendente. |
| Cada análise criava bitmaps de entrada e rotação. | `ReusableHandImage` reaproveita memória de RGBA, rotação e entrada de inferência. O MPImage usa um buffer próprio: fechar um MPImage criado diretamente de um bitmap no SDK atual pode reciclar esse bitmap. | Testes Android específicos do helper; contagem real no A03 pendente. |
| O serviço informava falhas genéricas de câmera e não distinguia recusa de gestos. | Observação de `CameraState` e contadores de gestos aceitos, recusados e cancelados. | Painel mostra câmera ocupada, desativada, erro de configuração ou falha crítica quando o Android reporta esses estados. Aceitação da API não prova que o alvo reagiu ao gesto. |
| Não havia pausa explícita da sessão global com liberação da câmera. | `PAUSAR` limpa o analisador, desfaz o vínculo CameraX, cancela gestos e fecha detector/buffers no executor. | Teste físico dos indicadores e recursos pendente. Retomar exige a tela visível; a notificação pausada abre o app. |
| Uma tarefa de preparação ou entrega antiga poderia retornar depois de pausar e retomar. | Geração de sessão de câmera invalida inicializações, resultados e callbacks anteriores. | Revisão de concorrência; teste de pausa/retomada rápida no aparelho pendente. |
| O acesso à configuração de acessibilidade estava disponível antes de marcar a divulgação. | O botão é habilitado após a autorização explícita da sessão apresentada no próprio painel. | Teste Android de limites de permissões; nenhuma permissão de câmera é pedida ao apenas abrir o Chat ou esse painel. |

## Arquivos envolvidos

- `app/src/main/java/br/com/nexus/assistant/gestures/global/GlobalMouseCameraService.kt`: sessão global independente, câmera, inferência, pausa e notificação.
- `app/src/main/java/br/com/nexus/assistant/gestures/global/GlobalMousePanel.kt`: divulgação, autorização, permissões e controles manuais.
- `app/src/main/java/br/com/nexus/assistant/gestures/global/GlobalMouseRuntime.kt`: estado não pessoal da sessão e comandos de parada/pausa/retomada.
- `app/src/main/java/br/com/nexus/assistant/gestures/global/NexusAccessibilityService.kt`: cursor e execução de gestos oficiais.
- `app/src/main/java/br/com/nexus/assistant/gestures/global/GlobalPointerGeometry.kt`: coordenadas compartilhadas.
- `app/src/main/java/br/com/nexus/assistant/gestures/GestureFeatureScreen.kt`: fluxo global antes do modo interno e seleção de perfil.
- `app/src/main/java/br/com/nexus/assistant/gestures/LatestFrameDelivery.kt`, `ReusableHandImage.kt`, `HandGeometry.kt` e `device/TrackingProfile*.kt`: mecanismos compartilhados de desempenho. Os controladores de mouse, mãos livres e 3D permanecem separados.

## Estados e controles reais

**Ativar mouse global** exige autorização da divulgação, acessibilidade conectada, câmera autorizada e notificações habilitadas. A solicitação de foreground service nasce de um toque enquanto a tela está `RESUMED`. A preparação do rastreador ocorre fora da thread principal. A sessão só informa que há quadros quando chega um resultado de análise.

**Pausar e liberar câmera** conserva a escolha de sessão, cancela ações pendentes e solicita a liberação da câmera pelo CameraX. Não mantém inferência ativa. O Android pode concluir o fechamento do driver de forma assíncrona. Para retomar, abra o NEXUS e use **Retomar mouse global nesta tela**; não há reinício da câmera escondido por uma notificação.

**PARAR** encerra serviço, notificação e sobreposições. **ENCERRAR TUDO** também encerra o serviço de voz. O botão sobre os outros aplicativos encerra somente a câmera e o mouse global. O microfone e o ambiente 3D têm controladores próprios.

O painel informa o tempo de processamento e a idade do resultado entre o início da análise e sua entrega à interface. Isso **não mede a latência absoluta desde a exposição do sensor**. O relógio da câmera é usado para ordenação e para estimar excesso de fila relativo ao melhor deslocamento observado; não é comparado diretamente ao relógio da interface como se tivessem origem idêntica. Quadros atrasados e resultados com mais de 800 ms de idade não executam novos gestos.

Os perfis escolhem uma resolução e um intervalo-alvo por capacidade observada e preferência. O CameraX pode negociar outra resolução, e a cadência se adapta ao custo real. Esses parâmetros não são uma promessa de FPS no A03.

## Roteiro físico no Samsung Galaxy A03

Todos os resultados abaixo permanecem **pendentes de execução no aparelho**. Não desative o Play Protect para realizá-los. Se a instalação continuar bloqueada, interrompa essa etapa e use o processo oficial documentado em `AUDITORIA_PLAY_PROTECT.md`.

1. Registre versão do Android, versão do NEXUS e perfil escolhido. Abra o app sem conceder sensores; o Chat, a Central de Extensões e Armazenamento devem abrir sem acessibilidade.
2. Em **Mouse Virtual**, confirme que há dois modos separados. Escolha **Econômico** para a primeira medição, com os dois mouses parados.
3. Leia a divulgação global, autorize a sessão e abra as configurações oficiais de acessibilidade. Ative apenas o serviço NEXUS. Volte ao aplicativo.
4. Toque em **Ativar mouse global** e conceda câmera e notificações quando solicitadas. Aguarde **Câmera global ativa** e quadros reais, sem confundir a ausência de uma mão detectada com ausência de câmera.
5. Mostre o indicador em iluminação adequada. Teste espelhamento e centralização. Anote o tempo de processamento e a idade de entrega em alguns movimentos; não use um único valor como média.
6. Vá à tela inicial do Android. Confirme presença do cursor, botão **PARAR NEXUS**, notificação e indicação de uso da câmera compatível com essa versão do Android.
7. Abra um navegador ou outro aplicativo de teste sem conteúdo sensível. Use pinça curta em um botão comum, duas pinças para duplo toque, pinça mantida para arrastar e dois dedos em movimento para rolar vertical e horizontalmente. Registre o efeito observado e os contadores depois de voltar ao painel.
8. Teste botões próximos das bordas, comparando o centro do cursor com o alvo. As telas de autorização e outros locais protegidos podem recusar controle; não tente contornar essa proteção.
9. Use **PAUSAR** pela notificação. Confirme que não chegam novos gestos e que a câmera é liberada. Volte à tela do NEXUS para retomar manualmente. Repita pausas rápidas e rotação de tela.
10. Abra a câmera de outro aplicativo ou utilize o bloqueio de câmera do Android, quando disponível. O NEXUS deve suspender gestos antigos e informar o estado recebido do CameraX; uma falha crítica ou ausência prolongada de quadros encerra a sessão.
11. Use **PARAR** pela notificação e depois pelo botão sobreposto em uma nova sessão. Confirme remoção do cursor, notificação e uso da câmera. Não deve haver reinício automático por boot, retorno de câmera ou `START_STICKY`.
12. Só depois, teste voz e mouse simultâneos. Confirme que parar um preserva o outro, e que **ENCERRAR TUDO** libera os dois.

## Evidências e limites desta revisão

Foram adicionados três testes JVM de geometria e dois testes JVM de diagnóstico de sessão. O resultado executado da suíte, Lint e compilação consta em `PROGRESSO_NEXUS_4_1.md`; a existência dos testes, isoladamente, não significa aprovação. Esta nota não declara um teste físico nem a aprovação do Play Protect.

O código usa exclusivamente CameraX/MediaPipe e APIs oficiais de acessibilidade. Não solicita `SYSTEM_ALERT_WINDOW`, não recupera árvores de interface e não coleta texto de outros apps. Não há reinício oculto, tentativa de impedir encerramento pelo usuário ou alteração das proteções do Android.

Não há garantia de continuidade da câmera em qualquer fabricante/versão, de interação em telas protegidas, de funcionamento com a tela bloqueada, nem de latência máxima no Galaxy A03. Esses pontos exigem medições físicas. O controle continua experimental até essa validação. Nenhum modelo generativo grande foi adicionado ao CORE por esta correção.
