# NEXUS 4.1 — auditoria da inteligência e ferramentas matemáticas

Data: 28/10/2026. Repositório existente: `Cazi1n8/nexus-android`.
Dispositivo informado para validação física: **Samsung Galaxy A03**. Nenhuma execução física neste aparelho foi realizada nesta auditoria.

## Problema encontrado e correção

O parser `ArithmeticTool` da fonte anterior (`a334cb2`) **já calculava corretamente** `Quanto é 50 + 50 + 50 × 50?`: `2600`. A fonte examinada não reproduziu a resposta `500` relatada; não é possível atribuir essa saída a um operador específico sem evidência da execução instalada.

Foi encontrado um defeito real de integração: `NexusViewModel.send()` e `retry()` verificavam a disponibilidade do modelo **antes de chegar ao coordenador de ferramentas**. Na instalação CORE sem pesos locais, contas e ações locais eram bloqueadas como se exigissem o download de um LLM. O parser correto não bastava para tornar esse fluxo utilizável.

Agora `AiCoordinator.builtInResponse()` é a mesma entrada usada pelo coordenador e pela validação do ViewModel. Cálculos válidos e propostas de ações locais passam pelo fluxo real de histórico/Room sem carregar pesos, consultar o token no Keystore ou contatar um servidor. As ações continuam sendo apenas propostas: o usuário confirma e o aplicativo verifica o resultado real. Uma mensagem com imagem **não** passa por esse atalho, mesmo se contiver uma expressão numérica. Escrita, conhecimento geral e pedidos visuais continuam exigindo um provedor real; não foi acrescentado fallback fictício.

Também foram identificadas variações aritméticas completas que antes escapavam para o modelo: `Ei, NEXUS, por favor calcule ...`, o sufixo `por favor`, `multiplicado por`, `elevado a/ao`, `ao quadrado` e `ao cubo`. A normalização agora aceita essas formas e somente avalia quando todo o restante é uma expressão aritmética. Um texto contendo números ou código permanece com o provedor de linguagem.

## Matemática e limites

O cálculo usa `BigDecimal` com precisão DECIMAL128; não usa `eval`, JavaScript, execução de código ou resposta estimada por LLM. Mantém precedência de potências, sinais, multiplicação/divisão e soma/subtração; produtos e quocientes de mesma precedência são avaliados da esquerda para a direita. Potências são associativas à direita. Ponto/vírgula representam decimais, sem separador de milhar.

Limites preservados: expressão de até 256 caracteres, até 80 algarismos por número, expoentes inteiros entre -100 e 100, até 100 operações, profundidade limitada e limite de magnitude/casas decimais. Divisão por zero, sintaxe inválida, expoente não suportado e excesso de limites retornam um erro explícito. Explicações são construídas a partir dos passos efetivamente executados.

| Entrada | Saída numérica esperada e coberta |
| --- | --- |
| `50 + 50 + 50 × 50` | `2600` |
| `10+10÷10×10` | `20` |
| `25 vezes 18` | `450` |
| `(50+50+50)*50` | `7500` |
| `2^3^2` | `512` |
| `-2^2` / `(-2)^2` | `-4` / `4` |
| `2^-3` | `0,125` |

## Testes executados nesta subetapa

O código Kotlin real do parser foi compilado diretamente com o compilador Kotlin 2.1.20/JDK 17 e executado pelo JUnit 4.13.2, sem Gradle concorrente.

| Execução | Resultado real | Escopo |
| --- | --- | --- |
| Parser original `a334cb2` contra os seis novos métodos | **4 passaram / 2 falharam** | A expressão exata já retornava `2600`; falhas nas novas formas de fala/polidez |
| Parser corrigido, mesmos seis métodos | **6 passaram / 0 falharam**, JUnit 0,088 s | Operações, precedência, expressões do relato, polidez, fala, limites e rejeição de prosa/código |
| `ArithmeticToolTest` ampliado | Pendente da execução Gradle coordenada | Provedores não são chamados para contas; somente mensagens de usuário sem imagem recebem o atalho |
| `CoreArithmeticToolsTest`, dois casos Android novos | Pendente de execução em emulador/dispositivo | ViewModel + Room sem pesos/endpoint, cálculo `2600`, ação somente proposta e conversa geral bloqueada honestamente |
| Nova avaliação semântica com LLM | **Não executada** | Este trabalho não certifica melhoria geral da inteligência |

Os seis métodos do parser são incluídos na suíte Gradle; não se deve somar a execução isolada à suíte como se fossem casos distintos. Os 0,088 s são duração da suíte no computador, não latência do chat ou benchmark do A03. Evidências locais: `/workspace/tooling/n41-arithmetic-before/result.json` e `/workspace/tooling/n41-arithmetic-pure/result.json`. A compilação e os resultados consolidados ficam em `PROGRESSO_NEXUS_4_1.md`.

