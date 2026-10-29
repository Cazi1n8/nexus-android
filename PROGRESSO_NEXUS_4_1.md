# NEXUS 4.1 — evolução do projeto existente

Dispositivo de validação informado pelo responsável: **Samsung Galaxy A03**. Início: 28/10/2026; continuidade em 29/10/2026. A revisão mantém `br.com.nexus.assistant`, os dados existentes e o CORE sem modelos generativos obrigatórios. Fonte compilada para avaliação: **4.1.0-alpha.2-core / 28**; não liberada como versão de produção.

Este documento distingue relato físico do usuário, inspeção de código, testes executados no ambiente e testes ainda pendentes. O ambiente não possui o Galaxy A03 conectado nem uma instância do Play Protect com a classificação do arquivo bloqueado. Os resultados da alpha.1 continuam preservados e não contam como aprovação automática da alpha.2.

## Continuidade da alpha.2 — estado de implementação

| Área | Causa ou alteração identificada nesta etapa | Evidência e limite atual |
| --- | --- | --- |
| IA geral | Falhas críticas da avaliação real anterior ocorreram em pedidos novos, sem poda do histórico nem truncamento. Corrigido o prefixo não-pensante do candidato Qwen3.5; comparados Qwen2.5 1,5B, Qwen3.5 2B e Qwen3 4B | **53 gerações reais concluídas no host; candidatos reprovados em qualidade**, sem promoção ao catálogo. IA geral continua sem aprovação |
| Chat remoto | Streaming SSE real, cancelamento do socket, métricas de primeiro texto/total; contexto preserva turnos completos e recusa solicitações grandes em vez de cortá-las | Protocolo testado com upstream controlado e modelo real; fragmentos reais coincidiram com a resposta final. Streaming local não foi simulado |
| Vision e pesquisa | Sessão CameraX por capturas, perguntas sobre a última foto e modo periódico opcional sem sobreposição; imagens reais no protocolo multimodal; fontes de pesquisa separadas, SearXNG do operador; orçamento de imagem reduzido em dispositivos com menos memória | Caminho Kotlin→gateway→modelo multimodal executado com pixels reais; identificação grosseira correta, mas ingredientes inventados: **qualidade visual reprovada**. Não há serviço público/pesquisa implantados nem teste físico de câmera |
| Voz | Serviço esperava um modelo ausente no CORE antes de ouvir; seleção pt-BR, prazo de inicialização, foco de áudio e bloqueio de callbacks após PARAR revisados | Correção de código e testes puros registrados; STT/TTS e continuidade física no A03 pendentes |
| Mouse global | Quadros/gestos pendentes podiam reativar ou continuar após PARAR/PAUSAR; parada imediata, descarte de callbacks e verificação periódica de permissões/notificações | Teste Android negativo adicionado; não foi comprovada operação global no A03. Algoritmos de clique/rolagem preservados |
| NEXUS 3D | Cena GLES com seis formas, até 12 objetos, pinça, transformações com duas mãos e salvamento privado; controlador separado do mouse | Testes puros integrados à suíte JVM; teste Android EGL incluído, ainda não executado nesta revisão; mãos reais pendentes |
| Libras | Três aulas introdutórias, progresso separado de leitura e prática, feedback geométrico por dedo, repetição sem parar câmera | 23 testes puros executados; L/V/Y permanecem hipóteses experimentais, sem certificação linguística |
| CORE e segurança | Sem novos pesos obrigatórios, dependências grandes ou permissões; publicação pública permanece bloqueada | Compilação final e auditorias do APK passaram; **108.635.674 bytes**, quatro ABIs e modelo de mãos incluído. Play Protect permanece sem resolução comprovada |

Relatórios por área: [transporte/Vision](docs/VISION_TRANSPORTE_4_1_CONTINUIDADE.md), [sessão visual](docs/VISION_CONVERSA_4_1.md), [voz](docs/VOZ_NEXUS_4_1_REVISAO.md), [mouse/segurança](docs/NEXUS_GLOBAL_SEGURANCA_4_1_CONTINUIDADE.md), [3D](docs/NEXUS_3D_CENAS_4_1.md) e [Libras](docs/LIBRAS_NEXUS_4_1_REVISAO.md). As contagens isoladas não devem ser somadas a futuras suítes integradas que executem os mesmos métodos.

