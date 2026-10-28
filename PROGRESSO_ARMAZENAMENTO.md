# NEXUS CORE — progresso da correção de armazenamento

Atualização: 28/10/2026. Projeto existente, `3.0.0-alpha.3-core`, versionCode 26, Android mínimo 8.0/API 26, alvo 35. Os arquivos abaixo foram modificados no próprio NEXUS; o histórico da alpha.2 foi preservado.

## Problema identificado e medição real

O Samsung Galaxy A03 informou aplicativo 1,90 GB, dados 498 MB e cache 13,51 MB, total 2,41 GB. Sem acesso ADB ao aparelho, esses números são o relato do usuário, não uma medição de arquivos feita por esta equipe. O APK local alpha.2 foi preservado antes das alterações para permitir comparação real.

| Artefato local compilado | Bytes medidos | Redução frente à alpha.2 |
| --- | ---: | ---: |
| Alpha.2 debug anterior | 1.738.201.604 | Referência |
| CORE debug final, ícones selecionados | 121.107.028 | 1.617.094.576 bytes; 93,0326% |
| CORE `coreTest` final, assinado para testes, sem R8 | 108.210.081 | 1.629.991.523 bytes; 93,7746% |
| Release final sem assinatura, não instalável | 108.197.793 | 93,7753%; somente inspeção |

Os dois modelos generativos somavam **1.608.720.768 bytes**, 92,5509% do APK anterior. Eles não participam do mouse. A [auditoria](AUDITORIA_ARMAZENAMENTO.md) contém os 20 maiores arquivos, ABIs, bibliotecas, recursos, duplicatas e detalhes dos 609 arquivos do APK antigo. O CORE debug serve à comparação entre variantes equivalentes. A compilação final usa somente os 22 vetores adicionais realmente usados, com origem/licença oficiais, e preserva os ícones básicos. R8 e redução automática de recursos estão desativados em release e `coreTest` para preservar as interfaces de SDK, JNI e instrumentação.

A tentativa anterior de aproximadamente 98,88 MB com R8 foi **experimental** e falhou antes de poder validar o funcionamento do CORE. Não foi publicada como APK final recomendado. [Registro das falhas](docs/evidencias/storage-core-instrumentation-failures.json): CORE2 teve timeout sem causa identificada; CORE3 falhou antes da Activity por `NoClassDefFoundError` em `androidx.tracing.Trace`. A inspeção estática confirmou remoção de interfaces JNI do MediaPipe, mas isso não prova a causa do timeout anterior. A arquitetura final sem R8 foi validada no CORE5, conforme os resultados abaixo; isso não apaga o registro das tentativas que falharam.

A inspeção dos APKs finais confirmou ausência de GGUF, ONNX, dados OCR e classificador de objetos no CORE; o inventário está em [storage-core-after.json](docs/evidencias/storage-core-after.json). O modelo real de mãos mantém os **7.819.105 bytes** e o SHA-256 fixado. As quatro arquiteturas são mantidas na compilação universal; não foi removida uma ABI apenas para fazer o número cair.

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
| Dependências de ícones | Catálogo `material-icons-extended` removido; 22 vetores oficiais usados conservados com licença/proveniência, além de `material-icons-core` |
| Variante instalável menor | `coreTest` não depurável e sem R8, assinada pela chave existente de testes; não é uma chave de produção nova |
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

A compilação local final terminou com **BUILD SUCCESSFUL em 8 min 17 s**, 207 tarefas, em `nexus-core-selected-icons-build.log`. O resultado corresponde aos ícones selecionados e à minificação desativada. Comando executado com JDK 17, SDK 35 e Gradle Wrapper 8.11.1:

```bash
./gradlew --no-daemon --no-build-cache --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process -PnexusTestBuildType=coreTest --stacktrace testDebugUnitTest lintDebug lintCoreTest assembleDebug assembleCoreTest assembleRelease assembleCoreTestAndroidTest
```

