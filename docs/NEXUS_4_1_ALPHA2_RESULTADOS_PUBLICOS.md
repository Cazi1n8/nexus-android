# NEXUS 4.1 alpha.2 — validação técnica

Atualizado em 29/10/2026. Mesmo repositório e aplicativo: `Cazi1n8/nexus-android`, `br.com.nexus.assistant`. Fonte do aplicativo testada: `de3e22b1a99f103dc75924f8cf6e50841e5a6a05`, versão `4.1.0-alpha.2-core`, código 28. Esta atualização de relatório não recompila nem altera o aplicativo.

**A IA geral e o candidato multimodal continuam reprovados.** Compilar, preservar mensagens e entregar uma imagem ao modelo não comprovam qualidade das respostas. Não há backend NEXUS de produção implantado ou credenciais de hospedagem/provedor configuradas neste ambiente. O CORE mantém os modelos de conversa opcionais e não recebeu os modelos experimentais maiores.

## Resultado verificável

| Verificação | Resultado real | Limite |
| --- | --- | --- |
| Gradle local | BUILD SUCCESSFUL em 7m09s; debug, CORE, release sem assinatura e instrumentação gerados | Não mede desempenho físico |
| JVM | 298 aprovados; zero falhas, erros e ignorados | Lógica e contratos |
| Scripts Python | 173 aprovados, inclusive após corrigir apenas o texto do diagnóstico do coletor | Não avalia inteligência |
| Servidor | 56 aprovados; SSE real por socket | Transporte controlado, sem certificação do modelo |
| Android CORE no emulador | 12 aprovados, incluindo EGL real de seis formas e parada segura sem permissão de câmera | Não usou mãos físicas |
| Extensões no emulador | 1 aprovado; download HTTPS real de 11.530.361 bytes, retomada, verificação, inferência OCR/classificação e remoção | Pacotes de dados, sem código remoto |
| Android geral | 35 aprovados e 10 ignorados, zero falhas | As exclusões não contam como aprovação |
| IA Android | 15 casos completos, 35 gerações reais, 1 cálculo, 1 proposta de ação; 37 respostas persistidas e verificadas após reabrir Room | Gate semântico reprovado, apesar de zero falhas técnicas |
| Lint CI debug/CORE | Zero erros, 58 avisos e 1 informação por variante | Os avisos não foram declarados resolvidos |
| APK CORE CI | Assinatura e alinhamento de 16 KiB verificados; 9 permissões; zero violações das regras auditadas | Certificado de teste, não aprovação do Google |

Os sete workflows terminaram: seis verificações técnicas passaram e a avaliação semântica falhou. A avaliação de IA durou 1.385.470 ms, não uma comparação de velocidade em condições controladas. A instrumentação terminou, mas o coletor corretamente devolveu falha por qualidade; sua mensagem foi ajustada para diferenciar reprovação de execução incompleta, sem mudar o gate.

