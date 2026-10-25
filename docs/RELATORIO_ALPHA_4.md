# NEXUS 2.0.0-alpha.4 — IA incluída e correções dos relatos

Atualizado em **25/10/2026 (UTC)**. Código de versão Android: **23**.

## Estado desta revisão

**APK de teste publicado, baixado e verificado; compilação e provas Android aprovadas.** Esta versão altera o pacote padrão para incluir a IA compacta, em vez de pedir um segundo download de modelo no telefone. Também trata pontos concretos de matemática, leitura de texto em imagens, prévia da câmera, sessão global, treino preparatório e diagnóstico.

O build local final terminou em **2 min 27 s**, e o build GitHub em **2 min 36 s**. Passaram **129 testes JVM**, **66 testes Python dos scripts** e **44 do servidor opcional**. A suíte comum Android teve **15 aprovados, zero falhas/erros e três casos opt-in ignorados**; os três passaram em suas execuções dedicadas. A prova do APK incluído abriu o Chat real sem rede, preparou o modelo pelo ViewModel, gerou uma resposta local, calculou a expressão relatada e leu uma imagem por OCR, persistindo três respostas no Room.

A entrega pública foi inspecionada separadamente do APK local. O Samsung Galaxy S9+ do usuário **não foi testado fisicamente** pelo ambiente; a aprovação do Play Protect **não foi obtida nem garantida**. As evidências abaixo pertencem à alpha.4; as provas da [alpha.3](RELATORIO_ALPHA_3.md) permanecem históricas.

O usuário confirmou que conseguiu concluir o download anterior, mas relatou bloqueio de instalação pelo Play Protect e falhas de uso. Esses relatos permanecem critérios de validação; não são descartados pelos testes emulados.

## Mudanças implementadas e limites

| Área | Alteração no código | O que ainda precisa ser comprovado |
| --- | --- | --- |
| IA dentro do APK | O build padrão inclui o Qwen compacto como asset não comprimido, preparado previamente no computador/CI com tamanho e SHA-256 fixados. Os pesos ficam fora do Git, com licença/atribuição incluídas no pacote. | APK publicado inspecionado: 615.085.340 bytes; GGUF, licença e integridade conferidos. Instalação física/Play Protect continuam sem aprovação. |
| Primeiro Chat | A abertura do Chat inicia a preparação local do asset, com cópia privada, progresso, cancelamento/retomada e verificação antes de `READY`. Não precisa buscar um GGUF pela rede no telefone. | Aprovado pela interface → ViewModel → cópia/verificação → inferência → Room, com rede desligada e sem modelo transferido por ADB. O primeiro chat no S9+ ainda precisa de confirmação. |
| Adaptação | O modelo base compacto permanece estável entre sessões; contexto, threads, saída e análise de câmera têm orçamento baseado nos recursos. Arquivos parciais entram no cálculo do espaço restante necessário. | Dispositivos de 32 bits, pouca RAM ou Android abaixo do mínimo não ganham suporte à IA local. Testar o perfil e a estabilidade no aparelho real. |
| Modo de IA | A escolha visível de local/provedor personalizado passa a persistir o modo utilizado pelo Chat. | Regressões emuladas aprovadas, incluindo token de sessão e recriação. O perfil compacto permaneceu estável após recriar a atividade na prova incluída. Não há fallback remoto silencioso. |
| Matemática | `ArithmeticTool` resolve expressões explícitas com números, parênteses, potência inteira, decimais, precedência e limites, antes de pedir uma resposta ao modelo. `10+10÷10×10` tem resultado `20`. | Testes JVM aprovados e expressão relatada respondida como `20` pelo Chat real no Android emulado. Não certifica raciocínio geral, programação ou todas as respostas do Qwen pequeno. |
| Imagem no Chat local | `LocalImageTextProvider` usa OCR local de português/inglês para transcrever texto de anexos e, quando solicitado, levar uma parte delimitada dessa transcrição ao modelo de texto. A resposta identifica a origem por OCR e sua limitação. | O Chat emulado transcreveu `OLA NEXUS 2026` de um anexo de teste e persistiu a resposta. Foto real no aparelho ainda precisa de teste. Não há reconhecimento local geral de objetos/cenas/circuitos/vídeo. |
| Prévia da câmera | Mouse interno permite ocultar a prévia; o mouse global usa análise sem janela de vídeo. Libras também permite ocultar a imagem. | Ocultar vídeo não desliga a câmera nem implementa segundo plano para outras telas. Indicadores de privacidade, notificação e parada continuam visíveis. |
| Mouse global | Serviço iniciado explicitamente preserva a sessão ao remover a tarefa dos recentes, com notificação persistente e parada de emergência. Não reinicia ocultamente após ser morto. | Faltam cliques/rolagem reais em outros apps no S9+. Android, fabricante, bloqueio, câmera ocupada, falta de quadros e permissões podem interromper a sessão. |
| Travamentos | Rastreamento entrega o quadro recente à UI sem acumular resultados antigos; compatibilidade do serviço é lida fora da thread principal. | São correções de pontos específicos. Não há medição física que comprove eliminação de todos os travamentos, consumo de bateria ou aquecimento. |
| Libras: continuidade | Registrar acerto não termina a sessão de treino; a referência da atividade aparece no canto. Há opção de ocultar vídeo e modo livre com configuração observada/recusa de avaliação. | O módulo reconhece somente duas configurações geométricas preparatórias, **não letras ou sinais de Libras**. Faltam classificador/conteúdo validado, testes físicos e execução do treino fora da atividade. |
| Diagnóstico e privacidade | Tela inicial mostra versão, compatibilidade, permissões e estados. Hardware fica atrás de uma ação explícita; modelo é o informado pelo Android e RAM é rotulada como leitura do sistema, não ficha técnica comercial. Consultas atualizadas ocorrem em executor de IO. | Nome pode ser código do fabricante/ROM. O app não infere que um telefone é S9+ por causa da conversa, não consulta IMEI/série/publicidade e não envia o diagnóstico. |
| Play Protect | Manifesto e capacidades sensíveis revisados; limites e distribuição documentados. Acessibilidade permanece declarada para os gestos globais autorizados, sem leitura da árvore de telas. | **O bloqueio relatado não está comprovadamente resolvido e não houve aprovação do Google.** Não foram ocultados serviços/avisos nem sugerido desativar a proteção. |

