# Verificação com modelo de IA real

Execução iniciada em **23/10/2026 às 12h39 em São Paulo** (15h39 UTC). Um modelo de linguagem real foi executado neste ambiente e recebeu solicitações pela ponte NEXUS. Nenhuma resposta predefinida foi usada como substituta do modelo nesta verificação.

## Ambiente da prova

| Componente | Identificação |
| --- | --- |
| Runtime | `llama-server`, release `b11838` do [llama.cpp](https://github.com/ggml-org/llama.cpp/releases/tag/b11838) |
| Modelo | [Qwen2.5-0.5B-Instruct-GGUF](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF), quantização `Q4_K_M` |
| Revisão do modelo | `9217f5db79a29953eb74d5343926648285ec7e67` |
| Tamanho do modelo | 491.400.032 bytes |
| Caminho exercitado | Cliente de teste → gateway NEXUS → `llama-server` → gateway → cliente |
| Registro da execução | `/workspace/tooling/ai-smoke/report.json` |

SHA-256 do arquivo do modelo:

```text
74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db
```

O pacote de runtime tinha 22.349.871 bytes e SHA-256 `507e2f4fe3553499b518efe74a4244e35d709154a4e51c67e3affeef228ceb30`.

## Resultados observados

| Verificação | Resultado |
| --- | --- |
| Pergunta “Quanto é 17 mais 25? Responda somente com o número.” | HTTP 200; resposta **“42”**; 1,14 segundo |
| Contexto informado: objeto preferido é um cubo ciano | Primeira inferência HTTP 200; o histórico produzido foi incluído na próxima pergunta |
| Pergunta sobre a cor do objeto preferido | HTTP 200; resposta **“Ciano.”**; 1,18 segundo |
| Solicitação sem autorização válida | HTTP **401**, sem resposta de IA |

Foram **três inferências reais**: cálculo, resposta inicial ao contexto e pergunta de acompanhamento. Os tempos acima pertencem apenas às solicitações medidas nesse ambiente; não são previsão de velocidade em um celular.

Os processos temporários do gateway e do `llama-server` foram encerrados após os testes, conforme `processes_stopped: true` no registro. O endpoint dessa prova não é um servidor publicado para uso no celular.

## O que esta prova permite afirmar

A ponte do NEXUS consegue encaminhar mensagens a um modelo real e retornar texto; o modelo resolveu este cálculo e utilizou o contexto fornecido nesta conversa. A autenticação recusou a solicitação não autorizada. Os [44 testes do servidor](RELATORIO_TESTES_SERVIDOR.md) verificam separadamente protocolo, limites e tratamento de falhas com um upstream controlado.

O teste real usou um modelo pequeno **somente de texto**, executado fora do Android. Ele não está embutido no APK e não valida compreensão de imagens, Libras, voz ou câmera. Duas verificações com respostas esperadas não comprovam qualidade geral, domínio de português, precisão escolar ou confiabilidade em outros assuntos.

Para conversar pelo app, configure um servidor acessível e mantido em execução. O [guia do servidor](../server/README.md) documenta uma opção gratuita com Ollama e as condições de acesso local ou HTTPS. Um modelo de texto não deve ser apresentado como capaz de analisar imagens.
