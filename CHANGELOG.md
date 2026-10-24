# Alterações do NEXUS

## 2.0.0-alpha.1 — versão de teste, 24/10/2026

A evolução preserva o aplicativo existente e seu histórico. A compilação integrada foi aprovada; funcionalidades ainda dependem dos testes físicos registrados em [PROGRESSO_NEXUS_2.md](PROGRESSO_NEXUS_2.md).

### IA e voz

- NEXUS AI local como modo separado do provedor HTTPS opcional, sem exigir URL ou chave para inferência local em aparelhos compatíveis.
- Runtime llama.cpp real via JNI, UTF-8, cancelamento e liberação de sessão; modelos Qwen 0.5B e 1.5B baixados com consentimento e SHA-256 fixado.
- Contexto curto e memórias autorizadas, perfis persistidos e controle de exclusão dos pesos.
- Propostas estruturadas de ações com argumentos validados e confirmação explícita de uso único.
- Voz por idioma e país, correção de voz estrangeira persistida, tom, velocidade, teste e leitura automática de respostas novas.

### Compatibilidade e controles

- Política de recursos por Android, ABI, RAM, CPU, armazenamento, câmeras, microfone e GLES; perfil automático e alternativa manual.
- APK universal para quatro ABIs; IA local limitada a processos de 64 bits.
- MediaPipe atualizado para 0.10.35; AAR oficial inspecionado com JNI nas quatro ABIs, incluindo x86_64, e alinhamento ELF de 16 KB.
- Resolução e frequência de análise adaptáveis, com redução de efeitos em aparelhos modestos e interface responsiva.
- Mouse interno com sessão preservada na navegação e eventos reais de toque, arrasto e rolagem.
- Controle global optativo por acessibilidade e serviço de câmera em primeiro plano, sem leitura de telas, com notificação e emergência.
- Controladores independentes para mouse, mãos livres e cubo OpenGL ES 2; seleção e manipulação com duas pinças.

### Educação, dados e verificação

- Dois exercícios geométricos preparatórios de Libras, demonstração própria, progresso, exportação e exclusão; sem reconhecimento de Libras completa.
- Diagnóstico com observações reais e testes explícitos de bibliotecas.
- Scripts de preparação nativa verificada, workflow de APK e workflow automático de instrumentação em emulador, também iniciável manualmente.
- Publicação configurada para oferecer APK direto em Releases após build aprovado da branch principal.
- Compilação integrada final aprovada com 76 testes JVM, oito testes no emulador Android 10, zero erros de lint, assinatura v2 e inspeção de alinhamento ZIP/ELF de 16 KB. Interruptores receberam rótulos individuais para acessibilidade e seleção correta nos testes.
- Auditoria, compatibilidade e relatório de testes separados dos resultados históricos 0.1.

### Limites conhecidos

Os modelos locais atuais não analisam imagens. Qualidade do modelo 0.5B é limitada, com erros observados em provas reais. A voz depende dos serviços Android. Não há escuta permanente de ativação, certificação de gestos no S9+, ancoragem espacial de AR ou validação pedagógica de Libras. Publicação na Google Play permanece pendente.

## 0.1.0 — base preservada

Primeiro APK de teste com Kotlin/Compose, Room, DataStore, cliente HTTP multimodal, voz Android e laboratório inicial de mãos. Resultados dessa etapa permanecem em [PROGRESSO.md](PROGRESSO.md) e no histórico Git.
