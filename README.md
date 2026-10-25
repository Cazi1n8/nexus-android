# NEXUS para Android

Assistente Android nativo em Kotlin e Jetpack Compose, em português brasileiro, com interface preta, azul e ciano. Código e histórico: https://github.com/Cazi1n8/nexus-android.

**Instalação bloqueada novamente pelo Play Protect no celular do usuário. A alpha.4 não está liberada para uso nesse aparelho.** Os testes funcionais aprovados não são aprovação do Google. [Pedido de revisão preparado](docs/PLAY_PROTECT_REVISAO.md); nenhuma solicitação foi enviada por este ambiente. [Assinatura e distribuição](docs/ASSINATURA_E_DISTRIBUICAO.md). Não remova a instalação atual nem desative a proteção para tentar instalar.

A compilação automática e os artefatos do Actions continuam disponíveis. A publicação automática de novos APKs de depuração nas Releases foi pausada; ela só ocorre com a variável explícita `NEXUS_PUBLICAR_APK_DEBUG=true`. O workflow manual `signed-release-review.yml` prepara um APK de release usando a mesma chave privada do responsável, configurada em quatro Secrets. Essa chave definitiva ainda não foi configurada; não há novo APK assinado ou aprovação do Play Protect.

**Versão de teste disponível: 2.0.0-alpha.4, versão 23.** A versão completa inclui a IA compacta no APK. Build, 129 testes JVM e a prova de Chat/OCR/cálculo sem internet passaram; o APK público foi baixado e inspecionado. A alpha.3 e suas evidências continuam no [relatório histórico](docs/RELATORIO_ALPHA_3.md).

## Mudanças desta etapa

- IA de texto Qwen2.5 0.5B incluída no APK, sem download separado no celular. Ao abrir o Chat, o app prepara uma cópia privada, confere tamanho e SHA-256 e mostra progresso real, pausa e retomada. O processamento se ajusta à memória/CPU, mantendo a mesma base compacta.
- Calculadora local real para expressões: `10+10÷10×10 = 20`. Não depende de o modelo adivinhar operações. Respostas de outros assuntos continuam podendo conter erros.
- Chat permite anexar imagem no modo local: extrai texto por OCR e pode explicar esse texto. **Não reconhece objetos, pessoas ou cenas.** A descrição geral de fotos requer provedor multimodal opcional autorizado.
- Mouse global experimental permanece ativo fora do NEXUS e ao remover a tarefa da lista de recentes, com notificação e **PARAR**. A prévia do vídeo vem oculta no mouse, mãos livres e 3D; Libras oferece botão para ocultá-la. Os indicadores de privacidade permanecem.
- Libras mantém o treino após o acerto, mostra o alvo no canto e oferece modo livre para configurações de mão efetivamente suportadas. **Não reconhece o alfabeto completo nem certifica sinais de Libras.** Conteúdo validado e classificador de letras continuam pendentes.
- Diagnóstico exibe compatibilidade/permissões/versão; detalhes locais do hardware aparecem somente por escolha explícita. Não coleta identificadores pessoais nem envia o diagnóstico.

Voz, Libras, mãos livres e 3D **ainda não operam fora do app**. Preservar uma sessão para retomá-la ao voltar não equivale a processamento em segundo plano. O mouse global possui serviço próprio; os demais modos precisam de uma etapa de implementação e testes. O Android pode interromper qualquer sessão por memória, bateria, permissões ou câmera ocupada. [Permissões, Play Protect e segundo plano](docs/PERMISSOES_E_SEGUNDO_PLANO.md).

## Instalar pelo celular

O usuário relatou que o **Play Protect bloqueou a instalação**. O aviso não identifica a causa exata. A acessibilidade usada no controle global é uma capacidade sensível; revisar o código não equivale à aprovação pelo Google. Não há garantia de liberação desta versão. Antes de distribuição pública, faltam assinatura de lançamento estável e revisão das capacidades/políticas. Não desative as proteções do aparelho para testar.

