# NEXUS 2.0.0-alpha.4 — IA incluída e correções dos relatos

Atualizado em **25/10/2026 (UTC)**. Código de versão Android: **23**.

## Estado desta revisão

**Compilação local aprovada; provas Android no GitHub e publicação ainda pendentes.** Esta versão altera o pacote padrão para incluir a IA compacta, em vez de pedir um segundo download de modelo no telefone. Também trata pontos concretos de matemática, leitura de texto em imagens, prévia da câmera, sessão global, treino preparatório e diagnóstico.

O build local completo terminou com sucesso em 2 min 35 s; uma rodada posterior de compilação AndroidTest/lint terminou em 26 s. Passaram 129 testes JVM e 66 testes Python dos scripts. O APK local foi inspecionado; os resultados Android no GitHub e a entrega pública ainda precisam ser registrados após execução efetiva. **As provas da [alpha.3](RELATORIO_ALPHA_3.md) não certificam automaticamente a alpha.4.** O Samsung Galaxy S9+ do usuário não foi testado fisicamente pelo ambiente.

O usuário confirmou que conseguiu concluir o download anterior, mas relatou bloqueio de instalação pelo Play Protect e falhas de uso. Esses relatos permanecem critérios de validação; não são descartados pelos testes emulados.

## Mudanças implementadas e limites

| Área | Alteração no código | O que ainda precisa ser comprovado |
| --- | --- | --- |
| IA dentro do APK | O build padrão inclui o Qwen compacto como asset não comprimido, preparado previamente no computador/CI com tamanho e SHA-256 fixados. Os pesos ficam fora do Git, com licença/atribuição incluídas no pacote. | Inspecionar o APK final e conferir o GGUF, sua licença e integridade. Registrar tamanho real do pacote. |
| Primeiro Chat | A abertura do Chat inicia a preparação local do asset, com cópia privada, progresso, cancelamento/retomada e verificação antes de `READY`. Não precisa buscar um GGUF pela rede no telefone. | Prova própria pela interface → ViewModel → cópia/verificação → inferência → Room, sem fixture de modelo transferida por ADB e sem internet. |
| Adaptação | O modelo base compacto permanece estável entre sessões; contexto, threads, saída e análise de câmera têm orçamento baseado nos recursos. Arquivos parciais entram no cálculo do espaço restante necessário. | Dispositivos de 32 bits, pouca RAM ou Android abaixo do mínimo não ganham suporte à IA local. Testar o perfil e a estabilidade no aparelho real. |
| Modo de IA | A escolha visível de local/provedor personalizado passa a persistir o modo utilizado pelo Chat. | Regressão da troca de modo, token de sessão e preferências após recriar a atividade. Não há fallback remoto silencioso. |
| Matemática | `ArithmeticTool` resolve expressões explícitas com números, parênteses, potência inteira, decimais, precedência e limites, antes de pedir uma resposta ao modelo. `10+10÷10×10` tem resultado `20`. | Registrar testes da ferramenta e da integração. A correção não certifica raciocínio geral, programação ou precisão de todas as respostas do Qwen pequeno. |
| Imagem no Chat local | `LocalImageTextProvider` usa OCR local de português/inglês para transcrever texto de anexos e, quando solicitado, levar uma parte delimitada dessa transcrição ao modelo de texto. A resposta identifica a origem por OCR e sua limitação. | Testar anexo real no aparelho. Não há reconhecimento local geral de objetos, cenas, circuitos ou vídeo; imagens sem texto legível recebem uma limitação explícita. |
| Prévia da câmera | Mouse interno permite ocultar a prévia; o mouse global usa análise sem janela de vídeo. Libras também permite ocultar a imagem. | Ocultar vídeo não desliga a câmera nem implementa segundo plano para outras telas. Indicadores de privacidade, notificação e parada continuam visíveis. |
| Mouse global | Serviço iniciado explicitamente preserva a sessão ao remover a tarefa dos recentes, com notificação persistente e parada de emergência. Não reinicia ocultamente após ser morto. | Faltam cliques/rolagem reais em outros apps no S9+. Android, fabricante, bloqueio, câmera ocupada, falta de quadros e permissões podem interromper a sessão. |
| Travamentos | Rastreamento entrega o quadro recente à UI sem acumular resultados antigos; compatibilidade do serviço é lida fora da thread principal. | São correções de pontos específicos. Não há medição física que comprove eliminação de todos os travamentos, consumo de bateria ou aquecimento. |
| Libras: continuidade | Registrar acerto não termina a sessão de treino; a referência da atividade aparece no canto. Há opção de ocultar vídeo e modo livre com configuração observada/recusa de avaliação. | O módulo reconhece somente duas configurações geométricas preparatórias, **não letras ou sinais de Libras**. Faltam classificador/conteúdo validado, testes físicos e execução do treino fora da atividade. |
| Diagnóstico e privacidade | Tela inicial mostra versão, compatibilidade, permissões e estados. Hardware fica atrás de uma ação explícita; modelo é o informado pelo Android e RAM é rotulada como leitura do sistema, não ficha técnica comercial. Consultas atualizadas ocorrem em executor de IO. | Nome pode ser código do fabricante/ROM. O app não infere que um telefone é S9+ por causa da conversa, não consulta IMEI/série/publicidade e não envia o diagnóstico. |
| Play Protect | Manifesto e capacidades sensíveis revisados; limites e distribuição documentados. Acessibilidade permanece declarada para os gestos globais autorizados, sem leitura da árvore de telas. | **O bloqueio relatado não está comprovadamente resolvido e não houve aprovação do Google.** Não foram ocultados serviços/avisos nem sugerido desativar a proteção. |

