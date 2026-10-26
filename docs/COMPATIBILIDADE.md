# Compatibilidade e ajuste automático

Estado da implementação 3.0 em 26/10/2026. O NEXUS adapta recursos medidos do aparelho; não garante todas as funções em qualquer celular. [Progresso atual](../PROGRESSO_NEXUS_AI_3.md).

## Instalação e arquiteturas

Android mínimo **8.0/API 26**, alvo **API 35**. O APK universal inclui `arm64-v8a`, `armeabi-v7a`, `x86_64` e `x86`. IA local exige processo de 64 bits; a interface e funções independentes podem continuar disponíveis em processos de 32 bits. A presença de uma biblioteca não comprova sensores ou desempenho físico.

| Processo | App base / provedor remoto opcional | IA local | MediaPipe |
| --- | --- | --- | --- |
| ARM64 | Previsto em Android compatível | Depende de RAM e armazenamento | Biblioteca incluída |
| ARM32 | Previsto em Android compatível | Indisponível neste runtime | Biblioteca incluída |
| x86_64 | Previsto em Android compatível | Depende de RAM e armazenamento | Biblioteca incluída |
| x86 | Previsto em Android compatível | Indisponível neste runtime | Biblioteca incluída |

O MediaPipe 0.10.35 derivado preserva os quatro nativos upstream; somente a fábrica de logging de uso foi substituída pela seleção no-op padrão oficial. [Proveniência](evidencias/ai3-mediapipe-runtime.json). Isso não é uma certificação de câmera, rastreamento ou Play Protect.

## O que é medido

`DeviceCapabilities` consulta versão Android, ABI/processo de 64 bits, RAM total utilizável e disponível, indicação Android de pouca memória, núcleos de CPU, espaço privado livre, câmeras, microfone e GLES. Não liga sensores para fazer essa consulta. O diagnóstico mostra detalhes locais apenas por escolha do usuário e não consulta IMEI ou número de série.

A leitura usada pela política é estabilizada ao iniciar o app. O nome comercial de um celular não determina o perfil; RAM disponível varia com outros aplicativos. Uma reabertura após liberar memória pode produzir outra recomendação. Isso evita trocar de modelo no meio de uma conversa.

## IA incluída e orçamento

A versão completa prepara ambos os perfis no mesmo APK, sem exigir outro APK por modelo de telefone. A cópia privada de um perfil incluído não usa rede. Os GGUFs são verificados por tamanho e SHA-256; um arquivo parcial/final já presente reduz o espaço adicional necessário, sem substituir a verificação de integridade.

| Critério | Política atual |
| --- | --- |
| IA local básica | Android compatível, ARM64/x86_64, sem indicação Android de pouca memória, pelo menos **3 GB de RAM utilizável total e 1 GB disponível** |
| Perfil equilibrado Qwen2.5 1,5B | Pelo menos **5 GB utilizáveis totais e 1,8 GB disponíveis**, além de espaço para preparar o modelo |
| Perfil compacto Qwen2.5 0,5B | Alternativa para a margem menor de memória/armazenamento; qualidade mais limitada |
| Contexto compartilhado por prompt, histórico e saída | **2.048 tokens**; **4.096** no perfil de maior folga de recursos |
| Saída | Até **384, 512 ou 768 tokens**, conforme o orçamento |
| CPU | **1 a 3 threads**, conforme recursos disponíveis |

Esses valores são limites de recursos, não garantia de fluidez ou qualidade. A avaliação real ainda encontrou falhas de interpretação e conhecimento no modelo 1,5B. [Resultados da IA](../TESTES_NEXUS_AI.md). Sem margem de IA local, um provedor remoto é uma opção explícita e precisa de infraestrutura real e consentimento; não existe encaminhamento automático.

O APK com ambos os perfis é grande, aproximadamente 1,7 GB antes de cada medição final. Download, instalação, atualizações e cópia privada precisam de espaço adicional; reserve pelo menos 6 GB livres e confira a mensagem real de armazenamento no aparelho.

## Câmera, voz e gráficos

A política reduz resolução, intervalo de análise e número de mãos conforme a margem do aparelho. O mouse usa cadência e suavização por tempo, com ajuste pelo custo efetivo dos quadros. Rastreamento de duas mãos/3D depende dos recursos e o renderer exige OpenGL ES 2. A câmera é um recurso compartilhado: iniciar dois módulos concorrentes pode ser impedido.

Vision classifica quadros periódicos localmente; não é compreensão geral de vídeo. OCR de foto importada pode funcionar sem câmera, microfone, modelo de conversa ou servidor. Sem câmera, os conteúdos educacionais de Libras e outras funções independentes continuam acessíveis.

Reconhecimento de fala e TTS são serviços Android: pt-BR offline depende do mecanismo e dados instalados. O serviço de voz manual e o mouse global são independentes e possuem notificações/parada. Fabricante, permissões, bateria, memória e aplicativos protegidos podem limitar a continuidade fora do NEXUS. [Permissões e segundo plano](PERMISSOES_E_SEGUNDO_PLANO.md).

## Conferir no aparelho

Abra **Central de Diagnóstico**, consulte a compatibilidade e inicie testes locais por botão. Depois verifique texto, voz, foto, uma mão, duas mãos e controle global separadamente. Os resultados não recebem aprovação automática por uma biblioteca inicializar.

O **Samsung Galaxy S9+** é o aparelho informado pelo usuário. A versão do sistema, a RAM livre, serviços de fala e desempenho desse exemplar ainda não foram medidos pelo ambiente. Os testes de segundo plano, sensores, aquecimento e instalação com Play Protect continuam precisando de aparelho físico.
