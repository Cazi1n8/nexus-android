# Ponte de IA do NEXUS

## NEXUS AI online — alpha.3 / código 29

O servidor existente agora oferece **dois modos explícitos**: serviço NEXUS AI com sessões individuais para um piloto administrado e a ponte legada de token compartilhado para testes controlados. A ponte legada não é usada como segredo embutido no APK. Este código não inclui conta, crédito, domínio, HTTPS implantado ou hospedagem já operacional.

Para o modo comum do app, o desenvolvedor configura `NEXUS_SESSION_DB`, uma conta de API e HTTPS no servidor. O APK contém somente a URL pública, escolhida no build (`nexusServiceUrl`). O usuário recebe convite de uso único e autoriza o envio de texto/imagens; não configura URL, modelo ou chave de API. O cliente guarda tokens individuais no Android Keystore e renova a sessão antes de inferir, sem repetir automaticamente uma geração potencialmente cobrada.

O preset principal é `NEXUS_PROVIDER=anthropic`, modelo `claude-haiku-5-5`; `gemini` configura `gemini-3.8-flash`, e `generic` preserva integração avançada compatível. A escolha do modelo e do upstream pertence ao operador; o cliente usa o alias estável `nexus-ai`. Para Haiku, o servidor omite parâmetros de amostragem incompatíveis. [Comparação oficial e custos](../docs/PROVEDORES_NEXUS_ONLINE.md).

### Sessões individuais

| Rota | Corpo/credencial | Resultado |
| --- | --- | --- |
| `POST /v1/sessions/redeem` | JSON com `invitation_code`, convite único emitido pelo operador | `access_token`, `refresh_token`, `expires_in`, `refresh_expires_at`, `model=nexus-ai` |
| `POST /v1/sessions/refresh` | JSON com `refresh_token` | Novas credenciais rotativas; repetir o refresh consumido revoga a família |
| `POST /v1/sessions/revoke` | JSON com `refresh_token` | Revogação da sessão; o cliente também apaga seu registro local |
| `GET /v1/capabilities` | Bearer individual | Capacidades configuradas, sem afirmar disponibilidade/qualidade de modelo |
| `POST /v1/chat/completions` | Bearer individual e modelo `nexus-ai` | JSON final ou SSE real; cálculo seguro e propostas Android validadas |

Convites possuem 160 bits aleatórios, expiram e são consumidos atomicamente. Tokens possuem 256 bits aleatórios, acesso de 15 minutos e limite absoluto de renovação de 30 dias. Somente hashes e contadores são guardados no banco; não há registro de prompts, fotos ou respostas. Autenticação não oferece cadastro público anônimo irrestrito. A emissão de convites é pelo CLI no host, com arquivo privado `0600`.

### Configuração do piloto

[Guia completo de implantação](../docs/IMPLANTACAO_NEXUS_AI_ONLINE.md), [exemplo de ambiente](.env.online.example), [proxy TLS](deploy/nginx.conf.example) e [serviço](deploy/nexus-ai.service). O exemplo não contém credenciais reais.

| Variável | Uso |
| --- | --- |
| `NEXUS_SESSION_DB` | Arquivo privado persistente; habilita contas individuais e desabilita token compartilhado |
| `NEXUS_PROVIDER` | `anthropic`, `gemini` ou `generic`; padrão online `anthropic` |
| `UPSTREAM_API_KEY` | Chave privada somente no servidor; obrigatória para os presets de API |
| `NEXUS_MODEL` / `UPSTREAM_BASE_URL` | Definidos pelo operador; os presets fornecem modelo/destino padrão |
| `NEXUS_UPSTREAM_WORKSPACE_ID` | Header de workspace Anthropic quando exigido pela conta, somente no servidor |
| `NEXUS_ACCOUNT_DAILY_REQUESTS` / `NEXUS_GLOBAL_DAILY_REQUESTS` | Limites padrão de 100/1.000 chamadas upstream por dia, incluindo continuações de cálculo |
| `NEXUS_ACCOUNT_DAILY_UNITS` / `NEXUS_GLOBAL_DAILY_UNITS` | Reservas padrão 8.000.000/80.000.000 unidades por dia; entrada UTF-8 e quatro vezes o teto de saída, incluindo imagens e histórico |
| `NEXUS_BIND` / `NEXUS_PORT` | Loopback `127.0.0.1:8787`; serviço de contas recusa bind não local |
| `NEXUS_RATE_PER_MINUTE` / `NEXUS_CONCURRENCY` | Limites temporários de autenticação/conta e duas inferências simultâneas por padrão |

