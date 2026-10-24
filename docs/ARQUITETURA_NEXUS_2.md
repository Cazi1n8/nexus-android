# Arquitetura da evolução 2.0

Este documento descreve a implementação, sem certificar uso físico. A base auditada permanece no histórico Git; o schema Room v1 e o identificador Android foram preservados.

## IA, dados e voz

`NexusViewModel` coordena conversas Room, preferências DataStore, cancelamento e estados da interface. `AiCoordinator` escolhe o provedor local ou HTTP explicitamente, sem fallback remoto silencioso. O token personalizado fica no Keystore e não é necessário no modo local.

`LocalModelManager` guarda pesos no diretório privado: download explícito HTTPS, limite exato de bytes, espaço disponível, arquivo `.part`, cancelamento e SHA-256 antes de promover o arquivo. `LocalChatContext` seleciona mensagens recentes e memórias autorizadas por relevância lexical. Isso não é memória semântica ilimitada ou resumo persistente.

`LocalAiProvider` serializa inferência em executor próprio. `NexusNativeBridge` transporta UTF-8 em bytes, incluindo acentos e emoji. O C++ usa o template real do GGUF, conta tokens e reduz turnos antigos para caber no contexto. Handles são identificadores de um registro com propriedade compartilhada; cancelamento/fechamento concorrente não libera memória ainda em uso. Ao sair do app, a sessão local é interrompida e liberada.

`ActionProposal` valida nomes e argumentos em uma lista restrita. `AiCompletion` distingue texto de uma proposta estruturada. Uma proposta nova tem identificador e só pode ser consumida uma vez após confirmação explícita. O modelo não recebe acesso direto a intents, shell, acessibilidade ou árvore de aplicativos.

`VoiceController` usa APIs reais de fala do Android. Seleção de voz considera idioma e país; uma voz estrangeira persistida não pode substituir pt-BR. Leitura automática usa eventos sem replay, em primeiro plano, para não reler histórico ao recompor telas.

## Vision e OCR offline

`VisionScreen` separa captura CameraX, importação da galeria, ditado editável e envio multimodal autorizado. Importar e ler uma imagem não requer câmera. `LocalTextReader` usa Tesseract4Android com `por+eng` em um worker serial, limita arquivo/resolução, aplica EXIF e verifica os modelos copiados ao diretório privado. Não inicia sensores nem acessa a rede. A chamada nativa iniciada termina antes de liberar o bitmap; cancelamento, troca de foto e fechamento descartam resultados tardios.

Copiar, exportar, ouvir e autorizar memória são ações explícitas. O texto pendente de exportação sobrevive à recriação da Activity. O OCR reconhece texto impresso; análise geral de imagens continua dependendo do provedor multimodal configurado. Pesos OCR e AAR são preparados no build com SHA-256 fixado; os pesos de conversa continuam sendo baixados separadamente pelo usuário.

## Rastreamento e controles

`HandCamera` usa CameraX e Hand Landmarker no modo VIDEO com timestamps crescentes, processamento local e handedness. O rastreador produz observações; interpretações ficam em controladores independentes.

- `MouseController`: identidade da mão, suavização, calibração, histerese da pinça, toque, arrasto e rolagem por dois dedos. Perda de mão cancela o toque interno.
- `GestureSession`: fica na raiz da navegação, preservando mouse ao mudar telas. `GestureSessionHost` exibe um cursor e envia eventos de toque à janela do NEXUS; para ao sair do primeiro plano.
- `HandsFreeController`: reconhece poses estáveis, aplica debounce e retorna somente ações configuradas permitidas. Não gera eventos do mouse.
- `CubeController`: seleção por pinça iniciada sobre o objeto; duas pinças controlam escala/rotação/deslocamento. `CubeScene` renderiza linhas tridimensionais em OpenGL ES 2. Não há ancoragem espacial de AR.

O controle global é uma sessão separada: `GlobalMouseCameraService` mantém câmera em foreground, sem reinício automático; `NexusAccessibilityService` usa `dispatchGesture` e overlay de acessibilidade. Não recebe eventos nem recupera conteúdo de telas. Notificação e overlay permitem parar. O app bloqueia aquisição simultânea da câmera por outros módulos. [Detalhes e limites](MOUSE_GLOBAL.md).

## Educação e diagnóstico

`LibrasPractice` avalia somente dois exercícios geométricos preparatórios. Rejeita mãos incompletas e configurações ambíguas, exige estabilidade e não apresenta probabilidades inventadas. Progresso e exportação ficam separados. Referências ao INES e UFSC não significam endosso nem validação pedagógica do aplicativo.

O diagnóstico mede hardware e inicialização de bibliotecas após toque explícito. Disponibilidade de serviço de fala, hash correto ou MediaPipe inicializado não equivalem a escuta, inferência ou mão detectada com sucesso.

## Distribuição

Fontes llama.cpp e modelo de mãos são preparados por scripts; o GGUF não é embutido no APK. JNI usa ARMv8-A de base e kernels otimizados também no APK debug, evitando exigir dotprod/i8mm/SVE do S9+. NDK 28 fornece alinhamento moderno; dependências nativas de terceiros ainda exigem inspeção antes da Google Play. CI executa testes e lint e publica APK de depuração. Um workflow separado permite instrumentação em emulador; execução e resultado devem ser conferidos, não presumidos.
