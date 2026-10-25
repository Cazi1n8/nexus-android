# Permissões, Play Protect e sessões fora do app

## Novo bloqueio da alpha.4

O usuário confirmou que a alpha.4 foi bloqueada novamente. O status permanece **não resolvido**. O APK exato, hashes, justificativa das permissões e uma minuta estão em [PLAY_PROTECT_REVISAO.md](PLAY_PROTECT_REVISAO.md); o pedido não foi enviado ao Google. A configuração de assinatura estável foi preparada, mas depende da chave definitiva e dos Secrets do responsável. [Distribuição](ASSINATURA_E_DISTRIBUICAO.md).

## Aviso relatado

O aviso enviado pelo usuário em 25/10/2026 mostra o Play Protect bloqueando a instalação do NEXUS por possível acesso a dados sensíveis. Ele não informa qual declaração do pacote motivou a decisão. A auditoria do código não equivale a aprovação pelo Google. Esta alteração não garante que o próximo APK seja aceito.

O NEXUS declara um serviço de acessibilidade para **executar gestos autorizados** em outros aplicativos. Essa é uma capacidade sensível. O serviço tem `canPerformGestures=true`, `canRetrieveWindowContent=false`, `eventTypes=0`, sem coleta de eventos e sem leitura da árvore de outros aplicativos. O app não declara permissões de SMS, contatos, chamadas, localização, instalação de pacotes ou acesso amplo aos arquivos. Câmera e microfone são permissões solicitadas para recursos iniciados pelo usuário.

Não foram ocultados serviços, alterados nomes para contornar classificação nem removidos avisos de privacidade. Para distribuição pública ainda são necessários assinatura de lançamento estável, descrição e divulgação das capacidades sensíveis, testes físicos e revisão das políticas aplicáveis. Se o bloqueio for indevido, o desenvolvedor pode pedir revisão no [formulário oficial do Play Protect](https://support.google.com/googleplay/android-developer/contact/protectappeals). A aprovação depende do Google.

## Mouse global

- O início exige um toque na tela visível, consentimento da sessão, câmera, notificações e acessibilidade habilitada pelo usuário nas configurações oficiais do Android.
- O serviço usa `ImageAnalysis` do CameraX com MediaPipe, sem janela de vídeo sobre outros aplicativos. Há um cursor, botão **PARAR NEXUS** e notificação persistente com ação **PARAR**. Indicadores de privacidade do Android continuam sob controle do sistema.
- A sessão continua ao sair da atividade e ao remover a tarefa da lista de recentes (`stopWithTask=false`). **PARAR**, revogação de permissão ou desconexão da acessibilidade encerram o controle. O serviço não se reinicia após ser morto (`START_NOT_STICKY`), não inicia no boot e não captura microfone.
- A falta de quadros cancela toques mantidos; falta persistente de quadros encerra a sessão com erro. Isso evita uma câmera/um toque sem resposta permanente.
- O sistema, fabricante, economia de bateria, câmera ocupada, bloqueio de tela e falta de memória podem encerrar ou impedir a sessão. Não há garantia de funcionamento ilimitado.

## Outros recursos

O parâmetro `showPreview` de `HandCamera` permite analisar mãos sem exibir o vídeo na interface. Isso não desliga a câmera nem os indicadores do sistema. A prévia permanece opcional nas telas que oferecem o controle.

Mãos livres, Libras, 3D e voz ainda não compartilham um proprietário de sessão em serviço de primeiro plano. A câmera dessas telas depende do ciclo de vida da atividade; voz pertence à atividade. Ocultar a prévia não implementa execução em segundo plano. Uma próxima etapa precisa transferir a sessão e seu estado para um serviço próprio, com consentimento, notificações e limites reais de cada API. Nenhuma declaração desta versão promete continuidade de todos esses modos fora do app.

## Desempenho alterado

A entrega de resultados de rastreamento à interface mantém apenas o quadro recente, em vez de acumular callbacks antigos quando a thread principal está ocupada. A leitura de compatibilidade do serviço global ocorre no executor, depois de publicar a notificação. O carregamento do MediaPipe e a análise continuam fora da thread principal, com resolução e cadência adaptadas pelo perfil do aparelho.

Essas alterações precisam de compilação e testes físicos no aparelho do usuário. Não demonstram por si só que todos os travamentos foram resolvidos.
