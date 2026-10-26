# NEXUS para Android

Projeto Android existente em Kotlin e Jetpack Compose, em português brasileiro, com interface preta, azul e ciano. Código e histórico: https://github.com/Cazi1n8/nexus-android.

**Etapa atual: NEXUS AI 3.0, alpha.1 (versionCode 24), em validação.** Esta etapa preserva Mouse Virtual, Mãos Livres, 3D, Libras, histórico e configurações. Veja [progresso atual](PROGRESSO_NEXUS_AI_3.md), [avaliações da IA](TESTES_NEXUS_AI.md), [testes de segundo plano](TESTES_SEGUNDO_PLANO.md) e [auditoria de segurança](AUDITORIA_PLAY_PROTECT.md). Compilar e passar testes unitários não comprova a qualidade das respostas nem a instalação no seu celular.

## Mudanças em validação

- Identidade fixa **NEXUS**, separada de nome preferido do usuário, personalidade, instruções e preferências. Migração das configurações existentes.
- Contexto por orçamento de tokens, com contagem real no runtime e remoção de turnos antigos completos. Sob pressão, trechos literais identificados preservam referências; não há limite fixo de quatro mensagens ou resumo inventado.
- Geração local real com parâmetros de amostragem e finalização explícita. Nenhum rodapé programado é acrescentado às respostas. Falhas por resposta vazia, marcadores internos ou repetição excessiva geram erro técnico, sem resposta falsa.
- Histórico local com busca, renomeação, continuação, exclusão e novo contexto. Markdown, blocos de código, copiar, ouvir e interromper no chat.
- Calculadora local: `10+10÷10×10 = 20`; `25 vezes 18 = 450`. Perguntas gerais continuam no modelo de linguagem, que pode errar.
- Imagens: OCR real e classificação local de objetos com EfficientNet-Lite0. Categorias são hipóteses; isso não equivale à interpretação geral de imagens por um modelo multimodal. [Visão local ao vivo](docs/VISAO_LOCAL_AO_VIVO.md) avalia quadros periódicos, com intervalo e prévia opcionais.
- Sessões independentes de mouse global e voz fora do aplicativo, iniciadas pelo usuário, com notificações e botões de parada. Compartilham a IA, sem carregar outro modelo de conversa. **Os cenários físicos de segundo plano ainda precisam de validação.**
- Voz do Android em pt-BR, correção da primeira resposta silenciosa e seleção de idioma quando o mecanismo não enumera vozes. O serviço de fala pode utilizar rede.
- Mouse com suavização por tempo e cadência adaptativa. Libras com tolerância corrigida ao intervalo entre quadros e candidatas experimentais L/V/Y; não reconhece nem certifica o alfabeto inteiro.
- MediaPipe derivado de forma reproduzível para usar a fábrica de métricas no-op prevista no código oficial. Não altera os binários nativos; fontes, hashes e limites constam na auditoria.

A qualidade de um modelo pequeno não equivale à de um serviço avançado. Os modelos reais estão sendo avaliados com os pedidos relatados pelo usuário; resultados incorretos permanecem registrados como falha. Não há backend NEXUS público implantado, API privada dentro do APK ou fallback remoto escondido. O [servidor opcional](server/README.md) usa um modelo real configurado pelo responsável.

## Usar e testar no Android

Android **8.0/API 26 ou superior**. Compatibilidade é verificada por recurso; não existe garantia de todos os recursos em qualquer celular. IA local exige processo ARM64/x86_64, memória e espaço suficientes. A recomendação usa recursos medidos, sem lista fixa de modelos de celular. Em aparelhos compatíveis, o app escolhe o perfil de maior capacidade que cabe na margem disponível; o perfil compacto tem qualidade mais limitada.

1. Em **Personalidade**, configure o nome do usuário. Pergunte no Chat “Qual é meu nome?” e “Qual é seu nome?”. Confira ambos separadamente.
2. Abra **Chat IA** e aguarde a cópia/verificação da IA incluída. Não é outro download. Teste texto, cálculo e anexo.
3. Em **Assistente de voz**, teste o áudio e conceda microfone somente ao ativar a escuta. Para sair da tela, use **Voz fora do aplicativo → Iniciar sessão de voz**.
4. Em **Central de controle**, ative mouse e voz separadamente. O mouse global precisa do consentimento e do serviço de acessibilidade oficial. As notificações permitem parar uma sessão ou todas.
5. Em **NEXUS Vision**, inicie a classificação ao vivo; o intervalo representa quadros analisados, não compreensão contínua de vídeo.