## Verificações executadas na continuidade alpha.2

| Verificação | Resultado e escopo |
| --- | --- |
| Compilação final da alpha.2 | **BUILD SUCCESSFUL**, **7m09s**, 207 tarefas, 61 executadas e 146 atualizadas; APKs debug/coreTest/release e instrumentação coreTest gerados após a última correção de disponibilidade visual |
| Testes JVM integrados | **298 aprovados**, zero falhas, erros ou ignorados; incluem identidade/capacidades, transporte, ferramentas, voz, mouse, cenas e Libras. Não são testes físicos |
| Scripts Python | **173 aprovados**; verificam preparação, auditoria, coletores e avaliadores, não precisão do modelo |
| Servidor opcional | **56 aprovados**, incluindo entrega SSE por socket e negociação de capacidades/ferramentas; testes controlados não contam como inferência real |
| Lint debug/coreTest | Zero erros, **57 avisos e 1 informação** por variante; relatório release com zero erros, 47 avisos e 1 informação |
| Investigação real de texto | **53 gerações**, lotes concluídos e saídas preservadas; os modelos continuam reprovados em escrita/interpretação. Não houve nova aprovação dos 15 casos no Android |
| Modelo multimodal real | Cliente Kotlin de produção→gateway→llama-server com Qwen3.5 2B e projetor verificados; 3 respostas iniciais, incluindo captura e pergunta contextual, sem falha técnica. Reconhece o hambúrguer mas inventa ingredientes: qualidade reprovada |
| Controle posterior sem imagem | Uma geração real com disponibilidade visual explicitada continua ambígua sobre “ver” o chat; não é aprovação do limite de visão |
| Novos testes Android | Recusa segura do mouse sem câmera e renderização EGL preparados no runner CORE; ainda **não executados** para esta fonte |
| Instalação e hardware | Play Protect, mãos/câmera, voz, mouse global e desempenho no Galaxy A03 **não testados fisicamente** |

Fontes e rubricas: [compilação/testes/tamanhos](docs/evidencias/n42-build-verification.json), [investigação de modelos](docs/NEXUS_AI_4_1_INVESTIGACAO_MODELO.md), [53 gerações/revisão](docs/evidencias/n42-ai-quality-review.json), [avaliação visual real](docs/VISION_AVALIACAO_REAL_4_1.md), [respostas multimodais](docs/evidencias/n42-vision-remote-model.json), [revisão visual](docs/evidencias/n42-vision-model-review.json) e [controle sem imagem](docs/evidencias/n42-vision-availability-model.json). O teste visual preserva a foto anterior no contexto, mas também propaga seus detalhes inventados; manter contexto não significa que a observação esteja correta. Os servidores temporários foram encerrados após a coleta.

**APK final local de avaliação:** `app/build/outputs/apk/coreTest/app-coreTest.apk`, **108.635.674 bytes (108,64 MB)**. SHA-256 `556872b2ecc467237017ec5e7c43f177996f372cf8a6599559bdd30f388e773f`; certificado de teste `ffc6524eee21a185872e946e78649a977f4b82011ee573c20adf5ca6ba376f79`. Aumentou **114.688 bytes (+0,106%)** sobre a alpha.1, preservando a otimização CORE. O pacote mantém quatro ABIs e o modelo de mãos verificado de 7.819.105 bytes, sem GGUF obrigatório. [Conteúdo medido](docs/evidencias/n42-core-package-audit.json) e [manifesto/assinatura](docs/evidencias/n42-security-package-audit.json) passaram nas verificações declaradas. Isso não é aprovação do Play Protect nem medição do espaço instalado no A03.

O release de **108.623.386 bytes** foi compilado **sem assinatura**, porque a chave estável do responsável não está configurada; não é instalável. Nenhum APK novo foi publicado como release público. O pacote de avaliação tem a finalidade delimitada de testar as correções técnicas e coletar as validações ainda ausentes; não deve ser apresentado como IA aprovada ou instalação já liberada pelo Google. A assinatura local não corresponde à do APK público CORE22, portanto não garante atualização direta daquela instalação.

