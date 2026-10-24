# NEXUS 2.0 — progresso verificável

Atualizado em **24/10/2026 (UTC)**. Versão em integração: **2.0.0-alpha.1**, código de versão 20. O projeto existente, o identificador Android e o schema Room v1 foram preservados. O Samsung Galaxy S9+ foi informado pelo usuário, mas não foi testado fisicamente pelo ambiente de desenvolvimento.

Esta atualização inclui o pedido de verificar o aparelho e ajustar recursos automaticamente. Compatibilidade do aplicativo, disponibilidade de uma função e teste físico aprovado são resultados diferentes.

## Estado dos sistemas

Estados utilizados: **Concluído e testado**; **Implementado aguardando teste em dispositivo**; **Parcialmente implementado**; **Ainda não implementado**; **Bloqueado por limitação técnica**.

| Sistema | Estado | O que existe e o que falta |
| --- | --- | --- |
| Auditoria anterior às mudanças | Concluído e testado | Código, dependências e manifesto examinados antes das alterações; evidências em [AUDITORIA_NEXUS.md](AUDITORIA_NEXUS.md). |
| NEXUS AI local sem URL/chave | Implementado aguardando teste em dispositivo | Runtime llama.cpp real, dois modelos de texto, download explícito e arquivos privados. A ponte JNI foi exercitada no computador; falta inferência dentro do Android e teste físico. |
| Download e integridade dos modelos | Implementado aguardando teste em dispositivo | Tamanho e SHA-256 fixados, arquivo temporário, espaço livre, cancelamento e exclusão. O fluxo Android de download ainda precisa de teste. |
| Perfil automático por aparelho | Implementado aguardando teste em dispositivo | Política considera Android, ABI do processo, RAM, armazenamento e recursos. Ajusta modelo recomendado e limites; não inicia downloads nem envio remoto sozinho. Integração em verificação. |
| Câmera e análise adaptativas | Implementado aguardando teste em dispositivo | Resolução e frequência são reduzidas conforme recursos; análise acompanha o custo dos quadros. Precisão, latência, bateria e temperatura precisam de medição física. |
| APK universal | Implementado aguardando teste em dispositivo | Primeiro APK 2.0 compilado e inspecionado com quatro ABIs, Android 8.0 ou superior. Revisão atual com MediaPipe 0.10.35 em nova validação. |
| Provedor personalizado opcional | Implementado aguardando teste em dispositivo | HTTPS, modelo, consentimento e token no Keystore separados da IA local. Não há fallback remoto silencioso nem chave privada embutida. |
| Imagens e NEXUS Vision | Parcialmente implementado | CameraX e captura explícita preservadas; análise exige provedor multimodal personalizado autorizado. Os modelos locais atuais não analisam imagens ou vídeo. |
| Ações estruturadas da IA | Implementado aguardando teste em dispositivo | Lista de ações permitidas, argumentos validados e confirmação explícita de uso único. Texto livre não executa comandos. |
| Voz em pt-BR | Implementado aguardando teste em dispositivo | Filtro por idioma e país, tom, velocidade, reprodução de teste, interrupção e leitura de respostas novas. Depende das vozes e serviços instalados. |
| Mouse interno persistente | Implementado aguardando teste em dispositivo | Sessão na raiz da navegação, cursor único e despacho real de toque, duplo toque, arrasto e rolagem. Falta confirmar alvos, coordenadas, perda de mão e navegação no aparelho. |
| Mouse global | Implementado aguardando teste em dispositivo | AccessibilityService sem leitura de telas, câmera em foreground, overlay, notificação e parada de emergência. Restrições do Android e dos aplicativos exigem teste físico. |
| Mãos livres | Implementado aguardando teste em dispositivo | Controlador independente, estabilidade, debounce e ações permitidas configuráveis, incluindo abertura de aplicativos. |
| 3D com duas mãos | Implementado aguardando teste em dispositivo | OpenGL ES 2, seleção por pinça sobre o cubo, transformação e liberação. Depende de GLES e rastreamento com duas mãos; não é AR com ancoragem espacial. |
| Exercícios preparatórios de Libras | Implementado aguardando teste em dispositivo | Dois exercícios geométricos limitados, demonstração própria, câmera, estabilidade, progresso, exportação e exclusão. Não equivalem a reconhecer sinais completos. |
| Tutor amplo e validação pedagógica de Libras | Ainda não implementado | Faltam biblioteca validada, avaliação educacional e cobertura de movimento, localização, expressão facial e gramática. |
| Diagnóstico | Implementado aguardando teste em dispositivo | Dados reais do aparelho e testes explícitos de biblioteca JNI, integridade e inicialização do MediaPipe. Disponibilidade não é aprovação de câmera, voz ou inferência. |
| Histórico, memória e exclusão | Implementado aguardando teste em dispositivo | Room/DataStore preservados; exclusão inclui modelos e progresso. Migração, cancelamento, exportação e limpeza precisam de regressão no Android. |
| IA local em processo de 32 bits | Bloqueado por limitação técnica | Esta versão empacota o runtime de linguagem apenas em ARM64/x86_64. O app base e provedor personalizado continuam previstos em ARM32/x86. |
| Rastreamento MediaPipe nas quatro ABIs | Implementado aguardando teste em dispositivo | Atualização para 0.10.35: AAR oficial contém JNI para ARM64, ARM32, x86_64 e x86, com segmentos ELF alinhados a 16 KB. Rastreamento físico ainda não validado. |
| Publicação na Google Play | Ainda não implementado | Assinatura de release, política de acessibilidade, privacidade, bibliotecas nativas e requisitos da loja ainda precisam de revisão. |

