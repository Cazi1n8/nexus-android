# NEXUS 4.1 — evolução do projeto existente

Dispositivo de validação informado pelo responsável: **Samsung Galaxy A03**. Início: 28/10/2026. A revisão mantém `br.com.nexus.assistant`, os dados existentes e o CORE sem modelos generativos obrigatórios. Versão candidata: **4.1.0-alpha.1-core / 27**.

Este documento distingue relato físico do usuário, inspeção de código, testes executados no ambiente e testes ainda pendentes. O ambiente não possui o Galaxy A03 conectado nem uma instância do Play Protect com a classificação do arquivo bloqueado.

## Critério de distribuição

**Bloqueio pelo Play Protect permanece sem resolução comprovada.** Não há autorização do Google ou teste de instalação física aprovado a apresentar. O APK desta etapa será um candidato para avaliação, não uma versão certificada ou liberada para uso. A publicação automática de APKs de depuração permanece suspensa. A configuração de assinatura de produção já existe, mas depende da chave estável do responsável em Secrets; nenhuma nova identidade de produção será inventada.

O APK público CORE anterior tem 108.210.081 bytes, SHA-256 `e76ff4e228206a11317000a238556d8c24693532c1527b05f8264f5ba6b50d15`, certificado de teste `0e72073647f9fd5b4bc27f6f8fbafa9100f4fadc391433ff6844fc2f96ec97fc`. Uma nova compilação local não herda sua chave privada. Não desinstale o aplicativo com dados importantes antes de exportá-los e verificar a compatibilidade de assinatura.

## Plano verificável

| Fase | Escopo e evidência exigida | Estado nesta implementação |
| --- | --- | --- |
| 1 — Segurança | Manifesto final, capacidades, consentimento, dependências, assinatura; instalação com proteção ativa e eventual revisão oficial | Auditoria e correções em andamento; aprovação física pendente |
| 2 — Mouse | Cadeia CameraX→MediaPipe→gestos→cursor, sessões global/interna, pausa/parada, medição de latência e regressão clique/rolagem | Correções em andamento; teste físico A03 pendente |
| 3 — IA | Ferramenta matemática offline, identidade, contexto, qualidade e latência com modelo real | Corrigir bloqueio da calculadora no CORE; troca de modelo/serviço integrado não implantada |
| 4 — Vision | Imagem chegando a provedor multimodal, contexto e fontes de pesquisa verificáveis | Integrações existentes preservadas; serviço público multimodal/pesquisa não configurado |
| 5 — 3D | Duas mãos medidas, seleção/transformações e múltiplos objetos/cenas | Preservar cubo existente e remover restrição de mãos por RAM; expansão de cenas pendente |
| 6 — Libras e Voz | Exercícios com referências validadas, STT/TTS reais, sessão autorizada fora do app | Código atual preservado; validação física e ampliação pedagógica pendentes |
| 7 — Extensões e desempenho | Modelos opcionais, remoção segura, tamanho final e perfis por recursos medidos | CORE e centrais preservados; perfis de rastreamento em implementação |
| 8 — Entrega | Compilação, relatórios e pacote identificado por hash/assinatura | Aguardando compilação desta revisão |

## Três falhas confirmadas no aparelho

### Play Protect bloqueia a instalação

Evidência disponível: relato e captura de tela enviados pelo responsável, com aviso de possível acesso a dados sensíveis. Isso não permite determinar o veredito interno do Google, nem excluir um problema real. Consulte [AUDITORIA_PLAY_PROTECT.md](AUDITORIA_PLAY_PROTECT.md) para permissões, achados confirmados, hipóteses, fontes oficiais e limites.

O mouse global precisa de capacidade sensível para executar gestos. Isso será mantido com escopo mínimo, sem leitura de janelas, notificações de outros aplicativos, senhas ou dados financeiros. Não serão usadas técnicas de evasão, alteração de identidade para escapar da verificação ou desativação da proteção.

### Cursor lento e travando

A implementação anterior já tinha executor único e `KEEP_ONLY_LATEST`; não era uma fila ilimitada de inferência. Foram identificados custos por quadro e suavização fixa que merecem correção. O trabalho introduz entrega com um único resultado pendente, orçamento de idade dos resultados, perfis de resolução/frequência e suavização adaptativa. Métricas sintéticas de filtro não representam a latência completa de uma câmera física.

### Mouse não funciona fora do NEXUS

Há dois modos separados. O interno é vinculado à janela do NEXUS e para em `ON_STOP`; isso é correto para o seu escopo. O global usa serviço foreground independente e sobreposição de acessibilidade. A tela apresentava primeiro o modo interno e escondia o global abaixo de controles e instruções. A revisão destaca o global e seus requisitos, sem afirmar que a seleção errada explique sozinha o problema do A03.

Serão inspecionados o ciclo de vida real da câmera, estados de erro, coordenadas entre cursor e toque, consentimento e controles de pausa/encerramento. A existência de um serviço no manifesto não comprova funcionamento em outro aplicativo.

## Como validar no Galaxy A03 quando houver instalação legítima aceita

1. Mantenha o Play Protect ativo. Registre versão, SHA-256, certificado e resultado da instalação. Se houver bloqueio, preserve a mensagem e siga a revisão oficial; não contorne a verificação.
2. Abra Chat e Mouse sem ativar recursos: câmera, microfone e serviço global devem continuar desligados.
3. Em Mouse, escolha o perfil Automático ou Econômico, leia a divulgação, autorize a sessão e habilite o serviço NEXUS na acessibilidade se o Android permitir. Inicie **mouse global**, não apenas mouse interno.
4. Verifique notificação, indicador de câmera, botão de parada e estados do serviço. Vá à Home do Android e abra um aplicativo comum. Teste movimento, clique curto, duas pinças, pinça mantida e rolagem vertical/horizontal. Não use telas bancárias, de senhas ou transações para este teste.
5. Registre pelo menos 30 segundos por perfil: tempo de análise, idade de entrega, fluidez, perda de mão e comportamento ao voltar ao app. Iluminação e posição da câmera devem permanecer semelhantes. Não registre imagens pessoais nos logs.
6. Pause: cursor e toques devem parar e a câmera deve ser liberada. Retome pelo NEXUS visível. Encerre pela notificação e confirme liberação completa, sem reinício automático.
7. Teste câmera ocupada, rotação, tela bloqueada, permissões revogadas e acessibilidade desconectada. Deve haver cancelamento de toque mantido e erro compreensível, sem ações ocultas.
8. Sem baixar modelo de conversa, pergunte `Quanto é 50 + 50 + 50 × 50?`: a ferramenta deve calcular **2600**. Perguntas gerais continuam exigindo um modelo real ou serviço configurado; não devem receber respostas simuladas.

## Resultados executados

A preencher somente com os resultados obtidos nesta revisão. Os resultados históricos da alpha.3 não serão contados como testes da 4.1. Os cenários de câmera, mãos reais, serviço global sobre outros apps, STT/TTS no A03 e instalação com Play Protect continuam **pendentes de hardware físico**.
