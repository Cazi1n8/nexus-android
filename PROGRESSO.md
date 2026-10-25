# Progresso histórico do NEXUS 0.1

**Estado atual 2.0:** veja [PROGRESSO_NEXUS_2.md](PROGRESSO_NEXUS_2.md). A alpha.3 teve compilação local e 90 testes JVM aprovados para corrigir o download que permanece em 100%; execução Android e publicação ainda estão em validação. O último APK publicado é a alpha.2; seus testes de inferência usavam modelo instalado por ADB e não testavam o downloader do app. Resultados novos: [RELATORIO_ALPHA_3.md](docs/RELATORIO_ALPHA_3.md). A [matriz dos pedidos](docs/PEDIDOS_E_STATUS.md) registra o que ainda falta. As evidências abaixo pertencem à versão 0.1.

Atualizado em **23/10/2026**, horário de São Paulo. O APK foi compilado e pode ser instalado para testes. A primeira fase ainda precisa de validação de uso em celular; não há declaração de que todas as funções ou fases estejam concluídas.

## Entrega verificada

| Item | Resultado |
| --- | --- |
| Código Android | Kotlin, Jetpack Compose, MVVM, Room, DataStore, CameraX, SpeechRecognizer, TextToSpeech e cliente multimodal real |
| APK de teste | **Gerado**, versão 0.1.0, Android 8.0/API 26 ou superior |
| Build local | `BUILD SUCCESSFUL in 1m 35s`, 85 tarefas |
| Testes Kotlin/JVM | **27 aprovados**, zero falhas, erros ou ignorados |
| Android Lint | **0 erros e 28 avisos**; avisos de dependências e melhorias permanecem no relatório |
| Assinatura | APK local verificado com `apksigner`, assinatura v2 válida |
| Testes instrumentados | 6 escritos e APK de testes compilado; **não executados** |
| Testes Python | **44 aprovados**, incluindo HTTP local, sem ignorados |
| Modelo de IA real | Qwen2.5-0.5B-Instruct em CPU: 3 inferências através do gateway; cálculo e contexto observados; modelo externo ao APK |
| Modelo MediaPipe | Oficial, baixado e incluído no APK; rastreamento em câmera física ainda não validado |
| GitHub | [Código publicado](https://github.com/Cazi1n8/nexus-android), com histórico de alterações |
| GitHub Actions | **Build aprovado**, incluindo testes, lint e geração do APK; [execução 39980316020](https://github.com/Cazi1n8/nexus-android/actions/runs/39980316020) |
| APK no GitHub | Artefato [nexus-debug-3](https://github.com/Cazi1n8/nexus-android/actions/runs/39980316020/artifacts/12513442249), retido por 30 dias |

O build aprovado no GitHub corresponde ao commit `d61a978aeff3d1e9f1a331a4fcb326217b11da15`. Commits posteriores que apenas atualizam documentação e o schema exportado do Room não mudam o código do APK.

## As 14 telas

| Tela | Código entregue | Validação ou expansão pendente |
| --- | --- | --- |
| Splash | Identidade animada e entrada na navegação | Verificação visual e acessibilidade em aparelho |
| Home | Painel com acesso às áreas e estado de configuração | Fonte ampliada, orientação e telas pequenas |
| Chat IA | Cliente OpenAI-compatible, contexto limitado, imagem, cancelamento, retry e erros | Teste do cliente Android com servidor próprio no aparelho |
| Assistente de Voz | Reconhecedor e síntese reais, pt-BR, interrupção, timeout e modo contínuo em primeiro plano | Microfone, vozes instaladas, silêncio e ciclo de vida no celular |
| NEXUS Vision | CameraX, captura explícita, pergunta sobre foto e envio ao chat | Câmeras frontal/traseira e modelo multimodal real; sem análise contínua de vídeo |
| Mouse Virtual | MediaPipe real, pontos, suavização, calibração, pinça, clique, duplo clique e arrasto dentro do laboratório | Precisão, falsos cliques, espelhamento e latência em mãos reais |
| Mãos Livres | Poses e associações persistidas a ações internas | Estabilidade; movimentos laterais e ações externas ainda pendentes |
| NEXUS 3D | Cubo com projeção 3D, modos mover/girar e escala por duas mãos | Validação física; sem rastreamento espacial de realidade aumentada |
| NEXUS Libras | Roteiro educacional, referência ao INES e limites explícitos | Biblioteca visual licenciada e validada, aulas, avaliação confiável e progresso |
| Central de Comandos | APIs oficiais para abrir configurações, câmera e apps; comandos personalizados em DataStore | Conferir aplicativos instalados e execução por voz no aparelho |
| Personalidade | Nome, idioma de resposta, estilo e detalhamento persistentes | Verificar preferências no cliente Android com modelo real |
| Memória e Histórico | CRUD Room, exclusão e exportação JSON sem tokens/imagens | Persistência, limpeza de arquivos e exportação no celular |
| Configurações | Endpoint, modelo, voz, token no Keystore e consentimento; troca de servidor limpa token anterior | Permissões e recuperação de falhas em aparelho |
| Diagnóstico | Hardware, permissões, vozes, asset do modelo e atalhos para testes | Medições de desempenho, bateria e latência |

## Verificações executadas

### Android

Comando aprovado, com JDK 17, SDK 35 e Gradle 8.11.1:

```bash
./gradlew --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

27 testes JVM: 4 de comandos, 12 de payload/contexto/erros da IA, 4 de fragmentação de fala e 7 de geometria/gestos. Os XMLs registram zero falhas. O APK tem versões nativas para ARM32, ARM64, x86 e x86_64 e inclui `assets/hand_landmarker.task`.

O emulador foi preparado, mas o `adb` não conseguiu criar `/home/agent/.android` no sistema de arquivos somente leitura. A concessão restrita de escrita não resolveu a limitação do ambiente. Não foi alterado `HOME`, não houve boot ou instalação confirmados e os 6 testes instrumentados permanecem não executados. Eles devem rodar em um emulador ou aparelho disponível com `./gradlew connectedDebugAndroidTest`.

Detalhes, hash do APK local e instalação: [RELATORIO_APK.md](docs/RELATORIO_APK.md).

### Servidor e IA

```bash
python3 -m unittest discover -s server/tests -v
```

44 testes passaram localmente em 16,813 s. No GitHub também passaram os 44 testes, em 17,940 s. A suíte usa upstreams controlados para validar o transporte; não mede qualidade de modelo.

Separadamente, um modelo Qwen real respondeu `42` para `17 + 25` e `Ciano.` ao recuperar a cor de um cubo do histórico. Foram três inferências no total e a ponte devolveu 401 para acesso não autorizado. O servidor temporário foi encerrado; não é um endereço publicado para o celular. Nenhuma imagem foi avaliada por esse modelo de texto.

Relatórios: [servidor](docs/RELATORIO_TESTES_SERVIDOR.md) e [IA real](docs/RELATORIO_IA_REAL.md).

## Correções e continuidade

- O acesso à rede foi restabelecido. O JDK e o Gradle foram obtidos de fontes oficiais com verificação de checksum; o wrapper oficial e seu JAR estão versionados.
- A CLI Android foi fixada em versão compatível com o ambiente. Diretórios Android conflitantes foram corrigidos.
- O primeiro workflow falhou porque tentava instalar o pacote `tools`, removido do catálogo Android. O workflow foi corrigido para `platform-tools` e passou.
- O erro de tema que usava um atributo API 27 no Android 8.0/API 26 foi corrigido por recursos versionados, mantendo o lint ativo.
- Foram corrigidos cancelamento da primeira mensagem, gravações concorrentes com exclusão, troca de servidor/token, respostas JSON nulas, timeouts e callbacks de voz.

## Fases e limites restantes

1. **Fase 1 — implementada e compilada; validação em aparelho pendente.** Testar navegação, chat com servidor próprio, imagens, voz, configurações, memória e acessibilidade.
2. **Fase 2 — código inicial compilado.** Validar câmera, MediaPipe, pontos, calibração e espelhamento.
3. **Fase 3 — laboratório interno compilado.** Validar cursor e gestos; ações laterais e controle global ainda não estão implementados.
4. **Fase 4 — parcial.** Cubo compilado; tutor de Libras ainda não possui conteúdo ou avaliação pedagógica validados.
5. **Fase 5 — em andamento.** APK e CI entregues; faltam testes físicos, otimização e preparação de release/Google Play.

A IA não está embutida no APK. Para conversar, configure um servidor compatível e autorize o envio; Ollama em computador próprio é uma opção gratuita. Não há chave de provedor no app nem no repositório. Room usa o sandbox do Android, sem criptografia adicional do banco; token usa Keystore e backup automático está desativado.

Reconhecimento e síntese de fala dependem dos serviços instalados e podem usar rede. Não há wake word permanente, AccessibilityService, monitoramento oculto, bloqueio de tela ou controle de outros apps por gestos. A interface está em português; tradução completa da interface está pendente. SDK alvo 35 não garante aceitação futura na Google Play.
