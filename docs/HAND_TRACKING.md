# Rastreamento de mãos: implementação e estado real

O código usa **MediaPipe Tasks Vision 0.10.21**, `HandLandmarker` com CPU e CameraX.
O processamento de imagens acontece no aparelho e não envia quadros ao servidor de IA.
O código deste módulo foi escrito, mas a compilação Android e o funcionamento em
câmera física ainda precisam ser verificados. Isso não é uma certificação de precisão,
latência, compatibilidade ou reconhecimento de sinais de Libras.

## Modelo necessário

O arquivo oficial `app/src/main/assets/hand_landmarker.task` **não estava disponível
no ambiente de desenvolvimento durante esta etapa**. O download falhou por falta
de conexão ao proxy; nenhum modelo fictício foi criado.

No computador ou no GitHub Actions, execute antes do Gradle:

```bash
python3 scripts/fetch_hand_model.py
./gradlew testDebugUnitTest lintDebug assembleDebug
```

O script baixa somente a versão 1 do modelo de mãos no endereço oficial:

<https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task>

Ele exige HTTPS com verificação TLS, limita o tamanho, verifica o conteúdo e a
integridade do pacote e imprime seu SHA-256. Um SHA-256 de referência ainda não foi
obtido neste ambiente; portanto, não há um hash inventado no projeto. Para fixar um
hash conferido por você, use `--sha256 VALOR_CONFERIDO`. Registre o hash e revise
licença e model card antes de distribuir o APK publicamente.

O aplicativo pode compilar sem esse arquivo. Nesse caso, a tela informa que não
conseguiu carregar o rastreador. Não há detecção de mãos por animação ou dados falsos.

Referências oficiais:

- [Guia do Hand Landmarker para Android](https://ai.google.dev/edge/mediapipe/solutions/vision/hand_landmarker/android).
- [Visão geral, modelos e limites](https://ai.google.dev/edge/mediapipe/solutions/vision/hand_landmarker).
- [Código e licença Apache 2.0 do MediaPipe](https://github.com/google-ai-edge/mediapipe).

## O que foi implementado no código

- Câmera frontal ou traseira com ciclo de vida da tela; os recursos são liberados
  quando o usuário sai ou para a câmera.
- Análise com fila de apenas um quadro recente, resolução preferida de 640×480 e
  frequência máxima aproximada de nove análises por segundo.
- Detecção de até duas mãos, desenho de 21 pontos e conexões por mão, espelhamento
  da câmera frontal e rotação segundo CameraX.
- Cursor único com suavização e calibração do centro; sensibilidade ajustável.
- Pinça com histerese: clique após soltura curta, duas pinças rápidas para duplo
  clique e pinça mantida por pelo menos 450 ms para arrastar um objeto de teste.
- Mãos livres com poses heurísticas — aberta, punho, dois dedos e pinça — e ações
  configuráveis dentro do NEXUS. As configurações ficam no armazenamento privado.
  Nenhuma ação vem habilitada por padrão.
- Cubo com oito vértices 3D e projeção em perspectiva sobre a câmera: pinça move
  ou gira, duas mãos mudam a escala. Existe controle manual identificado na tela.

O mouse só funciona no laboratório dentro do NEXUS. Não há serviço de
acessibilidade, sobreposição em outros aplicativos, bloqueio do telefone ou
controle oculto. Não há realidade aumentada com posicionamento espacial.

As poses são comparações geométricas simples, sensíveis a ângulo e oclusão. Elas
não avaliam Libras. Gestos laterais, comando avançar/voltar global, abertura de
outros aplicativos por gestos e referências validadas de Libras permanecem pendentes.

## Verificação

`HandGeometryTest` contém testes JVM para histerese, clique, duplo clique,
arrastar, perda da mão, repetição de poses, suavização/calibração e projeção 3D.
Esses testes não verificam o modelo de visão, a câmera nem o toque real no aparelho.

Antes de considerar as fases 2–4 utilizáveis, valide em pelo menos um aparelho:

1. Permissão recusada, concedida e revogada; câmera ocupada por outro aplicativo.
2. Rotação do telefone, câmera frontal espelhada e alternância frontal/traseira.
3. Desenho dos pontos alinhado à prévia, com indicador nos quatro cantos.
4. Mão entrando/saindo, duas mãos trocando de posição e iluminação fraca.
5. Cliques, duplos, arrasto, retorno da tela e câmera realmente encerrada.
6. Consumo de CPU, memória e bateria durante uma sessão de dez minutos.

Até essa validação, os módulos devem ser apresentados como experimentais.