| Verificação | Resultado confirmado |
| --- | --- |
| Suíte JVM Gradle final | **205 aprovados**, zero falhas, erros ou ignorados |
| Android Lint final debug e coreTest | Cada variante com **zero erros, 50 avisos e uma informação** |
| APKs finais debug, coreTest e release | Gerados e medidos; release sem credenciais permanece sem assinatura e não é instalável |
| APK final de testes Android coreTest | Gerado; a execução CORE5 foi concluída em emulador |
| Scripts Python atuais | **148 testes aprovados**, zero falhas |
| Servidor opcional | 44 casos executados: **25 aprovados e 19 ignorados** nesta execução; não são 44 aprovações e não comprovam servidor implantado |
| Scanner de armazenamento, execução isolada | 8 testes host aprovados: categorias, links, contagem, limpeza e dados/cache; [evidência](docs/evidencias/storage-scanner-tests.json) |
| Gerenciador de modelos, execução isolada | 18 testes host aprovados, incluindo migração sem HTTP/cópia duplicada; [evidência](docs/evidencias/storage-core-optional-model-tests.json) |
| Pacotes opcionais, execução isolada | 6 testes host aprovados: catálogo, integridade, caminhos e uso ativo; [evidência](docs/evidencias/storage-extensions-unit.json) |
| Instrumentação experimental com R8 | CORE2 e CORE3 falharam; sem relatório CORE aprovado e sem executar a etapa de extensões; [evidência negativa](docs/evidencias/storage-core-instrumentation-failures.json) |
| Nova instalação CORE sem R8 no emulador | [CORE5, fonte 4f0bdca](https://github.com/Cazi1n8/nexus-android/actions/runs/40669746317) **aprovado: 3 métodos Android**, dois CORE e um de extensões; [evidência](docs/evidencias/storage-core-ci5.json) |
| Downloads reais de extensões e uso dos dados privados no Android | **Aprovados no CORE5**: 11.530.361 bytes via HTTPS/SHA, OCR/classificação nativos, reutilização, retomada de prefixo verificado, bloqueio de remoção durante uso e remoção preservando mãos/dados sintéticos |
| Build universal no GitHub | [Build 22](https://github.com/Cazi1n8/nexus-android/actions/runs/40669746621) aprovado; APK real de 108.210.081 bytes, assinatura e conteúdo conferidos |
| Suíte adicional DeviceSmoke18 | **29 aprovados, zero falhas, 6 ignorados**. Os dois testes de primeiro Chat passaram após a correção exclusiva da navegação do teste; [evidência](docs/evidencias/storage-device-ci18.json). A tentativa 17 anterior permanece registrada |
| Samsung A03, mãos reais, cursor/voz em outros apps | **Não executados** nesta etapa |
| Nova instalação com Play Protect ativado | **Não executada**; sem aprovação do Google |

Os testes isolados não são somados à suíte Gradle como casos distintos quando são reexecutados por ela. Os APKs finais já foram medidos. A redução do total instalado no A03 ainda não foi medida. Os três métodos do CORE5 não incluem gestos de mãos reais, câmera/microfone físicos ou avaliação do Play Protect; o JUnit agregado do runner não é contado como um quarto método Android.

## Instalação, distribuição e limites

O **[APK CORE de teste está disponível por download direto](https://github.com/Cazi1n8/nexus-android/releases/download/v3.0.0-alpha.3-core-download-22/NEXUS-3.0.0-alpha.3-core.apk)**, sem login ou ZIP: 108.210.081 bytes. A publicação [40722842421](https://github.com/Cazi1n8/nexus-android/actions/runs/40722842421) preservou o arquivo e a assinatura originais do build 22. O download público completo foi verificado por SHA-256; [evidência](docs/evidencias/storage-core-direct-download.json). A alpha.2 histórica continua preservada em seu release próprio.

Identificadores distintos dos arquivos medidos:

| Arquivo | SHA-256 do APK | Certificado SHA-256 |
| --- | --- | --- |
| CORE local | `6a75f85920757dee8040c6abfc09593ac8e9ecb0e721c5b532d544c5eab7803d` | `ffc6524eee21a185872e946e78649a977f4b82011ee573c20adf5ca6ba376f79` |
| CORE do build 22, publicado e conferido | `e76ff4e228206a11317000a238556d8c24693532c1527b05f8264f5ba6b50d15` | `0e72073647f9fd5b4bc27f6f8fbafa9100f4fadc391433ff6844fc2f96ec97fc` |

Ambos possuem 108.210.081 bytes; assinatura e ambiente de compilação produzem hashes diferentes. O artefato do build 22 é `12807709211`, fonte `4f0bdca2d1ad105480ae2a03f60c9bdb3648392c`, ZIP SHA-256 `17da3bb506173a8164a817bb897b88c145e44018377c5b6b488aaebab9aa2d6b`.

O CORE local usa a chave de testes existente, certificado SHA-256 `ffc6524eee21a185872e946e78649a977f4b82011ee573c20adf5ca6ba376f79`. O APK público alpha.2 do build 16 foi assinado pelo certificado `22f77037ab58341a0066ab5089d5122755cebd79610f22e3b51cfb5c6cdfebc4`. A chave privada temporária daquele runner não está disponível. **Essa diferença impede atualização direta.** O build 22 também usa certificado diferente da alpha.2, portanto não é uma atualização direta compatível. A distribuição precisa registrar a assinatura real do novo APK; não se pode prometer continuidade de dados por atualização com outra chave. Exporte os dados antes de qualquer substituição e não remova o aplicativo atual sem conferir a assinatura e a recuperação dos dados.

A publicação estável ainda depende da chave protegida do responsável e de validação de distribuição. Não foram adicionadas credenciais ao repositório. Mantenha o Play Protect ativado e siga o [processo de revisão oficial](docs/PLAY_PROTECT_REVISAO.md) se houver bloqueio. O motivo exato do bloqueio anterior permanece desconhecido; reduzir o pacote não comprova sua aprovação.

Os modelos opcionais continuam sendo os mesmos modelos pequenos que falharam em parte da [avaliação semântica da alpha.2](TESTES_NEXUS_AI.md). Esta tarefa corrige armazenamento e entrega de dados, sem afirmar que resolveu a inteligência, o alfabeto completo de Libras ou todas as restrições de segundo plano.

## Como verificar a mudança no celular

1. Confira versão, hash e certificado do APK entregue; mantenha as proteções Android ativas.
2. Exporte histórico e memórias autorizadas antes de substituir uma instalação que use certificado diferente.
3. Abra o CORE e confirme que o mouse pode ser ativado sem baixar IA; câmera e acessibilidade só devem ser solicitadas na ação correspondente.
4. Abra a Central de Armazenamento e anote aplicativo, dados e cache antes de baixar qualquer pacote.
5. Na Central de Extensões, baixe somente um pacote desejado. Verifique a mudança de espaço e, depois de encerrar seu uso, remova-o pelo controle próprio.
6. Confira que conversas/configurações e o rastreador do mouse continuam presentes. Compare os valores do próprio Android com o registro anterior; não confunda APK com total instalado.
