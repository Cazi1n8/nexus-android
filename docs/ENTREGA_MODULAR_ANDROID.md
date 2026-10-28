# Entrega modular do NEXUS no Android

Consulta da documentação oficial: **27/10/2026**. Este documento distingue decisões da correção de armazenamento, limitações da instalação manual e opções futuras da Google Play. Ele não comprova uma publicação na loja, instalação no Galaxy A03 ou redução de tamanho; a medição do APK compilado pertence à [auditoria de armazenamento](../AUDITORIA_ARMAZENAMENTO.md).

O pedido atual substitui a preferência anterior por incluir os dois modelos generativos no APK. O novo contrato é: **NEXUS CORE instala primeiro; os modelos grandes são opcionais e só são transferidos após uma ação explícita do usuário**.

## O que a modularidade significa nesta etapa

Há duas separações diferentes:

1. **Pacotes de dados opcionais:** pesos de IA e dados de visão ficam fora da instalação inicial, podem ser baixados, verificados e removidos. Sua remoção libera os arquivos privados correspondentes.
2. **Código de funcionalidades:** os clientes, telas e bibliotecas que o APK contém continuam instalados. Ativar ou desativar um recurso integrado não desinstala seu código. Dividir pacotes Kotlin em diretórios também não equivale a instalar módulos binários sob demanda.

O caminho de APK manual desta correção usa a primeira separação. Não se anuncia Play Feature Delivery, AI packs da Play ou carregamento remoto de código como funcionalidades já implantadas. A disponibilidade real dos controles e o estado da compilação devem ser conferidos no README e nos testes da versão entregue.

| Parte | Instalação inicial e uso | O que pode ocupar dados adicionais |
| --- | --- | --- |
| NEXUS CORE | Interface, configurações, CameraX, rastreamento real de mãos, mouse e controles Android autorizados | Configurações, calibração e pequenos arquivos operacionais |
| Modelo de mãos do CORE | `hand_landmarker.task`, **7.819.105 bytes**, acompanha o APK para preservar o mouse offline | Não depende de baixar um LLM ou pacote de Vision |
| NEXUS AI online | Cliente compilado; precisa de servidor real, conexão e consentimento | Histórico local; não exige os pesos generativos no aparelho |
| NEXUS AI offline | Runtime compatível compilado; o modelo é opcional | Qwen2.5 compacto: **491.400.032 bytes**; equilibrado: **1.117.320.736 bytes**; transferência parcial também ocupa espaço |
| NEXUS Vision | Código integrado; dados opcionais de classificação/OCR | Classificador: **5.434.517 bytes**; OCR português: **1.982.756 bytes**; inglês: **4.113.088 bytes**; total desses três: **11.530.361 bytes** |
| NEXUS 3D | Ambiente e cubo procedural integrados, compartilha a capacidade de rastreamento | Configuração; futuros modelos 3D adicionais exigiriam catálogo e entrega próprios |
| NEXUS Libras | Exercícios integrados e rastreamento do CORE; reconhecimento permanece experimental | Progresso local; habilitar a tela não baixa um reconhecedor completo de Libras |
| NEXUS Voice | Integração compilada com reconhecimento e TTS do Android | O motor do sistema pode exigir dados de voz/rede; conversa com IA depende do modelo opcional ou do serviço online |

Os tamanhos de dados acima são os valores fixados no catálogo/scripts, não tamanhos medidos de uma instalação nova. Não some os dois LLMs se o usuário baixou apenas um. Licenças, avisos e hashes continuam necessários mesmo quando o modelo é opcional. Remover um pacote não deve apagar conversas, preferências ou o modelo de mãos do CORE.

## Instalação manual por APK

