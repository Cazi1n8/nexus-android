# Pedidos do NEXUS e estado real

Atualizado em **26/10/2026 (UTC)** para a etapa **3.0.0-alpha.1 / versão 24**, preservando o aplicativo existente. Os relatos do usuário não são considerados resolvidos apenas porque existe código novo. O Samsung Galaxy S9+ informado não foi testado fisicamente pelo ambiente.

O histórico das entregas anteriores permanece nos relatórios [alpha.4](RELATORIO_ALPHA_4.md), [alpha.3](RELATORIO_ALPHA_3.md) e [alpha.2](RELATORIO_ALPHA_2.md). Seus testes e APKs não são aprovação automática da nova versão. O estado mais recente é mantido no [progresso 3.0](../PROGRESSO_NEXUS_AI_3.md).

## IA e conversa

| Pedido | Implementação atual | Limite / verificação pendente |
| --- | --- | --- |
| Separar Calebe e NEXUS | `assistantName` fixo, `userPreferredName`, personalidade, instruções e preferências separados; migração das preferências antigas | Testes de identidade/prompt e avaliações reais; naturalidade e consistência não são garantidas pelo schema |
| Criar textos e interpretar informalidade | LLM real, prompt compartilhado e sem respostas prontas fingindo IA | **Ainda há falha real em “chat me ajda faze um texo de descupa”** e erros de conhecimento no baseline 1,5B; não declarar inteligência corrigida |
| Evitar repetição e frases programadas | Sampling, parada/metadata explícitas, conteúdo gerado sem rodapé automático; detecção de falhas vazias/internas/repetitivas | A detecção recusa falhas técnicas, não reescreve frases para esconder geração ruim; avaliação semântica continua necessária |
| Contexto além de quatro mensagens | Transporte de turnos por orçamento real de tokens; preserva pedido atual e remove turnos completos antigos; trechos literais sob pressão | Contexto é finito; não há resumo persistente semântico ou memória ilimitada. A avaliação de 20 turnos possui resultados próprios |
| Histórico e memória | Room local, busca literal em título/texto, renomear, continuar, nova conversa/contexto, excluir, editar memórias autorizadas e exportar | Exportação contém dados pessoais, sem token ou imagens; testes físicos de uso continuam separados |
| Matemática correta | Calculadora real antes do LLM; `10+10÷10×10 = 20` e `25 vezes 18 = 450`; explicação solicitada usa operações efetivamente calculadas | Não é uma solução geral de todos os problemas matemáticos. O modelo isolado já produziu explicação incorreta, registrada como falha |
| IA dentro do APK e ajuste ao telefone | Perfis Qwen2.5 0,5B e 1,5B incluídos, com cópia/hash privados e seleção por recursos reais | Android 8+, processo 64 bits, RAM e disco mínimos; sem garantia de qualquer aparelho ou qualidade avançada |
| Modelo online mais capaz sem configuração individual | Cliente remoto seguro e servidor opcional preparados | **Não há backend NEXUS público implantado**. Provedor opcional precisa de infraestrutura/modelo reais; chaves privadas ficam no servidor |
| Executar ações reais | Propostas estruturadas para rotas, Configurações e apps permitidos; confirmação de uso único; intents/APIs oficiais | Não afirmar execução antes de resultado. Ferramentas são limitadas; não existe planejamento autônomo irrestrito ou bloqueio do telefone implementado |
| Chat moderno | Markdown, código, copiar, ouvir, interromper e indicador de processamento | Compilação e testes da etapa atual não substituem revisão visual/áudio no telefone |

[TESTES_NEXUS_AI.md](../TESTES_NEXUS_AI.md) registra os quinze casos, comparação real e falhas semânticas sem tratar heurísticas como certificação de inteligência.

## Imagens, sensores e segundo plano

