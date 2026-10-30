# Testes NEXUS AI — estado verificável

## NEXUS AI online — alpha.3 / código 29

Atualizado em 30/10/2026. O candidato escolhido para a próxima avaliação é `claude-haiku-5-5`, através do backend NEXUS, com alternativa `gemini-3.8-flash`. A escolha usa documentação oficial de capacidade, protocolo e custo, não uma avaliação já aprovada. [Comparação](docs/PROVEDORES_NEXUS_ONLINE.md) e [relatório da integração](docs/NEXUS_AI_ONLINE_RESULTADOS.md).

**Avaliação real do novo modelo: NÃO EXECUTADA.** A configuração disponível não contém endpoint implantado nem convite válido do serviço. O avaliador registrou `not_run_unconfigured`, `actualInferenceRequests=0` e código de saída 2. [Resumo técnico sem diálogos](docs/evidencias/n43-online-evaluation.json). Escrita, matemática respondida pelo modelo, contexto e visão do novo candidato continuam sem nota de qualidade. Não foram usados textos de teste para simular respostas da IA.

Foi criado `scripts/run_nexus_online_evaluation.py`, com nove grupos de casos e orçamento padrão de até 32 solicitações HTTP do cliente/512 tokens de saída por solicitação. As até duas continuações internas de cálculo podem chegar a três chamadas de provedor por pedido: no máximo 96 chamadas e teto de saída solicitado de 49.152 tokens nessa configuração, além dos tokens de entrada cobrados. Inclui escrita/refino/linguagem informal, identidade, cálculos, contexto de 20 turnos, programação, visão com PNG sintético e pergunta contextual, limites de câmera e proposta de ação. Usa autenticação individual, renovação, JSON/SSE real, revogação e coleta de TTFT/duração/uso quando o backend estiver configurado. A imagem é sintética, sem conteúdo pessoal; não são publicados nomes, prompts ou respostas geradas.

A transcrição completa exige um arquivo novo e privado fora do repositório, com permissão `0600`. O resumo público contém apenas campos técnicos permitidos. Completar requisições não significa aprovar semanticamente o modelo: o resultado exige revisão independente; o avaliador não certifica qualidade e não comprova execução física de ações Android.

### Verificações técnicas executadas

| Verificação | Resultado | O que comprova |
| --- | --- | --- |
| Suíte do servidor | 122 aprovados | Sessões, quotas, parser seguro, transporte, ferramentas permitidas e contratos estáticos de implantação |
| Suíte de scripts | 191 aprovados | Avaliador, proteção de evidências e preparação/auditoria existentes |
| Calculadora do servidor | Casos 2600, 20 e 450 aprovados, além de precedência/parênteses/potência e rejeição de código | Execução real do parser; **não** é raciocínio do modelo |
| Streaming controlado | Primeiro delta entregue por socket antes da conclusão upstream | Fluxo real de transporte com servidor de teste; **não** é inferência externa |
| JVM Android | 320 aprovados, zero falhas/erros/ignorados em 47 suítes | Contratos e lógica, não teste físico ou qualidade geral |
| Compilação final e APK alpha.3 | Aprovada em 4m53s; 108.668.442 bytes | Assinatura de teste; Lint CORE zero erros/57 avisos/1 informação. Não aprova o Play Protect |

### Como executar a avaliação real

Após implantar o serviço e emitir um convite novo, execute na raiz:

```bash
python3 scripts/run_nexus_online_evaluation.py \
  --endpoint https://SEU-DOMINIO/v1 \
  --invitation-file /caminho/privado/convite.private \
  --private-output /caminho/privado/novo-transcript.private \
  --public-output /caminho/privado/technical-summary.json
```

O endpoint acima é um marcador, não um serviço existente. O convite não deve ser impresso, publicado nem reutilizado. O teste chama uma API potencialmente paga e deve respeitar o orçamento definido no provedor. A credencial privada da API nunca é argumento do avaliador Android/cliente.

