# Provedores para o NEXUS AI online

Pesquisa em fontes oficiais em **30/10/2026**, para continuar o aplicativo existente. Os valores são em dólares americanos, antes de impostos, câmbio, hospedagem e eventuais ferramentas pagas. São preços de API, não de assinaturas de aplicativos de chat. Revise os preços e a disponibilidade da conta antes da implantação.

**Escolha técnica para a primeira avaliação: Claude Haiku 5.5, modelo `claude-haiku-5-5`, pela API oficial da Anthropic.** Combina preço baixo, entrada de imagem e integração compatível com o transporte existente. Gemini 3.8 Flash fica como alternativa configurável pelo operador. A escolha é baseada em documentação e custo; **ainda não representa aprovação de qualidade em português, escrita ou visão no NEXUS**. Sem credenciais e servidor configurados, essas avaliações ficam pendentes e o aplicativo precisa informar a indisponibilidade real.

## Comparação

Entrada e saída abaixo são preços padrão por **um milhão de tokens**, sem cache nem processamento em lote. Contexto anunciado pelo fornecedor não é o limite que o NEXUS deve enviar em cada solicitação.

| Provedor/modelo | Entrada / saída | Contexto anunciado | Imagens, streaming e ferramentas | Integração e decisão |
| --- | ---: | ---: | --- | --- |
| Anthropic — `claude-haiku-5-5` | US$ 0,10 / US$ 0,50 até 100 mil tokens de entrada; acima disso, US$ 0,50 / US$ 2,50 | 1 milhão | Texto/imagens → texto; streaming e chamadas de função na camada compatível | Candidato principal; preço e protocolo adequados ao piloto. Confirmar desempenho e respostas reais antes de aprovar |
| Google — `gemini-3.8-flash` | US$ 0,75 / US$ 3,75 até 31/12/2026; US$ 1,50 / US$ 7,50 desde 01/01/2027 | 1.048.576 | Imagens, streaming e chamadas de função | Alternativa de maior custo; modelo estável e endpoint compatível documentados |
| Google — `gemini-3.5-flash-lite` | US$ 0,30 / US$ 2,50 | 1.048.576 | Multimodal, streaming e ferramentas | Alternativa econômica do Google; não presumir que satisfaça a qualidade exigida |
| OpenAI — `gpt-5.6-luna` | US$ 0,20 / US$ 1,20 | 1.050.000 | Imagens, streaming e chamadas de função | Alternativa de baixo custo; API de Chat Completions documentada, parâmetros próprios precisam ser respeitados |
| OpenAI — `gpt-5.6-terra` | US$ 2,00 / US$ 12,00 | 1.050.000 | Imagens, streaming e chamadas de função | Alternativa mais cara para tarefas complexas, sujeita a avaliação e orçamento |
| DeepSeek — `deepseek-flash` | Fora do pico: US$ 0,15 / US$ 0,60; pico: US$ 0,30 / US$ 1,20 | 1 milhão | A versão atual tem visão, chamadas de função e API compatível | Alternativa de custo baixo; revisar política de dados e validar o formato real antes de operar |

