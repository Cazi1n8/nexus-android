# Progresso NEXUS 4.1

## NEXUS AI online — alpha.3 / código 29

Atualização de 30/10/2026 no aplicativo existente. Fonte `4.1.0-alpha.3-core` / 29; aparelho de teste solicitado: **Samsung Galaxy A03**. [Relatório desta etapa](docs/NEXUS_AI_ONLINE_RESULTADOS.md).

**Estado: arquitetura online integrada; operação de produção e qualidade do candidato ainda não comprovadas.** Não há credenciais de provedor ou hospedagem disponíveis. O avaliador real terminou em `not_run_unconfigured`, com zero inferências. Os modelos locais anteriormente reprovados continuam opcionais, sem serem apresentados como solução da qualidade.

| Entrega | Estado real | Evidência/limite |
| --- | --- | --- |
| Provedor principal | Preset `anthropic` com `claude-haiku-5-5`; alternativa `gemini` com `gemini-3.8-flash` | [Comparação oficial de capacidade/custo](docs/PROVEDORES_NEXUS_ONLINE.md); nenhum candidato novo aprovado por inferência |
| Experiência comum | `NEXUS_ONLINE` para novas configurações; conexão por convite e consentimento, sem URL/chave do provedor | URL pública compilada pelo desenvolvedor; ausente por padrão. Escolhas offline/avançadas persistidas são preservadas |
| Sessões e segurança | Convites únicos; acesso de 15 minutos; renovação rotativa com limite absoluto; hashes/quotas SQLite privados; credenciais Keystore no Android | Testes de expiração, concorrência, revogação/replay e isolamento. Implantação HTTPS não executada |
| Identidade/memória | NEXUS AI chamado NEXUS; perfil do usuário, personalidade, memórias autorizadas e histórico existentes mantidos | O modelo recebe dados separados, sem confundir nome do usuário com identidade do assistente; qualidade semântica ainda depende da avaliação real |
| Streaming e ferramentas | SSE real, cancelamento, limites e medição; calculadora com precedência; ações Android somente propostas confirmadas | Transporte e parser testados. Resultado de ferramenta não equivale a ação global física nem a qualidade geral |
| Vision | Mesma sessão online para foto real/contexto; modelo multimodal configurado somente no servidor | Transporte controlado testado; reconhecimento com o candidato real ainda não executado |
| CORE e extensões | Sem novos pesos/dependências grandes/permissões; modelos offline e dados de visão permanecem opcionais | Compilação aprovada; CORE alpha.3 medido em 108.668.442 bytes (108,67 MB), sem GGUF obrigatório; auditorias sem violações nas regras verificadas |
| Avaliações | Bateria reproduzível de escrita, matemática, contexto de 20 turnos e imagem sintética criada | Preflight sem configuração, zero inferências; nova qualidade **não avaliada** |
| Distribuição | Publicação automática de releases suspensa | Play Protect continua bloqueador; assinatura estável e teste físico com proteção ativa pendentes |

Verificações executadas até este registro: **122 testes do servidor** e **191 testes de scripts**, sem falhas. São contratos e integrações de transporte controladas, separados da avaliação do modelo. **320 testes JVM passaram**, sem falhas, erros ou ignorados, em 47 suítes. Compilação final aprovada em 4m53s; Lint CORE com zero erros, 57 avisos e 1 informação. APK de 108.668.442 bytes, assinatura e alinhamento de 16 KiB verificados. [Medição e limites](docs/evidencias/n43-build-verification.json).

Para concluir a IA integrada, o responsável precisa configurar uma conta de API com orçamento, implantar o backend em HTTPS com disco persistente, definir `nexusServiceUrl` no build e emitir um convite individual. O [guia de implantação](docs/IMPLANTACAO_NEXUS_AI_ONLINE.md) descreve os arquivos, comandos, custos potenciais e limites do piloto. Não há chave privada no APK ou no repositório.

## Registro anterior — alpha.2

As medições, contagens e hashes seguintes pertencem à alpha.2 e ficam preservados para comparação; não aprovam a alpha.3 nem o novo modelo online.

