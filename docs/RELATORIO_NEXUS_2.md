# Relatório verificável do NEXUS 2.0

Registro de **24/10/2026 (UTC)**, versão de teste **2.0.0-alpha.1**. Este relatório separa provas executadas no computador, compilação Android e testes de uso físico. O estado mais recente da entrega fica em [PROGRESSO_NEXUS_2.md](../PROGRESSO_NEXUS_2.md).

## Projeto e escopo

O código existente foi auditado antes das mudanças. Preservam-se o pacote `br.com.nexus.assistant`, o schema Room v1, histórico, memórias autorizadas, Keystore e arquitetura Kotlin/Compose. A evolução adiciona runtime local, dois perfis de modelo, voz filtrada por idioma/país, propostas de ações com confirmação, mouse persistente/global, controladores separados, OpenGL ES 2, exercícios preparatórios e diagnóstico.

O ajuste por aparelho foi implementado e compilado: observações de recursos, política conservadora, alternativa manual, redução de processamento e indicação de funções indisponíveis. Um APK configurado para várias ABIs não garante que todos os recursos existam em todo aparelho.

## Testes com modelo real pela ponte JNI

Foram executados os mesmos arquivos da ponte C++/JNI usados no projeto, vinculados à distribuição Linux oficial correspondente de llama.cpp b11838. O processo Java carregou a biblioteca e os GGUFs reais. **Não foi um teste dentro de um APK ou celular.**

Comandos executados na raiz do projeto, com caminhos do ambiente de desenvolvimento:

```bash
JAVA_HOME=/workspace/tooling/jdk sh app/src/main/cpp/tests/run_host.sh /workspace/tooling/ai-smoke/qwen2.5-0.5b-instruct-q4_k_m.gguf
JAVA_HOME=/workspace/tooling/jdk sh app/src/main/cpp/tests/run_host.sh /workspace/tooling/ai-smoke/qwen2.5-1.5b-instruct-q4_k_m.gguf
```

Logs: `/workspace/tooling/nexus-jni-host.log` e `/workspace/tooling/nexus-jni-host-15.log`. Ambos terminam com `jni_real_model_smoke=passed`.

| Caso observado | 0.5B | 1.5B |
| --- | --- | --- |
| Cálculo 17 + 25 | `42` | `42` |
| Pergunta sobre a cor do cubo no contexto | `Ciano.` | `Ciano.` |
| Acentos e caractere suplementar | `Olá, coração! 🚀` | `Olá, coração! 🚀` |
| JSON de histórico inválido | Recusado | Recusado |
| Limite inválido de tokens | Recusado | Recusado |
| Cancelamento de inferência | `CancellationException` | `CancellationException` |
| Nova solicitação após cancelamento | Resposta produzida | Resposta produzida |
| Fechamento concorrente durante inferência | Encerrado com cancelamento | Encerrado com cancelamento |
| Reutilização de handle fechado | Recusada | Recusada |

O registro inicial de contexto no 0.5B apresentou uma recusa sem fundamento, dizendo não entender português brasileiro. A resposta posterior recuperou a cor corretamente. Esse erro é uma limitação observada, não deve ser ocultado por uma resposta fixa.

Os tempos desses logs pertencem a um computador compartilhado com tarefas de compilação e não formam benchmark controlado. Não permitem prever tokens por segundo, consumo de RAM, temperatura ou bateria do Galaxy S9+.

## Observações qualitativas, sem certificação de qualidade

Também foi executado `run_host.sh <modelo.gguf> --quality`, com quatro inferências por modelo, contexto de 1.024 tokens, duas threads e limite de 192 tokens de saída. Os logs `nexus-jni-quality-05.log` e `nexus-jni-quality-15.log` terminam com `quality_result=observations_only_no_semantic_assertions`.

Essa prova utilizou o prompt anterior que continha um exemplo de ação em toda conversa. A integração posterior restringe essa instrução a pedidos expressos de ação; a prova anterior não valida automaticamente o novo prompt.

| Solicitação | Observação 0.5B | Observação 1.5B |
| --- | --- | --- |
| Explicar fotossíntese em duas frases | Afirmou incorretamente que humanos participam desse processo para produzir energia e oxigênio. | Descreveu plantas, algas e algumas bactérias convertendo luz para produzir açúcar; produziu uma frase, sem cumprir a quantidade pedida. |
| Função Kotlin que soma dois inteiros | Exemplo simples de soma. | Exemplo simples de soma, com explicação. |
| Registrar cubo ciano no contexto | Inventou proposta `open_settings` com argumento não permitido. O validador do aplicativo deve rejeitá-la; nenhuma ação é executada por texto. | Respondeu reconhecendo o contexto, sem propor ação. |
| Perguntar a cor | `Ciano.` | `Ciano.` |

Essas poucas observações justificam limites explícitos e a opção de um modelo maior quando os recursos permitem. Não demonstram fluência geral, precisão escolar, raciocínio amplo ou confiabilidade em outros assuntos. O modelo continua sujeito a erros.

## Compilação Android e testes automatizados

