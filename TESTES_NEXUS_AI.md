# NEXUS AI 3 — testes e avaliações

## Revisão 4.1 — ferramenta matemática no CORE, 28/10/2026

O [CORE7](https://github.com/Cazi1n8/nexus-android/actions/runs/40784772782) executou no Android não depurável, sem pesos generativos, servidor e rede no estágio CORE:

- `Quanto é 50 + 50 + 50 × 50?` produziu **2600**, gravando no Room mensagens `user`/`assistant`, sem iniciar download; nomes NEXUS/Calebe permaneceram separados.
- `NEXUS, abra as configurações` produziu somente uma proposta estruturada, sem afirmar execução. Conversa geral sem modelo continuou recusada de forma explícita.

O parser já calculava corretamente a expressão na fonte anterior; não foi demonstrado que ele tenha originado o relato de resposta 500. A falha confirmada corrigida foi o bloqueio das ferramentas pelo requisito de instalar um modelo. Seis métodos novos de regressão matemática também passaram no JVM: operações elementares, parênteses, precedência, potências, formas faladas delimitadas e limites de segurança. Eles são parte dos 235 testes JVM, não resultados adicionais de LLM.

[Evidência Android CORE7](docs/evidencias/nexus41-core7.json), [regressão do parser](docs/evidencias/nexus41-arithmetic-host.json) e [auditoria de IA](docs/NEXUS_4_1_IA_AUDITORIA.md). Isso comprova ferramenta e persistência, não compreensão geral de português, raciocínio do modelo ou inteligência equivalente a um serviço de maior capacidade.

Os modelos Qwen2.5 0,5B/1,5B continuam opcionais e limitados. Não foi implantado backend público de IA, multimodalidade geral ou pesquisa. A nova avaliação semântica [execução 6](https://github.com/Cazi1n8/nexus-android/actions/runs/40783551169) **falhou no gate de qualidade**, novamente nos casos **3 (texto de desculpas), 9 (alegação falsa de acesso visual) e 12 (pedido com erros de digitação)**. Executou 15 casos, 35 gerações reais + calculadora + proposta de ação, com 37 respostas reabertas no Room e zero falhas técnicas; tempo total reportado 677.238 ms. Nove casos atenderam às heurísticas, o que não é aprovação humana. [Respostas reais](docs/evidencias/nexus41-ai-evaluation6.json) e [resumo/identificadores](docs/evidencias/nexus41-ai-evaluation6-summary.json) preservados. O modelo ainda não atende à experiência solicitada; não foi substituído nem promovido a IA competente por esta correção. Histórico abaixo preservado, com referências a aparelhos antigos pertencentes àquelas execuções, não ao **Galaxy A03** atual.

Atualizado em 27/10/2026. Teste de código, geração real, interpretação correta e uso no celular são verificações diferentes. **A inteligência do NEXUS ainda não está corrigida nem aprovada.**

## Histórico: alpha.2 concluiu a bateria, mas falhou em qualidade

A [execução 40389844392](https://github.com/Cazi1n8/nexus-android/actions/runs/40389844392), fonte `62ec1e3a714cdcc577e04f659185808bd6c9ab3f`, completou **15 casos e 37 respostas: 35 gerações reais, uma calculadora e uma proposta de ação**, em **904.636 ms (15 min 05 s)**. Usou o Qwen2.5 1,5B Q4_K_M incluído, contexto de 2.048 tokens, uma thread, Android 10 emulado e rede desligada antes/depois. Houve zero falhas técnicas e o Room foi reaberto com as 37 respostas verificadas.

O workflow terminou com **falha no gate de qualidade**, nos casos críticos **3, 9 e 12**. Nove dos quinze casos atenderam às heurísticas, o que não significa nove aprovações humanas: elas ainda aceitam respostas incompletas ou inadequadas. As respostas originais foram preservadas.

| Caso | Revisão das respostas reais da alpha.2 |
| --- | --- |
| 1 — nome do usuário | Identifica Calebe numa saudação, embora não responda diretamente à pergunta |
| 2 — nome do assistente | Identifica NEXUS corretamente |
| 3 — desculpas | **Falha:** dirige o texto a Calebe, inventa situação em conversas anteriores e assina “Att., NEXUS” |
| 4 — 25 vezes 18 | Calculadora real retorna `25 × 18 = 450` |
| 5 — explicação infantil | **Falha:** apresenta IA como “parte do cérebro humano” e termina cortada |
| 6 — história de Max | **Falha:** preserva personagens, mas os textos terminam cortados e não entregam um final completo |
| 7 — formal/informal | Parcial: permanece em português nesta execução, mas mantém Calebe como destinatário, assinatura NEXUS e expressão inválida “respostas asmaosas” |
| 8 — configurações | Proposta estruturada e envio do Intent oficial confirmados; não testa o diálogo de consentimento real nem garante a tela externa visível |
| 9 — câmera | **Falha:** afirma falsamente ver e interpretar imagens em tempo real |
| 10 — redação | **Falha:** erros históricos e corte em “A conclusão” |
| 11 — vinte turnos | Recupera corretamente **ORION e MARFIM** após os vinte turnos reais |
| 12 — erros de digitação | **Falha:** responde “Calebe, eu não posso ajudar com isso.” ao pedido de desculpas |
| 13 — troca para Ana | Usa Ana e mantém a identidade NEXUS |
| 14 — incerteza | Reconhece não ter acesso aos pensamentos do usuário |
| 15 — três parágrafos | Parcial: produz cinco parágrafos, com síntese repetida e referência a conversa anterior inexistente, apesar de passar a heurística |

O caso 11 terminou em **239.735 ms**: primeiro turno 9,523 s, penúltimo 13,265 s, último 17,774 s. Nos dezesseis turnos comparáveis ao teste anterior, o total observado caiu de 1.585,112 s para 182,530 s. São execuções em emuladores separados com a mesma configuração, não um benchmark controlado do S9+. Das 26 respostas de modelo comparáveis, 24 ficaram iguais e duas diferiram; a amostragem com seed fixo não garante identidade universal entre formas de processamento. A prova separada de vinte comparações no host continua identificada pelo seu escopo.

Evidências: [JSON completo](docs/evidencias/ai3-alpha2-evaluation.json), [resumo semântico](docs/evidencias/ai3-alpha2-semantic-summary.json), [instrumentação](docs/evidencias/ai3-alpha2-evaluation-instrumentation.txt), [JUnit técnico](docs/evidencias/ai3-alpha2-evaluation-junit.xml), [rede](docs/evidencias/ai3-alpha2-evaluation-network-restoration.json), [comparação de tempos](docs/evidencias/ai3-alpha2-android-comparison.json) e [resumo final](docs/evidencias/ai3-alpha2-final-evaluation-summary.json). O ZIP pequeno de evidências teve tamanho e SHA verificados. Nenhum teste físico ou aprovação do Play Protect foi obtido.

## Histórico: alpha.1 interrompida por timeout

A [execução 40303339463](https://github.com/Cazi1n8/nexus-android/actions/runs/40303339463), no commit `4228a54f09fcba3a55e0231f182d24db4707cb66`, **falhou pelo limite de 40 minutos do teste JUnit**. Ela usou o Qwen2.5 1,5B Q4_K_M incluído no APK, Android 10/API 29 emulado, uma thread e contexto de 2.048 tokens, sem internet. Não houve teste físico no Galaxy S9+.

Os casos 1–10 concluíram geração/ferramenta. O caso 11 registrou 16 respostas antes da interrupção; o caso 12 foi apenas iniciado, sem resposta; os casos 13–15 não foram executados. O JSON registra **28 respostas: 26 do modelo, uma da calculadora e uma proposta de ação**. A reabertura final do Room não foi alcançada. **Não foram executadas nem aprovadas as 35 gerações/37 respostas planejadas ou a bateria completa dos quinze casos.**

| Caso | Execução real | Revisão humana da resposta |
| --- | --- | --- |
| 1 — nome do usuário | Concluída | Parcial: chama Calebe corretamente, sem resposta direta sobre seu nome; não troca a identidade nessa saída |
| 2 — nome do assistente | Concluída | Atendeu: diz explicitamente “Meu nome é NEXUS” |
| 3 — texto de desculpas | Concluída | **Falhou:** pede desculpas a Calebe por “nossas conversas recentes” e assina “Att., NEXUS”; não escreve como remetente humano |
| 4 — 25 vezes 18 | Concluída, calculadora | Atendeu: `25 × 18 = 450`, em 3 ms; isso não mede o raciocínio do LLM |
| 5 — IA para dez anos | Concluída com corte | **Falhou:** descreve IA como parte do cérebro humano, usa fantasia confusa e termina sem completar o texto |
| 6 — história e final feliz | Dois turnos, ambos cortados | Parcial: preserva Max/David, mas não entrega história e final completos |
| 7 — formal para informal | Dois turnos | **Falhou:** preserva o assunto, porém muda para inglês e a primeira mensagem é assinada NEXUS |
| 8 — configurações | Concluída, ferramenta | Atendeu à proposta estruturada e ao envio do Intent oficial; não testa o diálogo de consentimento nem garante a tela externa visível |
| 9 — acesso visual | Concluída | **Falhou:** afirma ver imagens em tempo real sem sessão de câmera; é uma alegação falsa do modelo, não uma captura realizada |
| 10 — redação | Concluída com corte | **Falhou:** inclui atribuições históricas incorretas e para em “A conclusão” |
| 11 — vinte mensagens | Interrompida após 16 respostas | Não avaliável quanto à lembrança de ORION/MARFIM: a pergunta final não chegou a ser executada |
| 12 — erros de digitação | Iniciado sem resposta | Não executado até gerar resposta nesta prova |
| 13 — Calebe para Ana | Não executado | Sem resultado Android |
| 14 — incerteza | Não executado | Sem resultado Android |
| 15 — vários parágrafos | Não executado | Sem resultado Android |

A heurística marcou o caso 3 como aprovado apesar da assinatura indevida “NEXUS”: **falso positivo confirmado**. No caso 7, ela não reconheceu o assunto da reunião em inglês: falso negativo de tópico; o problema real continua sendo a mudança de idioma e de remetente. A revisão humana lê o conteúdo completo e não transforma esses resultados em uma nota global de inteligência.

A lentidão também é real: no caso 11, a mesma resposta de nove tokens levou de **47,369 s com 313 tokens de entrada** a **147,117 s com 1.010 tokens de entrada**. O runtime daquela versão limpava o contexto KV a cada geração e reprocessava todo o histórico. A alpha.2 passou a reaproveitar o prefixo; o resultado novo está no início deste documento. Aumentar o tempo máximo da bateria não corrigiria a causa nem a qualidade das respostas. Esses tempos são do emulador, não uma medição do S9+.

## Evidências preservadas

- [JSON original da execução parcial](docs/evidencias/ai3-android-evaluation-timeout.json), sem substituir respostas erradas por exemplos.
- [Instrumentação original](docs/evidencias/ai3-android-evaluation-instrumentation.txt), com `TestTimedOutException` após 2.400.000 ms.
- [Resumo técnico](docs/evidencias/ai3-android-evaluation-ci-summary.json), com contagens e origem das respostas.
- [Restauração da rede](docs/evidencias/ai3-android-evaluation-network-restoration.json): comandos concluídos sem erros registrados.

O artefato `nexus-ai3-evaluation-1`, ID `12643319841`, contém esses dados e o JUnit de falha. O executor não chegou a gerar um resumo semântico ou Markdown automático, pois a validação técnica falhou. Este documento registra a revisão humana posterior. Todas as perguntas/respostas são sintéticas; não são conversas pessoais do usuário.

A fonte principal do aplicativo desta avaliação tem árvore `8dde24ce23b4b7b6d545bdd350fb4acc4b774390`, preservada no commit `e98027c2aa0b07fdaaf366db6d94a42fd09c3f2b`; o ajuste posterior do teste de primeiro chat e os relatórios não mudaram essa fonte.

## Outras verificações Android e CI

| Verificação | Resultado real | Limite |
| --- | --- | --- |
| [Build 40305428821](https://github.com/Cazi1n8/nexus-android/actions/runs/40305428821) | 189 JVM aprovados; lint 0 erros, 48 avisos, 1 informação; APK debug gerado | Não aprova a inteligência nem o Play Protect |
| [Android comum 40305428792](https://github.com/Cazi1n8/nexus-android/actions/runs/40305428792) | 23 casos: 19 aprovados, 4 opcionais ignorados, 0 falhas/erros | Inclui classificação real da foto de hambúrguer, quadro vazio, fábrica MediaPipe no-op, histórico e UI sem sensores |
| [Primeiro chat corrigido 40305428815](https://github.com/Cazi1n8/nexus-android/actions/runs/40305428815) | 1 aprovado; pesos do APK preparados em 4,726 s; “Azul.” em 36,250 s; cálculo 20; OCR “OLA NEXUS 2026”; três respostas persistidas | Modelo compacto e fluxo real da interface, sem câmera/microfone físicos ou certificação de qualidade geral |
| [Inferência nativa 40303339754](https://github.com/Cazi1n8/nexus-android/actions/runs/40303339754) | 1 aprovado; respostas reais 42, Ciano e UTF-8 | Prova do JNI/modelo compacto, não dos quinze pedidos |
| [Download real 40303339485](https://github.com/Cazi1n8/nexus-android/actions/runs/40303339485) | Ambos os modelos chegaram a READY, com tamanho/SHA corretos e nova verificação; compacto gerou 42 | Downloader antigo de desenvolvimento; não mede o desempenho no celular |

O primeiro teste de chat incluído falhou no contrato antigo de perfil manual. O teste foi corrigido para exigir que BALANCED manual seja respeitado e que, após habilitar o ajuste automático, seja aplicada a recomendação real COMPACT do emulador de 4 GB. A execução corrigida verificou também a estabilidade após recriar a Activity; não foi alterada a política do aparelho para forçar sucesso. [Observações originais](docs/evidencias/ai3-bundled-fixed-observations.json) e [resumo das execuções de CI](docs/evidencias/ai3-ci-verification-summary.json).

## Repetir a bateria

O workflow `.github/workflows/ai3-evaluation.yml` continua disponível. A bateria foi projetada para 15 casos, 20 turnos relacionados, 35 gerações e duas ferramentas, mas a execução acima não concluiu esse plano. O teste usa banco/diretório próprios, preserva configurações e o executor recusa celulares físicos antes de instalar APKs ou mudar a rede.

```bash
python3 scripts/fetch_bundled_model.py --with-balanced
./gradlew --no-daemon --max-workers=2 assembleDebug assembleDebugAndroidTest
python3 scripts/run_android_ai3_evaluation.py --allow-reset-emulator --require-critical-checks
```

O teste de ferramentas autoriza somente a ação sintética de abrir configurações. Usuários reais continuam confirmando propostas na interface. Voz, câmera, gestos globais, Libras e instalação com Play Protect precisam de provas físicas separadas, registradas em `TESTES_SEGUNDO_PLANO.md` e `AUDITORIA_PLAY_PROTECT.md`.

## Correção do avaliador após a falha real

As respostas originais acima permanecem inalteradas. A versão seguinte do teste usa `schemaVersion: 2` e campos separados `executionCompleted`, `automaticSemanticChecksPassed` e `humanReviewRequired`, tanto por caso quanto no agregado. A conclusão de geração e persistência não declara aprovação semântica. Enquanto uma execução não terminar, seu resultado semântico agregado fica indefinido, sem inventar uma aprovação.

O falso positivo de desculpas foi corrigido no código exclusivo de testes: uma assinatura “Att., NEXUS”, variantes de assinatura do assistente e a autodefesa observada são rejeitadas. O teste de câmera exige uma negação referente ao acesso visual e rejeita afirmações contraditórias; “Não se preocupe” isolado não comprova ausência de câmera. Essas verificações continuam sendo heurísticas limitadas, com possíveis erros, e não alteram ou filtram respostas no aplicativo.

Com `--require-critical-checks`, a CI falha quando um dos casos **1, 2, 3, 9, 12 ou 13** não atende às verificações automáticas. O caso 9 de alegação falsa de câmera foi incluído nesse gate. O executor preserva o JSON e o Markdown antes de falhar. Casos não críticos com verificações falsas permanecem explicitamente registrados mesmo quando o gate crítico passa; a revisão humana segue obrigatória.

Verificações executadas nesta correção:

- **9 testes Kotlin/JUnit aprovados**, compilando no Linux o helper puro e suas regressões reais presentes no source set `androidTest`; não houve execução Android nem inferência nesses testes.
- `python3 -m unittest discover -s scripts/tests -p 'test_run_android_ai3_evaluation.py' -v`: **12 aprovados**. Fixtures e ADB simulado comprovam consistência dos metadados, preservação das respostas, restauração da rede, recusa de dispositivos físicos e falha do gate quando o caso 9 falha.
- A suíte completa de scripts registrou **115 testes aprovados**; são testes das ferramentas de desenvolvimento, não da inteligência.

[Evidência e hashes das fontes](docs/evidencias/ai3-evaluation-checks-regressions.json). O novo APK de testes foi compilado e as nove regressões passaram também no Android, dentro da suíte de 28 aprovados e quatro opt-in ignorados. A reexecução real dos quinze casos terminou e falhou no gate semântico, conforme o resultado atual acima. Nenhum resultado anterior foi recalculado ou marcado como aprovado retroativamente.

## Alpha.2: desempenho e testes técnicos novos

A fonte `62ec1e3a714cdcc577e04f659185808bd6c9ab3f` inclui cache do prefixo KV, preservando o modelo e o prompt. A [regressão Android](https://github.com/Cazi1n8/nexus-android/actions/runs/40389844498), o [primeiro chat offline](https://github.com/Cazi1n8/nexus-android/actions/runs/40389844275) e a [inferência nativa](https://github.com/Cazi1n8/nexus-android/actions/runs/40389844358) foram aprovados.

O teste nativo executou seis inferências: entrada inicial, repetição com cache, recuperação após cancelar, registro de contexto, pergunta contextual e Unicode. A pergunta aritmética gerou `42` em 7.100 ms inicialmente e em 423 ms com 51 de 52 tokens reaproveitados. Após cancelar, o contador de cache voltou a zero. A comparação desse teste Android usa o texto com espaços das extremidades removidos; a prova de igualdade **byte a byte** do texto completo está nas vinte comparações separadas no host, com ambos os modelos. Esses números não são um benchmark do S9+.

[Observações nativas](docs/evidencias/ai3-alpha2-native-observations.json), [primeiro chat](docs/evidencias/ai3-alpha2-bundled-observations.json), [regressão comum](docs/evidencias/ai3-alpha2-common-observations.json) e [relatório da alpha.2](docs/RELATORIO_IA3_ALPHA_2.md). O primeiro chat confirmou preparação a partir do APK, resposta real, cálculo 20, OCR e persistência sem rede; não testou sensores físicos nem aprovação pelo Play Protect.

Foram investigados LFM2.5, Gemma3 e um prompt curto em português, em **32 gerações reais**, com escopos e diferenças de amostragem registrados. Nenhum foi adotado: continuaram apresentando falhas críticas de identidade e interpretação. [Comparativo](docs/evidencias/ai3-alternative-models-quality.json) e [cinco testes com o sampler C++ de produção](docs/evidencias/ai3-qwen25-pt-short-native.json). Isso não demonstra que todo modelo dessa família falha, mas impede apresentar esses experimentos como uma correção validada do NEXUS.

Os sete workflows YAML foram carregados por PyYAML e tiveram a estrutura de jobs conferida. Isso é uma verificação sintática local, não uma execução do GitHub Actions.

## Comparação no computador e limites restantes

Há uma avaliação separada em `scripts/run_native_quality_eval.py`, que compila a construção de contexto Kotlin do aplicativo e usa o mesmo runtime C++/JNI com pesos GGUF reais em Linux. Seus resultados precisam ser identificados pela revisão do prompt e do código; resultados de revisões anteriores não validam alterações posteriores.

Em 26/10, a comparação do Qwen2.5 1,5B foi **interrompida após sete saídas reais e falhas semânticas confirmadas**. Evidência integral: [`docs/evidencias/ai3-qwen25-15b-baseline-interrompido.json`](docs/evidencias/ai3-qwen25-15b-baseline-interrompido.json). Foi usado contexto de 2.048 tokens, saída máxima de 512 tokens e duas threads em CPU Linux. Os pesos são a revisão SHA-256 `6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e`. O baseline isolado não gravou um hash do prompt; o [relatório consolidado da investigação](docs/evidencias/ai3-local-model-quality-investigation.json) identifica os hashes do prompt e de sua fonte. Essas provas não validam alterações posteriores.

| Saída observada | Resultado da revisão humana |
| --- | --- |
| Nome do usuário | Menciona Calebe corretamente, com saudação desnecessária |
| Nome do assistente | Menciona NEXUS, mas acrescenta uma frase ambígua sobre o nome escolhido |
| Pedido de desculpas | Produz uma mensagem copiável, mas inventa circunstâncias não informadas |
| Pedido com erros de digitação | **Falha:** interpreta como texto de descrição |
| Multiplicação com explicação, sem calculadora | **Falha:** cita 450 e depois fornece passos matemáticos errados |
| Explicação infantil de IA | **Falha:** inclui afirmações falsas sobre prever o futuro e dispensar energia |
| História de Max | Texto com começo e final, mas inconsistente; pedido posterior de continuação não foi executado |

As verificações automáticas de palavras aceitaram a matemática e a explicação infantil, apesar desses erros. Isso comprova sua limitação e a necessidade de ler as respostas. **Os quinze casos não foram concluídos nesse baseline.** A interpretação de português informal e a qualidade de escrita continuam sem correção comprovada; o caso 12 da avaliação Android antiga não chegou a produzir resposta antes do timeout. A alpha.2 executou esse caso e também falhou na interpretação, como registrado acima. Essa falha deve permanecer visível, mesmo se o aplicativo compilar e as ferramentas de cálculo funcionarem.

Foram testados separadamente protótipos Qwen3 1,7B e Qwen3.5 0,8B, incluindo templates oficiais sem raciocínio e prompts menores. As comparações reais, suas origens e revisões semânticas estão no [relatório consolidado](docs/evidencias/ai3-local-model-quality-investigation.json). Ambos foram rejeitados porque continuaram errando a intenção ou deixando de produzir os textos pedidos. **Esses protótipos não foram incluídos no APK.** A versão atual preserva os modelos Qwen2.5 0,5B e 1,5B com limitações explícitas, sem apresentar a troca de modelo como solução demonstrada.

As primeiras comparações reais detectaram falhas tanto no modelo compacto quanto no de 1,5B, incluindo troca de identidade, recusa indevida e interpretação errada de pedidos de escrita. Por isso, a simples troca do modelo não é apresentada como correção comprovada. Não foi usado filtro para apagar frases repetidas e esconder essas falhas.

Voz brasileira, reconhecimento de mãos, Libras, operação fora do aplicativo, câmera em uso por outro app e Play Protect exigem provas adicionais no dispositivo. A avaliação de texto no emulador não aprova esses recursos. Consulte também `TESTES_SEGUNDO_PLANO.md` e `AUDITORIA_PLAY_PROTECT.md`.
