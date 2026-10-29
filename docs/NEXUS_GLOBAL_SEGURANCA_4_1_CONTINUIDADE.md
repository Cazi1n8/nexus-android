# NEXUS 4.1 — continuidade da revisão do mouse global

Revisão de fonte em 29/10/2026, no projeto existente. O aparelho físico de referência continua sendo o **Samsung Galaxy A03**. Não há acesso físico a ele neste ambiente; os defeitos abaixo foram identificados no código, sem atribuir uma causa já comprovada à falha relatada pelo usuário.

## Causas encontradas e correções

| Problema confirmado no código | Alteração |
| --- | --- |
| A parada solicitada pelo painel/overlay dependia de `Service.onDestroy`, que chega de forma assíncrona. Um resultado de câmera já entregue poderia repor `active=true` ou enviar um gesto depois de uma falha do overlay. | `GlobalMouseRuntime.stop` publica estado inativo imediatamente, invalida pedidos de início antigos e conserva o erro. `updateFromFrame` recusa resultados quando parado/pausado. O serviço verifica o estado antes e depois de atualizar o cursor. |
| A conclusão de um gesto Android em andamento podia iniciar outro toque/pressionamento que já estava na fila, entre PARAR/PAUSAR e o fechamento do serviço. | A fila de `PointerGestures` verifica a sessão antes de aceitar eventos e antes de continuar. Depois da parada/pausa, descarta novos toques/pressionamentos e só permite a liberação de um toque anteriormente mantido. A ação PARAR da notificação e as falhas também desativam imediatamente a sessão e removem o overlay. |
| A revogação do canal/permissão de notificações não era revisada durante uma sessão já iniciada. | O watchdog verifica, a cada 1,5 s, câmera, acessibilidade e notificações/canal. A perda encerra o serviço; não solicita permissões nem reinicia a câmera escondido. A verificação não ocorre em cada quadro. |

Não foram alterados os algoritmos de clique, duplo clique, arraste ou rolagem, nem seus limiares. Mouse interno, mouse global, mãos livres e 3D continuam com controladores próprios. Não foram adicionadas permissões, dependências, modelos ou dados grandes ao CORE.

## Verificações executadas nesta revisão

- **35 testes Python aprovados:** 21 do coletor CORE e 14 do auditor de segurança. Os testes do coletor simulam ADB para validar o próprio coletor; não demonstram funcionamento do Android.
- Auditor de XMLs de fonte executado, **sem violações da política declarada**: oito permissões Android, serviços de câmera/microfone privados e separados, acessibilidade sem recuperação de janelas/captura de tela/teclas. [Relatório de fonte](evidencias/n42-global-source-security.json). Não é inspeção de um novo APK nem aprovação do Play Protect.
- Dois testes JVM adicionados para bloqueio imediato da parada e recusa de quadros atrasados após pausa/parada. Execução JVM e compilação são responsabilidade da verificação integrada do projeto; esta nota não os marca como aprovados antecipadamente.

Foi acrescentado `GlobalMousePermissionDenialTest`, teste Android **negativo** que inicia o serviço real com CAMERA negada e verifica sua recusa, encerramento, ausência de notificação persistente e independência da voz. Ele exige CORE, emulador descartável e `nexusGlobalBoundarySmoke=true`; não roda num aparelho físico, não concede permissões, não habilita acessibilidade e não injeta mãos artificiais. **Pendente de execução Android nesta revisão.**

O runner `scripts/run_android_core_storage.py` passou a exigir exatamente **12 testes CORE**, incluindo também `SceneRenderingTest` com opt-in `nexusSceneSmoke=true` para compilação real dos shaders e renderização GLES em contexto EGL e um relatório próprio dessa recusa, além do relatório CORE já existente. Ele salva `global-boundary-observations.json` e falha se a prova estiver ausente. A etapa opcional de extensões continua separada, com um teste. Relatórios antigos de dez testes conservam sua validade histórica e não são apresentados como evidência da nova fonte.

## Teste físico ainda necessário

O roteiro completo está em [NEXUS_4_1_MOUSE_GLOBAL.md](NEXUS_4_1_MOUSE_GLOBAL.md). No Galaxy A03, após uma instalação legítima com Play Protect ativo, verificar:

1. Ativar **Mouse global**, depois de ler a divulgação e conceder somente as permissões dessa função. Esperar quadros reais no painel antes de sair da tela.
2. Abrir a tela inicial e um aplicativo comum de teste, sem conteúdo sensível. Confirmar cursor, indicador/notificação de câmera e resposta real a clique, arraste e rolagem.
3. Registrar processamento/idade dos resultados e gestos aceitos/recusados/cancelados. Aceitação da API isolada não comprova reação do alvo.
4. Pausar/parar pela notificação e pelo botão sobreposto, inclusive durante pinça/arraste. Confirmar que nenhum toque novo ocorre depois da parada e que a câmera é liberada.
5. Revogar câmera/notificações/acessibilidade durante a sessão e verificar encerramento sem reinício. Conferir que voz e mouse param de modo independente.

A continuidade da câmera depende da versão Android, driver e políticas do fabricante. As telas protegidas podem rejeitar gestos. Ainda **não foi comprovado** que o mouse global funciona no Galaxy A03, nem medida uma melhora de latência no aparelho.

## Segurança e Play Protect

A auditoria anterior de manifesto final/APKs, assinatura e dependências permanece em [AUDITORIA_PLAY_PROTECT.md](../AUDITORIA_PLAY_PROTECT.md). O texto do bloqueio e a política de proteção contra fraude tornam acessibilidade em APK distribuído fora da loja uma hipótese relevante, mas o Google não informou a causa individual. A revisão atual não elimina essa incerteza.

A acessibilidade continua usando exclusivamente `dispatchGesture` e `TYPE_ACCESSIBILITY_OVERLAY`, sem ler árvores/textos de outros aplicativos, sem coletar eventos e sem ocultar os sensores. Não houve mudança de pacote/classificação para contornar detecção. A assinatura de teste não equivale à assinatura de produção; o novo APK precisa de inspeção própria e comparação de certificado antes de tentar atualizar uma instalação com dados.

Se o Play Protect continuar bloqueando, interromper a instalação e seguir a [solicitação oficial de revisão](PLAY_PROTECT_REVISAO.md), anexando o APK exato, hashes/certificado e evidências da versão. Não desativar a proteção. Esta revisão não enviou recurso nem recebeu aprovação do Google.