Fontes de preços e capacidades: [Haiku 5.5](https://platform.claude.com/docs/en/models/haiku-5-5/overview), [preços Anthropic](https://platform.claude.com/docs/en/about-claude/pricing), [preços Gemini](https://ai.google.dev/gemini-api/docs/pricing), [Gemini 3.8 Flash](https://ai.google.dev/gemini-api/docs/models/gemini-3.8-flash), [Gemini 3.5 Flash-Lite](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite), [GPT-5.6 Luna](https://developers.openai.com/api/docs/models/gpt-5.6-luna), [GPT-5.6 Terra](https://developers.openai.com/api/docs/models/gpt-5.6-terra), [DeepSeek preço](https://api-docs.deepseek.com/quick_start/pricing/) e [DeepSeek visão](https://api-docs.deepseek.com/guides/vision/).

Para OpenAI, solicitações acima de 272 mil tokens têm multiplicadores diferentes. Para DeepSeek, o pico depende do horário/dia; os valores da tabela são sem acerto de cache. Esses detalhes reforçam a necessidade de impor um orçamento bem menor no backend.

As páginas de GPT-4.1 mini e GPT-5 mini consultadas estão marcadas como obsoletas. O Google limita os modelos 2.5 a contas que já os utilizavam e recomenda modelos atuais para novos projetos. Por isso, eles não foram escolhidos como padrão de um novo serviço NEXUS. [GPT-4.1 mini](https://developers.openai.com/api/docs/models/gpt-4.1-mini), [GPT-5 mini](https://developers.openai.com/api/docs/models/gpt-5-mini) e [ciclo de vida Gemini](https://ai.google.dev/gemini-api/docs/deprecations).

## Custo de um exemplo simples

Exemplo calculado: **1.000 solicitações**, cada uma com **1.000 tokens de entrada e 500 de saída cobrados**. Total: um milhão de entrada e meio milhão de saída. Não inclui imagens, raciocínio adicional cobrado, crescimento do histórico, pesquisa, cache, repetição de solicitações ou infraestrutura.

| Modelo | Conta | Custo estimado das 1.000 solicitações |
| --- | --- | ---: |
| Haiku 5.5, faixa de entrada até 100 mil | 0,10 + 0,5 × 0,50 | **US$ 0,35** |
| Gemini 3.8 Flash até 31/12/2026 | 0,75 + 0,5 × 3,75 | US$ 2,625 |
| Gemini 3.8 Flash desde 01/01/2027 | 1,50 + 0,5 × 7,50 | US$ 5,25 |
| Gemini 3.5 Flash-Lite | 0,30 + 0,5 × 2,50 | US$ 1,55 |
| GPT-5.6 Luna | 0,20 + 0,5 × 1,20 | US$ 0,80 |
| GPT-5.6 Terra | 2,00 + 0,5 × 12,00 | US$ 8,00 |
| DeepSeek Flash, fora do pico / pico | 0,15 + 0,5 × 0,60 / 0,30 + 0,5 × 1,20 | US$ 0,45 / US$ 0,90 |

Esses valores são uma conta de referência, não uma promessa de mensalidade. Tokens variam por idioma e tokenizer. Imagens também consomem tokens; o histórico enviado em cada turno é cobrado novamente conforme as regras do provedor. Interromper a tela ou o streaming não garante cancelamento imediato da cobrança upstream.

## Protocolo escolhido e limitações

A Anthropic documenta a base **`https://api.anthropic.com/v1/`**, com o caminho de Chat Completions, Bearer privado, `stream`, mensagens com imagem, chamadas de ferramentas e `finish_reason`. Para Haiku 5.5, o backend deve omitir `temperature`, `top_p` e `top_k`. Na camada compatível, `reasoning_effort` e `strict` não oferecem a garantia que alguns clientes esperam: o primeiro é ignorado, e o segundo não valida o JSON de ferramenta. O NEXUS valida argumentos e ações antes de executá-los. Chaves não restritas a um workspace podem exigir o cabeçalho `anthropic-workspace-id`, configurado apenas pelo operador. [Compatibilidade oficial](https://platform.claude.com/docs/en/cli-sdks-libraries/libraries/openai-sdk).

O endpoint alternativo do Google é **`https://generativelanguage.googleapis.com/v1beta/openai/`**. O modelo 3.8 aceita `low`, `medium` e `high` para raciocínio; `minimal` é inválido. Uma troca de provedor precisa usar o preset correspondente, sem reutilizar parâmetros de outro modelo. [Compatibilidade Gemini](https://ai.google.dev/gemini-api/docs/openai) e [modelo 3.8](https://ai.google.dev/gemini-api/docs/models/gemini-3.8-flash).

O servidor não deve aceitar destino upstream, chave privada ou modelo arbitrário enviados pelo APK. O aplicativo usa a identidade NEXUS AI e um identificador de serviço estável; a escolha real do modelo fica no servidor. Calculadora e ações autorizadas são ferramentas reais e delimitadas. Uma solicitação de ferramenta do modelo não é prova de execução no Android.

Streaming deve repassar deltas reais, registrar tempo até o primeiro texto e tempo total, e recusar fluxo incompleto. A documentação de compatibilidade não substitui observar a conexão de um modelo real, incluindo o término do fluxo. Não foi medida latência real destes candidatos nesta pesquisa; descrições de velocidade publicadas pelos fornecedores não são benchmarks do Galaxy A03.

## Privacidade e hospedagem

A API comercial da Anthropic informa retenção padrão de entradas/saídas de até 30 dias, com exceções contratuais, legais e de segurança. O uso de dados para treinamento depende das condições comerciais e de escolhas explícitas; não confundir API com a conta de chat de consumidor. [Retenção Anthropic](https://privacy.claude.com/en/articles/7996866-how-long-do-you-store-my-organization-s-data) e [treinamento](https://privacy.claude.com/en/articles/7996868-is-my-data-used-for-model-training).

O Google distingue a camada gratuita, que pode usar conteúdo para melhorar produtos, da paga. Os termos também restringem público-alvo e regiões. Não usar uma cota de desenvolvimento como promessa de serviço gratuito ou como política de produção. [Preços/política Gemini](https://ai.google.dev/gemini-api/docs/pricing) e [termos](https://ai.google.dev/gemini-api/terms). A OpenAI informa que dados de API não são usados para treinamento por padrão, com retenção de monitoramento e exceções próprias. [Controles OpenAI](https://developers.openai.com/api/docs/guides/your-data). Antes de habilitar DeepSeek, revisar [política oficial](https://cdn.deepseek.com/policies/en-US/deepseek-privacy-policy.html); não presumir as mesmas garantias dos demais.

O backend precisa de HTTPS e armazenamento persistente para contas, sessões, revogação e limites. Valores de referência consultados: Render Starter de 512 MB por cerca de **US$ 7/mês**, mais disco a **US$ 0,25/GB/mês**; Fly.io de 512 MB por **US$ 3,69/30 dias** nas regiões de referência dos EUA, mais volume a **US$ 0,15/GB/mês**. Rede, região, backups e outros recursos podem acrescentar custos. [Render](https://render.com/pricing), [discos Render](https://render.com/docs/disks) e [Fly.io](https://docs.fly.io/about/pricing).

Cloud Run é cobrado por uso e possui franquias condicionais. Seu sistema de arquivos de instância não substitui um banco persistente: um backend com SQLite local precisa de outra implantação com volume durável ou de uma adaptação para banco gerenciado. Não há conta criada, domínio reservado ou hospedagem implantada nesta etapa. [Preços Cloud Run](https://cloud.google.com/run/pricing) e [sistema de arquivos de instância](https://cloud.google.com/run/docs/container-contract#file_system).

Para um piloto pequeno, uma instância com volume persistente e limites conservadores permite evitar a complexidade inicial de múltiplos servidores. Uma implantação com réplicas precisa de armazenamento e limites compartilhados; copiar SQLite para cada réplica perderia a garantia de orçamento global.

## O que falta comprovar

1. Configurar a conta do desenvolvedor, crédito/limites de cobrança e credencial privada no servidor.
2. Implantar HTTPS com banco persistente, criar convites/sessões individuais e configurar somente a URL pública no build Android.
3. Executar a bateria real de escrita, matemática, contexto e imagem; revisar qualidade e registrar TTFT, duração e uso reportado. Erros de configuração ou ausência de credenciais não contam como teste aprovado.
4. Testar o aplicativo físico e a instalação legítima com Play Protect ativo. O bloqueio permanece impeditivo de distribuição, independentemente do provedor escolhido.

O usuário comum não deve configurar URL nem chave de provedor. A configuração do serviço é responsabilidade do desenvolvedor; autenticação individual e consentimento para enviar texto/imagens continuam necessários. Modelos offline permanecem downloads opcionais, sem tornar o CORE maior com pesos generativos obrigatórios.