**[APK alpha.4 para auditoria e revisão](https://github.com/Cazi1n8/nexus-android/releases/download/v2.0.0-alpha.4-build-13/NEXUS-2.0.0-alpha.4.apk)** — **586,6 MiB**, download direto sem ZIP ou login. SHA-256 `0c7059c77d07bc7eed40d5d7701a36e98ca3495a1d77a74c2500f7c7e85286df`. O arquivo publicado foi baixado e conferido com o digest do GitHub, assinatura v2, ZIP 16 KB, quatro ABIs, 40 bibliotecas ELF e integridade do GGUF/OCR. Instalar e preparar a IA exige espaço adicional; reserve cerca de 2 GB livres para download, instalação e preparo.

1. Abra o APK somente se a verificação do Android permitir a instalação.
2. Abra **Chat IA** e aguarde **IA local pronta para conversar**. A preparação copia dados do APK; não baixa outro modelo.
3. Para imagem com texto, use o anexo no Chat e peça **“Leia esse texto”**. Para OCR sem carregar o modelo de conversa: **NEXUS Vision → Escolher imagem → Ler texto no aparelho**.
4. Voz usa os serviços reais de reconhecimento/síntese do Android; permita o microfone e instale uma voz Português (Brasil), se necessário. O serviço Android pode precisar de rede.
5. Controle fora do app: **Mouse Virtual → Mouse em outros aplicativos**, com consentimento e acessibilidade ativada nas configurações oficiais. **PARAR** encerra câmera e controle.

O certificado desta alpha.4 é diferente do APK alpha.3 publicado: o Android não aceita atualização direta entre eles. Exporte conversas em **Memória e histórico** antes de qualquer remoção. **Não remova a instalação atual enquanto a nova estiver bloqueada pelo Play Protect.** Desinstalar apaga dados privados, imagens e modelos; o JSON exporta textos, sem token ou imagens. Os pesos incluídos poderão ser preparados novamente sem internet.

## Compatibilidade e privacidade

Android **8.0/API 26 ou superior**. Interface e bibliotecas de câmera/OCR são empacotadas para ARM64, ARM32, x86_64 e x86. IA local requer processo de **64 bits**, pelo menos 3 GB de RAM total utilizável e 1 GB disponível, sem classificação Android de pouca memória. Esses limites não garantem velocidade em todos os aparelhos. Sem recursos suficientes, o app explica a limitação e permite um provedor opcional.

Câmera, microfone, memória disponível, CPU e gráficos são consultados no próprio aparelho para ajustar resolução, frequência, contexto e threads. Não são usados IMEI, número de série, contatos, localização ou conteúdo de outros aplicativos. Câmera e microfone exigem ações explícitas. A acessibilidade executa gestos autorizados; não lê janelas nem coleta eventos. Não há escuta permanente, reinício oculto ou compreensão contínua de vídeo.

Conversas e memórias autorizadas ficam em Room; preferências em DataStore. Token opcional fica protegido por Android Keystore. É possível exportar e apagar dados. Nenhuma conversa ou imagem é enviada a um servidor no modo local. O provedor HTTPS personalizado exige autorização separada, sem fallback remoto silencioso. [Servidor próprio opcional](server/README.md).

## Compilar no computador

Use Android Studio ou JDK 17, Android SDK 35, build-tools 35.0.0, NDK 28.2.13676358 e CMake 3.22.1. Defina `ANDROID_HOME` ou `sdk.dir` em `local.properties` (fora do Git). Gradle Wrapper 8.11.1; heap de compilação de 4 GB no computador/CI.

```bash
python3 scripts/fetch_llama_cpp.py
python3 scripts/fetch_hand_model.py
python3 scripts/fetch_ocr_models.py
python3 scripts/fetch_ocr_runtime.py
python3 scripts/fetch_bundled_model.py
./gradlew --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

No Windows, use `gradlew.bat`. APK: `app/build/outputs/apk/debug/app-debug.apk`. Os scripts usam fontes oficiais e tamanho/SHA-256 fixados. O modelo compacto fica em `.build-deps/bundled-assets`, fora do Git, e o Gradle verifica sua integridade antes de empacotar. Licenças incluídas no APK; veja [avisos de terceiros](THIRD_PARTY_NOTICES.md).

Para regressão do downloader antigo, `-PnexusBundleModel=false` compila uma variante de desenvolvimento sem os pesos. Ela não é o APK completo recomendado ao usuário. Os workflows de regressão usam essa opção; o workflow principal publica a versão completa. [Actions](https://github.com/Cazi1n8/nexus-android/actions) executa testes, lint e build. Um workflow dedicado verifica Chat → preparação do APK → resposta real → histórico, com Wi-Fi e dados do emulador desligados.

## Estado e testes

Compilação local e GitHub aprovadas; 129 testes JVM, 66 testes Python dos scripts e 44 do servidor opcional passaram. Lint: 0 erros, 42 avisos locais/43 no GitHub e 1 informação. Android comum: 15 aprovados, 0 falhas/erros, 3 casos opcionais ignorados e aprovados em execuções dedicadas. A prova do APK completo sem internet preparou a IA em 4,748 s, gerou “Azul.”, calculou 20 e leu “OLA NEXUS 2026” da imagem sintética no Chat; histórico Room e perfil após recriar a Activity foram conferidos. São tempos e resultados do emulador. APK público verificado. Não houve teste físico no Samsung Galaxy S9+ pelo ambiente. Não foi comprovada correção de todos os travamentos.

- [Relatório alpha.4](docs/RELATORIO_ALPHA_4.md)
- [Progresso por funcionalidade](PROGRESSO_NEXUS_2.md)
- [Todos os pedidos e pendências](docs/PEDIDOS_E_STATUS.md)
- [Permissões e segundo plano](docs/PERMISSOES_E_SEGUNDO_PLANO.md)
- [OCR local](docs/OCR_LOCAL.md)
- [Compatibilidade](docs/COMPATIBILIDADE.md)

Ainda faltam execução em segundo plano dos demais módulos, reconhecimento validado de letras/sinais de Libras, visão multimodal local geral, modelo de conversa mais capaz, resumo persistente de conversas, testes físicos, assinatura estável e preparação final para a Play Store. Esta é uma versão de teste em evolução.
