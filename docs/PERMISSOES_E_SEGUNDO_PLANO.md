# Permissões, Play Protect e sessões fora do app

Estado da implementação 3.0 em 26/10/2026. Código compilado não comprova funcionamento físico no Samsung Galaxy S9+. Os [13 testes funcionais de segundo plano](../TESTES_SEGUNDO_PLANO.md) e os cenários de segurança permanecem separados.

## Play Protect e distribuição

O usuário relatou novo bloqueio da alpha.4 pelo Play Protect. A mensagem não informa qual declaração ou comportamento motivou a decisão; a causa específica permanece desconhecida. A [auditoria atual](../AUDITORIA_PLAY_PROTECT.md) distingue achados confirmados, hipóteses e limites. Não houve aprovação do Google ou teste de instalação com Play Protect deste novo APK no aparelho físico.

A assinatura estável foi preparada por configuração de ambiente e workflow manual, mas depende da chave definitiva protegida do responsável. O build release sem essas credenciais fica sem assinatura e não é instalável. APKs debug de ambientes diferentes podem ter certificados diferentes e impedir atualização direta. [Assinatura e distribuição](ASSINATURA_E_DISTRIBUICAO.md).

Mantenha o Play Protect ativado. Se continuar bloqueado, registre o arquivo exato, versão, SHA-256, certificado e mensagem para a [revisão oficial](PLAY_PROTECT_REVISAO.md). Nenhum pedido de revisão foi enviado pelo ambiente. Não houve ocultação de serviços, troca de identidade para evasão ou remoção das divulgações sensíveis.

## Permissões por recurso

| Recurso | Permissões/autorizações | Momento e limite |
| --- | --- | --- |
| Chat local e dados | Não exige câmera, microfone ou acessibilidade | Modelos incluídos são copiados/verificados no armazenamento privado; chat não ativa sensores |
| IA remota opcional | `INTERNET`, `ACCESS_NETWORK_STATE` e consentimento de envio | Exige endpoint real configurado; não há fallback remoto silencioso |
| Foto, Vision, mãos e Libras | `CAMERA` quando a câmera é iniciada | Galeria e OCR podem funcionar sem autorizar câmera |
| Voz | `RECORD_AUDIO` ao iniciar escuta | Reconhecimento/TTS Android podem usar rede conforme o mecanismo instalado |
| Mouse global | Câmera, notificações e acessibilidade habilitada pelo usuário | Consentimento explícito; FGS do tipo `camera` e gestos oficiais |
| Voz fora do app | Microfone e notificações | Consentimento explícito; FGS do tipo `microphone`, iniciado com o NEXUS visível |

Os manifestos 3.0 debug/release inspecionados contêm oito permissões Android e uma própria AndroidX de nível `signature`, totalizando nove `uses-permission`. `BIND_ACCESSIBILITY_SERVICE` protege o componente para vinculação pelo sistema; não é uma permissão geral solicitada para ler dados do telefone. O app não declara SMS, contatos, chamadas, localização, instalação de pacotes, acesso amplo a arquivos, `QUERY_ALL_PACKAGES` ou `SYSTEM_ALERT_WINDOW`. A lista detalhada e origem de bibliotecas estão na auditoria.

## Mouse global

- Início por botão na tela visível, checkbox de consentimento da sessão, câmera, notificações e acessibilidade conectada nas configurações oficiais.
- `GlobalMouseCameraService` usa CameraX `ImageAnalysis` e MediaPipe com ciclo de vida próprio. Não depende de uma Activity visível para cada quadro. A prévia não precisa aparecer sobre outro aplicativo.
- Cursor e botão **PARAR NEXUS** usam `TYPE_ACCESSIBILITY_OVERLAY`. O serviço de acessibilidade executa `dispatchGesture`, com `canPerformGestures=true`, `canRetrieveWindowContent=false`, `eventTypes=0` e sem leitura das janelas.
- Notificação com **PARAR** e **ENCERRAR TUDO**. Saída da Activity ou remoção dos recentes não encerra deliberadamente a sessão (`stopWithTask=false`); Android/fabricante ainda podem fazê-lo.
- Suavização por tempo, cadência adaptativa, sensibilidade e calibração. Perda de quadros cancela gestos mantidos; falha persistente encerra a sessão. Proteções de geração impedem que um início atrasado reabra a câmera após PARAR.
- `START_NOT_STICKY`, sem início no boot, reinício oculto, leitura de conteúdo privado ou captura de microfone pelo serviço do mouse.

A implementação não comprova clique, arrasto, rolagem, latência ou continuidade reais no S9+. Aplicativos protegidos podem recusar interações.

## Assistente de voz fora do app

`BackgroundVoiceService` é separado do mouse, não exportado e declara o tipo `microphone`. Inicia somente por uma sessão manual enquanto o NEXUS está visível, depois das permissões e divulgação. A notificação oferece **OUVIR**, **PARAR** e **ENCERRAR TUDO**; a interface também permite interromper uma resposta.

O serviço usa a mesma instância de IA, preferências e conversa da interface; não carrega outro LLM. A posse de áudio impede escuta durante a própria resposta/TTS e impede que um controlador de áudio da interface interfira com a sessão em serviço.

O modo básico ouve uma pergunta e espera outro toque em OUVIR. A conversa contínua opcional alterna escuta/resposta/fala por até **15 minutos ou 30 perguntas**, com no máximo duas novas tentativas após silêncio. Depois aguarda ação do usuário. O serviço não é sticky e não inicia no boot.

**Não há detector permanente de “Ei, NEXUS”.** O prefixo pode ser usado depois de ativar a escuta; SpeechRecognizer não é reiniciado infinitamente para simular hotword. Reconhecimento offline e voz pt-BR dependem dos serviços/dados instalados; alguns mecanismos podem transmitir áudio/texto. Não se promete funcionamento sem rede em todo aparelho.

## Vision, Libras, Mãos Livres e 3D

Esses módulos mantêm a câmera vinculada ao ciclo de vida da tela. Vision oferece classificação local de quadros periódicos, com intervalo e prévia opcionais; isso não constitui um serviço global de compreensão de vídeo. Libras, Mãos Livres e 3D não são declarados como processamento contínuo fora do aplicativo. O modo 3D mantém controlador independente do mouse.

Ocultar a prévia não desliga a câmera. Os indicadores oficiais de privacidade permanecem sob controle do Android. Antes de abrir outro módulo de câmera, o NEXUS exige liberar o proprietário atual para evitar disputa pelo sensor.

## Limites e privacidade

O Android pode interromper qualquer sessão por memória, aquecimento, bateria, bloqueio de tela, câmera ocupada, revogação ou restrição do fabricante. A notificação e o FGS não concedem acesso irrestrito ao sistema. PARAR deve liberar sensores e gestos; o app não tenta impedir o usuário de encerrá-lo.

A distribuição Maven auditada de MediaPipe continha envio de métricas de uso. O AAR derivado usa a fábrica no-op padrão upstream, com integridade/proveniência documentadas. Isso corrige o caminho encontrado, sem provar ausência de todo tráfego ou a causa do Play Protect. [Auditoria e evidências](../AUDITORIA_PLAY_PROTECT.md).