Fontes: [preparação do modelo](../scripts/fetch_bundled_model.py), [gerenciador](../app/src/main/java/br/com/nexus/assistant/data/local/LocalModelManager.kt), [ViewModel](../app/src/main/java/br/com/nexus/assistant/NexusViewModel.kt), [cálculo](../app/src/main/java/br/com/nexus/assistant/data/ArithmeticTool.kt), [OCR no Chat](../app/src/main/java/br/com/nexus/assistant/data/LocalImageTextProvider.kt), [Libras](../app/src/main/java/br/com/nexus/assistant/libras/NexusLibrasScreen.kt) e [diagnóstico](../app/src/main/java/br/com/nexus/assistant/diagnostics/NexusDiagnostics.kt).

## Modelo incluído

| Item | Valor fixado |
| --- | --- |
| Modelo de terceiro | Qwen2.5-0.5B-Instruct, quantização Q4_K_M |
| Arquivo no APK | `models/qwen2.5-0.5b-instruct-q4_k_m.gguf` |
| Tamanho do GGUF | 491.400.032 bytes |
| SHA-256 | `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db` |
| Revisão upstream | `9217f5db79a29953eb74d5343926648285ec7e67` |
| Licença | Apache 2.0; cópia em `assets/licenses/Qwen2.5-0.5B-Apache-2.0.txt` e atribuição em `THIRD_PARTY_NOTICES.md` |

A inclusão aumenta o APK em aproximadamente o tamanho do modelo. No primeiro preparo, o app também precisa de espaço para sua cópia privada; o asset permanece no APK. Portanto, o espaço instalado é maior que o tamanho de download do APK. O modelo não está embutido em arquivos de código nem é tratado como criação ou treinamento exclusivo do NEXUS.

O APK único mantém os requisitos de Android 8/API 26 ou superior. Inferência local exige processo Android de 64 bits, RAM e armazenamento adequados. A interface e recursos compatíveis podem funcionar sem a inferência local; provedor personalizado é opcional e depende de consentimento/configuração explícitos.

## Verificação da revisão

Preencher somente com resultados efetivamente observados, vinculando a revisão testada e a evidência correspondente.