## Critério de distribuição

**Bloqueio pelo Play Protect permanece sem resolução comprovada.** Não há autorização do Google ou teste de instalação física aprovado a apresentar. O APK desta etapa é um candidato para avaliação, não uma versão certificada ou liberada para uso. A publicação automática de APKs de depuração permanece suspensa. A configuração de assinatura de produção já existe, mas depende da chave estável do responsável em Secrets; nenhuma nova identidade de produção será inventada.

O APK público CORE anterior tem 108.210.081 bytes, SHA-256 `e76ff4e228206a11317000a238556d8c24693532c1527b05f8264f5ba6b50d15`, certificado de teste `0e72073647f9fd5b4bc27f6f8fbafa9100f4fadc391433ff6844fc2f96ec97fc`. Uma nova compilação local não herda sua chave privada. Não desinstale o aplicativo com dados importantes antes de exportá-los e verificar a compatibilidade de assinatura.

## Plano verificável

| Fase | Escopo e evidência exigida | Estado nesta implementação |
| --- | --- | --- |
| 1 — Segurança | Manifesto final, capacidades, consentimento, dependências, assinatura; instalação com proteção ativa e eventual revisão oficial | Manifesto/APK auditados, dependências corrigidas, testes de limites automatizados; aprovação física pendente |
| 2 — Mouse | Cadeia CameraX→MediaPipe→gestos→cursor, sessões global/interna, pausa/parada, medição de latência e regressão clique/rolagem | Pipeline e controles corrigidos, testes JVM passaram; regressão Android da alpha.1 aprovada, instrumentação nova pendente; teste global físico A03 pendente |
| 3 — IA | Ferramenta matemática offline, identidade, contexto, qualidade e latência com modelo real | Ferramentas aprovadas na alpha.1; 53 gerações comparativas reais concluídas, modelos ainda reprovados; sem backend público implantado |
| 4 — Vision | Imagem chegando a provedor multimodal, contexto e fontes de pesquisa verificáveis | Transporte real verificado; candidato visual reprovado em detalhes. Modelo competente, pesquisa real e infraestrutura permanecem pendentes |
| 5 — 3D | Duas mãos medidas, seleção/transformações e múltiplos objetos/cenas | Expansão de cenas implementada com testes puros; EGL integrado e mãos físicas ainda precisam de execução |
| 6 — Libras e Voz | Exercícios com referências validadas, STT/TTS reais, sessão autorizada fora do app | Aulas/feedback e correções de voz implementados; validação pedagógica e física pendentes |
| 7 — Extensões e desempenho | Modelos opcionais, remoção segura, tamanho final e perfis por recursos medidos | CORE e centrais preservados; quatro perfis implementados com testes |
| 8 — Entrega | Compilação, relatórios e pacote identificado por hash/assinatura | Alpha.2 compilada com 108,64 MB, 298 JVM aprovados e auditorias do APK. CI/instrumentação nova e validação física pendentes; sem liberação de produção |

## Três falhas confirmadas no aparelho

### Play Protect bloqueia a instalação

Evidência disponível: relato e captura de tela enviados pelo responsável, com aviso de possível acesso a dados sensíveis. Isso não permite determinar o veredito interno do Google, nem excluir um problema real. Consulte [AUDITORIA_PLAY_PROTECT.md](AUDITORIA_PLAY_PROTECT.md) para permissões, achados confirmados, hipóteses, fontes oficiais e limites.

O mouse global precisa de capacidade sensível para executar gestos. Isso será mantido com escopo mínimo, sem leitura de janelas, notificações de outros aplicativos, senhas ou dados financeiros. Não serão usadas técnicas de evasão, alteração de identidade para escapar da verificação ou desativação da proteção.

### Cursor lento e travando

