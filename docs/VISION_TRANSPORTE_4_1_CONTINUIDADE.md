# NEXUS Vision e transporte — continuidade de 29/10/2026

## Causas identificadas e correções

- O transporte remoto antigo retinha no máximo 24 mensagens e podia cortar o texto da solicitação atual. A seleção agora preserva turnos completos dentro do limite de transporte de 63 mensagens/30 mil caracteres. Uma solicitação atual maior é recusada com explicação, sem corte silencioso. Esse orçamento não representa a contagem exata de tokens de todo provedor; o backend/modelo ainda impõe o contexto real.
- Imagens reais continuam chegando ao provedor como pixels JPEG/PNG/WebP em data URI, no máximo duas imagens de até 2 MiB. O turno completo da imagem mais recente é fixado junto ao pedido atual, mesmo quando o histórico intermediário precisa ser omitido. Se ambos não couberem, a requisição é recusada pedindo nova captura, em vez de continuar sem pixels; imagens mais antigas omitidas recebem indicação explícita. O modelo precisa ser realmente multimodal. OCR/classificação local não foi promovido a compreensão multimodal.
- O cliente passa a decodificar SSE real, com fragmentos únicos, UTF-8 estrito, limites de tamanho, condições de parada, marcador de conclusão e cancelamento que fecha a chamada OkHttp. Uma resposta cortada por conexão não vira resposta completa. Há métricas de tempo até o primeiro texto e tempo total, medidas na chamada efetiva.
- A construção das requisições de streaming e conversão base64 das imagens executam em `Dispatchers.IO`, evitando leitura de arquivo grande na thread da interface.
- O gateway Python agora retransmite SSE sanitizado e oferece pesquisa opcional pela API oficial do SearXNG administrado pelo operador. URLs de busca são configuração do servidor, nunca parâmetros escolhidos pelo cliente. Trechos de pesquisa são evidência externa identificada, não instruções de sistema; os resultados não são visitados.
- Fontes retornadas ficam em `AiStreamResult.sources`, separadas de `text`. Uma pesquisa requer chamada explícita `streamWithSearch`/`completeWithSearch`. Ausência/falha da infraestrutura gera erro claro.

## Arquivos

`data/AiProvider.kt`, `data/RemoteStreaming.kt`, testes correspondentes, `server/nexus_gateway.py`, `server/remote_support.py`, testes e instruções em `server/README.md`.

## Verificações e limites

O conjunto Python foi executado com sockets locais autorizados: 56 testes aprovados, zero ignorados em 29/10/2026. Inclui autenticação, expiração, limites, redirecionamentos, preservação de dados de imagem, pesquisa restrita, SSE incompleto e entrega por socket do primeiro fragmento antes de terminar a geração controlada. As respostas desses testes são produzidas por upstreams controlados: **não comprovam inteligência, reconhecimento visual ou pesquisa pública**.

Testes Kotlin novos cobrem a seleção de turnos, recusa de solicitação grande, montagem de ferramenta, fontes, UTF-8, fluxo incremental por MockWebServer e cancelamento. A execução Gradle e a integração à interface são verificadas pelo responsável principal; não foram executadas por este subtrabalho.

Não há backend público, credencial de provedor, conta de hospedagem ou SearXNG implantado. Não é possível afirmar IA integrada online sem configuração, visão real aprovada ou pesquisa atualizada disponível para usuários antes dessas etapas. Uma avaliação real com modelo/imagens precisa registrar os arquivos enviados e respostas de verdade, sem expor fotos pessoais.

Referências: https://docs.searxng.org/dev/search_api.html e https://docs.searxng.org/admin/installation.html .


## Correção de contrato app → gateway

A auditoria adicional encontrou perda silenciosa de capacidades: o app enviava `tools` e `tool_choice`, enquanto a ponte os descartava. Isso não interrompia conversas simples, mas as ferramentas estruturadas não atravessavam a ponte. Encaminhar tudo também seria incorreto: o modelo de exemplo e outros provedores podem não suportar function calling.

O cliente agora negocia capacidades pelo endpoint autenticado irmão da API. Somente uma resposta identificada como `nexus-gateway`, versão de protocolo 1, pode desativar os schemas de ação naquela chamada. Provedores diretos preservam os schemas originais; uma API genérica não pode mudar esse comportamento com um campo isolado. A ponte recusa schemas não negociados, evitando perda silenciosa. Pesquisa exige confirmação positiva de disponibilidade: não é enviada para um provedor que poderia ignorar `nexus_search`.

`GatewayNegotiationTest` constrói o corpo real do app e verifica as requisições por MockWebServer, incluindo histórico, cache de capacidades, preservação dos provedores diretos e pesquisa explícita. `test_gateway_rejects_unnegotiated_action_schemas_instead_of_silently_discarding` verifica a recusa real do handler Python. Esses testes de contrato são controlados; não representam uma avaliação real de geração ou uma implantação pública.
