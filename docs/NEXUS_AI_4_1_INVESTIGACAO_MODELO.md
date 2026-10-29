# NEXUS AI — investigação real de qualidade em 29/10/2026

**A IA geral continua sem aprovação.** Houve correções técnicas e respostas melhores em alguns casos, mas os candidatos ainda falham em pedidos importantes. Nenhum modelo novo foi promovido ao catálogo ou incluído no CORE. Não existe serviço de IA de produção implantado por esta execução.

## O que estava errado

O provedor local usa pesos reais Qwen2.5, via llama.cpp/JNI. Não foi encontrado um chatbot de respostas sorteadas. Calculadora e propostas de ações são ferramentas determinísticas reais, separadas da geração de linguagem. O nome do assistente e o nome preferido do usuário já têm campos distintos; ainda assim, um modelo pode interpretar mal instruções corretamente separadas.

As falhas anteriores de escrita e de alegação de acesso à câmera ocorreram sem descarte de mensagens nessas solicitações. Portanto, aumentar arbitrariamente o histórico não corrige esses casos. Uma conversa logicamente nova não significa, por si só, que uma nova instância nativa foi carregada: os testes abaixo usam o mecanismo real de cache do aplicativo. O controle adicional com o servidor oficial reproduziu falhas fora do wrapper NEXUS, mas não constitui uma análise exaustiva de todas as formas de cache.

O prompt misturava instruções em inglês e português, exigindo que modelos pequenos diferenciassem autor, destinatário e assistente em instruções extensas. A comparação com um prompt inteiramente em português melhorou respostas de identidade em alguns casos, mas **não resolveu escrita e interpretação informal**. Não se atribui a reprovação somente ao idioma do prompt.

## Avaliações executadas

Foram **53 gerações reais de texto**, em CPU Linux, com perguntas sintéticas e respostas efetivamente produzidas pelos modelos. Os lotes têm estado `completed`. Não foi reexecutada e aprovada a bateria completa de 15 casos no Android nesta etapa. A revisão textual foi feita pelo Codex sobre os resultados brutos; não é certificação independente nem teste no Galaxy A03.

| Modelo e controle | Gerações | Pesos | Pico RSS do processo nativo | Resultado observado |
| --- | ---: | ---: | ---: | --- |
| Qwen2.5 1.5B Q4_K_M, prompt atual versus português | 10 | 1.117.320.736 B | 1.824.456 KiB | Confunde escrita, destinatário e pedido com erros de digitação. |
| Qwen3.5 2B Q4_K_M, prefixo legado | 5 | 1.280.835.840 B | Não medido neste lote | Expõe raciocínio interno e consome o limite sem entregar a resposta; também afirma acesso visual indevido. |
| Qwen3.5 2B, prefixo oficial sem pensamento | 10 | 1.280.835.840 B | 1.902.432 KiB | A correção elimina o raciocínio nesses resultados, mas persistem erros semânticos. |
| Qwen3.5 2B, servidor/Jinja oficial | 6 | Os mesmos pesos | Não usado como medida comparável | Reproduz pedidos de descrição/descomplicação no lugar de desculpas e escrita inadequada. |
| Qwen3 4B Instruct 2507, prompt português inicial | 10 | 2.497.281.120 B | 4.510.164 KiB | Acerta identidade, câmera e cálculo; pede detalhes sem escrever e inventa contexto em um pedido explícito. |
| Qwen3 4B, regra geral de entrega do texto antes de perguntas | 12 | Os mesmos pesos | 4.510.172 KiB | Entrega uma desculpa geral, mas interpreta outro pedido como descrição, inventa motivo e trunca uma resposta de código. |

Os lotes têm perguntas e orçamentos diferentes. Não se deve usar seus tempos totais ou proporções como comparação controlada de velocidade ou uma nota geral de inteligência.

Na última rodada, o modelo respondeu corretamente `50 + 50 + 50 × 50 = 2600`, manteve uma transformação de texto em dois turnos e reconheceu que não pode prever a loteria. Entretanto:

- `chat me ajda faze um texo de descupa` virou um pedido de descrição de produto/perfil;
- em um pedido para escrever sem inventar motivo, inventou mensagens não respondidas e desorganização, e inseriu assinatura;
- uma explicação correta sobre `list.append()` chegou ao limite real de 512 tokens com o texto incompleto.