| Verificação alpha.4 | Resultado desta revisão |
| --- | --- |
| Scripts Python | **66 aprovados**, conforme execução local consolidada pelo agente principal |
| Servidor opcional | Pendente de consolidação; não certifica o modo local |
| Compilação local/GitHub | **Build local aprovado em 2 min 35 s**; rodada posterior de compilação AndroidTest/lint aprovada em **26 s**. GitHub pendente |
| Testes JVM, incluindo asset/cálculo/OCR | **129 aprovados, 0 falhas/erros e 0 ignorados** na execução local consolidada |
| Lint | **0 erros, 42 avisos e 1 informação** na execução local |
| Suíte comum Android | Pendente de consolidação; separar aprovados, falhas e casos opt-in ignorados |
| Primeiro Chat com IA do APK e rede desligada | Pendente de consolidação; confirmar preparo pelo ViewModel, `READY`, resposta real e persistência |
| APK local: assinatura, empacotamento e GGUF | **615.068.956 bytes**, assinatura v2, alinhamento ZIP 16 KB, quatro ABIs e 40 bibliotecas ELF. GGUF incluído com tamanho/SHA-256 fixados e hashes OCR conferidos |
| Download público do APK | Pendente de publicação e verificação da URL/bytes/SHA-256 reais |
| Testes físicos no S9+ | Não executados pelo ambiente |
| Instalação aceita pelo Play Protect | Não comprovada; bloqueio relatado pelo usuário continua aberto |

APK local: SHA-256 `4abba0fcf125548e40b8313c21f99541078da97e9369aa5a3f13f9e3b2e5903c`. Esse hash pertence ao arquivo local; o APK publicado precisará de comparação própria.

Nenhum número de uma execução anterior deve substituir esses campos. Testes geométricos com landmarks sintéticos não comprovam mãos reais ou letras de Libras. Um teste de OCR não comprova compreensão multimodal geral. Inicializar JNI ou MediaPipe não aprova câmera, voz, gestos ou desempenho físicos.

## Reproduzir o build padrão

```bash
python3 scripts/fetch_llama_cpp.py
python3 scripts/fetch_hand_model.py
python3 scripts/fetch_ocr_models.py
python3 scripts/fetch_ocr_runtime.py
python3 scripts/fetch_bundled_model.py
python3 -m unittest discover -s scripts/tests -v
./gradlew --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Os downloads de dependências/modelo acima acontecem no computador ou CI. A primeira abertura do Chat prepara o asset já incluído no APK; não corresponde a outra transferência de GGUF no telefone. Para builds de regressão sem o modelo incluído, o parâmetro de desenvolvimento é `-PnexusBundleModel=false`; esse pacote não deve ser confundido com a entrega padrão pedida pelo usuário.

A prova dedicada do Chat incluído é opt-in e usa o workflow [bundled-chat-smoke.yml](../.github/workflows/bundled-chat-smoke.yml). O runner deve restringir limpeza de dados e alteração temporária de conectividade ao emulador autorizado, restaurar conectividade ao encerrar e recolher as observações/JUnit. Não transfere um GGUF separado por ADB para satisfazer a prova.

## Segundo plano e interrupção

[Permissões e segundo plano](PERMISSOES_E_SEGUNDO_PLANO.md) descreve a implementação e os limites.

O mouse global possui serviço de câmera em primeiro plano. **Mãos livres, Libras, 3D, Vision e voz ainda não implementam toda a continuidade fora do app pedida pelo usuário.** Transferir essas sessões para serviços exige trabalho próprio de ciclo de vida, estado, consentimento, notificações, concorrência de câmera e limites do Android. Prévia oculta não equivale a serviço de segundo plano.

“Não parar em momento algum” não pode ser garantido: Android/fabricante podem encerrar processos, negar câmera/microfone ou limitar execução. A ação de parada explícita do usuário sempre precisa estar disponível; falhas, revogação e ausência persistente de quadros devem encerrar ações inseguras ou sem resposta.

## Distribuição e dados

Ainda é uma versão de teste. Assinatura de lançamento estável, revisão para Google Play e tratamento oficial do bloqueio Play Protect permanecem pendentes. O aviso apresentado não identifica sozinho a causa exata nem permite afirmar que uma alteração específica será aprovada.

As versões anteriores publicadas usaram certificados de depuração diferentes. Não há promessa de atualização direta preservando dados até conferir o certificado do APK final. Desinstalar remove dados privados; exporte conversas importantes antes de qualquer remoção. A exportação de texto não contém imagens nem token.

## Pendências do pedido completo

A [matriz dos pedidos](PEDIDOS_E_STATUS.md) preserva o estado de implementação e validação de cada recurso. Permanecem em aberto, entre outros: instalação aprovada no aparelho, estabilidade e sensores físicos, todos os modos em segundo plano, alfabeto/sinais de Libras validados, compreensão multimodal local geral, contexto extenso com resumo persistente e distribuição estável pela Google Play.