A implementação anterior já tinha executor único e `KEEP_ONLY_LATEST`; não era uma fila ilimitada de inferência. Foram identificados custos por quadro e suavização fixa que merecem correção. O trabalho introduz entrega com um único resultado pendente, orçamento de idade dos resultados, perfis de resolução/frequência e suavização adaptativa. Métricas sintéticas de filtro não representam a latência completa de uma câmera física.

### Mouse não funciona fora do NEXUS

Há dois modos separados. O interno é vinculado à janela do NEXUS e para em `ON_STOP`; isso é correto para o seu escopo. O global usa serviço foreground independente e sobreposição de acessibilidade. A tela apresentava primeiro o modo interno e escondia o global abaixo de controles e instruções. A revisão destaca o global e seus requisitos, sem afirmar que a seleção errada explique sozinha o problema do A03.

Serão inspecionados o ciclo de vida real da câmera, estados de erro, coordenadas entre cursor e toque, consentimento e controles de pausa/encerramento. A existência de um serviço no manifesto não comprova funcionamento em outro aplicativo.

## Como validar no Galaxy A03 quando houver instalação legítima aceita

1. Mantenha o Play Protect ativo. Registre versão, SHA-256, certificado e resultado da instalação. Se houver bloqueio, preserve a mensagem e siga a revisão oficial; não contorne a verificação.
2. Abra Chat e Mouse sem ativar recursos: câmera, microfone e serviço global devem continuar desligados.
3. Em Mouse, escolha o perfil Automático ou Econômico, leia a divulgação, autorize a sessão e habilite o serviço NEXUS na acessibilidade se o Android permitir. Inicie **mouse global**, não apenas mouse interno.
4. Verifique notificação, indicador de câmera, botão de parada e estados do serviço. Vá à Home do Android e abra um aplicativo comum. Teste movimento, clique curto, duas pinças, pinça mantida e rolagem vertical/horizontal. Não use telas bancárias, de senhas ou transações para este teste.
5. Registre pelo menos 30 segundos por perfil: tempo de análise, idade de entrega, fluidez, perda de mão e comportamento ao voltar ao app. Iluminação e posição da câmera devem permanecer semelhantes. Não registre imagens pessoais nos logs.
6. Pause: cursor e toques devem parar e a câmera deve ser liberada. Retome pelo NEXUS visível. Encerre pela notificação e confirme liberação completa, sem reinício automático.
7. Teste câmera ocupada, rotação, tela bloqueada, permissões revogadas e acessibilidade desconectada. Deve haver cancelamento de toque mantido e erro compreensível, sem ações ocultas.
8. Sem baixar modelo de conversa, pergunte `Quanto é 50 + 50 + 50 × 50?`: a ferramenta deve calcular **2600**. Perguntas gerais continuam exigindo um modelo real ou serviço configurado; não devem receber respostas simuladas.

## Resultados executados da alpha.1 — histórico