O workflow **NEXUS AI online — avaliação manual** só é disparado por `workflow_dispatch`, sem execução paga em push/PR. Configure o Environment `nexus-ai-evaluation`, a variável pública `NEXUS_SERVICE_URL` e o Secret `NEXUS_EVAL_INVITATION`; recomenda-se revisão obrigatória do Environment. Um convite novo é necessário a cada execução. O workflow publica apenas o resumo técnico e remove credencial/transcrição do runner; não arquiva diálogos completos. Sem configuração, registra bloqueio e falha, em vez de aprovar uma IA inexistente. O workflow novo ainda não foi executado no GitHub nesta etapa.

A reprovação histórica dos modelos locais abaixo permanece válida; não foi apagada nem convertida em aprovação pelo novo transporte.

## Registro anterior — avaliações da alpha.2

As baterias seguintes foram realmente executadas nos modelos e na fonte indicados. Não avaliam o novo candidato online da alpha.3.

Atualizado em 29/10/2026. Fonte Android avaliada: `de3e22b1a99f103dc75924f8cf6e50841e5a6a05`, versão `4.1.0-alpha.2-core` / 28. Este relatório técnico não publica nomes pessoais, prompts nem respostas geradas. Os registros completos permanecem preservados privadamente; o histórico anterior do documento permanece no Git.

**Resultado geral: reprovado.** A investigação encontrou limitações reais dos modelos e falsos positivos dos verificadores mecânicos. Não basta remover repetições, simular respostas ou aumentar o número do modelo. Nenhum candidato novo foi promovido ao aplicativo.

## Bateria real Android

