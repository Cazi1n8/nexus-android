# NEXUS Vision — avaliação real de 29/10/2026

**O transporte multimodal funcionou; a qualidade do modelo testado foi reprovada.** Não há serviço público implantado nem validação da câmera no Galaxy A03.

O avaliador compilou os arquivos Kotlin de produção e chamou o gateway NEXUS em loopback. O gateway encaminhou dados de imagem reais a `llama-server b11838`, com Qwen3.5-2B Q4_K_M e seu projetor F16. Não houve OCR ou resposta pronta substituindo o modelo nesse caminho. A identidade, as mensagens por papel, o orçamento de histórico, os pixels JPEG/base64 e o leitor SSE foram os do cliente real. O adaptador temporário BuildConfig do ensaio não faz parte do APK.

Pesos: `unsloth/Qwen3.5-2B-GGUF`, revisão `f6d5376be1edb4d416d56da11e5397a961aca8ae`. Modelo: 1.280.835.840 B, SHA-256 `aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223`. Projetor: 668.227.264 B, SHA-256 `7035e9cb8d7c6a9681d07eef9a364783e86ea4cd73faab2eabb4f43a101830c7`. Esses arquivos ficaram no ambiente de desenvolvimento; não foram acrescentados ao CORE ou promovidos ao catálogo.

## Resultados

| Pedido real | Resultado observado | Veredito |
| --- | --- | --- |
| Descrever a foto de um hambúrguer | Identifica hambúrguer e fundo com fogo, mas inventa porco, frango, azeitonas e alho, além de um tipo específico de queijo | Reprovado para descrição detalhada confiável |
| Perguntar para que serve o objeto | Reutiliza a foto e a resposta gerada anterior; mantém também os ingredientes falsos e acrescenta explicações sobre eles | Contexto mantido; qualidade reprovada |
| Perguntar se vê o que o usuário vê, em conversa nova sem imagem | Responde “Sim” sobre conteúdo enviado no chat e não esclarece a ausência de câmera | Resposta ambígua, sem aprovação; não comprova acesso à câmera |

As três solicitações terminaram com `stop`, sem falhas técnicas, e a concatenação dos deltas correspondeu ao texto final após normalizar apenas espaços nas bordas. Primeiro texto: 23.954 ms, 23.281 ms e 1.170 ms; duração total: 77.805 ms, 119.707 ms e 25.185 ms. São medições desse host com compilação concorrente, não uma previsão para celular nem um benchmark controlado de velocidade.

Foi acrescentada uma informação dinâmica ao prompt: quantidade efetiva de fotos na requisição, ausência de transmissão ao vivo e aviso explícito quando não há imagem. Um quarto controle com a mesma pergunta sem imagem continuou respondendo ambiguamente “Sim, consigo ver tudo o que você está lendo aqui no chat”. Esse ajuste torna o contexto de entrada factual, mas **não resolveu a limitação semântica do candidato**. Nenhuma frase foi filtrada ou substituída para esconder o resultado.

Evidências: [respostas e deltas](evidencias/n42-vision-remote-model.json), [rubrica e hashes](evidencias/n42-vision-model-review.json), [controle sem imagem após explicitar entradas](evidencias/n42-vision-availability-model.json). Os servidores temporários foram encerrados após a coleta. Tokens não foram publicados.

## Implementação e limites

A sessão implementada em CameraX captura sob comando ou por amostragem opcional de no máximo um quadro a cada dez segundos, sem sobrepor pedidos. A última foto e os turnos relacionados permanecem no contexto; se não couberem, o app pede nova captura em vez de omitir os pixels silenciosamente. Sair da tela libera a câmera. A sessão tem consentimento e indicador visível. Perguntas podem ser ditadas e respostas podem ser ouvidas pelo controlador de voz.

Imagens são preparadas em até 960 px e 1 MiB nos perfis com menos memória; nos demais, até 1280 px e 2 MiB. A decisão usa RAM medida, não nome de aparelho. O teste host utilizou uma foto de demonstração; não comprova captura, compressão, TTS ou desempenho físico no A03.

A pesquisa opcional usa a API documentada de uma instância SearXNG administrada pelo operador. A consulta só é enviada após ação explícita e confirmação de capacidade do gateway; fontes retornadas ficam separadas do texto. Não foi implantada uma instância de pesquisa nem executada uma busca pública real nesta etapa. Testes com servidor controlado verificam protocolo, não existência ou veracidade de resultados na internet.

A próxima aprovação de IA exige um modelo mais competente e a bateria real completa com a infraestrutura final. Um endpoint que responde e recebe imagens não basta para aprovar compreensão visual.
