# NEXUS AI 3 — testes e avaliações

Atualizado em 26/10/2026. Este arquivo distingue teste de código, execução do modelo e verificação no celular. Um projeto que compila ou um teste de parser aprovado não comprova qualidade das respostas.

## Estado da avaliação Android

A bateria está implementada em `app/src/androidTest/java/br/com/nexus/assistant/Ai3EvaluationTest.kt`. **A execução desta revisão no emulador Android ainda está pendente.** Nenhum dos quinze casos abaixo está marcado como aprovado antes dessa execução e da revisão das respostas reais.

| Caso | O que será observado | Android desta revisão |
| --- | --- | --- |
| 1 | Calebe identificado como usuário | Pendente |
| 2 | NEXUS identificado como assistente | Pendente |
| 3 | Texto de desculpas para o usuário copiar, sem autodefesa da IA | Pendente |
| 4 | `25 vezes 18` resolvido pela calculadora real: 450 | Pendente |
| 5 | Explicação de IA para uma criança de dez anos | Pendente |
| 6 | História de Max seguida de pedido de final feliz | Pendente |
| 7 | Reescrita informal da mensagem anterior | Pendente |
| 8 | Proposta estruturada de configurações e envio do Intent oficial | Pendente |
| 9 | Não inventar acesso a câmera ativa | Pendente |
| 10 | Redação sobre tecnologia | Pendente |
| 11 | Vinte mensagens relacionadas, com lembrança de ORION e MARFIM | Pendente |
| 12 | Pedido de desculpas com erros de digitação | Pendente |
| 13 | Mudar Calebe para Ana sem mudar o nome NEXUS | Pendente |
| 14 | Reconhecer que não sabe o número pensado pelo usuário | Pendente |
| 15 | Vários parágrafos sem repetição ou corte por limite de saída | Pendente |

A bateria usa o Qwen2.5 1.5B Instruct Q4_K_M **incluído no APK**, instala e verifica os pesos pelo `LocalModelManager`, passa por `AiCoordinator`, `LocalAiProvider` e JNI, salva 37 respostas em Room e reabre o banco para verificar a persistência. Dessas respostas, 35 devem vir do modelo, uma da ferramenta de cálculo e uma do roteador de propostas autorizadas. As origens e durações são registradas separadamente; metadados antigos de geração não são atribuídos às ferramentas.

As perguntas e respostas são sintéticas e podem ser guardadas nos relatórios de CI. A ferramenta não registra conversas pessoais. O teste preserva configurações anteriores e utiliza um banco e diretório de modelos próprios. O executor exige autorização explícita e recusa celulares físicos antes de instalar APKs ou mudar a rede.

## Execução e evidências

O workflow `.github/workflows/ai3-evaluation.yml` executa automaticamente após alterações relevantes na `main` e também oferece execução manual. Usa um emulador descartável Android 10, sem acesso à câmera ou microfone reais, com Wi-Fi e dados desligados durante a avaliação. Ao terminar, tenta restaurar o estado original da rede e registra eventuais falhas dessa restauração.

O artefato `nexus-ai3-evaluation-<número>` contém:

- `ai3-evaluation.json`: perguntas, respostas completas, tempos, origem e verificações observadas;
- `TESTES_NEXUS_AI.md`: relatório gerado a partir das respostas dessa execução;
- `semantic-summary.json`: casos executados e verificações não atendidas;
- `instrumentation.txt`, `junit.xml` e `network-restoration.json`: execução técnica e restauração da rede.

O workflow falha se ocorrer erro técnico, falta de persistência, modelo divergente ou falha nas verificações críticas dos casos 1, 2, 3, 12 e 13. Respostas incorretas continuam nos arquivos; não são substituídas por exemplos. Verificações de palavras e formato são heurísticas e **não certificam coerência, veracidade, português natural ou inteligência geral**. Os demais resultados também precisam de revisão, mesmo quando a execução técnica termina sem erros.

Para repetir a bateria em um emulador descartável configurado, prepare as dependências documentadas no README, compile o APK e os testes e execute:

```bash
python3 scripts/fetch_bundled_model.py --with-balanced
./gradlew --no-daemon --max-workers=2 assembleDebug assembleDebugAndroidTest
python3 scripts/run_android_ai3_evaluation.py --allow-reset-emulator --require-critical-checks
```

O teste usa orçamento explícito de 2.048 tokens e uma thread para tornar sua configuração rastreável. Ele não comprova a seleção automática de perfil, desempenho ou disponibilidade de memória no Galaxy S9+. A tela de confirmação de ações não é exercitada pelo caso 8; o opt-in da CI autoriza apenas a ação sintética de abrir configurações.

## Testes do executor realizados nesta revisão

Executado: `python3 -m unittest discover -s scripts/tests -p 'test_run_android_ai3_evaluation.py' -v`.

Resultado real: **10 testes aprovados**. Eles verificam validação de evidências, preservação de respostas incorretas, recusa de dispositivos físicos, necessidade de opt-in, restauração de rede, rejeição de relatórios antigos/incompletos e falha do gate crítico. Usam fixtures e ADB simulado; **não executam um modelo e não contam como aprovação dos quinze casos**.

Também foi executado `python3 -m unittest discover -s scripts/tests -v`: **113 testes aprovados** na árvore de trabalho desta revisão. Esse conjunto inclui os dez testes acima e a preparação verificada de dependências, modelos e assinatura. São testes das ferramentas de desenvolvimento, não provas de qualidade da IA ou de aprovação do Play Protect.

Os sete workflows YAML foram carregados por PyYAML e tiveram a estrutura de jobs conferida. Isso é uma verificação sintática local, não uma execução do GitHub Actions.

## Comparação no computador e limites restantes

Há uma avaliação separada em `scripts/run_native_quality_eval.py`, que compila a construção de contexto Kotlin do aplicativo e usa o mesmo runtime C++/JNI com pesos GGUF reais em Linux. Seus resultados precisam ser identificados pela revisão do prompt e do código; resultados de revisões anteriores não validam alterações posteriores. A avaliação final desta revisão será incorporada após sua conclusão e revisão das respostas.

As primeiras comparações reais detectaram falhas tanto no modelo compacto quanto no de 1,5B, incluindo troca de identidade, recusa indevida e interpretação errada de pedidos de escrita. Por isso, a simples troca do modelo não é apresentada como correção comprovada. Não foi usado filtro para apagar frases repetidas e esconder essas falhas.

Voz brasileira, reconhecimento de mãos, Libras, operação fora do aplicativo, câmera em uso por outro app e Play Protect exigem provas adicionais no dispositivo. A avaliação de texto no emulador não aprova esses recursos. Consulte também `TESTES_SEGUNDO_PLANO.md` e `AUDITORIA_PLAY_PROTECT.md`.