Fonte publicada no repositório existente: [`2602470`](https://github.com/Cazi1n8/nexus-android/commit/260247033611ab2eba76a33aa282f9e74a75a91e). A árvore de fonte local foi comparada à do GitHub e é idêntica. Os resultados históricos da alpha.3 não foram contados como testes da 4.1.

| Verificação executada nesta revisão | Resultado |
| --- | --- |
| Gradle local: testes, Lint, debug, coreTest, release e instrumentação coreTest | **BUILD SUCCESSFUL**, 6m44s, 207 tarefas |
| Testes JVM | **235 aprovados**, zero falhas/erros/ignorados, 33 suítes |
| Scripts Python | **164 aprovados**, zero falhas (162 antes da correção do coletor + 2 regressões novas) |
| Lint debug e coreTest | Zero erros; cada variante com **55 avisos e 1 informação**, incluindo recomendações de atualização/estilo, atributos aplicáveis a Android mais recente e avisos existentes |
| APK CORE universal | **108.520.986 bytes**, quatro ABIs, modelo de mãos preservado, sem pesos generativos/OCR/classificador obrigatórios |
| Comparação com CORE anterior | +310.905 bytes (**+0,287%**), principalmente código/dependências da revisão; não foi declarada uma nova redução |
| Comparação com APK antigo 1.738.201.604 bytes | Mantém redução de **93,7567%** no APK; não é medição de instalação no A03 |
| Manifesto e recursos compilados do APK coreTest | **9 permissões**, limites esperados de componentes/acessibilidade/HTTPS, zero violações da política auditada |
| Assinatura e alinhamento do coreTest | Assinatura válida com chave local de teste existente; `zipalign -c -P 16 4` aprovado |
| Porta de produção `--production` | **Recusou o certificado Android Debug, como esperado**; não confundir com o Play Protect |
| Guava e Protobuf resolvidos pelo Gradle | **33.7.2-android** e **4.28.3**; versões conferidas no classpath efetivo, além da consulta OSV |
| APK release | Compilado, **sem assinatura e não instalável**; não é distribuição de produção |

APK coreTest local: `app/build/outputs/apk/coreTest/app-coreTest.apk`. SHA-256: `0b6000e66bf42166b359457474a54fcb6bcdcf9a92949f7e0f360eaaffdb7df7`. Certificado local de teste: `ffc6524eee21a185872e946e78649a977f4b82011ee573c20adf5ca6ba376f79`. Ele difere do APK público CORE22; não há promessa de atualização direta. O pacote foi gerado para avaliação, não declarado pronto para distribuição.

O filtro foi medido com o **Kotlin de produção**, em entradas sintéticas determinísticas. A 20 amostras/s, o tempo para atingir 90% de um degrau caiu de **200 para 100 ms**; a 10 amostras/s permaneceu **200 ms**. O erro RMS estacionário caiu de 0,00114368 para 0,00069702 (coordenadas normalizadas). Sob 10.000 resultados enquanto a fila principal estava ocupada, o novo entregador agendou **1 callback**, contra 10.000 remarcações anteriores. Isso não mede toda a cadeia câmera→MediaPipe→display, não representa FPS da câmera e não garante o mesmo ganho no A03.

Evidências: [compilação e contagens](docs/evidencias/nexus41-build-local.json), [conteúdo do APK](docs/evidencias/nexus41-core-apk.json), [manifesto/assinatura](docs/evidencias/nexus41-core-security.json), [consultas de dependências](docs/evidencias/nexus41-dependency-advisories.json), [benchmark do filtro](docs/evidencias/nexus41-mouse-filter-benchmark.json), [auditoria do mouse](docs/NEXUS_4_1_MOUSE_GLOBAL.md) e [auditoria de IA](docs/NEXUS_4_1_IA_AUDITORIA.md).

Execuções Android iniciadas para esta fonte: [regressão em emulador](https://github.com/Cazi1n8/nexus-android/actions/runs/40783551204), [CORE sem rede e extensões](https://github.com/Cazi1n8/nexus-android/actions/runs/40783551252) e [compilação CI](https://github.com/Cazi1n8/nexus-android/actions/runs/40783551224). A regressão Android **19 passou: 35 aprovados de 43 cenários, 8 ignorados, zero falhas/erros**, em 27,684 s de JUnit. [Evidência detalhada](docs/evidencias/nexus41-android-device19.json). Inclui quatro testes reais do novo preparo de imagem (RGBA com padding/rotação, reutilização após fechar MPImage, quatro inferências MediaPipe sequenciais e benchmark), além de duas verificações de permissões/serviços/tela de mouse. A variante é debug com dados pequenos de visão incluídos, não o mesmo binário CORE universal.

O CORE **6 executou seus 10 testes Android com sucesso**, inclusive calculadora/ações locais sem modelo e câmera/microfone negados. Porém, o coletor Python ainda esperava o campo antigo da tela de autorização e interrompeu a validação antes dos downloads de extensões: a **pipeline falhou**, embora o JUnit tenha passado. A falha e a restauração de rede foram preservadas na [evidência CORE6](docs/evidencias/nexus41-core6-collector-failure.json). O coletor foi corrigido em [`d76a3ad`](https://github.com/Cazi1n8/nexus-android/commit/d76a3ad7c8396d16ecc93b125b6b96a8503ebfb4), com testes que usam o relatório real e rejeitam ausência dos campos novos. Não alterou o aplicativo nem relaxou suas asserções. A nova execução [CORE7](https://github.com/Cazi1n8/nexus-android/actions/runs/40784772782) **passou**: 10 métodos CORE em 10,298 s + 1 método de extensões em 1,541 s (o wrapper completo durou 31,173 s). Confirmou transferência HTTPS real de **11.530.361 bytes**, OCR/classificação nativos de fixtures, retomada por prefixo verificado, reutilização de arquivos, proteção contra remoção em uso e exclusão preservando dados sintéticos e o modelo de mãos. Sem testes ignorados no estágio CORE. [Evidência CORE7](docs/evidencias/nexus41-core7.json). A [CI25](https://github.com/Cazi1n8/nexus-android/actions/runs/40784772759) substitui a CI24 cancelada pela concorrência da nova revisão.

A [CI25](https://github.com/Cazi1n8/nexus-android/actions/runs/40784772759) **passou**, com 235 testes JVM, 164 testes de scripts e 44 testes do servidor opcional (zero ignorados nestas suítes da CI). O Gradle terminou em 9m19s. O artefato `nexus-core-25` foi baixado integralmente e conferido por tamanho/SHA; sua assinatura, manifesto compilado, conteúdo universal e alinhamento foram verificados novamente fora da CI. [Evidência CI25](docs/evidencias/nexus41-ci25-build.json).

APK da CI: **108.520.986 bytes**, SHA-256 `56b0d30b6799753ef39c623b1063034b8a12b85f0c6b6c8e5dc7f9a9a45e369b`, certificado de teste `0af8cb8d9d487b164da9509b0910a5ff96bd7d9337f71e9ff3c93873576af734`. Ele é diferente da compilação local e do CORE22. [Artefato de avaliação da CI](https://github.com/Cazi1n8/nexus-android/actions/runs/40784772759/artifacts/12858804039) (ZIP, acesso pelo GitHub; não é link de instalação aprovado). O job de publicação pública foi **ignorado pelo bloqueio explícito de segurança**. Não foi criado outro release público.

A [regressão Android 20](https://github.com/Cazi1n8/nexus-android/actions/runs/40784772829) repetiu **35 aprovados, 8 ignorados, zero falhas/erros**, na fonte do coletor corrigido e com o aplicativo inalterado. [Evidência detalhada](docs/evidencias/nexus41-android-device20.json). Não somar casos repetidos entre as execuções ou contar o teste-wrapper Python como outro método Android.

No benchmark Android de preparo de imagens (320×240, 60 amostras + 10 de aquecimento), as alocações de bitmap passaram de **140 para 2**. A mediana no emulador debug foi de 0,337190 para **0,344880 ms** (pequeno aumento); no CORE foi de 0,676960 para **0,395240 ms**. A variação entre execuções impede afirmar um ganho de latência no A03. O resultado comprovado é o reaproveitamento de alocações e o processamento nativo correto; os tempos não representam a cadeia completa nem usuários físicos.

A [avaliação real de IA 6](https://github.com/Cazi1n8/nexus-android/actions/runs/40783551169) concluiu **15 casos, 35 gerações reais e duas ferramentas**, com 37 respostas persistidas e zero falhas técnicas, mas **reprovou o gate de qualidade nos casos 3, 9 e 12** (redação de desculpas, limite de visão e interpretação informal). Não é uma falha corrigida por atualizar dependências ou pelo parser matemático. [Saídas reais](docs/evidencias/nexus41-ai-evaluation6.json) e [resumo](docs/evidencias/nexus41-ai-evaluation6-summary.json). Não foi implantado um modelo de maior capacidade nem um backend público; a inteligência geral continua insuficiente para os objetivos solicitados.

Na alpha.1, os cenários de câmera, mãos reais, serviço global sobre outros apps, STT/TTS no A03 e instalação com Play Protect ficaram **pendentes de hardware físico**. O backend público, a análise multimodal geral, a pesquisa online, as cenas com múltiplos objetos e a ampliação pedagógica não foram implementados por aquela revisão. A continuidade da alpha.2 acima separa as novas alterações de código daquilo que ainda exige implantação e validação.