O arquivo `.apk` assinado é instalado pelo mecanismo oficial do Android. Se ele for universal, pode conter bibliotecas de várias ABIs; um APK específico por ABI pode ser menor, mas precisa corresponder ao processo Android do aparelho. A alternativa universal evita escolher o arquivo errado. Na Google Play, um App Bundle permite que a loja entregue os recursos e bibliotecas apropriados ao dispositivo. [APK por arquitetura](https://developer.android.com/build/configure-apk-splits), [Android App Bundles](https://developer.android.com/guide/app-bundle).

Um `.aab` é um formato de publicação e não um instalador para abrir no gerenciador de arquivos. Um `.apks` gerado por `bundletool` também não é o mesmo que um APK único; instalações por splits precisam do conjunto completo e da ferramenta apropriada. O fato de uma biblioteca Play Core estar no projeto não transforma um APK baixado do navegador em aplicativo publicado na Play. [App Bundles e instalações parciais](https://developer.android.com/guide/app-bundle).

Para os pacotes de dados do NEXUS manual:

- O catálogo vem no aplicativo e fixa origem HTTPS, versão, tamanho máximo e SHA-256. O usuário escolhe o download e vê sua dimensão antes de começar.
- Somente modelos/dados previstos são aceitos. Arquivos temporários ficam na área privada; só um arquivo completo e íntegro pode ser promovido ao destino utilizável.
- Cancelamento, falta de rede, falta de espaço e arquivo incompatível precisam gerar estados claros. A transferência parcial deve aparecer no armazenamento.
- A remoção é específica para o pacote, após interromper seu uso. Não se apagam arquivos arbitrários nem todos os dados do aplicativo para recuperar espaço.
- Novos clientes, motores nativos ou outras mudanças de código exigem uma atualização legítima e assinada do aplicativo, respeitando a identidade de assinatura existente.

Não são extensões aceitas downloads de `.dex`, `.jar` ou `.so` carregados de servidores próprios ou de terceiros. O Android recomenda evitar carregamento dinâmico arriscado; a política da Google Play restringe atualização de código fora do mecanismo da loja. Usar apenas HTTPS não transforma código remoto em seguro. [Riscos de Dynamic Code Loading](https://developer.android.com/privacy-and-security/risks/dynamic-code-loading), [política Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/16559646).

## Opções oficiais para uma publicação futura na Google Play

| Mecanismo | Finalidade e limite de escopo | Estado no NEXUS desta correção |
| --- | --- | --- |
| Android App Bundle | A Play gera APKs otimizados para a configuração do dispositivo | Opção de distribuição futura; não equivale ao APK manual |
| Play Feature Delivery | Código e recursos em módulos `com.android.dynamic-feature`, com entrega por condição ou solicitação | Requer refatoração em módulos reais, integração e teste pela Play; não foi implementado apenas por criar uma Central de Extensões |
| Play Asset Delivery | Pacotes de assets sem código, com entrega durante instalação, posterior automática ou sob demanda; a documentação principal é voltada a jogos | Não assumir suporte implantado para o NEXUS ou trocar pacotes manuais por PAD sem integração e teste |
| Play for On-device AI, **beta** | AI packs de modelos, entregues em modos equivalentes; bibliotecas Java/Kotlin e nativas ficam na base ou em feature modules | Candidato específico para modelos do NEXUS na Play; não é o downloader do APK manual |

As funcionalidades realmente removíveis da Play precisam ser módulos configurados para isso e dependem do processo oficial de entrega. Separação lógica de telas não proporciona a mesma remoção de código. [Play Feature Delivery](https://developer.android.com/guide/playcore/feature-delivery).

PAD exclui código executável dos asset packs. Seu modo `fast-follow` inicia transferência automaticamente, portanto não corresponde à preferência atual do usuário por downloads opcionais conscientes; para esses dados, o modo relevante seria `on-demand`. [Play Asset Delivery](https://developer.android.com/guide/playcore/asset-delivery).

A documentação de Play for On-device AI consultada identifica o recurso como beta. Ela exige AGP 8.8 ou posterior para AI packs e 8.10.0 ou posterior para o direcionamento avançado descrito. O projeto partia de AGP 8.9.2; não se deve afirmar que todo exemplo de targeting funciona sem atualização. A página informa limites de 1,5 GB comprimido por AI pack e 4 GB cumulativos; devem ser reconfirmados no Play Console antes da publicação. AI packs não admitem Java/Kotlin ou bibliotecas nativas. [Play for On-device AI](https://developer.android.com/google/play/on-device-ai).

Testes locais com `bundletool` e compartilhamento/testes internos da Play fazem parte da preparação dessas alternativas, mas não são prova de entrega real em produção. Assinatura estável, configuração do Play Console, permissões e análise de políticas continuam sendo etapas separadas. Não é necessário acrescentar um SDK beta ao APK manual para retirar os GGUFs da instalação inicial.

## Medir armazenamento sem confundir categorias

Os **1,90 GB de aplicativo** informados pelo usuário não equivalem necessariamente ao tamanho do arquivo de download. A API `StorageStats.getAppBytes()` inclui APKs, saída otimizada do compilador e bibliotecas nativas descompactadas. A API também define dados e cache com sobreposição: `getDataBytes()` inclui diretórios de cache. Por isso, não se deve somar contadores dessa API como se fossem categorias independentes. [StorageStats](https://developer.android.com/reference/android/app/usage/StorageStats).

A varredura dos arquivos privados mostra categorias úteis — modelos completos, transferências parciais, dados de extensões, banco, imagens, preferências e cache —, mas pode divergir dos números do sistema por otimizações, blocos alocados, arquivos inacessíveis e regras da interface Samsung. Valores lógicos medidos devem ser identificados como tais. Somente uma nova leitura no A03 pode confirmar o total instalado desse aparelho.

A hipótese de que os **498 MB de dados** incluem a cópia privada do modelo compacto precisa ser confrontada com o inventário, não tratada como prova. Na atualização para CORE, um modelo já instalado pode continuar útil; sua exclusão deve ser opcional. Reduzir o APK não remove automaticamente os dados antigos, nem comprova que 498 MB foram liberados. Limpar os 13,51 MB de cache também não resolve o peso dos modelos que estavam no pacote.

## Compatibilidade do Galaxy A03 e de outros aparelhos

O nome comercial não basta para deduzir RAM disponível ou se o processo é de 32/64 bits. O NEXUS deve verificar a versão do Android, ABI do processo, `Process.is64Bit()`, indicação de pouca memória, RAM disponível, espaço e câmera efetivamente presentes. Um processador de hardware 64 bits pode executar um sistema/processo de 32 bits.

O mouse deve usar o modelo de mãos do CORE mesmo quando nenhum LLM foi baixado. Se o processo não suportar o runtime generativo, ou faltar memória, o catálogo informa a incompatibilidade e mantém os recursos permitidos. A configuração de câmera/cadência pode se adaptar aos recursos medidos; não é uma garantia de fluidez, câmera em segundo plano ou ausência de aquecimento no A03 sem teste físico.

IA online evita pesos generativos locais, mas ainda exige um provedor e infraestrutura reais, autenticação e limites de uso. Não há um backend público NEXUS implantado só por disponibilizar o cliente. Credenciais privadas do provedor devem ficar no servidor, não no APK. Não se promete serviço gratuito ilimitado.

## Fontes oficiais consultadas

As páginas abaixo foram abertas em 27/10/2026; a consulta não significa aprovação do NEXUS pela Google Play ou pelo Play Protect.

- [Android App Bundles](https://developer.android.com/guide/app-bundle)
- [Build multiple APKs / divisão por ABI](https://developer.android.com/build/configure-apk-splits)
- [Play Feature Delivery](https://developer.android.com/guide/playcore/feature-delivery)
- [Play Asset Delivery](https://developer.android.com/guide/playcore/asset-delivery)
- [Play for On-device AI — beta](https://developer.android.com/google/play/on-device-ai)
- [Dynamic Code Loading](https://developer.android.com/privacy-and-security/risks/dynamic-code-loading)
- [Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/16559646)
- [StorageStats](https://developer.android.com/reference/android/app/usage/StorageStats)