| Pedido | Implementação atual | Limite / verificação pendente |
| --- | --- | --- |
| Chat ler imagens sem texto | OCR real e EfficientNet-Lite0 real fornecem texto/categorias como evidências para o LLM | Categorias aproximadas não equivalem a modelo visual geral, descrição de ambiente ou análise completa de circuito. VLM remoto é opcional |
| Vision ao vivo | CameraX com classificação de quadros periódicos no aparelho, intervalo e prévia opcionais | Não é compreensão contínua de vídeo. Testes de classificador e câmera física devem ser documentados separadamente |
| Voz falar pt-BR | TTS real com fila da primeira resposta e seleção/fallback de idioma; interromper e configurar voz/velocidade | Dados de voz podem precisar ser instalados; mecanismo Android pode usar rede; resposta audível no S9+ ainda não comprovada |
| Voz fora do app | Serviço `microphone` independente, manual, com mesma IA e conversa; OUVIR/PARAR/ENCERRAR TUDO; posse de áudio evita realimentação | Sessão opcional até 15 minutos/30 perguntas; duas tentativas após silêncio; **sem detector permanente de “Ei, NEXUS”**; testes físicos pendentes |
| Mouse global | FGS `camera`, MediaPipe, acessibilidade com `dispatchGesture`, cursor/overlay e controles explícitos de parada | Sem leitura de janelas; Android e apps protegidos podem recusar/interromper. Clique, rolagem e continuidade no S9+ ainda precisam de prova física |
| Mouse parar de travar | Filtro por tempo, cadência adaptativa, preferências fora do caminho por quadro e proteção contra início atrasado após PARAR | Correções de pontos identificados; não comprovam que todos os travamentos desapareceram |
| Câmera nem sempre visível | Prévia opcional/oculta nos módulos compatíveis e mouse global sem janela de vídeo | Ocultar prévia não desliga sensor; indicadores oficiais e notificações permanecem |
| Toda função de câmera fora do app | Mouse e voz possuem serviços próprios independentes | **Vision, Libras, Mãos Livres e 3D ainda usam câmera vinculada à tela**; não são apresentados como sessões globais |
| Mouse e 3D separados | Controladores independentes; 3D não despacha cliques de mouse | Regressão física simultânea e disponibilidade da câmera continuam pendentes |
| Mãos Livres e 3D | Gatilhos configuráveis existentes e cubo OpenGL real com transformação por mãos | Não aprende gestos arbitrários nem oferece rastreamento espacial AR; modos de câmera continuam vinculados à tela |
| Libras continuar e mostrar alvo/letra | Sessão de treino não termina automaticamente após acerto, alvo no canto, janela temporal corrigida, modo livre com candidatas experimentais L/V/Y | **Sem alfabeto ou sinais completos validados**. Geometria não representa toda Libras; falta validação pedagógica e física |

Os [13 cenários de segundo plano](../TESTES_SEGUNDO_PLANO.md) permanecem explicitamente pendentes em aparelho real. Testes com landmarks sintéticos não comprovam reconhecimento físico ou clique em outro aplicativo.

## Segurança, compatibilidade e entrega

| Pedido | Implementação atual | Limite / verificação pendente |
| --- | --- | --- |
| Diagnóstico correto e discreto | Usa observações do Android, detalhes de hardware opcionais, sem IMEI/série; telemetria de geração sem conteúdo pessoal | Dados utilizáveis pelo app não são ficha comercial; aparelho do usuário não foi inspecionado remotamente |
| Funcionar em qualquer modelo | Um APK universal e política por ABI, memória, armazenamento, sensores e gráficos | Não garante todos os recursos em todo aparelho; [critérios atuais](COMPATIBILIDADE.md) |
| Permissões estritamente necessárias | Nove `uses-permission` no manifesto mesclado: oito Android + uma signature AndroidX; sensores/serviços opcionais por ação explícita | Revogação, indicadores e término devem ser conferidos fisicamente |
| Segurança de SDKs | MediaPipe com fábrica de logging no-op padrão upstream, preparação com origem/hash e nativos preservados | Achado e derivação documentados; isso não certifica ausência de todo tráfego nem causa exata do bloqueio |
| Instalar legitimamente com Play Protect | Manifestos, assinatura de teste, DEX e capacidades auditados; revisão oficial e assinatura estável preparadas | **Bloqueio relatado continua aberto**, sem aprovação do Google; não se orienta desativar proteções |
| APK de teste e CI no mesmo GitHub | Build debug e workflows reais, incluindo avaliações Android opt-in e artefatos; publicação pública debug continua pausada | Novo APK final precisa de hash/assinatura próprios; release assinada depende da chave protegida do responsável |
| Preservar progresso | Mesmo repositório, applicationId e dados; documentação/testes e histórico Git mantidos | Certificados de debug diferentes podem impedir atualizar instalação anterior; exportar antes de remover dados |

A auditoria detalhada está em [AUDITORIA_PLAY_PROTECT.md](../AUDITORIA_PLAY_PROTECT.md). Compilar, executar testes ou preparar uma minuta não significa que o Play Protect aprovou o aplicativo.

## Evidências desta retomada

Na primeira compilação integrada desta etapa, **188 testes JVM**, **113 testes Python dos scripts** e **44 do servidor opcional** passaram; lint debug/release terminou sem erros. O APK debug intermediário versão 24 foi criado e inspecionado. Alterações posteriores exigem nova compilação/resultado, mantidos no progresso e relatórios atuais; esses números não certificam automaticamente o próximo artefato.

A avaliação real do modelo 1,5B foi interrompida após sete saídas e registra falhas de informalidade e explicação. Não são quinze casos aprovados. Ainda não houve teste físico no S9+, aprovação do Play Protect ou comprovação de todos os travamentos resolvidos.

## Critérios para fechar pendências

1. Concluir compilação do código final, testes/lint e inspeção do APK exato; registrar falhas reais e links de evidência.
2. Executar as avaliações Android com modelo real, revisar as respostas além das heurísticas e manter erros como falha.
3. Testar áudio pt-BR, permissões, mouse global, câmera, Libras e interrupções no telefone físico.
4. Configurar assinatura definitiva protegida, verificar atualização legítima e usar a revisão oficial com Play Protect ativo.
5. Validar conteúdo/classificador de Libras e preparar uma solução de IA/visão mais capaz sem respostas simuladas ou backend fictício.
