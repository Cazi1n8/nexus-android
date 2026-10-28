# NEXUS para Android

> **NEXUS 4.1 em validação (Galaxy A03):** correções de segurança, mouse global e latência estão sendo verificadas no projeto existente. Consulte [PROGRESSO_NEXUS_4_1.md](PROGRESSO_NEXUS_4_1.md). O bloqueio pelo Play Protect ainda não foi comprovadamente resolvido; não desative a proteção. CORE continua sem pesos generativos obrigatórios. A distribuição automática de novos APKs permanece suspensa nesta revisão.

Aplicativo Android existente em Kotlin e Jetpack Compose, em português brasileiro, com interface preta, azul e ciano. Código e histórico: https://github.com/Cazi1n8/nexus-android.

**Etapa atual: NEXUS CORE 3.0.0-alpha.3-core, versionCode 26.** A instalação padrão preserva o rastreamento de mãos e o mouse offline e deixa os modelos grandes de conversa e os dados de visão como downloads opcionais. Não é um aplicativo novo. Veja a [auditoria com os 20 maiores arquivos](AUDITORIA_ARMAZENAMENTO.md), o [progresso do armazenamento](PROGRESSO_ARMAZENAMENTO.md) e a [arquitetura de entrega modular](docs/ENTREGA_MODULAR_ANDROID.md).

## Tamanho medido e APK

Os arquivos abaixo foram realmente compilados e medidos. MB e GB são decimais; estes são tamanhos dos APKs, não uma previsão do total instalado no Samsung A03.

| APK local | Bytes | Tamanho | Comparação com alpha.2 |
| --- | ---: | ---: | ---: |
| Alpha.2 debug anterior | 1.738.201.604 | 1,74 GB | Referência |
| CORE debug final | 121.107.028 | 121,11 MB | 93,03% menor |
| CORE `coreTest`, assinado para testes, sem R8 | 108.210.081 | 108,21 MB | 93,77% menor |

Os dois GGUFs obrigatórios da alpha.2 somavam **1.608.720.768 bytes**, 92,55% daquele APK. O CORE não os empacota; também deixa OCR e classificação de objetos para download. O modelo de mãos de **7.819.105 bytes** continua incluído e verificado. A comparação entre debug e debug isola melhor a mudança de empacotamento. O catálogo de mais de 11 mil ícones foi substituído pelos 22 vetores oficiais efetivamente usados, mantendo suas licenças e a biblioteca de ícones básicos. `coreTest` e release preservam as classes e interfaces JNI, sem R8 ou redução automática de recursos nesta alpha.

