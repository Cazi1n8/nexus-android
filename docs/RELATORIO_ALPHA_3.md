# NEXUS 2.0.0-alpha.3 — correção do download

Atualizado em **25/10/2026 (UTC)**. Código de versão Android: **22**. Esta etapa preserva o aplicativo existente e trata o relato do usuário: **a tela mostra “Baixando” com o tamanho completo, mas não conclui**.

## Estado da entrega

**Compilação local aprovada; validação Android e publicação pendentes.** O build local concluiu em 2m 5s, com 90 testes JVM aprovados e lint sem erros. O APK local foi gerado e inspecionado. Os testes Android e a prova de download dos modelos reais ainda precisam ser executados; a publicação alpha.3 ainda não foi verificada.

A [alpha.2 publicada](https://github.com/Cazi1n8/nexus-android/releases/tag/v2.0.0-alpha.2-build-9) permanece uma referência histórica. Seu build, OCR e inferência foram testados, mas o modelo da prova de IA era transferido por ADB: esse teste não exercitava o download feito pelo aplicativo. O bloqueio relatado pelo usuário precisa de validação própria. [Evidências anteriores](RELATORIO_ALPHA_2.md).

O ambiente ainda não reproduziu o problema no Samsung Galaxy S9+ físico. Portanto, a espera por fechamento da conexão é um cenário concreto tratado pelo novo código e pelos testes, sem afirmar que já foi comprovado como a causa específica nesse aparelho.

## Alterações implementadas

| Área | Comportamento da alpha.3 | Alcance e validação necessária |
| --- | --- | --- |
| Final de transferência | Recebe o número exato de bytes fixados do modelo e encerra a chamada antes de fechar o corpo HTTP; não faz outra leitura esperando EOF depois do tamanho completo | Testar servidor que mantém a conexão aberta após enviar todos os bytes e comprovar retorno de `download()` |
| Integridade e liberação | Verifica tamanho e SHA-256 do arquivo privado, promove o arquivo e publica `READY` somente após verificação válida | Arquivo corrompido nunca pode liberar o chat; progresso de verificação deve chegar ao estado terminal correto |
| Retomada | Preserva `.part` em pausa/falha de rede e reaproveita o prefixo ao reabrir o gerenciador | Testar cancelamento, recriação e `Range`/`Content-Range` corretos |
| Servidor sem retomada | Se responder `200` ao pedido de intervalo, reinicia a gravação desde o início com aviso | Não pode concatenar duas cópias nem aceitar intervalo inesperado |
| Arquivo completo já recebido | Pode conferir o parcial completo sem repetir o download | O arquivo precisa pertencer à instalação atual e ter tamanho/hash corretos |
| Limites de espera | Espera de cabeçalhos limitada a 30 s por padrão, até 45 s; leitura limitada a 20 s e timeout total de 30 min | Mensagem de falha/pausa e nova tentativa precisam aparecer; rede parada não pode parecer progresso válido |
| Interface | Mostra conexão, bytes e porcentagem recebidos, velocidade e progresso de verificação separados | “100% recebido” não é apresentado como modelo pronto antes de `READY` |
| Pausa e recuperação | Botões de pausar, continuar, concluir verificação e descartar apenas o parcial com confirmação | Conversas e outro perfil de modelo devem permanecer preservados |
| Ciclo de vida do job | O ViewModel mantém a barreira até a conclusão e libera a referência/estado ativo no término | Nova tentativa não pode ficar bloqueada por job encerrado nem concorrer com o anterior |

Fontes principais: [LocalModelManager](../app/src/main/java/br/com/nexus/assistant/data/local/LocalModelManager.kt), [ModelIntegrity](../app/src/main/java/br/com/nexus/assistant/data/local/ModelIntegrity.kt), [NexusViewModel](../app/src/main/java/br/com/nexus/assistant/NexusViewModel.kt) e [interface](../app/src/main/java/br/com/nexus/assistant/ui/NexusApp.kt).

## Testes preparados e diferenças entre as provas

Os testes HTTPS controlados usam arquivos sintéticos pequenos, com SHA-256 conhecido. Eles executam o downloader e o sistema de arquivos reais; não são arquivos de linguagem capazes de responder perguntas. O cliente TLS de teste confia apenas no certificado do servidor local controlado, sem alterar a política HTTPS de produção.

- JVM: `LocalModelDownloadTest` cobre conclusão, resposta de comprimento desconhecido que permanece aberta, redirecionamento, transferência em chunks, proibição de HTTP, corrupção, tamanho incorreto, interrupção, espera sem resposta, leitura parada, cancelamento/retomada, intervalo inválido e exclusão isolada.
- Android: `LocalModelDownloadDeviceTest` reproduz o tamanho completo sem EOF e o cancelamento seguido de retomada por um gerenciador recriado, usando o diretório privado do aplicativo.
- Produção Android: `ProductionModelDownloadTest` foi preparado para baixar os dois GGUFs públicos pelo próprio `LocalModelManager`, comprovar retorno/`READY`/SHA-256, revalidar o arquivo e gerar uma resposta com o compacto. Não há envio de modelo por ADB nessa prova.

O teste de produção é opt-in: requer o argumento `nexusDownloadSmoke=true`. Na suíte comum ele fica explicitamente ignorado, evitando baixar aproximadamente 1,6 GB sem uma escolha de teste. O caso `LocalAiInferenceTest` também é ignorado sem sua fixture própria. Os totais de aprovados, falhos e ignorados precisam refletir a execução efetiva.

O [workflow de download real](../.github/workflows/model-download-smoke.yml) prepara Android 10/API 29 x86_64 com 3 GB de RAM no runner temporário. O [runner](../scripts/run_android_model_download.py) instala os APKs, inicia a instrumentação, recolhe JSON/log/JUnit e limpa apenas o diretório fixo dos modelos de teste. Os pesos não são incluídos no APK nem publicados como evidência. A configuração do workflow não equivale a aprovação de execução.

## Modelos de produção

Os catálogos e seus hashes permanecem fixados:

| Perfil | Arquivo | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| Compacto | `qwen2.5-0.5b-instruct-q4_k_m.gguf` | 491.400.032 | `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db` |
| Equilibrado | `qwen2.5-1.5b-instruct-q4_k_m.gguf` | 1.117.320.736 | `6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e` |

O downloader faz somente a conexão necessária para obter o modelo público autorizado. Conversas não entram no pedido de download. A inferência local é limitada ao hardware compatível; o modelo equilibrado não é carregado apenas para comprovar seu download na prova de produção.

## Execução reproduzível

```bash
python3 scripts/fetch_llama_cpp.py
python3 scripts/fetch_hand_model.py
python3 scripts/fetch_ocr_models.py
python3 scripts/fetch_ocr_runtime.py
python3 -m unittest discover -s scripts/tests -v
./gradlew --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Com aparelho ou emulador conectado, para a suíte comum:

```bash
./gradlew connectedDebugAndroidTest
```

Para autorizar explicitamente a prova que baixa os dois modelos reais no Android conectado:

```bash
python3 scripts/run_android_model_download.py
```

O Gradle usa heap de 4 GB no computador/CI, após uma falha de empacotamento observada com 3 GB em uma rodada anterior. Isso não altera o requisito de RAM do aplicativo instalado.

## Resultados registrados nesta etapa

| Verificação alpha.3 | Resultado atual |
| --- | --- |
| Scripts Python | **51 aprovados**, conforme execução registrada pelo agente principal; não comprovam o downloader Android |
| Compilação e testes JVM | **BUILD SUCCESSFUL in 2m 5s**; **90 aprovados**, 0 falhas/erros/ignorados, incluindo 14 testes HTTPS novos |
| Lint | **0 erros, 37 avisos e 1 informação** na execução local |
| APK local/assinado | **Gerado e inspecionado:** 123.615.014 bytes; assinatura v2, ZIP 16 KB, quatro ABIs e 40 bibliotecas ELF. Bibliotecas de 64 bits ≥16 KB e de 32 bits ≥4 KB. Modelos OCR verificados e nenhum GGUF |
| HTTPS controlado no Android | Testes preparados; resultado ainda não registrado |
| Download real dos dois GGUFs no Android | Workflow e teste preparados; execução ainda não registrada |
| Inferência após o download pelo app | Aguardando a prova de produção nova; aprovação alpha.2 por ADB não substitui essa verificação |
| Publicação e download do APK alpha.3 | Ainda não registrados; nenhum link alpha.3 é apresentado como pronto |
| Teste físico no S9+ | Não executado pelo ambiente; confirmação do usuário necessária após uma entrega verificável |

APK local: SHA-256 `11a8a1b2fabd712dd346fe95e6a470cf27a34aa70a87ec29c3587ace2ff9bf09`. Esse hash pertence ao arquivo local; o APK publicado precisa de verificação própria.

Registrar ao concluir: revisão do código, links e tentativas dos workflows, totais de testes/ignorados, tamanho e SHA-256 do APK, assinatura, inspeção de ABIs/ELF, resultado de download público e observações de produção sem URLs assinadas ou dados pessoais.

## Atualização e dados existentes

Pausar/reabrir pode reaproveitar o parcial que continua no armazenamento privado desta instalação. **Desinstalar apaga conversas, modelos e parciais.** APKs de depuração podem ter certificados diferentes entre execuções: se o Android recusar a atualização por assinatura, exporte conversas importantes antes de remover a instalação antiga. Esta correção não permite recuperar arquivos privados que já foram apagados por desinstalação.

## Pendências do pedido completo

A alpha.3 trata o bloqueio do download, sem declarar todas as correções concluídas. A [matriz do texto grande](PEDIDOS_E_STATUS.md) separa implementação, teste e pendência. Permanecem sem conclusão: Libras completa e validação pedagógica, compreensão multimodal local geral, resumo persistente de conversas, validação física de voz pt-BR/mouse global/gestos/duas mãos e distribuição na Google Play. O SDK alvo 35 e a assinatura de depuração também precisam de revisão antes da publicação na loja.
