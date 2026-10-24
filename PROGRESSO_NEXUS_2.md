# NEXUS 2.0 — progresso

Atualizado em 23/10/2026 (São Paulo). Aparelho informado pelo usuário: Samsung Galaxy S9+. Projeto preservado no repositório existente. A versão anterior permanece no histórico.

## Estado inicial após auditoria

| Sistema | Estado | Evidência e próxima ação |
| --- | --- | --- |
| Auditoria | Concluído e testado | Código, manifesto, dependências e fluxo examinados; achados e limites em `AUDITORIA_NEXUS.md` |
| NEXUS AI padrão sem URL/chave | Ainda não implementado | Cliente HTTP atual exige servidor; implementar modelo local com download consentido e inferência real |
| Provedor personalizado opcional | Parcialmente implementado | HTTP/Keystore já existem; separar do padrão e adicionar teste de conexão |
| Ferramentas estruturadas da IA | Ainda não implementado | Implementar validação, lista permitida e confirmação antes de executar |
| Voz pt-BR | Parcialmente implementado | STT/TTS existem; corrigir filtro e validação das vozes, tom e reprodução automática |
| Mouse persistente e clique/rolagem reais | Ainda não implementado | Laboratório atual só conta cliques e move objetos desenhados |
| Mouse global | Ainda não implementado | Depende de integração oficial e teste físico, consentimento e serviço visível |
| Mãos livres | Parcialmente implementado | Poses internas existem; separar controlador e associar ações reais |
| 3D com duas mãos | Parcialmente implementado | Cubo projetado em Canvas; faltam seleção e renderer apropriado |
| Libras | Ainda não implementado | Tela informativa não equivale a tutor ou avaliação |
| Histórico e memória local | Implementado, aguardando teste em dispositivo | Room/DataStore existentes; preservar e testar regressões |
| APK 2.0 | Ainda não implementado | Nenhum novo APK foi gerado nesta etapa |

Os resultados de build/testes da versão 0.1 estão em `PROGRESSO.md`; não são resultados da versão 2.0. Nenhum teste físico do Samsung S9+ foi executado pelo ambiente de desenvolvimento.

## Etapa em andamento

Etapa 1: IA local padrão e voz em português brasileiro. Avaliar modelo compacto para hardware do S9+, mantendo explícitas as limitações de qualidade e de visão. Não há servidor online próprio nem credenciais de provedor configuradas.

## Critérios a registrar em cada entrega

Arquivos alterados; comportamento corrigido; comando e resultado dos testes; APK existente e assinatura; instruções de teste no celular; limitações e estado de cada sistema. Não marcar uma função como concluída apenas porque compila.
