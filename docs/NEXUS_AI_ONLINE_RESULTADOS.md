# NEXUS AI online — resultados da alpha.3

Atualizado em **30/10/2026**, no mesmo repositório e aplicativo NEXUS 4.1 CORE. Fonte de versão `4.1.0-alpha.3-core`, código 29, pacote `br.com.nexus.assistant`, Android mínimo 26/alvo 35. O aparelho de teste solicitado continua sendo o Samsung Galaxy A03.

**Resultado desta etapa: arquitetura online implementada e contratos técnicos testados; modelo online novo ainda não avaliado.** O ambiente não dispõe de credenciais de provedor, conta de hospedagem ou endpoint NEXUS implantado. O preflight do avaliador produziu `not_run_unconfigured`, código de saída 2 e **zero inferências**. [Resumo técnico sem diálogos](evidencias/n43-online-evaluation.json). Não existe comprovação de melhora semântica, visão correta ou latência real deste candidato. Os modelos locais reprovados não foram promovidos nem tiveram seus resultados históricos apagados.

## Escolha do provedor

O candidato principal para a primeira avaliação é **Claude Haiku 5.5**, modelo `claude-haiku-5-5`, pela API oficial da Anthropic. O preset alternativo é **Gemini 3.8 Flash**, `gemini-3.8-flash`. A [comparação em fontes oficiais](PROVEDORES_NEXUS_ONLINE.md) inclui capacidades documentadas, protocolos, custo, ciclo de vida, privacidade e hospedagem. Nenhum desses dados substitui medir português, escrita, contexto e visão no NEXUS.

Como referência de preço consultada, 1.000 solicitações de 1.000 tokens de entrada e 500 de saída cobrados, na faixa de entrada até 100 mil tokens do Haiku 5.5, resultam em aproximadamente **US$ 0,35 de API**. A conta não inclui imagens, raciocínio adicional cobrado, crescimento do histórico, pesquisa, hospedagem, câmbio ou impostos. Não é uma mensalidade nem uma promessa de gratuidade. O operador precisa definir orçamento na conta do provedor, além das cotas do NEXUS.

## O que mudou no código

| Área | Mudança implementada | Estado e limite |
| --- | --- | --- |
| Seleção no Android | Novo `NEXUS_ONLINE` como opção inicial para configurações novas; offline e `CUSTOM` mantidos | Configurações já salvas preservam sua escolha, sem envio remoto silencioso |
| Conexão comum | URL pública definida pelo desenvolvedor no build; convite de uso único e consentimento no app | Usuário comum não informa URL, modelo técnico ou chave do provedor. URL vazia mostra serviço indisponível |
| Sessões | Convites aleatórios únicos; tokens de acesso de 15 minutos; renovação rotativa com limite absoluto de 30 dias; revogação por replay | Hashes e cotas persistem em SQLite privado; não há cadastro público sem controle |
| Proteção no Android | Sessão online separada da configuração avançada, cifrada no Android Keystore; renovação serializada e vinculada ao serviço | Não há chave privada do desenvolvedor no APK, redirecionamento de credenciais ou repetição automática de uma geração após falha de conexão/autenticação |
| Identidade e memória | NEXUS AI chamado NEXUS; perfil do usuário separado; personalidade, preferências e memórias autorizadas preservadas no prompt | Não representa treinamento de um modelo próprio. O respeito semântico do candidato continua por testar |
| Streaming | Mesmo transporte SSE real, cancelamento e métricas; o backend entrega deltas reais e recusa conclusão incompleta | Primeiro delta antes do fim upstream comprovado com servidor controlado, sem avaliação de modelo externo |
| Matemática | Ferramenta `calculate` com parser delimitado e precedência; até duas rodadas internas de cálculo | Sem `eval`, código remoto ou laço ilimitado. O parser passou nos casos pedidos; não é inteligência simulada |
| Ferramentas Android | Somente schemas canônicos de abrir configurações, abrir app conhecido e navegar para telas permitidas | Servidor emite propostas; o app valida novamente, pede confirmação e executa quando autorizado |
| Vision | Sessão multimodal integrada ao cliente existente; captura otimizada, anexos e contexto da imagem preservados | O candidato real não recebeu imagem nesta etapa. Capacidade declarada pelo operador não é reconhecimento aprovado |
| Presets | Modelo/upstream controlados pelo servidor; Haiku sem parâmetros de amostragem incompatíveis; workspace quando exigido | Cliente usa `nexus-ai`, sem escolher destino ou modelo de maior custo arbitrariamente |
| Armazenamento | Nenhum peso generativo, biblioteca grande ou permissão adicional acrescentado | Modelos offline e dados de visão continuam opcionais; CORE compilado de 108.668.442 bytes (108,67 MB) |
| Implantação | Exemplos de serviço sem privilégios, proxy HTTPS e ambiente sem credenciais | Piloto de uma instância com volume persistente. Produção, TLS e runtime do host não foram testados |