Runs: [compilação CI26](https://github.com/Cazi1n8/nexus-android/actions/runs/40922199666), [Android geral21](https://github.com/Cazi1n8/nexus-android/actions/runs/40922199766), [CORE8](https://github.com/Cazi1n8/nexus-android/actions/runs/40922199626), [IA local12](https://github.com/Cazi1n8/nexus-android/actions/runs/40922199621), [modelo integrado10](https://github.com/Cazi1n8/nexus-android/actions/runs/40922199845), [download8](https://github.com/Cazi1n8/nexus-android/actions/runs/40922199839) e [avaliação IA7 reprovada](https://github.com/Cazi1n8/nexus-android/actions/runs/40922199954).

## APK de avaliação e integridade

[Baixar ZIP do artefato CI26](https://github.com/Cazi1n8/nexus-android/actions/runs/40922199666/artifacts/12919601582). Contém o APK de avaliação e os relatórios de integridade; o GitHub pode exigir login e o artefato tem retenção limitada. Não foi criado novo release público. A finalidade do pacote é verificar as correções técnicas e coletar os testes físicos pendentes, sem apresentar a IA como aprovada.

| Arquivo | Bytes | SHA-256 |
| --- | ---: | --- |
| CORE CI26, assinado para teste | 108.652.058 | `da35fb93a94436109be12b82079ab9102a6f83c89bdd9310cb3da2ad6f18f760` |
| CORE local, assinado para teste | 108.635.674 | `556872b2ecc467237017ec5e7c43f177996f372cf8a6599559bdd30f388e773f` |
| Release local sem assinatura | 108.623.386 | `c624df52a72cfb451c4a9768c30dbf4aca95a4ad9357839e2738d35127bff346` |

Certificado CI26: `fa6d7278f338b47ca3660ba3e51e270949a6f4cae3e453ebf9c111a1e82fc2e7`. Certificado local: `ffc6524eee21a185872e946e78649a977f4b82011ee573c20adf5ca6ba376f79`. Ambos são identidades de teste distintas e também diferem do CORE público anterior; não há garantia de atualização direta sobre a instalação existente. Exporte os dados antes de qualquer substituição. O release sem assinatura não é instalável. A chave estável de distribuição do responsável continua necessária.

O CORE local cresceu 114.688 bytes (+0,106%) sobre a alpha.1 de 108.520.986 bytes. Isso preserva a otimização, sem declarar nova redução. O APK mantém quatro ABIs, o modelo de mãos verificado de 7.819.105 bytes e nenhum GGUF obrigatório. Tamanho de APK não equivale ao armazenamento instalado mostrado pelo Android.

## Qualidade da IA e limites de infraestrutura

A investigação inicial executou 53 gerações reais de texto com Qwen2.5 1,5B, Qwen3.5 2B e Qwen3 4B. Mais 14 gerações com Qwen2.5 7B Q3_K_M e 7 com SmolLM3 3B Q4_K_M também apresentaram erros de escrita, identidade e interpretação. Esses ensaios adicionais são testes no servidor de desenvolvimento, não no Galaxy A03. Nenhum candidato foi promovido ou empacotado. Um protótipo adicional de prompt foi revertido por não melhorar a qualidade.

Quatro gerações multimodais reais verificaram o caminho cliente Kotlin → gateway → modelo, o contexto da foto e a condição sem imagem. O candidato inventou detalhes e não respeitou de modo confiável o limite de visão. Não há aprovação de visão geral nem de pesquisa online de produção. A pesquisa SearXNG exige infraestrutura real configurada pelo operador e nunca é apresentada como resultado existente sem execução.

Para uma IA integrada mais capaz sem pedir URL/chave a cada usuário, falta escolher e disponibilizar um serviço real com autenticação, limites de uso, política de dados e custo mantido pelo desenvolvedor. O cliente/gateway e as opções avançadas existem; isso não representa um serviço já implantado. Não há promessa de hospedagem gratuita ilimitada. Modelos locais maiores não se tornam compatíveis com o A03 apenas porque executaram neste servidor.

## Segurança e testes físicos

Não há comprovação de instalação aprovada pelo Play Protect, nem causa interna do veredito confirmada. A análise de permissões e assinatura não substitui essa verificação. A publicação automática de releases permanece suspensa. Consulte [AUDITORIA_PLAY_PROTECT.md](../AUDITORIA_PLAY_PROTECT.md); mantenha as proteções do Android ativas e use o processo oficial de revisão se houver bloqueio.

Permanecem pendentes em aparelho físico compatível, principalmente Galaxy A03: latência de ponta a ponta e gestos com câmera real, mouse fora do NEXUS, voz pt-BR e sessão em segundo plano, duas mãos reais no 3D, validação pedagógica/linguística de Libras e instalação com Play Protect ativo. Nenhum desses itens foi aprovado por testes unitários ou emulador.

O mouse preserva clique/rolagem e os controles separados de iniciar, pausar e parar, com notificação. O serviço não lê conteúdo de janelas de outros aplicativos. Vision usa capturas consentidas com intervalo mínimo de dez segundos no modo periódico e apenas uma análise pendente; não é compreensão contínua de vídeo. O 3D é OpenGL, independente do mouse, e possui seis formas/múltiplos objetos/cenas. Libras oferece aulas e feedback geométrico introdutório; não reconhece integralmente a língua.

## Escopo do relatório público

[Resumo técnico estruturado](evidencias/n42-public-validation.json), sem nomes pessoais, prompts ou respostas geradas. Os registros completos continuam preservados privadamente no workspace. A revisão automática de publicação recusou enviar os diálogos completos ao repositório público por risco de exposição; esta entrega contém apenas os dados técnicos agregados. Os resultados originais não foram alterados e não se deve confundir ausência de transcrições públicas com aprovação da IA.