A conversa de voz opcional alterna escuta, resposta e fala por até **15 minutos ou 30 perguntas**, com no máximo duas tentativas após silêncio. O modo básico espera novo toque em OUVIR. **Não há detector permanente de “Ei, NEXUS”.** Android/fabricante pode interromper câmera, microfone ou processo por restrições de bateria, memória ou permissões. Vision, Libras, Mãos Livres e 3D usam câmera vinculada à tela; não são apresentados como serviços globais.

A versão completa com os dois perfis incluídos é grande, aproximadamente 1,7 GB antes da medição final. Reserve pelo menos **6 GB livres** para download, instalação e cópia selecionada; atualizações podem precisar de mais espaço. Não há download de um APK diferente por modelo de telefone.

## Play Protect e distribuição

O usuário relatou bloqueio da alpha.4 pelo Play Protect. A mensagem não prova qual permissão ou comportamento causou a decisão. A auditoria distingue problemas confirmados de hipóteses; não houve aprovação do Google ou teste de instalação física desta nova etapa.

A publicação automática de novos APKs debug nas Releases continua pausada. O Actions mantém artefatos de teste, que exigem login no GitHub. A [assinatura de distribuição](docs/ASSINATURA_E_DISTRIBUICAO.md) usa a chave privada estável do responsável, configurada em Secrets; essa chave definitiva ainda não foi fornecida. Builds release sem esses Secrets permanecem **sem assinatura e não instaláveis**. Não foi criada uma nova chave de produção para substituir silenciosamente a anterior.

Mantenha o Play Protect ativado. Se a instalação for bloqueada, registre versão, hash, certificado e mensagem para [revisão oficial](docs/PLAY_PROTECT_REVISAO.md). Não remova a instalação atual antes de exportar os dados e conferir a possibilidade de atualização: certificados diferentes impedem atualização direta. O APK histórico alpha.4 está no [relatório correspondente](docs/RELATORIO_ALPHA_4.md); ele não contém as correções 3.0.

## Compilar no computador

JDK 17, Android SDK 35, build-tools 35.0.0, NDK 28.2.13676358 e CMake 3.22.1. Defina `ANDROID_HOME` ou `sdk.dir` em `local.properties` (fora do Git). Gradle Wrapper 8.11.1; reserve 4 GB de heap para compilação.

```bash
python3 scripts/fetch_llama_cpp.py
python3 scripts/fetch_hand_model.py
python3 scripts/prepare_mediapipe_runtime.py
python3 scripts/fetch_object_model.py --with-test-fixture
python3 scripts/fetch_ocr_models.py
python3 scripts/fetch_ocr_runtime.py
python3 scripts/fetch_bundled_model.py --with-balanced
./gradlew --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

No Windows, use `gradlew.bat`. APK de teste: `app/build/outputs/apk/debug/app-debug.apk`. Os arquivos grandes ficam fora do Git; os scripts verificam fontes oficiais, tamanho e SHA-256 e o Gradle verifica ambos os GGUFs e AARs antes de empacotar. [Licenças de terceiros](THIRD_PARTY_NOTICES.md).

Para testes de infraestrutura sem pesos, use `-PnexusBundleModel=false`; essa variante não oferece IA incluída sem preparação. `assembleRelease` sem assinatura serve para análise estática. A distribuição assinada exige o procedimento documentado e teste real do APK resultante.

## Privacidade e verificações

Conversas/memórias autorizadas ficam em Room; preferências em DataStore. Token opcional fica protegido pelo Android Keystore. Diagnóstico técnico é local e não contém mensagens, fotos, nomes, caminhos ou chaves. Detalhes do aparelho só aparecem por escolha explícita. Não são usados IMEI, número de série, contatos, SMS ou leitura de janelas de outros aplicativos. O serviço de acessibilidade executa gestos autorizados; câmera/microfone são opcionais e visíveis.

Nesta retomada, **113 testes Python dos scripts e 44 do servidor passaram**. Os resultados completos do novo build, testes JVM, emulador e avaliações de linguagem são atualizados no [progresso 3.0](PROGRESSO_NEXUS_AI_3.md). Resultados da alpha.4 permanecem no relatório histórico e não são reutilizados como aprovação da nova versão.
