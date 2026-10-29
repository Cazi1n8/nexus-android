# Testes NEXUS AI — estado verificável

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