Atualizado em 29/10/2026. Continuação do aplicativo existente, sem novo repositório. Dispositivo de teste solicitado: Samsung Galaxy A03. Fonte do aplicativo compilada: `de3e22b1a99f103dc75924f8cf6e50841e5a6a05`, versão `4.1.0-alpha.2-core` / 28; Android mínimo 26 e alvo 35.

**Estado: compilação e verificações técnicas aprovadas, IA geral reprovada, testes físicos e Play Protect pendentes.** Nenhum modelo experimental foi promovido. A distribuição automática de releases permanece suspensa.

[Resultados completos, APK de avaliação, hashes e runs](docs/NEXUS_4_1_ALPHA2_RESULTADOS_PUBLICOS.md). [Resumo técnico estruturado](docs/evidencias/n42-public-validation.json). O histórico anterior deste documento permanece no Git; esta edição consolida os resultados novos sem divulgar diálogos.

## Fases e evidências

| Fase | Implementado e preservado | Resultado e pendências |
| --- | --- | --- |
| 1 — Segurança | Permissões por recurso, consentimento explícito, notificações, serviços interrompíveis, auditoria de manifesto/assinatura/conteúdo; sem novos pesos ou permissões | APK CI com 9 permissões e zero violações nas regras auditadas; assinatura/alinhamento verificados. Causa interna do bloqueio Google não confirmada e Play Protect não aprovado |
| 2 — Mouse | Pipeline assíncrono, descarte de quadros antigos, resolução/FPS por capacidades, suavização adaptativa, clique/rolagem preservados; controles de sessão e parada protegidos de callbacks tardios | JVM e emulador aprovados, incluindo serviço sem câmera. Fluidez, gestos físicos e controle fora do NEXUS no A03 continuam pendentes; não há medição física antes/depois |
| 3 — IA | Identidade/preferências separadas, cálculo seguro, turnos completos, cancelamento, SSE remoto real e métricas reais de tempo; sem filtro para esconder respostas ruins | 15 casos Android concluídos; gate semântico reprovado. Investigação adicional de modelos também reprovada. Backend integrado competente ainda não implantado |
| 4 — Vision | Captura real, imagens reduzidas conforme memória, contexto da foto, captura periódica opcional sem sobreposição, fontes separadas da resposta e pesquisa opcional SearXNG | Transporte multimodal real executado; candidato inventou detalhes. Qualidade visual, pesquisa implantada e câmera física pendentes. Não há análise contínua de vídeo |
| 5 — 3D | OpenGL independente do mouse; cubo, esfera, pirâmide, cilindro, cone e toro; até 12 objetos, seleção, transformação, cores, duplicação, exclusão e cenas locais | EGL real das seis formas aprovado em emulador. Rastreamento de duas mãos e manipulação com mãos físicas ainda pendentes |
| 6 — Libras | Três aulas introdutórias, questionários, progresso/revisão e feedback geométrico por dedo; repetição sem encerrar a câmera | Testes de lógica aprovados. Hipóteses L/V/Y experimentais; não equivalem a reconhecimento da língua nem a avaliação pedagógica validada |
| 6 — Voz | Correção da espera por modelo ausente no CORE, enumeração/seleção de vozes pt-BR, áudio/tempo limite e estado de encerramento; sessão manual com notificação | Testes de contratos passaram. STT/TTS real e sessão fora do app no A03 pendentes; não há promessa de palavra de ativação sempre disponível |
| 7 — Extensões | CORE leve, centrais de Extensões/Armazenamento, modelos opcionais com tamanho/hash e remoção consciente; nenhuma execução de código remoto | Teste Android com 11.530.361 bytes de dados de visão por HTTPS aprovado; quatro ABIs preservadas. Desempenho térmico/bateria físico pendente |
| 8 — Entrega | APKs compilados, verificações locais e sete workflows concluídos; relatórios técnicos publicados sem transcrições | Seis workflows técnicos passaram; um falhou por qualidade da IA. Sem release de produção ou aprovação de instalação |

## Verificações executadas

