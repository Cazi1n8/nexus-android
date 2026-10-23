# Ponte de IA do NEXUS

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

O modelo padrão `gemma3:4b` tem entrada de imagem; o download e a execução exigem recursos do seu computador. É possível usar um modelo de texto menor, mas a análise de imagens não funcionará com ele. A instalação e o teste de um modelo real não ocorreram neste ambiente.

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

Para usar API externa, configure `UPSTREAM_BASE_URL`, `UPSTREAM_API_KEY` e `NEXUS_MODEL` no servidor. Confira os custos e a política de privacidade desse provedor. A ponte não adiciona um pagamento obrigatório: Ollama local continua sendo a alternativa gratuita.

Uma URL que contém somente a origem, como `https://provedor.example`, usa `/v1/chat/completions`. Bases com caminho próprio, como `https://provedor.example/api/v1`, recebem apenas `/chat/completions`. A URL completa é preservada, removendo somente uma barra final.

O token deve ser mantido privado. Ao completar o tempo de validade, a ponte retorna 401. Para revogar uma sessão, substitua o token no arquivo privado, reinicie o processo e atualize o app. Reiniciar com o mesmo token reinicia sua janela de validade; isso não equivale a um sistema de login com emissão centralizada e revogação persistente.

## Publicar com HTTPS

Para acesso remoto, use um reverse proxy com TLS válido, firewall e limitação de acesso. Configure o proxy para encaminhar somente `/v1/chat/completions` para o processo local e limitar corpos a 8 MB. Não exponha o `http.server` Python diretamente à internet: ele não é uma plataforma completa de hospedagem pública.

Mantenha a ponte em loopback quando o reverse proxy estiver na mesma máquina. Para contêineres ou outra topologia, configure conscientemente a rede privada. O app aceita HTTP apenas em endereços locais específicos de builds de depuração; em distribuição, precisa de HTTPS.

O acesso entre ponte e provedor também exige HTTPS, exceto para endereços de loopback, como um Ollama local. URLs não podem conter credenciais, query ou fragmento. Redirecionamentos upstream são recusados para evitar encaminhar a chave a outro destino.

O rate limit por IP desta versão não confia em `X-Forwarded-For`. Atrás de um proxy, os clientes podem compartilhar o mesmo limite. Um serviço multiusuário precisa de autenticação individual e limites no proxy/aplicação adequados à implantação.

## Protocolo e limites

- `POST /v1/chat/completions` com `Authorization: Bearer <token da sessão>` e `Content-Type: application/json`.
- Entre 1 e 64 mensagens `system`, `user` ou `assistant`; até 120 mil caracteres de texto por solicitação.
- `stream=false`; até 4096 tokens de saída solicitados. O provedor pode ter limites menores ou contar tokens de maneira diferente.
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

Há testes de protocolo em memória e testes HTTP por sockets com upstream controlado. Nenhum deles mede qualidade de modelo. Na execução mais recente, após liberar a rede do terminal, **44 testes passaram sem ignorados**; no GitHub Actions os 44 também passaram. Veja [relatório](../docs/RELATORIO_TESTES_SERVIDOR.md). Uma verificação separada com modelo real está em [RELATORIO_IA_REAL.md](../docs/RELATORIO_IA_REAL.md).