Unidades não representam tokens faturados nem limite em dólares; **configure orçamento também no provedor**. Reservas não são devolvidas automaticamente depois de erro/timeout porque a execução pode já ter sido cobrada. Uma instância precisa de disco persistente; não escale copiando o SQLite para réplicas independentes. O `ThreadingHTTPServer` deve permanecer atrás do proxy TLS documentado, com limites de corpo, conexões e tempo. A configuração é de piloto, não uma prova de capacidade pública de alta escala.

### Streaming, ferramentas e visão

O servidor encaminha deltas reais do provedor, com limite de evento/texto, marcador de conclusão obrigatório e erro explícito se o fluxo for interrompido. Não transforma uma resposta fixa em animação de tokens. JSON final coleta a mesma engine SSE. Credenciais, IDs privados e assinaturas de pensamento do provedor não são enviados ao APK; assinaturas necessárias a continuações ficam apenas na memória daquele pedido.

A calculadora é uma ferramenta executada no servidor com parser delimitado, sem `eval`, e pode continuar o contexto do modelo por no máximo duas rodadas. Cada chamada upstream reserva cota antes da execução. Somente as três ações permitidas do Android são propostas ao dispositivo; o servidor não abre apps, executa toques ou confirma resultados inexistentes. O aplicativo valida novamente e pede confirmação.

Imagens anexadas seguem o transporte multimodal existente, sem URLs remotas arbitrárias; o app reduz tamanho conforme os recursos do aparelho. O endpoint de capacidades informa `operator_declared_not_tested`, pois a configuração visual não comprova que a imagem foi corretamente compreendida. Vision não recebe compreensão contínua de vídeo. Pesquisa SearXNG continua opcional e depende de instância real administrada pelo operador.

### Resultados desta etapa

**122 testes do servidor e 191 de scripts passaram**, com upstream controlado. A avaliação real do candidato novo **não foi executada**: ausência de endpoint implantado/convite resultou em `not_run_unconfigured`, zero inferências. [Resultados e limitações](../docs/NEXUS_AI_ONLINE_RESULTADOS.md). Não há aprovação semântica ou instalação aprovada pelo Play Protect.

Execute testes de protocolo na raiz com `python3 -m unittest discover -s server/tests -v`. Para avaliação do modelo, use `scripts/run_nexus_online_evaluation.py` após a implantação, com convite em arquivo privado e transcrição fora do repositório. O workflow manual publica apenas o resumo técnico; não executa inferência paga em push/PR. Consulte [TESTES_NEXUS_AI.md](../TESTES_NEXUS_AI.md).

## Ponte legada — registro e uso controlado

As instruções abaixo continuam disponíveis para testes locais com token compartilhado. Referências a “esta primeira ponte” e à ausência de contas descrevem o modo legado, não as sessões individuais implementadas acima. Não publique esse modo como serviço multiusuário aberto.

Servidor opcional em Python 3.10+, somente com biblioteca padrão. O app chama `/v1/chat/completions`; a ponte chama um modelo Ollama ou uma API compatível com esse protocolo. Ela não responde com texto predefinido fingindo IA.

As chaves privadas do provedor ficam aqui, fora do APK. O app recebe apenas um token de sessão da ponte. Esta primeira ponte atende um ambiente controlado com um token compartilhado; contas, emissão individual de tokens e revogação por usuário precisam ser implementadas antes de um serviço público para várias pessoas.

## Uso local com Ollama