As ferramentas locais de cálculo e proposta de ação continuam disponíveis sem baixar um modelo generativo. Isso não é apresentado como capacidade de conversa geral. Ausência ou falha de infraestrutura não produz resposta de IA falsa nem fallback remoto do modo offline.

## Testes executados e escopos

| Verificação | Resultado real desta etapa | Escopo |
| --- | --- | --- |
| Testes JVM | **320 aprovados**, zero falhas/erros/ignorados, 47 suítes | Seleção/migração, autenticação e transporte Android, além das regressões existentes; não hardware físico |
| Scripts Python | **191 aprovados**, 2,995 s | Preparação, auditorias, avaliador online e proteção de evidências; os servidores de teste não são modelos |
| Servidor Python | **122 aprovados**, 28,007 s | Sessões, concorrência, expiração/replay, cotas, HTTP/SSE, schemas e contratos de implantação |
| Parser matemático | Casos pedidos retornaram 2600, 20 e 450; precedência, parênteses, potência e rejeição de código verificadas | Execução real de cálculo determinístico, não resposta de um modelo |
| SSE por socket | Primeiro fragmento recebido antes da conclusão upstream nos testes controlados | Transporte real, sem alegar qualidade ou latência de API de produção |
| Configuração Nginx/systemd | Contratos estáticos testados; Nginx não instalado e verificação systemd limitada pelo sandbox | Não são validações de runtime/implantação. Rodar as verificações no host real |
| Avaliador online | Preflight `not_run_unconfigured`, zero inferências, saída 2 | Falta endpoint implantado e convite; não conta como aprovação |
| Escrita/contexto/visão do novo modelo | **Não executados** | Necessitam infraestrutura e credenciais do desenvolvedor |
| Compilação final, Lint e APK alpha.3 | **BUILD SUCCESSFUL**, 4m53s; 108.668.442 bytes | 134 tarefas, 43 executadas/91 atualizadas. Lint CORE sem erros, 57 avisos e 1 informação; instrumentação compilada, não executada |
| Testes físicos/Play Protect | **Pendentes** | Não há aparelho físico disponível ou aprovação de instalação |

Respostas HTTP 401/403 invalidam a sessão individual e atualizam a interface para reconectar; erros transitórios 502 preservam a sessão. O cliente de inferência recusa redirecionamentos e repetição automática de conexão, evitando tentar novamente uma geração potencialmente cobrada. A configuração Anthropic pede uma ferramenta de cada vez e valida o resultado localmente.

Os testes técnicos usam respostas controladas somente nos fixtures de teste. O aplicativo e o backend de produção não usam essas respostas como IA. Nenhuma transcrição com nomes, prompts ou saídas geradas foi adicionada ao relatório público.

## Pacote compilado

CORE local de avaliação: **108.668.442 bytes (108,67 MB)**. SHA-256 `30d0928839840fc997ea582d59cc48f3d5eaa30e5533c283361e01c80049a9d1`. Certificado de teste `ffc6524eee21a185872e946e78649a977f4b82011ee573c20adf5ca6ba376f79`; assinatura e alinhamento de 16 KiB verificados. O aumento sobre a alpha.2 local foi de **32.768 bytes (+0,0302%)**, preservando o CORE sem modelos generativos obrigatórios. O APK tem quatro ABIs e o modelo de mãos incluído.

[Compilação e contagens](evidencias/n43-build-verification.json), [conteúdo/maiores arquivos](evidencias/n43-core-package-audit.json) e [manifesto/assinatura](evidencias/n43-security-package-audit.json). Ambos os auditores passaram sem violações nas regras verificadas; isso não certifica ausência de todos os riscos ou aprovação pelo Google. O APK está em `app/build/outputs/apk/coreTest/app-coreTest.apk`. Não há novo release público ou configuração de serviço online de produção neste pacote. O certificado local pode diferir da instalação existente; não é garantida atualização direta.

