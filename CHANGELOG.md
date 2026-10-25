# Alterações do NEXUS

## 2.0.0-alpha.3 — em validação, 25/10/2026

Versão Android 22. Correção do relato de download que permanece em “Baixando” com o tamanho completo. Build local aprovado em 2m 5s, com 90 testes JVM, 51 testes Python dos scripts e lint sem erros; APK local gerado e inspecionado. Os testes Android e a publicação alpha.3 ainda estão pendentes. [Relatório da etapa](docs/RELATORIO_ALPHA_3.md).

- Finalização pelo número exato de bytes fixados, sem aguardar fechamento da conexão depois do tamanho completo. Verificação SHA-256 do arquivo antes de liberar o chat.
- Arquivo parcial preservado em pausa/interrupção e retomada HTTPS com `Range`; validação do intervalo e reinício informado se o servidor ignorar a retomada.
- Fases de conexão, transferência e verificação separadas, com quantidade recebida, velocidade e progresso real de verificação.
- Retomada de arquivo completo para verificar sem baixar novamente; descarte confirmado apenas do parcial para recomeçar.
- Liberação explícita do job de download no ViewModel para permitir nova tentativa depois de concluir ou pausar.
- 14 novos testes HTTPS do downloader aprovados na JVM; dois testes Android controlados preparados e workflow opt-in que baixa os modelos reais pelo próprio aplicativo e executa o compacto. Resultados Android ainda pendentes de registro.
- Heap de desenvolvimento do Gradle ajustado para 4 GB, sem alterar os critérios de RAM do aplicativo instalado.
- Matriz dos pedidos originais adicionada, com implementações e pendências separadas. Retomada não preserva dados através de uma desinstalação.

## 2.0.0-alpha.2 — versão de teste publicada, 24/10/2026

Versão Android 21. A implementação preserva a base alpha.1, o identificador do aplicativo e o banco Room. Build local aprovado com 76 testes JVM, 44 testes Python dos scripts, 44 do servidor opcional e zero erros de lint. A revisão final passou com 13 testes comuns no Android emulado, um caso opcional ignorado sem fixture e um teste dedicado de IA real aprovado. O APK foi publicado, baixado e verificado; teste físico no S9+ permanece pendente. Resultados detalhados: [RELATORIO_ALPHA_2.md](docs/RELATORIO_ALPHA_2.md).

- Leitura de texto local no NEXUS Vision por Tesseract4Android 4.9.0, variante de uma thread, com modelos português/inglês empacotados e integridade fixada. Sem API ou transmissão de imagem para realizar OCR.
- Importação da galeria independente da permissão de câmera; botões explícitos para copiar, ouvir e salvar texto como memória.
- Ditado de perguntas no Vision para um campo editável, com envio manual à IA.
- Recursos de OCR delimitados por tamanho de arquivo e resolução; descarte de resultados quando a imagem ou a sessão muda. Processamento não é apresentado como análise contínua de vídeo.
- Scripts de preparação dos pesos e do AAR OCR com tamanho e SHA-256 fixados, validação adicional no Gradle e licenças de redistribuição. O AAR fica em `.build-deps`, fora do Git; preparação direta substitui a resolução de metadados JitPack que falhou no CI.
- Teste dedicado de inferência real JNI aprovado em Android 10 emulado, com GGUF Qwen 0.5B temporário, workflow separado e relatório das quatro respostas observadas. O teste padrão informa o caso ignorado quando a fixture não está presente.
- Teste da tela Vision usa ação de clique de acessibilidade e aguarda o destino da navegação, preservando as asserções de sensores desligados e pergunta persistida. A correção passou na rodada final de CI; o teste não abre o seletor de imagens do Android nem valida sensores físicos.


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
