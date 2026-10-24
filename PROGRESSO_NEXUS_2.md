# NEXUS 2.0 — progresso verificável

Atualizado em **24/10/2026 (UTC)**. Versão de teste: **2.0.0-alpha.1**, código de versão 20. O projeto existente, o identificador Android e o schema Room v1 foram preservados. O Samsung Galaxy S9+ foi informado pelo usuário, mas não foi testado fisicamente pelo ambiente de desenvolvimento.

Esta atualização inclui o pedido de verificar o aparelho e ajustar recursos automaticamente. Compatibilidade do aplicativo, disponibilidade de uma função e teste físico aprovado são resultados diferentes.

## Estado dos sistemas

Estados utilizados: **Concluído e testado**; **Implementado aguardando teste em dispositivo**; **Parcialmente implementado**; **Ainda não implementado**; **Bloqueado por limitação técnica**.

| Sistema | Estado | O que existe e o que falta |
| --- | --- | --- |
| Auditoria anterior às mudanças | Concluído e testado | Código, dependências e manifesto examinados antes das alterações; evidências em [AUDITORIA_NEXUS.md](AUDITORIA_NEXUS.md). |
| NEXUS AI local sem URL/chave | Implementado aguardando teste em dispositivo | Runtime llama.cpp real, dois modelos de texto, download explícito e arquivos privados. A ponte JNI foi exercitada no computador; falta inferência dentro do Android e teste físico. |
| Download e integridade dos modelos | Implementado aguardando teste em dispositivo | Tamanho e SHA-256 fixados, arquivo temporário, espaço livre, cancelamento e exclusão. O fluxo Android de download ainda precisa de teste. |
| Perfil automático por aparelho | Implementado aguardando teste em dispositivo | Política considera Android, ABI do processo, RAM, armazenamento e recursos. Ajusta modelo recomendado e limites; não inicia downloads nem envio remoto sozinho. Integração compilada e regras cobertas por testes JVM. |
| Câmera e análise adaptativas | Implementado aguardando teste em dispositivo | Resolução e frequência são reduzidas conforme recursos; análise acompanha o custo dos quadros. Precisão, latência, bateria e temperatura precisam de medição física. |
| APK universal | Implementado aguardando teste em dispositivo | APK 2.0 final compilado e inspecionado com quatro ABIs; assinatura v2, ZIP e 24 bibliotecas ELF com alinhamento de 16 KB verificados. Android 8.0 ou superior. |
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
| Revisão com MediaPipe 0.10.35 | **BUILD SUCCESSFUL in 2m 42s**, 76 testes JVM aprovados, 0 falhas/erros/ignorados; lint 0 erros, 37 avisos e 1 informação. APK e APK de testes instrumentados gerados. |
| Testes instrumentados no Android | **8 aprovados, 0 falhas/erros/ignorados** no emulador Android 10/API 29 x86_64; [execução 40053992145](https://github.com/Cazi1n8/nexus-android/actions/runs/40053992145), `connectedDebugAndroidTest` em 47s. Sem testes físicos. |
| Testes físicos | **Não executados** pelo ambiente de desenvolvimento. |

Detalhes e limites das provas: [RELATORIO_NEXUS_2.md](docs/RELATORIO_NEXUS_2.md). Os resultados antigos da versão 0.1 em [PROGRESSO.md](PROGRESSO.md) são históricos e não substituem a validação da 2.0.

## APK publicado

**APK final 2.0 confirmado localmente:** 101.515.753 bytes, assinatura v2 válida, quatro ABIs e alinhamento ZIP/ELF de 16 KB. Não contém pesos GGUF. SHA-256: `b1d2cbf860647fe6fc324b4660fae29deea0d42df24e1813304768dfa7976588`.

**[Baixar o APK publicado no GitHub](https://github.com/Cazi1n8/nexus-android/releases/download/v2.0.0-alpha.1-build-5/NEXUS-2.0.0-alpha.1.apk)**. Release: [v2.0.0-alpha.1-build-5](https://github.com/Cazi1n8/nexus-android/releases/tag/v2.0.0-alpha.1-build-5), **91.496.785 bytes**, assinatura v2 válida, checksum conferido contra o digest do GitHub e `SHA256SUMS.txt`. SHA-256: `a51ea58489c42139b80aae259ee95c6bc0d6b3fae3d37f06731c91ec9bdb570b`. Quatro ABIs e alinhamento ZIP/ELF de 16 KB inspecionados também no arquivo remoto.

Revisão do APK: `f95cf02e78b82411ec233d78df51b1344a1a2e69`. [Build e publicação aprovados](https://github.com/Cazi1n8/nexus-android/actions/runs/40053992143). [Oito testes no emulador aprovados](https://github.com/Cazi1n8/nexus-android/actions/runs/40053992145). O APK local e o remoto têm certificados de depuração e hashes diferentes; prefira o arquivo remoto para instalação.

Depois, testar no aparelho: download e inferência offline, voz pt-BR, permissões negadas, botões reais por mouse, navegação persistente, rolagem, emergência do controle global e duas mãos no cubo. Só registrar aprovação dos itens realmente observados.
