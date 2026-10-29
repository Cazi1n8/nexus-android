# Conversa visual por capturas — NEXUS 4.1

Esta alteração acrescenta `VisionConversationPanel`, sem substituir o classificador local, a leitura OCR nem o Chat. O painel usa `CameraX ImageCapture` e o provedor multimodal selecionado no aplicativo. A imagem não é substituída por um nome de objeto nem por uma resposta preparada.

## Fluxo implementado

1. A pessoa inicia a sessão com o aplicativo visível e aceita a divulgação de envio de fotos, perguntas e memórias autorizadas ao provedor.
2. A sessão cria uma conversa própria no Room. A divulgação informa que um anexo ainda não enviado será descartado.
3. A permissão de câmera é solicitada nesse momento. Não é exigida para abrir o Chat.
4. Ao tocar em **Capturar e perguntar**, a câmera produz um JPEG real. A importação do ViewModel comprime a imagem, remove metadados e a armazena privadamente.
5. O envio só ocorre quando a importação terminou e existe um caminho novo de imagem. Falhas não reutilizam silenciosamente uma captura anterior.
6. O provedor recebe o histórico e a imagem por seu transporte multimodal. O painel mostra o texto final correspondente a essa solicitação e os fragmentos de streaming realmente recebidos, quando suportados.
7. **Perguntar sobre a última foto** envia uma nova pergunta na mesma conversa. **Pesquisar sobre o objeto** exige confirmação adicional e a ferramenta de pesquisa do gateway configurada pelo operador.

A implementação não instala um servidor público nem fornece credenciais. O usuário comum só pode utilizar um serviço integrado sem configurar endereço/chave depois que a infraestrutura do desenvolvedor estiver implantada e autenticada. Um endpoint configurado não comprova que seu modelo aceita imagens; essa capacidade precisa ser verificada com uma avaliação real do serviço utilizado.

## Uso de recursos e privacidade

- Câmera limitada a uma sessão visível. `ON_PAUSE`, `ON_STOP`, saída do painel ou ativação de uma sessão conflitante encerram a câmera; ela não volta sozinha.
- A prévia pode ser escondida, mas o aviso de câmera ativa permanece visível.
- Capture resolution solicitada: 960×720 no perfil de câmera mais econômico ou 1280×960 nos demais. O CameraX pode selecionar uma resolução suportada próxima. A compressão final continua obedecendo aos limites comuns de anexos.
- A análise periódica é opcional e inicialmente desligada. Ela começa somente ao marcar o controle e envia no máximo uma captura a cada dez segundos, sem sobrepor a solicitação anterior.
- Uma falha desativa o envio periódico, sem tentativas em ciclo.
- Não há envio de todos os quadros da câmera nem compreensão contínua de vídeo.
- O painel não inicia serviços de câmera/microfone em segundo plano. Mouse global e sessão de voz possuem controles próprios.
- A fala é iniciada por botão. O reconhecimento Android pode usar rede; a permissão de microfone é solicitada somente ao ditar. A leitura da resposta usa o `VoiceController` real e a voz selecionada.
- Imagens e mensagens enviadas permanecem no histórico local até a exclusão da conversa/dados. O painel não apaga silenciosamente o histórico ao encerrar a câmera.

## Verificações desta alteração

Executados quatro testes JVM reais do gate de sessão: ativação explícita, uma solicitação por vez, intervalo mínimo, invalidação de callbacks após parar/reiniciar e recusa de configuração excessiva de frequência. Evidência: `docs/evidencias/n42-vision-session-gate.json`.

Esses testes não comprovam qualidade multimodal, CameraX em aparelho físico, voz real, latência no Galaxy A03 nem aprovação do Play Protect. Compilação Android e eventual avaliação multimodal do gateway são registradas separadamente pelo relatório principal; não foram executadas por este subtrabalho.

## Roteiro de teste físico pendente

1. Com um provedor realmente multimodal configurado, iniciar a conversa visual e conferir as divulgações/permissões.
2. Apontar para um objeto, perguntar **O que é isso?** e verificar a identificação contra a foto realmente enviada.
3. Sem nova captura, perguntar **Para que serve?** e **Como funciona?**, conferindo o contexto.
4. Trocar o objeto, capturar novamente e verificar que o modelo não responde sobre a foto anterior.
5. Esconder a prévia e conferir que a captura manual ainda funciona e que o indicador de câmera continua visível.
6. Ativar o modo periódico, provocar uma falha de conexão e verificar que não há mais envios automáticos.
7. Sair da tela durante captura/importação/análise e conferir liberação da câmera e ausência de novos envios.
8. Testar câmera/microfone negados, indisponibilidade de câmera e seleção de um modelo somente de texto: o aplicativo deve mostrar erro honesto, sem reconhecimento inventado.
9. Com a ferramenta de pesquisa implantada, consultar informação atualizada e verificar as fontes separadamente do que aparece na foto.
