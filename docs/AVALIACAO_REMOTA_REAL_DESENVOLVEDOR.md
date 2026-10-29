# Avaliação remota com o transporte real do aplicativo

`scripts/evaluate_remote_ai.py` compila os arquivos Kotlin de produção `AiProvider`, `RemoteStreaming`, `GatewayCapabilities`, `AssistantIdentity`, `NexusSettings`, `AiCompletion`, `ActionProposal` e `LocalModelCatalog`. O executável host usa o mesmo construtor de requisições, negociação de capacidades, envio de pixels e decodificador SSE do app. Ele não inicia um servidor nem baixa um modelo.

É um teste **no computador**, não no Android. O adaptador temporário de `BuildConfig.DEBUG=true` permite somente um endpoint de loopback explicitamente escolhido; esse arquivo nunca entra no projeto Android ou no APK. URLs remotas, credenciais na URL e respostas de assistente na fixture são recusadas.

## Preparação

1. Tenha um JDK 17+ e as dependências já resolvidas pelo Gradle normal do projeto. O script só lê os JARs do cache; não executa Gradle.
2. Inicie um servidor de inferência com um modelo real. Para analisar imagens, ele precisa de pesos/projetor e template multimodais compatíveis. Registre nome, revisão e SHA-256 dos pesos usados. A identificação do modelo é responsabilidade do operador, não algo comprovado por uma string retornada na API.
3. Inicie o gateway NEXUS em loopback com esse upstream. Configure um token privado e o modelo correspondente; nunca versione o arquivo privado.
4. Disponibilize o mesmo token apenas pela variável privada `NEXUS_EVAL_SESSION_TOKEN`. O script não aceita token como argumento e não o registra no relatório.

Exemplo de fixture sintética sem respostas preparadas:

```json
{
  "userPreferredName": "Calebe",
  "cases": [
    {"id": "escrita", "turns": [{"text": "me faz um texto de desculpa"}]},
    {"id": "imagem", "turns": [
      {"text": "O que é isso? Descreva somente o que está na foto.", "imagePath": "/caminho/privado/foto-de-teste.jpg"},
      {"text": "Para que serve o que você identificou?"}
    ]}
  ]
}
```

Execução, depois de configurar a variável privada no ambiente:

```bash
python3 scripts/evaluate_remote_ai.py \
  --endpoint http://127.0.0.1:8787/v1 \
  --model nome-configurado-no-gateway \
  --fixture /caminho/privado/fixture.json \
  --output /caminho/privado/resultado-novo.json
```

Use `--gradle-cache` para indicar o diretório `GRADLE_USER_HOME`, `--java` para selecionar o executável Java e `--timeout` para limitar toda a execução. `--compile-only` verifica a compilação sem chamar o modelo. Um relatório existente nunca é sobrescrito: escolha outro caminho para cada execução.

## Evidência produzida

Cada turno grava texto real e fragmentos recebidos, igualdade exata/normalizada entre deltas e resposta final, condições de parada, corte de resposta, fontes, SHA-256/tamanho da imagem e métricas. `firstTextMsWall` inclui descoberta/preparação e espera até o primeiro fragmento; `providerFirstTokenMs` também inclui a preparação e descoberta medidas dentro do provedor de produção; a medida externa permite conferir a fronteira da chamada. Uso de tokens fica nulo se o servidor não o reportar. Há SHA-256 dos fontes Kotlin, runner e fixture.

O turno seguinte usa a resposta realmente gerada, nunca uma resposta prevista. Falha técnica interrompe aquela conversa e preserva o relatório parcial, sem inventar um turno de assistente. Propostas de ação são registradas e **nenhuma ação é executada** pelo avaliador.

O relatório fica com permissão `0600`. Ele contém as perguntas e respostas da fixture escolhida: use imagens e textos de teste sem dados pessoais, e revise antes de compartilhar. A sessão e chaves de API não são incluídas.

O script não aprova semanticamente a IA. É necessário revisar as respostas com critérios independentes, registrar falhas de compreensão e comparar variantes usando a mesma bateria. Uma igualdade de deltas ou resposta HTTP bem formada não comprova compreensão da imagem. O teste de integração deve também observar no servidor que os pixels chegaram ao modelo selecionado, sem armazenar fotos pessoais em logs.

## Verificações do runner nesta etapa

- Compilação host dos arquivos de produção executada com sucesso em 29/10/2026, sem pedido ao modelo.
- Três testes automatizados do runner passaram: restrição a loopback, recusa de respostas preparadas e aceitação de imagem com pergunta posterior.
- Resultados de inferência real devem ser registrados separadamente após a execução contra o modelo operacional; esta verificação de compilação não os substitui.
