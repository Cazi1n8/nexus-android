# Progresso real do NEXUS

Atualizado em **23/10/2026**. “Código criado” não significa “testado no celular”. Não há fase Android concluída enquanto compilação e testes de uso real estiverem pendentes.

## Entrega atual

| Item | Estado verificável |
| --- | --- |
| Projeto Android Kotlin/Compose | Arquivos reais de projeto, manifesto, dependências, recursos e 14 telas integradas no código; revisão estática feita, compilação pendente |
| Compilação Android | Retomada: rede liberada, JDK 17 e Gradle 8.11.1 oficiais instalados; SDK 35 em preparação para o primeiro build |
| APK | **Não gerado** |
| Testes Android/JVM | 27 testes JVM e 6 testes instrumentados escritos; execução Android pendente |
| Servidor Python | 44 testes aprovados, incluindo conexões HTTP locais, após liberar rede do terminal |
| Modelo de IA real | Não executado neste ambiente; requer servidor/modelo configurados pelo usuário |
| Modelo MediaPipe | Script de download criado; arquivo `.task` ainda não baixado |
| Git local | Inicializado na pasta `nexus`, branch `main`; histórico e cópia de segurança incluídos na entrega |
| GitHub remoto | Conexão confirmada; nenhum repositório acessível encontrado. Aguardando repositório para enviar o projeto |
| GitHub Actions | Workflow criado; nenhuma execução remota confirmada |

## As 14 telas

A interface está integrada no código em `ui/NexusApp.kt`. A tabela identifica o escopo do código e as validações necessárias; não certifica funcionamento em aparelho.

| Tela | Base prevista nesta versão | Validação/expansão pendente |
| --- | --- | --- |
| Splash | Identidade e transição para a navegação | Compilar e verificar animação/acessibilidade |
| Home | Acesso às áreas e indicação de estado | Testar telas pequenas e fonte ampliada |
| Chat IA | Cliente real OpenAI-compatible, contexto, imagem e erros | Conversa e imagem com modelo real; teste ponta a ponta |
| Assistente de Voz | SpeechRecognizer e TextToSpeech reais | Microfone, vozes, interrupção e ciclo de vida em aparelho |
| NEXUS Vision | CameraX, captura explícita de quadro e envio ao chat | Câmera frontal/traseira, consentimento e modelo visual; análise periódica futura |
| Mouse Virtual | Código MediaPipe, suavização, pinça e área de interação no NEXUS | Baixar modelo, compilar, calibrar e medir falsos cliques/latência |
| Mãos Livres | Código de gestos e associações a ações internas | Validar estabilidade e configuração no celular; ações externas futuras |
| NEXUS 3D | Código de manipulação de cubo projetado na tela | Validar movimento/rotação/escala com uma ou duas mãos; sem promessa de AR espacial |
| NEXUS Libras | Área educacional e indicação de limites | Referências visuais validadas, aulas e avaliação confiável; não reconhecer Libras completa |
| Central de Comandos | Ações Android por APIs oficiais e comandos personalizados persistidos em DataStore | Testar apps instalados, comandos personalizados e limitações do sistema |
| Personalidade | Preferências, nome, idioma e detalhamento persistentes | Verificar persistência e efeito com modelo real |
| Memória e Histórico | Room, CRUD de memórias/conversas e exportação JSON textual | Testar telas, exclusão, persistência e arquivos no aparelho |
| Configurações | DataStore, endpoint/modelo, token protegido e autorização | Testar permissões, vozes e recuperação de falhas |
| Diagnóstico | Disponibilidade de hardware, permissões, asset, voz, configuração e atalhos de testes manuais | Validar disponibilidade real de câmera, microfone, IA e desempenho |

## Plano de implementação por etapas

1. **Fase 1 — base funcional, em andamento:** navegação e 14 áreas integradas no código; compilar; corrigir erros; testar chat real, imagens, voz, dados e configurações. Libras e recursos futuros informam seu estado.
2. **Fase 2 — visão e voz, código inicial:** validar CameraX e MediaPipe reais; pontos das mãos, espelhamento, calibração e encerramento de recursos; melhorar sessão de voz sem prometer escuta permanente.
3. **Fase 3 — gestos, código inicial:** validar cursor, pinça, clique, duplo clique, arraste e ações internas. Controle de outros apps depende de análise separada das APIs oficiais e de consentimento explícito.
4. **Fase 4 — recursos especiais, parcial:** validar cubo controlado pelas mãos. Tutor de Libras precisa de referências educacionais verificadas, avaliação limitada e progresso antes de ser declarado entregue.
5. **Fase 5 — estabilidade/distribuição, pendente:** testar aparelhos, corrigir erros, medir bateria/latência, gerar APK, preparar assinatura e revisar requisitos atuais da Google Play.

Critérios detalhados: [ARQUITETURA.md](docs/ARQUITETURA.md). Matriz de uso real: [TESTES_E_DISTRIBUICAO.md](docs/TESTES_E_DISTRIBUICAO.md).

## Testes realmente executados

### Retomada — 23/10/2026