Esta rodada compilou a instrumentação Android, mas não a executou em emulador ou aparelho físico. As aprovações Android históricas da alpha.2 não são reutilizadas como prova da alpha.3. Novos workflows de regressão ainda precisam ser executados para esta fonte.

## Bateria real preparada

`scripts/run_nexus_online_evaluation.py` prepara nove grupos: identidade, escrita/refino, linguagem informal, matemática, contexto de 20 turnos, programação, visão com imagem sintética e pergunta seguinte, limite de câmera e proposta de ação. O orçamento padrão é **até 32 solicitações HTTP do cliente**, com teto de **512 tokens de saída por solicitação**. As continuações internas de cálculo podem gerar até três chamadas ao provedor por pedido, portanto até 96 chamadas e teto de saída solicitado de 49.152 tokens nessa configuração, além dos tokens de entrada cobrados. A contagem HTTP do cliente não comprova quantas rodadas o provedor realmente executou. O script usa convite privado, renova a sessão, verifica capacidades, faz chamadas JSON/SSE e revoga a sessão ao terminar.

A imagem original tem 930 bytes, produzida pelo próprio avaliador sem fotografias pessoais. O roteiro separa foto recebida de acesso contínuo à câmera. Perguntas sobre ação não executam comandos no aparelho e não contam como teste de mouse global. A avaliação HTTP também não substitui executar o cliente Kotlin e o aplicativo físico.

O resumo público registra tempos/uso e verificações mecânicas permitidas, sem copiar diálogos. A transcrição completa fica em arquivo novo fora do repositório com permissão `0600`. Completar o roteiro não aprova qualidade: é necessária revisão independente das respostas, incluindo erros, detalhes inventados e finalização. Não há resultados semânticos novos para publicar nesta etapa.

O workflow [nexus-online-evaluation.yml](../.github/workflows/nexus-online-evaluation.yml) só executa manualmente. Ele usa o Environment `nexus-ai-evaluation`, a variável pública `NEXUS_SERVICE_URL` e o Secret `NEXUS_EVAL_INVITATION`. Configure revisores e um convite novo antes de cada execução; uma rodada pode gerar cobrança. Em push/PR não há inferência paga. O workflow histórico de avaliação Android também foi limitado ao resumo semântico, sem arquivar diálogos novos; a reprovação continua visível. O artefato contém somente `technical-summary.json`; credencial e transcrição são removidas do runner, sem publicação dos diálogos. O workflow novo ainda não foi executado no GitHub nesta etapa.

## O que falta para conversar no celular

1. O responsável configura uma conta de API com acesso ao modelo e orçamento real. A chave entra somente no arquivo/secret privado do servidor.
2. Implanta HTTPS com domínio válido, serviço em loopback e banco persistente de uma instância. Valida Nginx/systemd no host e mantém os limites documentados.
3. Define a URL pública em `nexusServiceUrl` ao compilar; no CI Android, a variável de repositório `NEXUS_SERVICE_URL` alimenta essa propriedade. Endereço público não é credencial. URL ausente não aponta para servidor fictício.
4. Emite um convite e conecta a sessão no app após consentimento. O usuário comum não precisa de URL ou chave de API; o piloto usa convite individual para controlar acesso e custo.
5. Executa a bateria real, revisa qualidade e registra TTFT, duração, uso e erros. Se o candidato falhar, ajusta ou troca o modelo e repete a avaliação, sem promover uma resposta ruim.
6. Testa o Android no Galaxy A03, assinatura estável e instalação com Play Protect ativo. A distribuição permanece bloqueada até validação apropriada; não se pede para desativar a proteção.

O [guia de implantação](IMPLANTACAO_NEXUS_AI_ONLINE.md) contém comandos, arquivos privados, convites, revogação e limites. O modo avançado de outros provedores permanece disponível; uma escolha offline nunca é encaminhada ao online automaticamente.

## Bloqueadores preservados

**Infraestrutura de IA:** não há serviço NEXUS de produção implantado nem credenciais disponíveis neste ambiente. O código integrado está preparado, mas a experiência de instalar e conversar depende dessa etapa operacional.

**Qualidade:** candidatos locais anteriores foram reprovados e o novo candidato online ainda não foi avaliado. Não há garantia de inteligência perfeita nem aprovação por testes unitários.

**Distribuição:** Google Play Protect continua bloqueador registrado em [AUDITORIA_PLAY_PROTECT.md](../AUDITORIA_PLAY_PROTECT.md). Não houve liberação pelo Google, instalação física aprovada ou novo release público. Auditoria de permissões e assinatura não substitui essa decisão.