| Verificação | Evidência disponível nesta atualização |
| --- | --- |
| Runtime nativo ARM64 e x86_64 | `externalNativeBuildDebug`: **BUILD SUCCESSFUL in 3m 47s**. Compilação não comprova inferência Android. |
| Otimização dos kernels | Flags geradas conferidas com `-O3`; ARM64 configurado para base ARMv8-A, sem exigir dotprod/i8mm/SVE. |
| Preparação segura das fontes | **5 testes Python aprovados**, cobrindo checksum, extração e preservação de diretório existente. |
| Servidor opcional | `python3 -m unittest discover -s server/tests -v`: **44 aprovados em 17,286 s**, sem ignorados. Upstreams controlados; não avalia qualidade de IA. |
| Primeira compilação Kotlin integrada, testes JVM e lint | **BUILD SUCCESSFUL in 2m 5s**; **76 testes JVM aprovados**, zero falhas/ignorados; lint com **zero erros, 37 avisos e uma informação**. Resultado anterior à atualização MediaPipe 0.10.35. |
| Primeiro APK universal e APK de instrumentação 2.0 | Gerados; o APK foi inspecionado com quatro ABIs. Tamanho e hash da revisão anterior não identificam a nova revisão definitiva. |
| Dependência nativa atualizada | MediaPipe 0.10.35 compilado no APK final: **BUILD SUCCESSFUL in 2m 42s**, 76 testes JVM sem falhas/erros/ignorados, lint com 0 erros, 37 avisos e 1 informação. JNI de tarefas presente nas quatro ABIs. |
| Instrumentação Android | **8 aprovados, 0 falhas/erros/ignorados** no Android 10/API 29 x86_64; `connectedDebugAndroidTest` em 47s. XML preservado em [android10-junit.xml](evidencias/android10-junit.xml). |
| Uso físico no S9+ | Não executado pelo ambiente. |

Comando de validação integrada:

```bash
./gradlew --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

O primeiro build integrado desta evolução encontrou conflito entre setters JVM e métodos públicos da sessão de gestos. O código foi corrigido e a compilação integrada posterior passou. A validação final com MediaPipe 0.10.35 também passou, conforme a tabela.

O emulador local anterior não pôde completar a inicialização por restrição de escrita do ADB. O workflow no GitHub usa um executor temporário com KVM; configuração do workflow e compilação dos testes não equivalem a execução aprovada. A versão atual do MediaPipe possui JNI x86_64. Mesmo assim, o workflow usa câmeras desativadas e os testes existentes não certificam rastreamento de mãos ou câmera física.

A primeira execução real no emulador Android 10 executou oito testes: sete passaram, incluindo inicialização do JNI e processamento de um quadro preto pelo modelo de mãos. Um teste de interface falhou porque seu seletor encontrou três interruptores. Os controles receberam rótulos individuais de acessibilidade e o teste passou a selecionar o rótulo específico. A segunda execução passou nos oito testes, sem falhas, erros ou ignorados, após essa correção. Esse processamento sem câmera não certifica reconhecimento de mãos reais.

## APK e publicação

O APK local `NEXUS-2.0.0-alpha.1-local.apk` tem **101.515.753 bytes**, assinatura v2 válida e SHA-256 `b1d2cbf860647fe6fc324b4660fae29deea0d42df24e1813304768dfa7976588`. A verificação `zipalign -c -P 16 4` passou; `llvm-readelf` confirmou segmentos LOAD alinhados a pelo menos 16 KB nas 24 bibliotecas empacotadas. Não há pesos GGUF no APK.

A revisão com rótulos individuais de acessibilidade foi publicada em `f95cf02e78b82411ec233d78df51b1344a1a2e69`; recompilou localmente em **1m 47s**, mantendo os 76 testes aprovados. O build remoto dessa revisão passou em **2m 38s**, e os oito testes do emulador passaram em **47s**. O APK do GitHub possui certificado de depuração e hash próprios, identificados na seção seguinte. Arquivos locais do ambiente não são links públicos para o celular.

### Arquivo distribuído pelo GitHub

- [APK direto](https://github.com/Cazi1n8/nexus-android/releases/download/v2.0.0-alpha.1-build-5/NEXUS-2.0.0-alpha.1.apk) e [release](https://github.com/Cazi1n8/nexus-android/releases/tag/v2.0.0-alpha.1-build-5).
- Tamanho: **91.496.785 bytes**; SHA-256: `a51ea58489c42139b80aae259ee95c6bc0d6b3fae3d37f06731c91ec9bdb570b`.
- Hash confrontado com o digest do asset do GitHub e com `SHA256SUMS.txt`; assinatura v2 válida.
- Pacote API mínimo 26, alvo 35, versão 20/2.0.0-alpha.1; quatro ABIs, 24 bibliotecas com ELF de 16 KB e ZIP alinhado; sem GGUF embutido.
- Revisão: `f95cf02e78b82411ec233d78df51b1344a1a2e69`; [build/publicação aprovados](https://github.com/Cazi1n8/nexus-android/actions/runs/40053992143).

## Testes de dispositivo ainda necessários

- Instalar e abrir a versão correta, conferir Android/ABI/RAM e recomendação automática, fonte grande e tela pequena.
- Autorizar o download escolhido, cancelar/repetir, verificar integridade, reiniciar e conversar offline; conferir que o modo local não envia conteúdo ao provedor remoto.
- Testar pt-BR, silêncio, interrupção, voz ausente, fala contínua em primeiro plano e parada ao sair do app.
- Usar o indicador para tocar um botão real, navegar com mouse ativo, rolar vertical/horizontalmente, arrastar, soltar e perder a mão sem toque preso.
- Ativar controle global conscientemente, conferir câmera/notificação/overlay e parar por ambos os controles; testar recusa de permissões e câmera ocupada.
- Selecionar cubo com pinça, rejeitar pinça fora do objeto, usar duas mãos e liberar sem transferir o controle indevidamente.
- Testar exercícios preparatórios com mãos incompletas ou ambíguas e verificar progresso/exportação/exclusão, sem apresentar aprovação de um sinal completo de Libras.
- Medir travamentos, latência, RAM, aquecimento e bateria em aparelhos de capacidades diferentes antes de afirmar otimização comprovada.