- Com acesso de rede concedido ao terminal, `python3 -m unittest discover -s server/tests -v`: **44 testes aprovados em 16,813 s, sem ignorados**. Isso valida o transporte e a ponte com upstream de testes; não valida um modelo de IA real.
- URLs de origem, base `/v1` e endpoint completo agora são normalizadas pela ponte sem duplicação de caminhos. Respostas em partes de texto e recusas são tratadas. JSON `null` não é exibido como resposta de IA.
- Corrigidos cancelamento do primeiro envio, concorrência de gravações/exclusão, mudança de servidor e token, timeouts e callbacks de voz.
- Adicionados testes instrumentados de cancelamento com Room real, exclusão concorrente, preservação do rascunho sem servidor e não restauração do token digitado.
- O Gradle oficial foi obtido com verificação SHA-256 e gerou o wrapper oficial (incluindo JAR). O checksum da distribuição está fixado no projeto.
- A API de download do Adoptium respondeu 403. O JDK de desenvolvimento foi obtido de `download.java.net` com checksum oficial verificado. A CLI Android 23 tentou gravar fora do espaço permitido; o instalador foi ajustado para a CLI clássica 19 e diretórios graváveis.

As próximas seções preservam os resultados anteriores para comparação. Elas não substituem os resultados desta retomada.

### Python — 23/10/2026

Comando na raiz do projeto:

```bash
python3 -m unittest discover -s server/tests -v
```

Resultado: `Ran 40 tests` e `OK (skipped=18)`, código de saída 0. **22 aprovados, 18 ignorados**, sem modelo real e sem internet.

- 4 testes de configuração e limites de payload aprovados.
- 18 testes do handler HTTP com transporte em memória aprovados: token obrigatório, expiração, modelo fixo, multimodal, bloqueio de URLs de imagem remotas, limite de corpo, rotas, rate limit, concorrência, timeout e erros upstream sem vazamento de segredo.
- 18 testes de integração por sockets locais ignorados explicitamente porque a criação de sockets retorna `PermissionError: Operation not permitted` neste sandbox. Devem executar em computador/CI com sockets disponíveis.

A primeira tentativa de testes por sockets encontrou esse bloqueio. A tentativa de obter permissão de rede foi interrompida pelo usuário; não foi usada para declarar nenhum teste aprovado. Os testes em memória foram adicionados para permitir verificação sem rede. Detalhes: [RELATORIO_TESTES_SERVIDOR.md](docs/RELATORIO_TESTES_SERVIDOR.md).

### Android — pendente

Tentativa real em 23/10/2026: `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug` encerrou com código 7 antes de executar o Gradle: `curl: (7) Failed to connect to proxy port 8080`. Não houve compilação parcial nem APK.

Verificações adicionais executadas: 7 XMLs analisados, YAML do workflow analisado, `sh -n gradlew` e `py_compile` aprovados. A revisão estática corrigiu callbacks, concorrência de pedidos, limpeza de imagens, validação de tokens e fluxo de voz. Essas verificações não substituem o compilador Kotlin/Android.

Os testes escritos cobrem payload/contexto/erros de IA, fragmentação de voz, geometria de gestos e correspondência exata dos comandos; 2 testes instrumentados verificam exclusão em cascata no Room e exportação sem caminhos privados. Ainda não executados. Para os instrumentados, conecte um aparelho/emulador e rode `./gradlew connectedDebugAndroidTest`.

Comandos a executar quando SDK, Gradle e dependências estiverem disponíveis:

```bash
python3 scripts/fetch_hand_model.py
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew build
```

O bootstrap temporário foi substituído pelo wrapper oficial gerado pelo Gradle verificado. O workflow usa Gradle 8.11.1 configurado por uma action. A compilação e os testes Android ainda precisam de resultado verificável; instalação, permissão, fala, câmera e benchmark não foram declarados aprovados.

## Limitações que permanecem explícitas

- A IA depende de um modelo acessível; não há LLM embarcado no APK. Ollama é uma alternativa gratuita em computador próprio, com requisitos de hardware próprios.
- Não houve validação de respostas de modelo, leitura de imagem ou conversação natural neste ambiente.
- Reconhecimento de fala e vozes dependem dos serviços instalados no Android e podem usar rede.
- Não há wake word permanente em segundo plano, AccessibilityService, monitoramento oculto ou bloqueio da tela por mecanismos não oficiais.
- Dados Room ficam no sandbox do app sem criptografia adicional; token da sessão usa Keystore e backup está desativado. Exportações textuais precisam de cuidado pelo usuário.
- MediaPipe precisa do modelo oficial e de validação com mãos reais. Geometria e pontos detectados não equivalem a compreensão completa de Libras.
- SDK de destino 35 é a base de desenvolvimento, não garantia de aceitação futura na Google Play.

## Próximo marco verificável

Conseguir acesso às ferramentas/dependências, executar o workflow ou build local, corrigir os erros encontrados e confirmar a existência do APK. Depois, instalar em um Android e preencher a matriz manual antes de marcar a Fase 1 como concluída.