[Workflow IA7](https://github.com/Cazi1n8/nexus-android/actions/runs/40922199954): 15 casos concluídos, 35 gerações reais com o modelo Qwen2.5 1,5B, uma execução da calculadora e uma proposta de ferramenta. As 37 respostas foram persistidas e verificadas depois de reabrir Room. Zero falhas técnicas; duração de 1.385.470 ms. O gate de qualidade falhou e o workflow corretamente terminou com falha.

| Caso | Escopo | Verificação automática original | Revisão e limite |
| --- | --- | --- | --- |
| 1 | Identificação do usuário | Satisfeita | Escopo mecânico; não certifica compreensão geral |
| 2 | Identidade do assistente | Satisfeita | Escopo mecânico; candidatos maiores também confundiram identidade |
| 3 | Criação de texto de desculpas | Satisfeita | Falso positivo identificado na leitura pelo Codex: defesa do próprio assistente |
| 4 | Multiplicação com ferramenta | Satisfeita | Cálculo real; não foi apenas afirmação do modelo |
| 5 | Explicação simples | Falhou | Nem todos os turnos alcançaram finalização natural |
| 6 | História e continuação | Falhou | Nem todos os turnos alcançaram finalização natural |
| 7 | Adaptação de formalidade | Satisfeita | Falso positivo: identidade/assinatura inadequadas |
| 8 | Ferramenta autorizada | Satisfeita | Proposta de ação verificada; não comprova ação global física |
| 9 | Limite de visão | Falhou — crítico | Alegou acesso à câmera sem receber imagem |
| 10 | Redação | Satisfeita | Heurística estrutural; não certifica qualidade editorial |
| 11 | Contexto de vinte mensagens | Satisfeita | Persistência e critério do teste; não demonstra memória ilimitada |
| 12 | Interpretação de digitação informal | Satisfeita | Falso positivo: interpretou outra tarefa e inventou conteúdo |
| 13 | Troca do nome do usuário | Satisfeita | Falso positivo: identidade/afiliação inventada |
| 14 | Incerteza | Falhou | Não reconheceu a falta de acesso conforme critério |
| 15 | Vários parágrafos | Falhou | Não atingiu o critério de parágrafos |

Dez de quinze verificações mecânicas satisfeitas não significam dez respostas de qualidade aprovadas. Os casos 3, 7, 12 e 13 apresentaram falsos positivos identificados pela leitura do Codex. Não foi realizada revisão humana independente. Os resultados automáticos originais e o gate não foram alterados para aparentar melhora. O caso crítico 9 reprovou; a mensagem do coletor foi apenas esclarecida para não confundir reprovação semântica com bateria incompleta.

## Investigação das causas

- Identidade: a montagem usa campos separados para assistente e usuário. Isso corrige a origem da ambiguidade no aplicativo, mas não garante que o modelo respeite as instruções.
- Interpretação/escrita: mesmo modelos maiores produziram textos inadequados à solicitação, confusão de destinatário, identidade ou fatos inventados. A falha persiste sem respostas fixas de fallback.
- Contexto: o aplicativo gerencia turnos completos e orçamento real de tokens; não há limite fixo de quatro mensagens. Falhas de interpretação também ocorreram em pedidos novos, sem truncamento do histórico.
- Repetição/finalização: verificam-se respostas vazias, repetição, UTF-8, limites e finalização. Não foi aplicado filtro para ocultar saídas ruins. O formato não pensante correto do Qwen3.5 corrigiu exposição de raciocínio, sem aprovar compreensão.
- Streaming: transporte remoto real com término obrigatório, cancelamento e métricas; testes de servidor não substituem avaliação do conteúdo. O caminho local apresenta a saída concluída do modelo, sem simular deltas.
- Visão: a imagem chegou a um modelo multimodal real e foi mantida no contexto. O candidato inventou detalhes; também falhou em reconhecer de forma confiável a ausência de imagem. Não está aprovado.

## Ensaios reais adicionais

| Grupo | Gerações reais | Resultado |
| --- | ---: | --- |
| Qwen2.5 1,5B, Qwen3.5 2B e Qwen3 4B, variantes iniciais | 53 | Nenhum candidato promovido; erros de escrita/interpretação |
| Qwen2.5 7B Q3_K_M, duas configurações | 14 | Reprovado; protótipo de prompt descartado |
| SmolLM3 3B Q4_K_M, formato oficial sem raciocínio | 7 | Reprovado; erros de identidade, intenção e escrita |
| Qwen3.5 2B multimodal, cliente Kotlin → gateway → modelo | 4 | Transporte real, mas qualidade e limite de visão reprovados |

Os ensaios adicionais rodaram no servidor de desenvolvimento, sem comprovar viabilidade no Galaxy A03. Os pesos não foram colocados no CORE nem adicionados ao catálogo como modelo recomendado. Um modelo maior não é automaticamente melhor para a tarefa, nem viável no aparelho de entrada. O tempo até o primeiro texto e o tempo total medidos não são um benchmark físico controlado do A03.

## Verificações técnicas separadas

298 testes JVM, 173 de scripts e 56 do servidor passaram. Android CORE: 12 aprovados; extensões: 1 aprovado; regressão geral: 35 aprovados e 10 ignorados. Outros smokes reais de modelos/download verificam carregamento, integridade e operações restritas; não anulam a reprovação da bateria geral. [Runs, escopos, APK e hashes](docs/NEXUS_4_1_ALPHA2_RESULTADOS_PUBLICOS.md). [Resumo estruturado sem diálogos](docs/evidencias/n42-public-validation.json).

## Dependência para concluir

Não há backend de produção NEXUS acessível e configurado neste ambiente. A opção sem URL/chave para o usuário final requer infraestrutura do desenvolvedor, autenticação, limites, política de dados e custos definidos, ou um modelo local que passe nas avaliações nos recursos suportados. O cliente e o gateway existem, inclusive provedores externos opcionais; não são anunciados como um serviço já implantado.

Permanecem pendentes: avaliação de um modelo realmente capaz e disponível, validação humana independente, teste físico no A03, pesquisa online de produção, STT/TTS físico e aprovação de instalação pelo Play Protect. Não há aprovação da inteligência geral nesta entrega.
