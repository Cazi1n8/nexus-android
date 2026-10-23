# Arquitetura e plano do NEXUS

O NEXUS é um aplicativo Android nativo. A primeira entrega prioriza navegação, conversa por texto e imagem, voz e armazenamento local. A existência de uma tela não significa que a função completa já foi entregue: consulte [PROGRESSO.md](../PROGRESSO.md).

## Organização

```text
app/              Aplicativo Android
  src/main/       Interface, dados, integração de IA e recursos do aparelho
  src/test/       Testes locais que não precisam de um celular
server/           Ponte de IA executada fora do APK
docs/             Decisões, testes e distribuição
.github/workflows/ Compilação automatizada
```

A interface usa Kotlin e Jetpack Compose. O ViewModel mantém o estado e coordena tarefas com coroutines. Os repositórios cuidam dos dados; a interface não precisa conhecer os detalhes de uma API de IA. Essa separação permite trocar o provedor e testar a integração com um servidor controlado.

| Decisão | Motivo |
| --- | --- |
| Android nativo, Kotlin e Compose | Integração com permissões e ciclo de vida do Android; interface adaptável e acessível |
| Room | Conversas, mensagens e memórias com consultas e exclusão local |
| DataStore | Preferências persistentes sem bloquear a interface |
| Android Keystore | Proteger o token de acesso à ponte de IA; a chave de criptografia fica no sistema |
| CameraX | Câmera vinculada à tela e ao ciclo de vida, com liberação dos recursos |
| SpeechRecognizer e TextToSpeech | Utilizar os serviços de fala disponíveis no aparelho |
| Provedor OpenAI-compatible | Integração real e substituível, inclusive com Ollama quando o modelo escolhido oferece esse protocolo |
| Ponte Python fora do APK | Manter chaves de provedores no servidor e controlar limites das requisições |

A compilação inicial usa Android SDK 35, Android Gradle Plugin 8.9.2, Gradle 8.11.1 e Kotlin 2.1.20, com Android 8.0/API 26 como mínimo. A configuração efetiva fica nos arquivos Gradle. O SDK de destino deverá ser revisto antes de publicar na Google Play.

## Caminho de uma conversa

1. O usuário configura a URL HTTPS da ponte e autoriza o envio de mensagens e imagens.
2. O aplicativo prepara o contexto da conversa e as memórias explicitamente salvas pelo usuário.
3. O provedor envia a requisição para `/v1/chat/completions`.
4. A ponte encaminha para o modelo configurado, como um modelo local do Ollama ou uma API externa.
5. A resposta volta ao aplicativo e é armazenada no histórico local.

Sem um modelo acessível e configurado, a IA informa o erro. O aplicativo não substitui a resposta por frases fixas fingindo inteligência artificial. Imagens só podem ser interpretadas se o modelo configurado aceitar entrada visual.

## Privacidade e limites de segurança

- Conversas, memórias e preferências ficam no armazenamento privado do aplicativo. Esse isolamento não equivale a criptografia de todo o banco de dados. Um aparelho comprometido exige proteções adicionais.
- O backup do aplicativo deve permanecer desativado para evitar cópias automáticas de histórico e tokens. Confira o manifesto ao alterar a configuração de distribuição.
- Chaves privadas de provedores não pertencem ao código, a `BuildConfig`, ao APK ou ao Git. A ponte recebe essas chaves por variáveis de ambiente. O aplicativo usa apenas um token de acesso à ponte, protegido pelo Keystore.
- A ponte inicial é para um ambiente controlado. Para múltiplos usuários, são necessários contas, emissão e expiração de tokens, revogação e limites por usuário antes da distribuição pública.
- Câmera e microfone exigem permissão do Android e ativação visível. O aplicativo não deve mantê-los funcionando escondidos.
- O reconhecimento de voz pode usar um serviço de rede do aparelho se o reconhecimento local não estiver disponível. O usuário também deve consultar a política desse serviço.
- HTTPS é obrigatório em acesso remoto. A exceção HTTP de desenvolvimento existe apenas para endereços locais permitidos no APK de depuração.
- Fotos e mensagens enviadas ao servidor passam a seguir também a política do operador desse servidor e do provedor escolhido. O consentimento deve explicar esse caminho.

## Etapas com critérios verificáveis

| Fase | Entrega | Critério para considerar concluída |
| --- | --- | --- |
| 1 — Base | Navegação e identidade visual; chat real; imagens; voz básica; memória e configurações | Compilar APK; testes automatizados aprovados; enviar mensagem e imagem a um modelo real; validar permissões, voz e persistência em aparelho |
| 2 — Visão e voz | Sessões de câmera, análise de quadros, rastreamento real de mãos e calibração | Testar iluminação, orientação, troca de câmera, perda de rastreamento, latência e encerramento da câmera |
| 3 — Gestos | Cursor no próprio NEXUS, pinça, arraste e ações configuráveis | Medir falsos cliques, tremor e tempo de resposta; validar alternativas acessíveis e consentimento |
| 4 — Recursos especiais | Objeto 3D e exercícios iniciais de Libras | Validar duas mãos, escala e rotação; usar referências educacionais confiáveis; admitir limites da avaliação de Libras |
| 5 — Distribuição | Estabilidade, desempenho, assinatura e preparação da loja | Executar a matriz de aparelhos; revisar privacidade, SDK de destino, políticas e assinatura; produzir artefatos reproduzíveis |

O rastreamento de mãos deverá usar MediaPipe ou tecnologia real equivalente. Animações e pontos demonstrativos não comprovam reconhecimento. Uma análise de quadros de câmera não deve ser apresentada como compreensão contínua de vídeo. Libras envolve orientação, localização, movimento, expressões e gramática; comparar dedos isolados não comprova compreensão da língua.

Controle de outros aplicativos e bloqueio de tela dependem de APIs oficiais e de uma avaliação separada de permissões e políticas. Um serviço de acessibilidade não deve ser adicionado para monitoramento oculto ou para contornar proteções do Android.
