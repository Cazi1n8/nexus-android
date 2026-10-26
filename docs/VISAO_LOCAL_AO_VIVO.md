# Visão local de objetos e câmera

O NEXUS 3.0 acrescenta **classificação real de imagem** ao OCR existente. O modelo é o EfficientNet-Lite0 INT8 distribuído oficialmente pelo MediaPipe, com cerca de mil categorias ImageNet. Ele calcula rótulos e pontuações no aparelho; não é um modelo multimodal de conversação, não localiza todos os objetos nem entende a cena inteira.

O modelo de conversa recebe apenas o texto OCR e as categorias efetivamente produzidas pela ferramenta, como evidência delimitada e sujeita a erros. Não recebe os pixels no modo local. A explicação gerada não recebe um prefixo e um rodapé fixos adicionados pelo aplicativo a cada resposta. O pedido específico para transcrever texto pode retornar diretamente o resultado OCR identificado como ferramenta.

## Preparação no computador ou CI

```bash
python3 scripts/fetch_object_model.py --with-test-fixture
```

A preparação verifica tamanho exato e SHA-256, recusa links simbólicos/arquivos corrompidos e não baixa modelos durante o uso do celular. O modelo TFLite fica fora do Git e é incluído em `assets/vision/efficientnet_lite0_int8.tflite`. O classificador confere novamente seu hash antes da primeira inferência de cada sessão.

| Arquivo | Bytes | SHA-256 |
| --- | --- | --- |
| EfficientNet-Lite0 INT8 v1 | 5.434.517 | `bc2ffe19c1118de0c0c2a9088992da5589722656e0fba81421385300a4a34b16` |
| Fotografia oficial de teste burger.jpg | 92.220 | `08151ebb48f30a6cfbea02168ec0f3c0f1694d64c8d0f75ca08a63a89302853f` |

Fontes: [modelo oficial](https://storage.googleapis.com/mediapipe-models/image_classifier/efficientnet_lite0/int8/1/efficientnet_lite0.tflite), [arquitetura EfficientNet Lite](https://github.com/tensorflow/tpu/tree/master/models/official/efficientnet/lite), [fotografia de exemplo oficial](https://storage.googleapis.com/mediapipe-tasks/image_classifier/burger.jpg). O exemplo é obtido separadamente para testes instrumentados e não integra o APK principal. Licença do modelo Apache 2.0 e atribuição incluídas nos assets. A utilização e redistribuição de conjuntos de imagens de terceiros requerem revisão das respectivas licenças; este módulo não inclui um conjunto de treinamento.

## Vision ao vivo

A sessão é iniciada explicitamente com permissão de câmera. CameraX fornece quadros de 480×360 com descarte dos antigos. O usuário escolhe intervalo de 0,75 a 5 segundos. Só um classificador serial processa um quadro por vez; a UI recebe apenas o resultado mais recente. A sessão oferece prévia opcional, troca de lente, parada e leitura do resultado por voz.

Isso significa **análise periódica local de quadros**, e não compreensão contínua de vídeo. A análise não envia imagens a um provedor remoto. As categorias do modelo vêm em inglês e a pontuação não é uma garantia de acerto. Imagens cobertas ou sem contraste são recusadas antes de classificar, para evitar atribuir objetos a um quadro preto. Se a pontuação for baixa ou várias categorias forem próximas, a tela mostra incerteza.

O painel usa o ciclo de vida da Activity; pausa a câmera ao sair e pode retomar a sessão ao voltar. Não implementa classificação da câmera em segundo plano. O mouse global impede a abertura concorrente desta câmera. O proprietário da tela deve encerrar uma captura anterior antes de montar este painel.

## Verificação

Sete testes Python cobrem preparação/integridade/HTTPS. Testes JVM verificam evidência, incerteza e o contrato do adaptador OCR/classificação. `LocalObjectClassifierTest` executa o modelo real na fotografia oficial de um cheeseburger e rejeita quadro preto; precisa ser executado no Android, não basta compilar. O resultado físico com a câmera, iluminação e objetos do usuário requer teste no aparelho. Não marcar identificação geral de imagens ou visão multimodal completa como concluídas por essa prova.