Fontes: [preparação do modelo](../scripts/fetch_bundled_model.py), [gerenciador](../app/src/main/java/br/com/nexus/assistant/data/local/LocalModelManager.kt), [ViewModel](../app/src/main/java/br/com/nexus/assistant/NexusViewModel.kt), [cálculo](../app/src/main/java/br/com/nexus/assistant/data/ArithmeticTool.kt), [OCR no Chat](../app/src/main/java/br/com/nexus/assistant/data/LocalImageTextProvider.kt), [Libras](../app/src/main/java/br/com/nexus/assistant/libras/NexusLibrasScreen.kt) e [diagnóstico](../app/src/main/java/br/com/nexus/assistant/diagnostics/NexusDiagnostics.kt).

## Modelo incluído

| Item | Valor fixado |
| --- | --- |
| Modelo de terceiro | Qwen2.5-0.5B-Instruct, quantização Q4_K_M |
| Arquivo no APK | `models/qwen2.5-0.5b-instruct-q4_k_m.gguf` |
| Tamanho do GGUF | 491.400.032 bytes |
| SHA-256 | `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db` |
| Revisão upstream | `9217f5db79a29953eb74d5343926648285ec7e67` |
| Licença | Apache 2.0; cópia em `assets/licenses/Qwen2.5-0.5B-Apache-2.0.txt` e atribuição em `THIRD_PARTY_NOTICES.md` |

A inclusão aumenta o APK em aproximadamente o tamanho do modelo. No primeiro preparo, o app também precisa de espaço para sua cópia privada; o asset permanece no APK. Portanto, o espaço instalado é maior que o tamanho de download do APK. O modelo não está embutido em arquivos de código nem é tratado como criação ou treinamento exclusivo do NEXUS.

O APK único mantém os requisitos de Android 8/API 26 ou superior. Inferência local exige processo Android de 64 bits, RAM e armazenamento adequados. A interface e recursos compatíveis podem funcionar sem a inferência local; provedor personalizado é opcional e depende de consentimento/configuração explícitos.

Revisão final testada/publicada: `48938e7205b329565766e3e4342aff016d801c03`. As alterações finais foram restritas aos testes; a árvore de `app/src/main` é `ff4e354de03ea744ec242d3b9a3ad60bc81854b7`, idêntica à fonte local `340bb35a4548f4e131db13ec4522b104c17d1923`. [Evidência local](evidencias/alpha4-local-verification.json) e [inspeção do APK local](evidencias/alpha4-local-apk.json).

## Verificação da revisão

Resultados observados na revisão final, com evidências locais, emuladas e do pacote publicado separadas:

| Verificação alpha.4 | Resultado desta revisão |
| --- | --- |
| Scripts Python | **66 aprovados**, localmente e no GitHub |
| Servidor opcional | **44 aprovados no GitHub**; não certificam o modo local |
| Compilação local/GitHub | **Build local final aprovado em 2 min 27 s; build GitHub aprovado em 2 min 36 s** |
| Testes JVM, incluindo asset/cálculo/OCR | **129 aprovados, 0 falhas/erros e 0 ignorados**, localmente e no GitHub |
| Lint | Local: **0 erros, 42 avisos, 1 informação**. GitHub: **0 erros, 43 avisos, 1 informação** |
| Suíte comum Android | **18 casos: 15 aprovados, 0 falhas/erros, 3 opt-in ignorados**. Os três casos passaram nas provas dedicadas |
| Primeiro Chat com IA do APK e rede desligada | **1 prova dedicada aprovada**, instrumentação em **33,272 s**. Preparo pelo ViewModel em **4.748 ms**, modelo `BUNDLED`/`COMPACT`, inferência `Azul.` em **10.686 ms**, cálculo `20`, OCR `OLA NEXUS 2026`, três respostas persistidas e perfil estável após recriação |
| APK local: assinatura, empacotamento e GGUF | **615.322.523 bytes**, assinatura v2, alinhamento ZIP 16 KB, quatro ABIs e 40 bibliotecas ELF. GGUF incluído com tamanho/SHA-256 fixados e hashes OCR conferidos |
| Download público do APK | **Publicado, baixado e verificado:** 615.085.340 bytes; SHA-256, assinatura v2, ZIP 16 KB, quatro ABIs, 40 ELFs, GGUF/OCR e licença conferidos |
| Inferência JNI com fixture | **1 teste dedicado aprovado**, com quatro casos de resposta real; a fixture é fornecida por ADB somente nessa prova separada |
| Download legado pelo app | **1 teste dedicado aprovado**, dois modelos baixados pelo próprio Android, verificados e inferência `42` após o compacto. Esse fluxo usa o build de regressão sem GGUF incluído |
| Testes físicos no S9+ | Não executados pelo ambiente |
| Instalação aceita pelo Play Protect | Não comprovada; bloqueio relatado pelo usuário continua aberto |

APK local: SHA-256 `5886db00ce9db33fddf202bcb87601ce7ba63d04d53a9c387d44f6884b00d716`. Esse hash pertence ao arquivo local; o pacote publicado foi comparado separadamente e tem hash próprio abaixo.

Nenhum número de uma execução anterior deve substituir esses campos. Testes geométricos com landmarks sintéticos não comprovam mãos reais ou letras de Libras. Um teste de OCR não comprova compreensão multimodal geral. Inicializar JNI ou MediaPipe não aprova câmera, voz, gestos ou desempenho físicos.

## Execuções Android e evidências