## Verificações executadas nesta evolução

| Verificação | Resultado registrado |
| --- | --- |
| Runtime Android em ARM64 e x86_64 | `externalNativeBuildDebug`: **BUILD SUCCESSFUL in 3m 47s**, com kernels otimizados. Este resultado precede a confirmação do build universal integrado. |
| Ponte JNI com modelo real 0.5B no computador | Inferência, contexto, acentos/emoji, erros de entrada, cancelamento e fechamento concorrente executados; marcador `jni_real_model_smoke=passed`. |
| Ponte JNI com modelo real 1.5B no computador | Mesmas verificações executadas; marcador `jni_real_model_smoke=passed`. |
| Avaliação qualitativa pequena | Quatro observações por modelo. O 0.5B apresentou erro de conteúdo e ação inventada; o 1.5B respondeu melhor nessas observações. Isso não certifica qualidade geral ou desempenho no S9+. |
| Servidor opcional Python | **44 testes aprovados em 17,286 s**, sem ignorados. Usa upstreams controlados para transporte, não para avaliar qualidade de IA. |
| Preparação das fontes nativas | **5 testes Python aprovados**, incluindo checksum e extração segura. |
| Primeira validação integrada 2.0 | **BUILD SUCCESSFUL in 2m 5s**; **76 testes JVM aprovados**, zero falhas ou ignorados; lint com **zero erros, 37 avisos e uma informação**; APK e APK de instrumentação gerados. Resultado anterior à atualização MediaPipe 0.10.35. |
| Revisão com MediaPipe 0.10.35 | AAR oficial inspecionado: biblioteca JNI nas quatro ABIs e alinhamento ELF 16 KB. Novo build integrado e inspeção final do APK em andamento. |
| Testes instrumentados no Android | Workflow automático em mudanças relevantes da branch `main`, com início manual também disponível; resultado de execução ainda não confirmado nesta atualização. |
| Testes físicos | **Não executados** pelo ambiente de desenvolvimento. |

Detalhes e limites das provas: [RELATORIO_NEXUS_2.md](docs/RELATORIO_NEXUS_2.md). Os resultados antigos da versão 0.1 em [PROGRESSO.md](PROGRESSO.md) são históricos e não substituem a validação da 2.0.

## APK e próxima confirmação

**Um primeiro APK 2.0 foi gerado; a revisão definitiva ainda está em validação.** A atualização MediaPipe 0.10.35 amplia compatibilidade nativa e exige novo build, assinatura e inspeção do pacote final. O tamanho e o hash da revisão anterior não devem ser apresentados como identificação definitiva.

O workflow está configurado para publicar o APK de teste diretamente em [Releases](https://github.com/Cazi1n8/nexus-android/releases) após o build aprovado da branch `main`. A publicação e o link do novo arquivo ainda precisam ser confirmados. O arquivo 0.1 antigo não deve ser apresentado como a entrega 2.0.

Depois, testar no aparelho: download e inferência offline, voz pt-BR, permissões negadas, botões reais por mouse, navegação persistente, rolagem, emergência do controle global e duas mãos no cubo. Só registrar aprovação dos itens realmente observados.