**[Baixar NEXUS CORE — APK de teste de 108,21 MB](https://github.com/Cazi1n8/nexus-android/releases/download/v3.0.0-alpha.3-core-download-22/NEXUS-3.0.0-alpha.3-core.apk)**. Link direto, sem login ou extração de ZIP. É exatamente o APK do [build 22](https://github.com/Cazi1n8/nexus-android/actions/runs/40669746621), publicado sem recompilar ou assinar novamente. O download público completo foi conferido por tamanho e SHA-256. [Evidência da publicação](docs/evidencias/storage-core-direct-download.json).

**A assinatura de teste é diferente da alpha.2 do build 16 e impede atualização direta. Exporte seus dados antes de substituir uma instalação.** A tentativa experimental de 98,88 MB com R8 não foi publicada como versão recomendada; o arquivo entregue preserva as APIs necessárias sem R8.

O [APK alpha.2 histórico](https://github.com/Cazi1n8/nexus-android/releases/download/v3.0.0-alpha.2-download-16/NEXUS-3.0.0-alpha.2.apk) continua disponível sem login, mas possui aproximadamente 1,74 GB e **não contém a otimização CORE**. Não use seu tamanho ou seu hash para validar o novo arquivo.

## O que está incluído e o que pode ser baixado

| Recurso | Instalação CORE |
| --- | --- |
| Interface, navegação, configurações, histórico e memória local | Incluídos |
| CameraX, MediaPipe, modelo de mãos, cursor, clique e rolagem | Incluídos; não precisam de pesos de chat nem internet para o rastreamento |
| Serviços opcionais de mouse global e voz | Código incluído; ativação manual, consentimento e notificações |
| Central de Extensões e Central de Armazenamento | Incluídas |
| Chat com IA offline | Modelo opcional: compacto 491.400.032 bytes ou 1,5B 1.117.320.736 bytes, conforme compatibilidade |
| Classificação local de objetos Vision | Dados opcionais: 5.434.517 bytes |
| OCR português e inglês | Dados opcionais: 6.095.844 bytes |
| 3D e exercícios experimentais de Libras | Código integrado; usam o rastreador incluído, sem baixar código adicional |
| Voz Android em pt-BR | Código integrado; reconhecimento/TTS e eventuais pacotes de voz dependem do mecanismo Android instalado |
| IA online | Cliente integrado; precisa de servidor real configurado e autorização de envio. Não há backend NEXUS público implantado |

Os pacotes baixados são **dados de modelos de origens fixadas**, com tamanho e SHA-256 verificados. Não são `.dex`, bibliotecas `.so` ou plugins executáveis remotos. Nesta versão, os códigos leves de Vision, 3D, Libras e voz permanecem no APK; a Central de Extensões informa isso. Módulos binários independentes da Google Play ainda não foram implementados.

**Não há download automático ao abrir o Chat ou o aplicativo.** A seleção por compatibilidade recomenda um perfil, mas a instalação do modelo depende de sua escolha. Baixar um perfil não muda silenciosamente o perfil usado na conversa.

## Usar e conferir no celular

Android **8.0/API 26 ou superior**. A instalação universal mantém ARM32, ARM64, x86 e x86_64. O NEXUS consulta os recursos reais do Android, sem presumir RAM ou arquitetura pelo nome “Galaxy A03”. A IA local exige processo de 64 bits, memória e espaço suficientes; uma limitação da IA local não desativa o mouse offline.

1. Na Home, abra **Mouse virtual** para usar o rastreador incluído. A câmera só deve ser autorizada ao ativar o recurso. O mouse global usa consentimento e acessibilidade oficiais; a notificação permite interrompê-lo.
2. Abra **Central de extensões** para escolher os dados opcionais. Consulte o tamanho antes de baixar e aguarde a verificação. Os dados de classificação e OCR permitem as análises locais de Vision.
3. Para conversar offline, abra **Gerenciar modelos de IA**, escolha um modelo compatível e confirme o download. Depois selecione **Usar este modelo no chat**. Sem modelo local e sem servidor online configurado, o Chat informa essa condição, sem simular respostas.
4. Abra **Armazenamento → Atualizar medição**. A tela separa aplicativo, dados e cache informados pelo Android e mostra modelos, downloads parciais, extensões, imagens de conversa, banco, preferências e outros arquivos privados.
5. Em **Gerenciar modelos de IA**, remova explicitamente um perfil que não queira manter. Em **Gerenciar extensões e pacotes**, remova os dados de visão. **Limpar cache temporário** preserva modelos, conversas, configurações e cache de código.

Uma atualização com assinatura compatível para CORE **preserva os dados antigos**: os cerca de 498 MB relatados não desaparecem automaticamente. A cópia privada do modelo compacto, mais os dados OCR copiados na alpha.2, somam 497.495.876 bytes; é uma hipótese compatível com o relato, ainda sem inspeção física do A03. A Central permite medir e remover os pacotes conscientemente. O cache de 13,51 MB não era a causa dos 1,90 GB do aplicativo.

Os totais do Android incluem otimizações e contabilização do sistema. A medição lógica dos arquivos não é somada novamente a esses totais; o cache é separado dos dados sem contá-lo duas vezes. A consulta é somente do próprio NEXUS, sem permissão para observar armazenamento de outros aplicativos.

## Estado real das outras funções

A redução de tamanho **não muda a capacidade dos modelos de linguagem**. A avaliação completa da alpha.2 registrou 15 casos, 35 gerações reais e duas ferramentas; a qualidade foi reprovada por erros de interpretação e alegação falsa de acesso à câmera. Os mesmos modelos continuam opcionais, com essas limitações. [Resultados de IA](TESTES_NEXUS_AI.md) e [progresso 3.0](PROGRESSO_NEXUS_AI_3.md).

- A identidade **NEXUS** é separada do nome do usuário; preferências, histórico, Markdown, copiar, ouvir e interromper permanecem no projeto.
- O contexto usa orçamento de tokens e reaproveitamento do prefixo, sem limite fixo de quatro mensagens. A calculadora real resolve `10+10÷10×10 = 20` e `25 vezes 18 = 450`; o modelo ainda pode errar outros problemas.
- Vision local faz OCR e classificação de objetos em quadros periódicos. Não equivale à compreensão geral de imagens por um modelo multimodal nem a processamento contínuo de vídeo. Um provedor remoto compatível com imagens exige configuração e consentimento.
- Mouse global e voz têm sessões independentes iniciadas pelo usuário, notificações e parada. **Continuidade, precisão e consumo na câmera/microfone físicos do A03 ainda precisam ser testados.**
- A voz depende de SpeechRecognizer/TTS instalados e pode usar rede. A conversa opcional tem limite de 15 minutos ou 30 perguntas e até duas tentativas após silêncio; não há detector permanente de “Ei, NEXUS”.
- Libras possui candidatas experimentais L/V/Y e exercícios preparatórios; não reconhece nem certifica o alfabeto inteiro. O 3D usa projeção e mãos rastreadas, sem posicionamento espacial preciso de realidade aumentada.

Vision, Libras, Mãos Livres e 3D usam câmera vinculada à tela. Restrições de Android, fabricante, bateria, memória ou permissões podem interromper recursos; não há promessa de operação ilimitada. [Testes de segundo plano](TESTES_SEGUNDO_PLANO.md).

## Compilar no computador

JDK 17, Android SDK 35, build-tools 35.0.0, NDK 28.2.13676358 e CMake 3.22.1. Defina `ANDROID_HOME` ou `sdk.dir` em `local.properties` (fora do Git). Gradle Wrapper 8.11.1; reserve 4 GB de heap para compilação.

Preparação do CORE, sem baixar os GGUFs:

```bash
python3 scripts/fetch_llama_cpp.py
python3 scripts/fetch_hand_model.py
python3 scripts/prepare_mediapipe_runtime.py
python3 scripts/fetch_ocr_runtime.py
./gradlew --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process -PnexusTestBuildType=coreTest testDebugUnitTest lintDebug lintCoreTest assembleDebug assembleCoreTest assembleCoreTestAndroidTest
```

No Windows, use `gradlew.bat`. O runtime nativo de IA e OCR continua integrado para usar dados opcionais; seus pesos não são obrigatórios. Saídas:

- `app/build/outputs/apk/debug/app-debug.apk`: depuração, sem redução R8.
- `app/build/outputs/apk/coreTest/app-coreTest.apk`: teste instalável, não depurável, sem R8; usa a chave de testes existente, não uma identidade de produção.
- `app/build/outputs/apk/androidTest/coreTest/app-coreTest-androidTest.apk`: instrumentação da variante `coreTest`. Para `assembleDebugAndroidTest`, use o tipo de testes padrão `debug`, em outra execução sem `-PnexusTestBuildType=coreTest`.
- `assembleRelease` sem credenciais: análise do release sem assinatura; o resultado **não é instalável**. Para distribuir com chave estável, siga [assinatura e distribuição](docs/ASSINATURA_E_DISTRIBUICAO.md).

Para repetir a variante antiga com todos os dados embutidos, prepare os arquivos e opte explicitamente:

```bash
python3 scripts/fetch_bundled_model.py --with-balanced
python3 scripts/fetch_object_model.py --with-test-fixture
python3 scripts/fetch_ocr_models.py
./gradlew -PnexusBundleModel=true -PnexusBundleVision=true assembleDebug
```

`nexusBundleModel=false` e `nexusBundleVision=false` são os padrões. `-PnexusAbis=x86_64` é uma opção para emulador, não uma exigência para o celular; a compilação padrão mantém as quatro arquiteturas. Na Google Play, AAB e mecanismos oficiais de entrega exigem configuração própria; o APK manual atual oferece downloads de dados verificados. [Detalhes e limites da entrega modular](docs/ENTREGA_MODULAR_ANDROID.md). [Licenças de terceiros](THIRD_PARTY_NOTICES.md).

## Verificações desta etapa

A compilação local final terminou com **BUILD SUCCESSFUL em 8 min 17 s**, com 207 tarefas: **205 testes JVM aprovados**, zero falhas, erros ou ignorados; lint debug e `coreTest` com **zero erros, 50 avisos e uma informação** cada. Os APKs debug, `coreTest`, release sem assinatura e instrumentação `coreTest` foram gerados. A suíte Python teve **148 testes aprovados**; no servidor opcional, 25 de 44 casos passaram e 19 foram ignorados nesta execução. [Resultados e limites](PROGRESSO_ARMAZENAMENTO.md).

O [build 22 no GitHub](https://github.com/Cazi1n8/nexus-android/actions/runs/40669746621) passou. O [CORE5](https://github.com/Cazi1n8/nexus-android/actions/runs/40669746317) confirmou **3 métodos de teste Android aprovados**: instalação CORE sem internet e sem download automático; rastreador real em quadro vazio e telas de extensões/armazenamento; downloads HTTPS de 11.530.361 bytes, OCR/classificação nativos, reutilização, retomada a partir de prefixo verificado e remoção preservando o modelo de mãos e dados sintéticos. [Evidência real](docs/evidencias/storage-core-ci5.json).

A suíte adicional [DeviceSmoke18](https://github.com/Cazi1n8/nexus-android/actions/runs/40721031770) confirmou **29 aprovados, zero falhas e 6 ignorados**. O teste de primeiro Chat passou após usar a ação acessível real do cartão, já adotada nos testes de Vision. A falha anterior de toque sintético durante a rolagem foi preservada no relatório. A revisão alterou somente testes e configuração do emulador; o código do aplicativo continua igual ao do APK do build 22. [Evidência e limites](docs/evidencias/storage-device-ci18.json). Os resultados não tornam aprovadas as avaliações semânticas anteriores da IA.

Duas tentativas anteriores de instrumentação CORE falharam: timeout sem causa demonstrada e ausência de `androidx.tracing.Trace` antes de iniciar a Activity na variante experimental com R8. A inspeção também encontrou interfaces JNI removidas. A versão final preserva essas APIs sem R8 e usa somente os ícones necessários. Os resultados do emulador não comprovam mãos/câmera/voz físicas no A03, o total instalado no aparelho ou aprovação do Play Protect.

## Segurança e atualização

Conversas/memórias autorizadas ficam em Room; preferências em DataStore. O token opcional usa Android Keystore. O diagnóstico técnico é local e não registra mensagens, fotos, nomes ou chaves. Não usamos IMEI, SMS, contatos, leitura de janelas alheias ou `PACKAGE_USAGE_STATS`. Acessibilidade executa somente gestos autorizados; câmera e microfone são opcionais e visíveis. [Auditoria Play Protect](AUDITORIA_PLAY_PROTECT.md).

**Mantenha o Play Protect ativado.** O motivo exato do bloqueio relatado não foi comprovado. Se o APK for bloqueado, registre versão, hash, certificado e mensagem para [revisão oficial](docs/PLAY_PROTECT_REVISAO.md). Não há garantia de aprovação por reduzir o tamanho.

O APK do build 22 possui SHA-256 `e76ff4e228206a11317000a238556d8c24693532c1527b05f8264f5ba6b50d15` e certificado SHA-256 `0e72073647f9fd5b4bc27f6f8fbafa9100f4fadc391433ff6844fc2f96ec97fc`. A cópia local possui outro hash/certificado; não misture seus identificadores. O certificado do build 22 e o do CORE local diferem do APK alpha.2 público do build 16. A chave privada temporária daquele runner não está disponível; a assinatura antiga não pode ser reproduzida a partir do APK. **Certificados diferentes impedem atualização direta. Exporte seus dados antes de qualquer substituição e não remova a instalação atual sem conferir a assinatura.** Não foi criada uma chave de produção para substituir silenciosamente a anterior. O responsável ainda precisa configurar sua chave estável em Secrets para futuras distribuições. Publicações de APK de teste devem registrar o certificado real de cada arquivo.
