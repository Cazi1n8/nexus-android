# NEXUS CORE — progresso da correção de armazenamento

Atualização: 28/10/2026. Projeto existente, `3.0.0-alpha.3-core`, versionCode 26, Android mínimo 8.0/API 26, alvo 35. Os arquivos abaixo foram modificados no próprio NEXUS; o histórico da alpha.2 foi preservado.

## Problema identificado e medição real

O Samsung Galaxy A03 informou aplicativo 1,90 GB, dados 498 MB e cache 13,51 MB, total 2,41 GB. Sem acesso ADB ao aparelho, esses números são o relato do usuário, não uma medição de arquivos feita por esta equipe. O APK local alpha.2 foi preservado antes das alterações para permitir comparação real.

| Artefato local compilado | Bytes medidos | Redução frente à alpha.2 |
| --- | ---: | ---: |
| Alpha.2 debug anterior | 1.738.201.604 | Referência |
| CORE debug | 121.054.931 | 1.617.146.673 bytes; 93,0356% |
| CORE otimizado `coreTest`, assinado para testes | 98.878.796 | 1.639.322.808 bytes; 94,3114% |

Os dois modelos generativos somavam **1.608.720.768 bytes**, 92,5509% do APK anterior. Eles não participam do mouse. A [auditoria](AUDITORIA_ARMAZENAMENTO.md) contém os 20 maiores arquivos, ABIs, bibliotecas, recursos, duplicatas e detalhes dos 609 arquivos do APK antigo. O CORE debug serve à comparação entre variantes equivalentes; o `coreTest` também aplica R8 e redução de recursos. SHA-256 do `coreTest` local medido: `e407d5d35732321fca76e9723433feb297cdcb2da14f900e33d5ba44492064fe`. Esse hash não deve ser atribuído a um APK recompilado em outro ambiente sem nova verificação.

Não há GGUF, ONNX, dados OCR ou classificador de objetos nos APKs CORE medidos. O modelo real de mãos mantém os **7.819.105 bytes** e o SHA-256 fixado. As quatro arquiteturas são mantidas na compilação universal; não foi removida uma ABI apenas para fazer o número cair.

**Os 498 MB de dados:** a cópia privada do modelo compacto soma 491.400.032 bytes e as cópias de OCR somam 6.095.844 bytes: total **497.495.876 bytes**. É uma hipótese forte para o relato, baseada em caminhos e tamanhos reais do código, mas não confirma os arquivos presentes no A03. A atualização preserva esses dados; só o usuário decide remover os modelos e pacotes. Limpar 13,51 MB de cache não resolveria o peso embutido no APK.

## Alterações implementadas

| Área | Estado real |
| --- | --- |
| CORE padrão | `nexusBundleModel=false` e `nexusBundleVision=false`; assets gerados por Sync excluem dados opcionais sem apagar os arquivos-fonte |
| Mouse offline | CameraX, MediaPipe e `hand_landmarker.task` preservados; rastreamento não depende de IA generativa ou conexão |
| Central de Extensões | Interface e catálogo; download de dados fixados por tamanho/SHA, retomada, remoção e estados reais |
| Modelos de conversa | Inventário dos dois perfis, download manual, seleção explícita no chat e remoção por perfil, inclusive modelos antigos inativos |
| Dados de Vision | Classificador e OCR opcionais; leitores usam arquivos privados verificados e mantêm compatibilidade com a variante completa |
| Reutilização de dados | Modelos válidos e OCR da alpha.2 são reaproveitados, sem outra cópia obrigatória |
| Remoção segura | Inferência encerrada antes de remover modelo; pacotes em uso têm proteção contra remoção |
| Central de Armazenamento | Medição assíncrona local de APK/splits, modelos, parciais, extensões, imagens de chat, banco, preferências, cache e demais arquivos |
| Contabilização Android | Consulta somente do próprio UID; cache separado dos dados sem soma duplicada; nenhum `PACKAGE_USAGE_STATS` |
| Limpeza de cache | Confirmação, cancelamento de importações e barreira contra nova importação durante limpeza; preserva modelos, conversas, configurações e cache de código |
| Variante instalável menor | `coreTest` otimizada com R8, assinada pela chave existente de testes; não é uma chave de produção nova |
| Entrega Android oficial | [Comparação documentada](docs/ENTREGA_MODULAR_ANDROID.md): APK manual com dados opcionais versus AAB e entrega oficial Play; recursos binários dinâmicos ainda não implementados |

O scanner não segue links simbólicos e conta arquivos físicos com mais de um nome uma única vez. A tela distingue tamanho lógico dos arquivos, contabilização Android e espaço livre. Leituras incompletas são informadas; não são apresentadas como medição exata.

## Opcionais e recursos preservados

| Pacote/recurso | Espaço dos dados | Condição |
| --- | ---: | --- |
| IA offline compacta Qwen2.5 0,5B | 491.400.032 bytes | Download manual em aparelho compatível de 64 bits e com RAM/espaço disponíveis |
| IA offline Qwen2.5 1,5B | 1.117.320.736 bytes | Download manual; exige mais recursos |
| Vision — classificação | 5.434.517 bytes | Download manual de modelo de dados |
| Vision — OCR português/inglês | 6.095.844 bytes | Download manual, ou reaproveitamento dos dados privados antigos |
| Total dos dados opcionais de Vision | 11.530.361 bytes | Sem necessidade de baixar GGUF de conversa |
| Mouse, câmera de mãos, cursor, clique, rolagem, ajustes e serviços autorizados | Incluídos no CORE | Sem download generativo; precisão e continuidade físicas ainda pendentes |
| 3D e Libras experimental | Código incluído | Usam o rastreador de mãos; não são plugins de código baixados |
| Voz Android | Código incluído | Vozes/reconhecimento podem depender do mecanismo e de pacotes externos do sistema |
| Chat online | Sem pesos locais de geração | Exige endpoint real e consentimento; backend NEXUS público não implantado |

