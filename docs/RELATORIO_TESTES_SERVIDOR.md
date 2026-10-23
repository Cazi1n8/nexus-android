# Relatório dos testes do servidor

Data: **23/10/2026**. Ambiente de execução: Python, Linux em sandbox. A execução final foi realizada pelo agente principal com acesso de rede autorizado, permitindo sockets locais. O upstream é um servidor de teste controlado, sem modelo de linguagem real.

Comando executado na raiz:

```bash
python3 -m unittest discover -s server/tests -v
```

Resultado final observado:

```text
Ran 44 tests in 16.813s
OK
```

Código de saída: **0**. São **44 testes aprovados, sem testes ignorados**.

| Grupo | Executados/aprovados | Ignorados | Evidência |
| --- | --- | --- | --- |
| Configuração e validação | 6 | 0 | Token ausente, HTTPS upstream, limites, ocultação de credenciais e normalização das URLs de provedores/Ollama |
| Handler HTTP com transporte em memória | 19 | 0 | Request/response HTTP, autenticação, expiração, modelo, multimodal, rotas, limites, falhas, timeout, redirecionamento e respostas em partes/recusas |
| HTTP com sockets de loopback | 19 | 0 | Chamadas HTTP reais entre cliente, gateway e upstream de teste em `127.0.0.1`; mesmos casos de protocolo |

Os testes em memória usam o `GatewayHandler`, a validação, o limitador e a política real de redirecionamentos. Substituem o transporte por objetos locais e o provedor por respostas controladas. Verificam que a chave do upstream não é trocada pelo token da sessão, que campos arbitrários não são repassados e que corpos de erro com conteúdo privado não chegam ao cliente.

As regressões novas verificam URLs de origem, base `/v1` e endpoint completo sem caminhos duplicados; a URL do Ollama pode incluir `/v1` sem duplicá-lo. Verificam também respostas válidas em partes de texto e recusas explícitas, mantendo a rejeição de respostas nulas ou com texto numérico e sem revelar credenciais do upstream.

Não foram verificados por esta suíte: conexão com Ollama/API externa, compreensão de imagens, qualidade de respostas, latência de modelos, TLS de uma implantação, acessibilidade, câmera/microfone ou execução no Android. O gateway realmente troca HTTP nos testes de loopback, mas recebe respostas controladas: isso não equivale a testar um LLM. O estado da compilação Android e eventuais testes separados de modelo devem ser consultados em [PROGRESSO.md](../PROGRESSO.md).

A primeira entrega encontrou erros de permissão nos testes por sockets e registrou **22 aprovados e 18 ignorados**, de 40 testes. Após as correções desta revisão, a execução sem rede registrou **25 aprovados e 19 ignorados**, de 44 testes. Esses resultados foram superados pela execução final acima, com sockets autorizados: **44 aprovados e zero ignorados**. Solicitações de permissão interrompidas não foram registradas como testes executados.