| Workflow | Execução aprovada | Escopo |
| --- | --- | --- |
| Build e publicação | [40199143023](https://github.com/Cazi1n8/nexus-android/actions/runs/40199143023) | JVM, scripts, servidor, lint, APK e publicação |
| Android comum | [40199143106](https://github.com/Cazi1n8/nexus-android/actions/runs/40199143106) | 15 aprovados e três opt-in ignorados |
| Chat incluído offline | [40199143131](https://github.com/Cazi1n8/nexus-android/actions/runs/40199143131) | Chat real, preparo do asset, inferência, matemática, OCR e Room |
| JNI com fixture | [40197768477](https://github.com/Cazi1n8/nexus-android/actions/runs/40197768477) | Quatro casos reais na prova dedicada |
| Download legado | [40197768490](https://github.com/Cazi1n8/nexus-android/actions/runs/40197768490) | Dois modelos pelo downloader e resposta após preparo |

Na prova incluída, não havia rede ativa antes nem depois da sessão. O próprio ViewModel preparou os **491.400.032 bytes** do asset e conferiu o SHA-256. A imagem de OCR era um anexo sintético conhecido; não se trata de uma foto real ou prova de compreensão de objetos. Os tempos pertencem ao emulador Android 10/API 29 x86_64 e não estimam velocidade no S9+.

Evidências: [resumo CI](evidencias/alpha4-ci-final.json), [JUnit comum](evidencias/alpha4-android10-junit.xml), [observações do Chat incluído](evidencias/alpha4-bundled-chat-bundled-chat-observations.json), [JUnit dedicado](evidencias/alpha4-bundled-chat-junit.xml), [instrumentação](evidencias/alpha4-bundled-chat-instrumentation.txt), [JNI](evidencias/alpha4-native-ai-local-ai-observations.json), [download legado](evidencias/alpha4-production-production-download-observations.json) e [APK publicado](evidencias/alpha4-published-apk.json).

## APK publicado

**[Baixar NEXUS 2.0.0-alpha.4.apk](https://github.com/Cazi1n8/nexus-android/releases/download/v2.0.0-alpha.4-build-13/NEXUS-2.0.0-alpha.4.apk)** — **615.085.340 bytes** (~586,6 MiB), com a IA compacta incluída. Release [v2.0.0-alpha.4-build-13](https://github.com/Cazi1n8/nexus-android/releases/tag/v2.0.0-alpha.4-build-13).

SHA-256 do APK: `0c7059c77d07bc7eed40d5d7701a36e98ca3495a1d77a74c2500f7c7e85286df`. Assinatura v2 e alinhamento ZIP de 16 KB verificados; quatro ABIs e 40 bibliotecas ELF, além do GGUF exato, OCR e licença. Essa inspeção comprova o pacote, sem representar aprovação do Play Protect ou certificação física do telefone.

## Reproduzir o build padrão

```bash
python3 scripts/fetch_llama_cpp.py
python3 scripts/fetch_hand_model.py
python3 scripts/fetch_ocr_models.py
python3 scripts/fetch_ocr_runtime.py
python3 scripts/fetch_bundled_model.py
python3 -m unittest discover -s scripts/tests -v
./gradlew --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Os downloads de dependências/modelo acima acontecem no computador ou CI. A primeira abertura do Chat prepara o asset já incluído no APK; não corresponde a outra transferência de GGUF no telefone. Para builds de regressão sem o modelo incluído, o parâmetro de desenvolvimento é `-PnexusBundleModel=false`; esse pacote não deve ser confundido com a entrega padrão pedida pelo usuário.

A prova dedicada do Chat incluído é opt-in e usa o workflow [bundled-chat-smoke.yml](../.github/workflows/bundled-chat-smoke.yml). O runner restringe limpeza de dados e alteração temporária de conectividade ao emulador autorizado, restaura a conectividade ao encerrar e recolhe observações/JUnit. Não transfere um GGUF separado por ADB para satisfazer a prova.

## Segundo plano e interrupção

[Permissões e segundo plano](PERMISSOES_E_SEGUNDO_PLANO.md) descreve a implementação e os limites.

O mouse global possui serviço de câmera em primeiro plano. **Mãos livres, Libras, 3D, Vision e voz ainda não implementam toda a continuidade fora do app pedida pelo usuário.** Transferir essas sessões para serviços exige trabalho próprio de ciclo de vida, estado, consentimento, notificações, concorrência de câmera e limites do Android. Prévia oculta não equivale a serviço de segundo plano.

“Não parar em momento algum” não pode ser garantido: Android/fabricante podem encerrar processos, negar câmera/microfone ou limitar execução. A ação de parada explícita do usuário sempre precisa estar disponível; falhas, revogação e ausência persistente de quadros devem encerrar ações inseguras ou sem resposta.

## Distribuição e dados

Ainda é uma versão de teste. Assinatura de lançamento estável, revisão para Google Play e tratamento oficial do bloqueio Play Protect permanecem pendentes. O aviso apresentado não identifica sozinho a causa exata nem permite afirmar que uma alteração específica será aprovada.

O certificado publicado da alpha.4 tem SHA-256 `65297709dfb7a0149397313c1249cf40b3392913a4050f0af0c8a5951c394c1f` e difere do certificado da alpha.3. O Android não aceita atualização direta entre esses pacotes. **Se houver uma versão anterior instalada, exporte conversas importantes antes de qualquer remoção. Não desinstale enquanto a instalação nova estiver bloqueada pelo Play Protect.** Após resolver a autorização de instalação e a incompatibilidade de certificado, será necessária uma instalação nova da alpha.4. Desinstalar remove todos os dados privados e modelos/parciais anteriores; a exportação de texto não contém imagens nem token. O GGUF incluído no novo APK será preparado novamente, sem download separado do modelo no telefone.

## Pendências do pedido completo

A [matriz dos pedidos](PEDIDOS_E_STATUS.md) preserva o estado de implementação e validação de cada recurso. Permanecem em aberto, entre outros: instalação aprovada no aparelho, estabilidade e sensores físicos, todos os modos em segundo plano, alfabeto/sinais de Libras validados, compreensão multimodal local geral, contexto extenso com resumo persistente e distribuição estável pela Google Play.
