# Etapa atual — 2.0.0-alpha.4 (versão 23)

Etapa compilada, testada e publicada: IA compacta incluída no APK, cópia e verificação local sem download no celular, perfil base estável com processamento adaptativo; calculadora real; OCR integrado ao chat local; preview opcional oculto; mouse global continua fora do app/inrecentes; Libras sem encerramento automático após acerto, alvo no canto e modo livre de configurações geométricas; diagnóstico mínimo e detalhes por escolha.

**129 testes JVM e 66 testes Python aprovados localmente.** Compilação final aprovada em 2m27, APK local 615.322.523 bytes verificado, lint 0 erros/42 avisos/1 informação. GitHub aprovado: 129 JVM, 66 scripts e 44 testes do servidor opcional; lint 0 erros/43 avisos/1 informação. Android comum: 15 aprovados e 3 opcionais ignorados, com as três provas dedicadas aprovadas. Primeiro Chat com IA do APK sem internet: preparo em 4,748 s, resposta “Azul.”, cálculo 20, OCR “OLA NEXUS 2026” e três respostas persistidas em Room. APK público 615.085.340 bytes, assinatura v2/ZIP16K/GGUF/OCR verificados.

**[Baixar alpha.4](https://github.com/Cazi1n8/nexus-android/releases/download/v2.0.0-alpha.4-build-13/NEXUS-2.0.0-alpha.4.apk)**. SHA-256 `0c7059c77d07bc7eed40d5d7701a36e98ca3495a1d77a74c2500f7c7e85286df`. Código `48938e7205b329565766e3e4342aff016d801c03`; [evidência final](docs/evidencias/alpha4-ci-final.json). Resultados serão atualizados no [relatório alpha.4](docs/RELATORIO_ALPHA_4.md).

**Bloqueio de instalação relatado pelo Play Protect não foi resolvido ou aprovado pelo Google.** [Auditoria de permissões e limites de segundo plano](docs/PERMISSOES_E_SEGUNDO_PLANO.md). Voz, Libras, mãos livres e 3D ainda não têm serviço para operar fora do app. Não há reconhecimento validado do alfabeto de Libras ou visão multimodal local geral. As correções não demonstram ausência de travamentos no S9+ físico; essa validação permanece pendente.

O APK completo conterá 491.400.032 bytes de modelo Qwen0.5B, além do código/OCR. Nenhum peso/segredo é versionado. Assinatura estável para distribuição continua pendente. [Matriz de pedidos](docs/PEDIDOS_E_STATUS.md).

---

# NEXUS 2.0 — progresso verificável

Atualizado em **25/10/2026 (UTC)**. Código atual: **2.0.0-alpha.3**, código de versão 22, com APK de teste publicado, baixado e verificado. A alpha.2 permanece documentada como versão anterior com o bloqueio de download relatado pelo usuário. O projeto, o identificador Android e o schema Room v1 foram preservados. O Samsung Galaxy S9+ informado pelo usuário ainda não foi testado fisicamente pelo ambiente.

Esta atualização inclui o pedido de verificar o aparelho e ajustar recursos automaticamente. Compatibilidade do aplicativo, disponibilidade de uma função e teste físico aprovado são resultados diferentes.


## Correção alpha.3 entregue para testes

O usuário confirmou que a tela permanece em **“Baixando” com o tamanho completo recebido**. A alpha.3 altera a finalização: o downloader recebe somente o orçamento de bytes fixado do modelo, encerra a chamada sem esperar um EOF adicional, confere o SHA-256 do arquivo privado e promove o modelo a `READY`. Isso trata um cenário reproduzível de servidor que mantém a conexão aberta; a causa específica no S9+ ainda precisa de confirmação física.

| Item alpha.3 | Estado registrado |
| --- | --- |
| Finalizar transferência sem espera extra em 100% | 14 testes HTTPS aprovados na JVM e dois testes controlados aprovados no Android; tamanho completo sem EOF e retomada verificados |
| Retomar arquivo parcial | Implementado com `Range`, validação de `Content-Range` e reinício avisado quando o servidor retorna o arquivo inteiro; retomada aprovada em HTTPS controlado na JVM e no Android |
| Verificar arquivo completo já recebido | Implementado sem repetir o download; modelo só fica pronto após SHA-256 válido |
| Interface do download | Fases de conexão, transferência, verificação, pausa e pronto; bytes, velocidade e progresso de verificação implementados e compilados. O fluxo completo até `READY` foi aprovado no Android emulado; interação física no S9+ pendente |
| Pausar ou descartar parcial | Ações implementadas; descarte exige confirmação e preserva conversas/outro perfil. Desinstalar o app apaga seus arquivos privados |
| Testes Python dos scripts | **51 aprovados** na execução informada nesta etapa; não comprovam o download no Android |
| Download real no Android | Aprovado: os dois GGUFs foram baixados pelo próprio app, verificados e revalidados em `READY`; compacto gerou `42` após o download, sem instalar modelo por ADB |
| Compilação, JVM, lint e APK local alpha.3 | **BUILD SUCCESSFUL in 2m 5s**, 90 testes JVM aprovados sem falhas/erros/ignorados; lint 0 erros, 37 avisos e 1 informação. APK local e publicado gerados e inspecionados; CI com 90 JVM aprovados e Android comum com 15 aprovados, 0 falhas/erros e 2 opcionais ignorados, executados separadamente |

APK local alpha.3: **123.615.014 bytes**, SHA-256 `11a8a1b2fabd712dd346fe95e6a470cf27a34aa70a87ec29c3587ace2ff9bf09`. Assinatura v2 válida, ZIP 16 KB, quatro ABIs, 40 bibliotecas ELF e modelos OCR conferidos; sem GGUF no APK. A publicação foi inspecionada separadamente, e os resultados Android estão no relatório.

**[Baixar o APK alpha.3](https://github.com/Cazi1n8/nexus-android/releases/download/v2.0.0-alpha.3-build-10/NEXUS-2.0.0-alpha.3.apk)** — **123.631.398 bytes**, SHA-256 `779b42cd94980a6eea489d20db4d51be5f65829a6241c54e793ae9de8d7eecd1`. Assinatura v2, ZIP 16 KB, quatro ABIs, 40 ELFs e modelos OCR conferidos; nenhum GGUF. Fonte `887da8abae76d2730f5ff228ea0e575808de414e`. [Build/publicação](https://github.com/Cazi1n8/nexus-android/actions/runs/40151770859), [Android comum](https://github.com/Cazi1n8/nexus-android/actions/runs/40151770843), [download real](https://github.com/Cazi1n8/nexus-android/actions/runs/40151770826), [inferência com fixture](https://github.com/Cazi1n8/nexus-android/actions/runs/40151770987).

A prova de produção baixou o compacto em 37,789 s e o equilibrado em 82,455 s, ambos com `downloadReturned=true`, `READY`, SHA-256 correto e revalidação aprovada. A inferência do compacto respondeu `42` em 5.305 ms. A instrumentação inteira levou 134,447 s. São tempos desse emulador, sem teste físico no S9+.

Os APKs alpha.2/alpha.3 publicados têm certificados diferentes e não permitem atualização direta entre si. Exporte conversas antes de desinstalar a versão antiga; desinstalar apaga modelos e parciais privados. A retomada não recupera arquivos removidos pela desinstalação. Assinatura de release estável continua pendente.

O teste anterior de IA real instalava o GGUF por ADB. Sua aprovação validou inferência, não o caminho de download relatado pelo usuário. [Relatório alpha.3](docs/RELATORIO_ALPHA_3.md) e [matriz completa dos pedidos](docs/PEDIDOS_E_STATUS.md).

Todas as correções do texto grande ainda não estão concluídas ou comprovadas. Permanecem pendentes Libras completa e conteúdo validado, visão multimodal local geral, resumo persistente de conversas, validação física de voz/gestos/mouse e preparação final para a Play Store.

## Etapa alpha.2 — entrega histórica

A etapa atual acrescenta OCR local no NEXUS Vision com Tesseract4Android 4.9.0 e modelos português/inglês incluídos no APK. A interface permite escolher imagem da galeria, ler, copiar, ouvir e salvar uma memória por autorização explícita. O ditado de uma pergunta somente preenche o campo editável; nenhuma imagem é enviada automaticamente.

Um workflow separado executou um GGUF Qwen 0.5B real dentro de um emulador Android 10/API 29 x86_64. A [execução final 40106216702](https://github.com/Cazi1n8/nexus-android/actions/runs/40106216702), revisão `b7a5c1b52186fd5ad014c74dafec4649d62c7bb5`, passou com um teste instrumentado e quatro respostas geradas. O arquivo foi verificado antes e depois da transferência para o diretório privado do aplicativo. As respostas e tempos são registrados; os pesos não são incluídos no APK ou nos artefatos de evidências.

**Validação final aprovada:** 76 testes JVM, 44 testes Python dos scripts e 44 do servidor opcional, sem falhas ou ignorados. A suíte Android comum passou com **13 aprovados, 0 falhas/erros e 1 ignorado** sem fixture; a prova dedicada de IA passou sem ignorados. Build local em 2m 10s e build GitHub em 4m 2s. Lint: **0 erros**, 37 avisos locais/38 no GitHub e 1 informação; o aviso adicional trata do SDK alvo 35 e de exigências futuras da Play. O APK publicado foi baixado e inspecionado. Evidências: [RELATORIO_ALPHA_2.md](docs/RELATORIO_ALPHA_2.md); os números e links alpha.1 abaixo são históricos.

| Acréscimo alpha.2 | Estado nesta atualização |
| --- | --- |
| OCR offline de imagens privadas, português e inglês | Quatro testes do leitor OCR aprovados na rodada final: texto real, branco/EXIF, limites e leitor encerrado; medição física pendente |
| Importação da galeria sem exigir permissão de câmera | Teste da tela aprovado na rodada final, sem ativar sensores ou enviar imagem; seletor Android e fluxo físico ainda pendentes |
| Copiar, ouvir e salvar OCR como memória | Ações explícitas implementadas; teste integrado pendente |
| Ditado de pergunta no Vision | Implementada e compilada; reconhecimento depende do serviço de fala e de teste físico |
| Preparação dos modelos OCR e licenças | Modelos incluídos e SHA-256 verificado no APK; AAR fixado por tamanho/hash e conferido pelo Gradle, preparação coberta pela suíte Python |
| Inferência real no Android com GGUF | Aprovada em emulador API 29 x86_64; respostas `42`, `Ciano.` e `Olá, coração! 🚀` observadas. Rodada final aprovada; não certifica qualidade geral ou S9+ |
| APK alpha.2 | Publicado, baixado e verificado: 123.631.398 bytes, assinatura v2, ZIP 16 KB, quatro ABIs e 40 ELFs; modelos OCR verificados, sem GGUF |

## APK alpha.2 publicado

**[Baixar NEXUS 2.0.0-alpha.2.apk](https://github.com/Cazi1n8/nexus-android/releases/download/v2.0.0-alpha.2-build-9/NEXUS-2.0.0-alpha.2.apk)**. Release: [v2.0.0-alpha.2-build-9](https://github.com/Cazi1n8/nexus-android/releases/tag/v2.0.0-alpha.2-build-9). **123.631.398 bytes** (~117,9 MiB), SHA-256 `6185c50c7dd75d6c2b160d9933f37e0c23cd3717ae9007a0183faadda663e422`, conferido com o digest do GitHub e `SHA256SUMS.txt`. Assinatura v2, ZIP alinhado a 16 KB e 40 ELFs inspecionados: 22 de 64 bits ≥16 KB e 18 de 32 bits ≥4 KB. Quatro ABIs, modelos OCR íntegros e nenhum GGUF.

Fonte: `b7a5c1b52186fd5ad014c74dafec4649d62c7bb5`. [Build e publicação](https://github.com/Cazi1n8/nexus-android/actions/runs/40106216752), [instrumentação comum, tentativa 2](https://github.com/Cazi1n8/nexus-android/actions/runs/40106216728/attempts/2), [IA real](https://github.com/Cazi1n8/nexus-android/actions/runs/40106216702). A primeira tentativa da suíte comum falhou por falta de heap durante o empacotamento no runner; a repetição completou com sucesso, sem atribuir essa falha ao funcionamento do aplicativo.

## Estado dos sistemas — base alpha.1

Estados utilizados: **Concluído e testado**; **Implementado aguardando teste em dispositivo**; **Parcialmente implementado**; **Ainda não implementado**; **Bloqueado por limitação técnica**.

| Sistema | Estado | O que existe e o que falta |
| --- | --- | --- |
| Auditoria anterior às mudanças | Concluído e testado | Código, dependências e manifesto examinados antes das alterações; evidências em [AUDITORIA_NEXUS.md](AUDITORIA_NEXUS.md). |
| NEXUS AI local sem URL/chave | Implementado aguardando teste em dispositivo | Runtime llama.cpp real, dois modelos de texto, download explícito e arquivos privados. A ponte JNI foi exercitada no computador na alpha.1 e em Android emulado na alpha.2, com quatro respostas reais. Regressão final em emulador aprovada; falta teste físico. |
| Download e integridade dos modelos | Implementado aguardando teste em dispositivo | Tamanho e SHA-256 fixados, arquivo temporário, espaço livre, cancelamento e exclusão. O fluxo Android de download ainda precisa de teste. |
| Perfil automático por aparelho | Implementado aguardando teste em dispositivo | Política considera Android, ABI do processo, RAM, armazenamento e recursos. Ajusta modelo recomendado e limites; não inicia downloads nem envio remoto sozinho. Integração compilada e regras cobertas por testes JVM. |
| Câmera e análise adaptativas | Implementado aguardando teste em dispositivo | Resolução e frequência são reduzidas conforme recursos; análise acompanha o custo dos quadros. Precisão, latência, bateria e temperatura precisam de medição física. |
| APK universal | Implementado aguardando teste em dispositivo | APK 2.0 final compilado e inspecionado com quatro ABIs; assinatura v2, ZIP e 24 bibliotecas ELF com alinhamento de 16 KB verificados. Android 8.0 ou superior. |
| Provedor personalizado opcional | Implementado aguardando teste em dispositivo | HTTPS, modelo, consentimento e token no Keystore separados da IA local. Não há fallback remoto silencioso nem chave privada embutida. |
| Imagens e NEXUS Vision | Parcialmente implementado | CameraX e captura explícita preservadas; análise geral exige provedor multimodal personalizado autorizado. A alpha.2 acrescenta OCR separado com quatro testes do leitor OCR aprovados em emulador; teste da interface aprovado na rodada final, sem validar o seletor Android ou sensores físicos. Os modelos locais de linguagem atuais não analisam imagens ou vídeo. |
| Ações estruturadas da IA | Implementado aguardando teste em dispositivo | Lista de ações permitidas, argumentos validados e confirmação explícita de uso único. Texto livre não executa comandos. |
| Voz em pt-BR | Implementado aguardando teste em dispositivo | Filtro por idioma e país, tom, velocidade, reprodução de teste, interrupção e leitura de respostas novas. Depende das vozes e serviços instalados. |
| Mouse interno persistente | Implementado aguardando teste em dispositivo | Sessão na raiz da navegação, cursor único e despacho real de toque, duplo toque, arrasto e rolagem. Falta confirmar alvos, coordenadas, perda de mão e navegação no aparelho. |
| Mouse global | Implementado aguardando teste em dispositivo | AccessibilityService sem leitura de telas, câmera em foreground, overlay, notificação e parada de emergência. Restrições do Android e dos aplicativos exigem teste físico. |
| Mãos livres | Implementado aguardando teste em dispositivo | Controlador independente, estabilidade, debounce e ações permitidas configuráveis, incluindo abertura de aplicativos. |
| 3D com duas mãos | Implementado aguardando teste em dispositivo | OpenGL ES 2, seleção por pinça sobre o cubo, transformação e liberação. Depende de GLES e rastreamento com duas mãos; não é AR com ancoragem espacial. |
| Exercícios preparatórios de Libras | Implementado aguardando teste em dispositivo | Dois exercícios geométricos limitados, demonstração própria, câmera, estabilidade, progresso, exportação e exclusão. Não equivalem a reconhecer sinais completos. |
| Tutor amplo e validação pedagógica de Libras | Ainda não implementado | Faltam biblioteca validada, avaliação educacional e cobertura de movimento, localização, expressão facial e gramática. |
| Diagnóstico | Implementado aguardando teste em dispositivo | Dados reais do aparelho e testes explícitos de biblioteca JNI, integridade e inicialização do MediaPipe. Disponibilidade não é aprovação de câmera, voz ou inferência. |
| Histórico, memória e exclusão | Implementado aguardando teste em dispositivo | Room/DataStore preservados; exclusão inclui modelos e progresso. Migração, cancelamento, exportação e limpeza precisam de regressão no Android. |
| IA local em processo de 32 bits | Bloqueado por limitação técnica | Esta versão empacota o runtime de linguagem apenas em ARM64/x86_64. O app base e provedor personalizado continuam previstos em ARM32/x86. |
| Rastreamento MediaPipe nas quatro ABIs | Implementado aguardando teste em dispositivo | Atualização para 0.10.35: AAR oficial contém JNI para ARM64, ARM32, x86_64 e x86, com segmentos ELF alinhados a 16 KB. Rastreamento físico ainda não validado. |
| Publicação na Google Play | Ainda não implementado | Assinatura de release, política de acessibilidade, privacidade, bibliotecas nativas e requisitos da loja ainda precisam de revisão. |

## Verificações executadas na alpha.1

| Verificação | Resultado registrado |
| --- | --- |
| Runtime Android em ARM64 e x86_64 | `externalNativeBuildDebug`: **BUILD SUCCESSFUL in 3m 47s**, com kernels otimizados. Este resultado precede a confirmação do build universal integrado. |
| Ponte JNI com modelo real 0.5B no computador | Inferência, contexto, acentos/emoji, erros de entrada, cancelamento e fechamento concorrente executados; marcador `jni_real_model_smoke=passed`. |
| Ponte JNI com modelo real 1.5B no computador | Mesmas verificações executadas; marcador `jni_real_model_smoke=passed`. |
| Avaliação qualitativa pequena | Quatro observações por modelo. O 0.5B apresentou erro de conteúdo e ação inventada; o 1.5B respondeu melhor nessas observações. Isso não certifica qualidade geral ou desempenho no S9+. |
| Servidor opcional Python | **44 testes aprovados em 17,286 s**, sem ignorados. Usa upstreams controlados para transporte, não para avaliar qualidade de IA. |
| Preparação das fontes nativas | **5 testes Python aprovados**, incluindo checksum e extração segura. |
| Primeira validação integrada 2.0 | **BUILD SUCCESSFUL in 2m 5s**; **76 testes JVM aprovados**, zero falhas ou ignorados; lint com **zero erros, 37 avisos e uma informação**; APK e APK de instrumentação gerados. Resultado anterior à atualização MediaPipe 0.10.35. |
| Revisão com MediaPipe 0.10.35 | **BUILD SUCCESSFUL in 2m 42s**, 76 testes JVM aprovados, 0 falhas/erros/ignorados; lint 0 erros, 37 avisos e 1 informação. APK e APK de testes instrumentados gerados. |
| Testes instrumentados no Android | **8 aprovados, 0 falhas/erros/ignorados** no emulador Android 10/API 29 x86_64; [execução 40053992145](https://github.com/Cazi1n8/nexus-android/actions/runs/40053992145), `connectedDebugAndroidTest` em 47s. Sem testes físicos. |
| Testes físicos | **Não executados** pelo ambiente de desenvolvimento. |

Detalhes e limites das provas: [RELATORIO_NEXUS_2.md](docs/RELATORIO_NEXUS_2.md). Os resultados antigos da versão 0.1 em [PROGRESSO.md](PROGRESSO.md) são históricos e não substituem a validação da 2.0.

## APK alpha.1 publicado

**APK alpha.1 confirmado localmente:** 101.515.753 bytes, assinatura v2 válida, quatro ABIs e alinhamento ZIP/ELF de 16 KB. Não contém pesos GGUF. SHA-256: `b1d2cbf860647fe6fc324b4660fae29deea0d42df24e1813304768dfa7976588`.

**[Baixar o APK publicado no GitHub](https://github.com/Cazi1n8/nexus-android/releases/download/v2.0.0-alpha.1-build-5/NEXUS-2.0.0-alpha.1.apk)**. Release: [v2.0.0-alpha.1-build-5](https://github.com/Cazi1n8/nexus-android/releases/tag/v2.0.0-alpha.1-build-5), **91.496.785 bytes**, assinatura v2 válida, checksum conferido contra o digest do GitHub e `SHA256SUMS.txt`. SHA-256: `a51ea58489c42139b80aae259ee95c6bc0d6b3fae3d37f06731c91ec9bdb570b`. Quatro ABIs e alinhamento ZIP/ELF de 16 KB inspecionados também no arquivo remoto.

Revisão do APK: `f95cf02e78b82411ec233d78df51b1344a1a2e69`. [Build e publicação aprovados](https://github.com/Cazi1n8/nexus-android/actions/runs/40053992143). [Oito testes no emulador aprovados](https://github.com/Cazi1n8/nexus-android/actions/runs/40053992145). O APK local e o remoto têm certificados de depuração e hashes diferentes; prefira o arquivo remoto para instalação.

Depois, testar no aparelho: download e inferência offline, voz pt-BR, permissões negadas, botões reais por mouse, navegação persistente, rolagem, emergência do controle global e duas mãos no cubo. Só registrar aprovação dos itens realmente observados.