Na raiz do repositório, depois de instalar e iniciar o [Ollama](https://ollama.com/):

```bash
ollama pull gemma3:4b
python3 server/setup_local.py
python3 server/nexus_gateway.py --env-file server/.env.local
```

O arquivo privado é gerado uma vez, com token aleatório e permissão `0600`. O gerador não imprime o token e recusa sobrescrever um arquivo existente. Edite-o localmente para trocar o modelo ou configurações. Não o publique; tanto o `.gitignore` raiz quanto o desta pasta ignoram arquivos `.env` privados.

O modelo de exemplo `gemma3:4b` tem entrada de imagem; o download e a execução exigem recursos do seu computador. É possível usar um modelo de texto menor, mas a análise de imagens não funcionará com ele. A instalação e o teste de um modelo real não ocorreram neste ambiente.

O servidor escuta em `127.0.0.1:8787` por padrão. No emulador Android, use `http://10.0.2.2:8787/v1`; no celular por USB, `adb reverse tcp:8787 tcp:8787` permite usar `http://localhost:8787/v1` em um build de depuração. O nome do modelo no app deve ser igual a `NEXUS_MODEL`. Copie o token somente do arquivo privado para o campo de sessão do NEXUS.

`GET /healthz` indica que a ponte está respondendo, **sem testar a disponibilidade do modelo**. Faça uma conversa real para verificar o caminho completo.

## Configurações

Variáveis existentes no processo têm prioridade sobre `--env-file`. O arquivo suporta somente `KEY=value` das opções abaixo; não executa shell nem interpola conteúdo.

| Variável | Uso/padrão |
| --- | --- |
| `NEXUS_ACCESS_TOKEN` | Obrigatória; valor aleatório de no mínimo 24 caracteres, sem espaços |
| `NEXUS_MODEL` | Único modelo permitido para a sessão; padrão `gemma3:4b` |
| `OLLAMA_BASE_URL` | Padrão `http://127.0.0.1:11434`; aceita também base `/v1` ou endpoint completo sem duplicar o caminho |
| `UPSTREAM_BASE_URL` | Alternativa para outro provedor; aceita origem, base `/v1` ou URL completa `/v1/chat/completions`; tem prioridade sobre Ollama |
| `UPSTREAM_API_KEY` | Credencial privada opcional do provedor; nunca copiada para o app |
| `NEXUS_BIND` / `NEXUS_PORT` | Padrões `127.0.0.1` e `8787` |
| `NEXUS_TOKEN_TTL_SECONDS` | Validade a partir da inicialização: padrão 28800 segundos/8 horas; máximo 7 dias |
| `NEXUS_RATE_PER_MINUTE` | Solicitações por IP e minuto; padrão 30 |
| `NEXUS_CONCURRENCY` | Inferências simultâneas; padrão 2, máximo 16 |
| `NEXUS_UPSTREAM_TIMEOUT_SECONDS` | Timeout de rede com o provedor; padrão 90, máximo 300 segundos |
| `NEXUS_MODEL_VISION` | Declaração do operador (`true`/`false`); padrão `false`, sem prova de inferência |
| `NEXUS_SEARCH_BASE_URL` | Instância SearXNG administrada pelo operador, opcional; HTTPS ou HTTP em loopback |

Para usar API externa, configure `UPSTREAM_BASE_URL`, `UPSTREAM_API_KEY` e `NEXUS_MODEL` no servidor. Confira os custos e a política de privacidade desse provedor. A ponte não adiciona um pagamento obrigatório: Ollama local continua sendo a alternativa gratuita.

Uma URL que contém somente a origem, como `https://provedor.example`, usa `/v1/chat/completions`. Bases com caminho próprio, como `https://provedor.example/api/v1`, recebem apenas `/chat/completions`. A URL completa é preservada, removendo somente uma barra final.

O token deve ser mantido privado. Ao completar o tempo de validade, a ponte retorna 401. Para revogar uma sessão, substitua o token no arquivo privado, reinicie o processo e atualize o app. Reiniciar com o mesmo token reinicia sua janela de validade; isso não equivale a um sistema de login com emissão centralizada e revogação persistente.

## Publicar com HTTPS

Para acesso remoto, use um reverse proxy com TLS válido, firewall e limitação de acesso. Configure o proxy para encaminhar `/v1/chat/completions` e, se usado, `/v1/capabilities` para o processo local e limitar corpos a 8 MB. Não exponha o `http.server` Python diretamente à internet: ele não é uma plataforma completa de hospedagem pública.

Mantenha a ponte em loopback quando o reverse proxy estiver na mesma máquina. Para contêineres ou outra topologia, configure conscientemente a rede privada. O app aceita HTTP apenas em endereços locais específicos de builds de depuração; em distribuição, precisa de HTTPS.

O acesso entre ponte e provedor também exige HTTPS, exceto para endereços de loopback, como um Ollama local. URLs não podem conter credenciais, query ou fragmento. Redirecionamentos upstream são recusados para evitar encaminhar a chave a outro destino.

O rate limit por IP desta versão não confia em `X-Forwarded-For`. Atrás de um proxy, os clientes podem compartilhar o mesmo limite. Um serviço multiusuário precisa de autenticação individual e limites no proxy/aplicação adequados à implantação.

## Protocolo e limites

- `POST /v1/chat/completions` com `Authorization: Bearer <token da sessão>` e `Content-Type: application/json`.
- Entre 1 e 64 mensagens `system`, `user` ou `assistant`; até 120 mil caracteres de texto por solicitação.
- `stream=false` para resposta JSON ou `stream=true` para SSE real; até 4096 tokens de saída solicitados. O provedor pode ter limites menores ou contar tokens de maneira diferente.
- Corpo máximo de 8 MB. Até duas imagens de usuário em data URI base64 JPEG, PNG ou WebP, com até 3 MB por imagem. O app tem limites menores para reduzir memória e tráfego.
- URLs remotas de imagem são recusadas, evitando que o cliente use o provedor para buscar endereços arbitrários.
- O cliente não pode escolher outro upstream nem trocar o modelo configurado. Parâmetros arbitrários, ferramentas e credenciais no JSON não são repassados.
- Máximo de 16 conexões em atendimento; timeout de leitura do cliente de 10 segundos e limite de inferências simultâneas.
- Respostas têm `Cache-Control: no-store`. Logs contêm apenas estados gerais e status HTTP, sem prompts, fotos, tokens, URLs do cliente ou corpos de erro do provedor.
- Falhas de autenticação, limite, carga, conexão, timeout e resposta incompatível retornam erros estruturados. Não há resposta de IA de fallback.

O timeout configurado é o timeout das operações de rede do Python, não um orçamento global rígido contra um provedor que envie bytes lentamente. Uma implantação pública deve acrescentar limites de duração total no proxy/processo.

## Testes

Na raiz:

```bash
python3 -m unittest discover -s server/tests -v
```

Há testes de protocolo em memória e testes HTTP por sockets com upstream controlado. Nenhum deles mede qualidade de modelo. Na execução de 29/10/2026, **56 testes passaram, sem ignorados**, incluindo SSE por socket real com primeiro fragmento recebido antes da conclusão upstream. O conjunto anterior de 44 também passou no GitHub Actions; as mudanças novas ainda precisam dessa execução remota. Veja [relatório](../docs/RELATORIO_TESTES_SERVIDOR.md). Uma verificação separada com modelo real está em [RELATORIO_IA_REAL.md](../docs/RELATORIO_IA_REAL.md).


## Resposta incremental real

O app pode pedir `stream=true`; o servidor aceita somente um upstream que responda `Content-Type: text/event-stream`. Cada fragmento de texto é validado e transmitido assim que chega, sem esperar uma resposta JSON inteira e sem animação que simule geração. O gateway não encaminha metadados privados do provedor. Exige `finish_reason` compatível e `[DONE]` de verdade; conexão incompleta produz erro, nunca um marcador de sucesso inventado. Limites: evento de 64 KiB, fluxo de 1 MiB e texto de 24 mil caracteres. Erros upstream depois de começar o fluxo são eventos `error`, sem corpo privado.

Configure o reverse proxy com buffering desativado para essa rota (`proxy_buffering off` no Nginx) e timeout total compatível com o modelo. O gateway envia `X-Accel-Buffering: no`. Desconectar o cliente fecha o socket upstream ao detectar a desconexão; isso não garante que um provedor externo cancele imediatamente a computação já iniciada.

`GET /v1/capabilities`, com o mesmo Bearer de sessão, informa modelo, streaming e disponibilidade de pesquisa configurada. `vision` é uma **declaração do operador**, acompanhada de `vision_status=operator_declared_not_tested`; `/healthz` e esse endpoint não aprovam qualidade nem disponibilidade do modelo. Ferramentas de ação do cliente continuam bloqueadas; `tools=false` descreve essa limitação da ponte. O app consulta esse endpoint autenticado antes do chat e só respeita essa limitação se `service=nexus-gateway` e `protocol_version=1`. A declaração tem cache de 60 segundos separado por destino e sessão (a chave do cache usa hash do token). O cliente então omite `tools`/`tool_choice` para esta ponte. Uma solicitação não negociada com esses campos é recusada explicitamente, em vez de perdê-los silenciosamente.

Um provedor externo direto sem esse protocolo (por exemplo, `/capabilities` devolvendo 404) continua recebendo as três ferramentas autorizadas do cliente. Uma resposta genérica `tools=false`, sem identificação NEXUS, não muda o protocolo do provedor. A descoberta ocorre no mesmo destino, sem redirecionamentos e sem envio de mensagens ou imagens; falha de descoberta não simula capacidade. A chamada normal ainda apresenta seus próprios erros de rede/API.

## Pesquisa opcional por SearXNG

O operador pode hospedar uma instância [SearXNG](https://docs.searxng.org/admin/installation.html), habilitar o formato JSON em `search.formats` e configurar `NEXUS_SEARCH_BASE_URL`. Isso utiliza a [API oficial de pesquisa](https://docs.searxng.org/dev/search_api.html), `GET /search?q=...&format=json&language=pt-BR&safesearch=1`. Não existe instância pública, gratuidade ilimitada ou infraestrutura implicitamente incluída neste repositório.

A chamada é explícita e exige antes uma declaração autenticada da ponte com `search=true`. Assim, provedores que ignoram campos desconhecidos não podem aparentar ter pesquisado. O app acrescenta `"nexus_search":{"query":"consulta"}` ao pedido normal. O cliente pode enviar somente `query`, entre 1 e 500 caracteres; não pode selecionar servidor, URL de busca, chave ou endereço de página. A consulta vai à instância configurada, que poderá repassá-la aos motores habilitados: esse envio precisa fazer parte do consentimento e da política de privacidade da implantação.

A ponte recebe no máximo cinco resultados com título, URL e trecho, nunca visita as páginas dos resultados e nunca trata os trechos como instruções de sistema. Os dados entram na conversa como uma observação identificada de pesquisa, separada das imagens reais. O modelo é orientado a citar URLs e a distinguir observação visual de pesquisa. Resultados podem estar errados ou desatualizados; o código não garante que o modelo respeite todas as citações. Falha ou ausência de configuração gera erro explícito, sem resultados fabricados.

A resposta JSON contém `nexus_sources`; SSE pode emitir um evento com `choices:[]` e `nexus_sources`. O cliente guarda `AiSource(title,url,snippet)` separadamente do texto gerado. Não confunda testes de transporte com pesquisa real: os testes atuais usam uma resposta de busca controlada, e **nenhuma instância SearXNG foi implantada ou validada com uma pesquisa pública nesta etapa**.

Para utilização padrão sem configurar URL/token por usuário, ainda é necessária uma implantação operada pelo desenvolvedor, com HTTPS, login/emissão individual de sessão, limites e política de uso. O gateway de sessão compartilhada é uma base para testes controlados, não um serviço público multiusuário pronto.