A Central de Extensões não finge desinstalar bytecode que continua no APK. Nesta etapa os pacotes removíveis são **dados**; runtimes e código leve integrados continuam instalados. Não há download de `.dex` ou `.so` remoto. A03 é avaliado por ABI e recursos reais; não se presume memória ou sistema de 64 bits pelo modelo comercial. Outros dispositivos Android compatíveis continuam suportados.

## Verificações executadas

Build local concluído: `BUILD SUCCESSFUL in 7m 45s`, 158 tarefas, com JDK 17, SDK 35 e Gradle Wrapper 8.11.1:

```bash
./gradlew --no-daemon --no-build-cache --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process --stacktrace testDebugUnitTest lintDebug lintCoreTest assembleDebug assembleCoreTest assembleDebugAndroidTest
```

| Verificação | Resultado confirmado |
| --- | --- |
| Suíte JVM Gradle | **205 testes**, zero falhas, erros ou ignorados nos XMLs |
| Android Lint debug | **Zero erros**, 50 avisos e uma informação |
| Android Lint coreTest | **Zero erros**, 50 avisos e uma informação |
| APK debug e coreTest | Gerados e medidos; valores na tabela inicial |
| APKs de testes Android debug e coreTest | Compilados; coreTestAndroidTest terminou em 1 min 2 s. Compilação não significa execução |
| Scripts Python existentes | **130 testes aprovados**, zero falhas |
| Servidor opcional | 44 casos executados: **25 aprovados e 19 ignorados** nesta execução; não são 44 aprovações e não comprovam servidor implantado |
| Scanner de armazenamento, execução isolada | 8 testes host aprovados: categorias, links, contagem, limpeza e dados/cache; [evidência](docs/evidencias/storage-scanner-tests.json) |
| Gerenciador de modelos, execução isolada | 18 testes host aprovados, incluindo migração sem HTTP/cópia duplicada; [evidência](docs/evidencias/storage-core-optional-model-tests.json) |
| Pacotes opcionais, execução isolada | 6 testes host aprovados: catálogo, integridade, caminhos e uso ativo; [evidência](docs/evidencias/storage-extensions-unit.json) |
| Instalação CORE em emulador | Testes instrumentados preparados; execução e evidência ainda pendentes neste registro |
| Downloads reais de extensões e uso dos dados privados no Android | Em preparação; não declarar aprovação antes do resultado |
| Samsung A03, mãos reais, cursor/voz em outros apps | **Não executados** nesta etapa |
| Nova instalação com Play Protect ativado | **Não executada**; sem aprovação do Google |

Os testes isolados acima não são somados aos 205 como se representassem casos distintos; parte deles é executada novamente pelo Gradle. A redução do APK já foi medida; a redução do total instalado no A03 ainda não foi medida.

## Instalação, distribuição e limites

O novo download público ainda está em preparação. O arquivo local instalável fica em `app/build/outputs/apk/coreTest/app-coreTest.apk`. O [download alpha.2 existente](https://github.com/Cazi1n8/nexus-android/releases/download/v3.0.0-alpha.2-download-16/NEXUS-3.0.0-alpha.2.apk) é histórico e continua grande; não foi substituído silenciosamente.

O CORE local usa a chave de testes existente, certificado SHA-256 `ffc6524eee21a185872e946e78649a977f4b82011ee573c20adf5ca6ba376f79`. O APK público alpha.2 do build 16 foi assinado pelo certificado `22f77037ab58341a0066ab5089d5122755cebd79610f22e3b51cfb5c6cdfebc4`. A chave privada temporária daquele runner não está disponível. **Essa diferença impede atualização direta.** A distribuição precisa registrar a assinatura real do novo APK; não se pode prometer continuidade de dados por atualização com outra chave. Exporte os dados antes de qualquer substituição e não remova o aplicativo atual sem conferir a assinatura e a recuperação dos dados.

A publicação estável ainda depende da chave protegida do responsável e de validação de distribuição. Não foram adicionadas credenciais ao repositório. Mantenha o Play Protect ativado e siga o [processo de revisão oficial](docs/PLAY_PROTECT_REVISAO.md) se houver bloqueio. O motivo exato do bloqueio anterior permanece desconhecido; reduzir o pacote não comprova sua aprovação.

Os modelos opcionais continuam sendo os mesmos modelos pequenos que falharam em parte da [avaliação semântica da alpha.2](TESTES_NEXUS_AI.md). Esta tarefa corrige armazenamento e entrega de dados, sem afirmar que resolveu a inteligência, o alfabeto completo de Libras ou todas as restrições de segundo plano.

## Como verificar a mudança no celular

1. Confira versão, hash e certificado do APK entregue; mantenha as proteções Android ativas.
2. Exporte histórico e memórias autorizadas antes de substituir uma instalação que use certificado diferente.
3. Abra o CORE e confirme que o mouse pode ser ativado sem baixar IA; câmera e acessibilidade só devem ser solicitadas na ação correspondente.
4. Abra a Central de Armazenamento e anote aplicativo, dados e cache antes de baixar qualquer pacote.
5. Na Central de Extensões, baixe somente um pacote desejado. Verifique a mudança de espaço e, depois de encerrar seu uso, remova-o pelo controle próprio.
6. Confira que conversas/configurações e o rastreador do mouse continuam presentes. Compare os valores do próprio Android com o registro anterior; não confunda APK com total instalado.
