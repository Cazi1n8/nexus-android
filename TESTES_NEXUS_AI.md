# NEXUS AI 3 — testes e avaliações

Atualizado em 26/10/2026. Teste de código, geração real, interpretação correta e uso no celular são verificações diferentes. **A inteligência do NEXUS ainda não está corrigida nem aprovada.**

## Resultado real da avaliação Android

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

A lentidão também é real: no caso 11, a mesma resposta de nove tokens levou de **47,369 s com 313 tokens de entrada** a **147,117 s com 1.010 tokens de entrada**. O runtime atual limpa o contexto KV a cada geração e reprocessa todo o histórico. Essa implementação precisa de otimização e testes de reutilização segura de prefixo; aumentar o tempo máximo da bateria não corrigiria a causa nem a qualidade das respostas. Esses tempos são do emulador, não uma medição do S9+.

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

[Evidência e hashes das fontes](docs/evidencias/ai3-evaluation-checks-regressions.json). A compilação do novo APK de testes e a reexecução real dos quinze casos após a correção do avaliador ainda não foram concluídas neste registro. Nenhum resultado anterior foi recalculado ou marcado como aprovado retroativamente.

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

As verificações automáticas de palavras aceitaram a matemática e a explicação infantil, apesar desses erros. Isso comprova sua limitação e a necessidade de ler as respostas. **Os quinze casos não foram concluídos nesse baseline.** A interpretação de português informal e a qualidade de escrita continuam sem correção comprovada; o caso 12 da avaliação Android não chegou a produzir resposta antes do timeout, portanto sua aprovação continua sem prova. Essa falha deve permanecer visível, mesmo se o aplicativo compilar e as ferramentas de cálculo funcionarem.

Foram testados separadamente protótipos Qwen3 1,7B e Qwen3.5 0,8B, incluindo templates oficiais sem raciocínio e prompts menores. As comparações reais, suas origens e revisões semânticas estão no [relatório consolidado](docs/evidencias/ai3-local-model-quality-investigation.json). Ambos foram rejeitados porque continuaram errando a intenção ou deixando de produzir os textos pedidos. **Esses protótipos não foram incluídos no APK.** A versão atual preserva os modelos Qwen2.5 0,5B e 1,5B com limitações explícitas, sem apresentar a troca de modelo como solução demonstrada.

As primeiras comparações reais detectaram falhas tanto no modelo compacto quanto no de 1,5B, incluindo troca de identidade, recusa indevida e interpretação errada de pedidos de escrita. Por isso, a simples troca do modelo não é apresentada como correção comprovada. Não foi usado filtro para apagar frases repetidas e esconder essas falhas.

Voz brasileira, reconhecimento de mãos, Libras, operação fora do aplicativo, câmera em uso por outro app e Play Protect exigem provas adicionais no dispositivo. A avaliação de texto no emulador não aprova esses recursos. Consulte também `TESTES_SEGUNDO_PLANO.md` e `AUDITORIA_PLAY_PROTECT.md`.