- Gradle: BUILD SUCCESSFUL em 7m09s; 298 testes JVM aprovados, zero falhas/erros/ignorados.
- Scripts: 173 aprovados; servidor: 56 aprovados, inclusive SSE por socket.
- Android CORE: 12 aprovados; extensões: 1 aprovado; Android geral: 35 aprovados, 10 ignorados, zero falhas.
- Lint CI debug/CORE: zero erros, 58 avisos e 1 informação por variante. O relatório local tem 57 avisos e o release 47; não são resultados intercambiáveis.
- IA Android: 15 casos, 35 gerações reais, uma calculadora, uma proposta de ação, 37 respostas persistidas e verificadas; zero falhas técnicas. Dez verificações mecânicas satisfeitas não certificam qualidade. Reprovação crítica por alegar visão sem imagem.
- Ensaios de texto no servidor: 53 gerações iniciais, mais 14 de Qwen2.5 7B e 7 de SmolLM3 3B; nenhum modelo novo aprovado. Vision: quatro gerações reais, qualidade reprovada.

## Tamanho e distribuição

CORE local: **108.635.674 bytes (108,64 MB)**. CORE CI26: **108.652.058 bytes (108,65 MB)**. A diferença provém das compilações distintas, identificadas pelos próprios hashes. O CORE local cresceu 114.688 bytes (+0,106%) sobre a alpha.1; não se declara nova redução. O modelo de mãos incluído tem 7.819.105 bytes; os pesos generativos continuam opcionais. Tamanho de APK não é o armazenamento total instalado.

[ZIP do APK CI26 de avaliação](https://github.com/Cazi1n8/nexus-android/actions/runs/40922199666/artifacts/12919601582). GitHub pode exigir login e o artefato expira. Certificado de teste distinto do APK local e do CORE público anterior: não há garantia de atualização direta. Exporte dados antes de substituir uma instalação. O release local de 108.623.386 bytes não tem assinatura e não é instalável; falta a chave estável do responsável.

## Como verificar no Galaxy A03

1. Mantenha o Play Protect ativo. Confirme o hash/certificado indicado no relatório. Se bloquear, registre o aviso e siga o procedimento oficial em [AUDITORIA_PLAY_PROTECT.md](AUDITORIA_PLAY_PROTECT.md); não contorne a verificação.
2. Ao abrir o Chat sem ativar recursos, câmera/microfone devem ficar desligados. Sem modelo local ou servidor configurado, deve aparecer a indisponibilidade real da IA.
3. No Mouse, escolha explicitamente o modo global, leia a divulgação, autorize as permissões e inicie enquanto o NEXUS estiver visível. Confirme notificação, cursor e indicador de câmera; volte à Home do Android e teste em outro aplicativo comum. Anote modelo/Android/perfil e métricas, sem dados privados.
4. Teste movimento, clique, duas pinças, pinça mantida e rolagem; pause e encerre pela notificação. Confirme que câmera/cursor/gestos param e não reiniciam sozinhos. Teste revogação de permissão/câmera ocupada. Esse roteiro continua pendente fisicamente.
5. Inicie e encerre voz separadamente, selecione uma voz pt-BR disponível e confirme resposta audível, foco de áudio e interrupção. A rede pode ser necessária dependendo do mecanismo Android.
6. Em Vision, autorize apenas o envio da foto escolhida ao servidor configurado. Faça pergunta contextual. Detalhes incertos devem permanecer incertos; o candidato avaliado não passou nesse requisito.
7. No 3D, verifique formas e cenas manualmente antes do rastreamento com duas mãos. Em Libras, trate o feedback como treino geométrico introdutório, sem certificação linguística.

## Próxima dependência real

A IA padrão mais capaz exige um modelo local que passe nas avaliações dentro dos recursos disponíveis ou um serviço de produção do desenvolvedor, com autenticação, limites, política de dados e custos definidos. Não há hospedagem, provedor nem credenciais configurados neste ambiente. O cliente/gateway pronto não substitui essa infraestrutura. Nenhuma chave privada será incluída no APK.