Isso impede declarar que a IA geral foi corrigida. Não foi criado um texto pronto de desculpas para substituir a geração defeituosa, nem uma regra que reescreve esse erro de digitação.

As perguntas, prompts exatos, parâmetros, textos gerados, hashes dos modelos/fontes e a rubrica estão em [n42-ai-quality-review.json](evidencias/n42-ai-quality-review.json) e nos artefatos referenciados por ele. O ensaio C++ usa o mesmo motor nativo do aplicativo, mas o prompt do ensaio é uma fixture explícita: não equivale a executar toda a interface Android ou um backend de produção.

## Correção técnica encontrada durante o ensaio de candidatos

`llama_chat_apply_template` é um reconhecedor legado, não um interpretador Jinja. Para Qwen3.5, gerava apenas o prefixo ChatML e não aplicava a ramificação oficial `enable_thinking=false`. Foi acrescentado o prefixo de geração oficial **na entrada do modelo**, com verificação de arquitetura `qwen35`, template e sufixo esperado. Outros modelos preservam seu formato anterior.

Referência: [chat_template.jinja de Qwen3.5-2B, revisão imutável](https://huggingface.co/Qwen/Qwen3.5-2B/blob/15852e8c16360a2fea060d615a32b45270f8a8fc/chat_template.jinja).

Não há filtro que apague pensamentos ou frases da resposta para aparentar uma boa geração. A correção é relevante ao candidato Qwen3.5 testado; **não é a explicação para a reprovação original do Qwen2.5**. Esse candidato continua fora do catálogo distribuído.

## Medidas reais de latência e memória

O motor passou a informar `firstTokenMs` e `totalMs`, medidos com relógio monotônico. Esses valores incluem prefill, espera pelo motor e páginas de pesos carregadas sob demanda, mas excluem a chamada de carga do modelo. `LocalAiProvider` também registra o tempo até o primeiro token incluindo preparação e carga; os diagnósticos não guardam prompt, resposta, nome, imagem ou chave.

No último lote 4B, o primeiro token frio levou **29.801 ms**, a mediana das demais solicitações foi **1.352 ms**, e a duração das respostas variou entre **1.685 e 125.476 ms**. Havia outras tarefas de desenvolvimento, inclusive compilação, no host. Essas são medidas brutas deste ambiente, **não desempenho do A03 nem evidência de aceleração**. A visualização incremental remota é uma funcionalidade diferente de geração local incremental; o motor local não ganhou streaming nesta mudança.

O pico RSS de aproximadamente **4,30 GiB só no processo nativo** torna o candidato 4B inadequado como recomendação automática para um aparelho de entrada. No Android ainda haveria custo de interface, câmera e outros recursos. Compatibilidade deve usar RAM realmente disponível, ABI do processo e medições; não o nome comercial do aparelho.

## Arquitetura e dependência pendente

O CORE continua sem pesos generativos obrigatórios. Modelos locais opcionais e provedores externos continuam separados. Um serviço operado pelo desenvolvedor pode oferecer um modelo mais competente sem exigir URL ou chave do usuário comum, mas precisa ser implantado, protegido, autenticado e ter limites de uso. Não há endpoint de produção, credenciais protegidas do operador ou infraestrutura de conta de usuário provisionados nesta sessão.

Os servidores de modelos deste ensaio são processos de desenvolvimento em loopback. Não são um serviço online disponível aos usuários. O teste multimodal e o cliente/gateway devem ser avaliados separadamente; sucesso de transporte de uma imagem não torna um modelo aprovado para reconhecer objetos em geral.

## Verificações específicas desta investigação

- Quatro testes do avaliador e do limite do prefixo C++ executados: passaram. Eles verificam formato e fronteiras; não qualidade de linguagem.
- Duas regressões Kotlin foram acrescentadas para latências válidas, legado sem medidas e metadados inconsistentes. O resultado Gradle consolidado deve ser consultado no relatório da compilação desta entrega.
- Os resultados brutos das gerações foram preservados, inclusive as falhas. Nenhum peso novo foi copiado para assets ou acrescentado ao catálogo.
- Continuam pendentes a aprovação da IA geral no Android com o provedor final, desempenho no Galaxy A03 e implantação de um serviço integrado para os aparelhos que não comportam um modelo competente local.