## Modelo utilizado e limites existentes

O NEXUS não treinou um modelo fundamental próprio. A identidade e as ferramentas pertencem ao aplicativo; os pesos opcionais são **Qwen2.5 Instruct de terceiros**, executados realmente no CPU por `llama.cpp`/JNI:

| Perfil opcional | Quantização | Tamanho exato | Entrada visual direta |
| --- | --- | --- | --- |
| Qwen2.5 0.5B Instruct | Q4_K_M | 491.400.032 bytes | Não |
| Qwen2.5 1.5B Instruct | Q4_K_M | 1.117.320.736 bytes | Não |

Revisões upstream, tamanhos e SHA-256 estão fixados em `LocalModelCatalog.kt`. Nenhum desses pesos foi tornado obrigatório no CORE. Não houve novo download ou troca de modelo nesta correção.

O runtime mantém cache de prefixo KV e poda turnos considerando o orçamento real de contexto. Conforme RAM/processo/CPU medidos, a política usa contexto de 2.048 ou 4.096 tokens, uma a três threads e 384–768 tokens de saída. A amostragem registrada usa temperatura 0,65, top-p 0,9, top-k 40, penalidade de repetição 1,08 para 128 tokens e seed 42. Os limites não garantem compreensão ou correção. A avaliação Android anterior **falhou em escrita, informalidade e alegação indevida de visão**, conforme [TESTES_NEXUS_AI.md](../TESTES_NEXUS_AI.md). Esses resultados permanecem válidos como limitações conhecidas.

Os campos `assistantName` (NEXUS), `userPreferredName`, `assistantPersonality`, `assistantInstructions` e `userPreferences` já estão separados; a migração preserva o antigo nome do usuário. O prompt identifica esses papéis explicitamente. Isso corrige a organização dos dados, mas não transforma um modelo pequeno em um assistente infalível. Nenhum teste de nomes foi marcado novamente como aprovado sem execução.

Imagens no modo local passam por ferramentas opcionais de OCR e classificação; seus resultados textuais são enviados ao LLM. **O Qwen local não recebe pixels.** O provedor remoto pode enviar as imagens reais em data URI quando o usuário autoriza e escolhe um modelo realmente multimodal. A classificação de categorias não deve ser anunciada como reconhecimento confiável de modelo, preço ou compreensão geral da cena.

## Assistente online integrado: infraestrutura ainda necessária

O projeto já contém cliente HTTPS e `server/nexus_gateway.py`, uma ponte opcional com autenticação por token, expiração, limites de concorrência e uso, recusa de redirecionamento e proteção de credenciais do provedor. O servidor pode usar Ollama ou uma API compatível. A prova anterior em [RELATORIO_IA_REAL.md](RELATORIO_IA_REAL.md) foi temporária e **não é um backend público disponível no celular**.

A ponte atual usa um token compartilhado para ambiente controlado. Antes de um serviço integrado para vários usuários, faltam implantação HTTPS mantida, contas/sessões individuais, emissão e revogação de tokens, cotas por usuário, política de retenção e orçamento do provedor. Ela também exige `stream=false`, não repassa ferramentas arbitrárias e não é uma plataforma pública de hospedagem. O aplicativo deve continuar explicando quando essas dependências faltarem.

O cliente remoto atual não implementa streaming e o runtime local retorna a resposta concluída. Não há medição de primeiro token; o diagnóstico local registra duração total e contagens de tokens. Streaming, autenticação pública e nova avaliação de um modelo remoto competente são trabalho posterior, sem promessa de IA online gratuita e ilimitada.

## Verificação no Android

1. Com uma instalação CORE e sem baixar pesos, abra o chat e envie `Quanto é 50 + 50 + 50 × 50?`. Deve aparecer `50 + 50 + 50 × 50 = 2600`, salva no histórico.
2. Envie `10+10÷10×10`, `Quanto é 25 vezes 18?` e `Quanto é (2+3) elevado a 2?`; espere `20`, `450` e `25`.
3. Envie `NEXUS, abra as configurações`. Deve surgir uma proposta com confirmação; nenhuma permissão é concedida nem ação executada automaticamente.
4. Sem pesos nem backend, peça uma história. O NEXUS deve informar a dependência real e preservar o rascunho. Isso não deve produzir texto simulado.
5. Anexe uma imagem com um pedido de conta. O atalho matemático deve ser recusado; a análise depende do processamento visual real.

O teste instrumental novo é opt-in em emulador CORE descartável com `nexusCoreSmoke=true`; não solicita sensores, não acessa serviços externos e restaura suas configurações, excluindo somente as conversas que criou. As verificações no Galaxy A03 continuam pendentes até a execução no aparelho.
