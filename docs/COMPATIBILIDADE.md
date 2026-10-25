# Compatibilidade e ajuste automático

O objetivo é permitir que o NEXUS funcione em mais aparelhos, ajustando o que consegue executar. Nenhum aplicativo pode garantir câmera, IA offline e controle global em todo celular: sistema, arquitetura, memória, sensores e políticas do fabricante variam.

## Base de instalação

A versão 2.0 tem **Android mínimo 8.0/API 26** e oferece um APK com quatro arquiteturas: `arm64-v8a`, `armeabi-v7a`, `x86_64` e `x86`. O APK alpha.2 publicado foi inspecionado: assinatura v2 válida, ZIP alinhado a 16 KB e 40 bibliotecas nativas. As 22 de 64 bits têm alinhamento ELF de pelo menos 16 KB; as 18 de 32 bits, pelo menos 4 KB. [Inspeção e testes da versão](RELATORIO_ALPHA_2.md). Isso não é uma lista de aparelhos fisicamente testados.

| Arquitetura do processo | App base e chat remoto | IA local | Rastreamento MediaPipe |
| --- | --- | --- | --- |
| ARM64 | Previsto | Depende de RAM e modelo | Biblioteca incluída na dependência |
| ARM32 | Previsto | Indisponível neste runtime | Biblioteca incluída na dependência |
| x86_64 | Previsto | Depende de RAM e modelo | Biblioteca incluída na dependência |
| x86 | Previsto | Indisponível neste runtime | Biblioteca incluída na dependência |

O AAR oficial MediaPipe 0.10.35 foi inspecionado: `tasks-core` fornece `libmediapipe_tasks_jni.so` para as quatro ABIs, com segmentos ELF alinhados a 16 KB. A atualização substitui a dependência 0.10.21 utilizada na base auditada. O aplicativo continua exigindo API 26, embora a biblioteca declare API mínima 24.

“Biblioteca incluída” não comprova reconhecimento de mãos ou funcionamento da câmera. CameraX, captura de imagens, chat e rastreamento são capacidades separadas. Sem rastreamento, conteúdo educacional e outras funções compatíveis podem continuar disponíveis.

O OCR da alpha.2 pode ler uma foto importada da galeria sem câmera, microfone, modelo de conversa ou provedor remoto. Os modelos português/inglês vêm no APK; a imagem é reduzida a no máximo 2.048 pixels por lado e cerca de 4 megapixels antes do reconhecimento. A execução usa um worker serial compartilhado. Cancelar descarta o resultado; uma chamada nativa já iniciada termina antes de liberar os recursos. [Uso e limites do OCR](OCR_LOCAL.md).

## O que o aplicativo verifica

`DeviceCapabilities` coleta um retrato do aparelho sem ligar câmera ou microfone: versão Android, arquiteturas, processo de 64 bits, RAM total e disponível, indicação de pouca memória do Android, núcleos de CPU, espaço privado livre, câmeras frontal/traseira, microfone e versão GLES.

Na alpha.4 completa, `DevicePolicy` mantém a mesma base compacta incluída no APK e ajusta os limites de processamento. Não há escolha de modelo recomendado no telefone. A variante de desenvolvimento sem pesos preserva a seleção manual para regressão; não autoriza servidor externo.

Os critérios atuais usam RAM total e livre, não apenas o nome comercial do celular:

- IA local requer processo ARM64 ou x86_64, Android compatível, ausência da indicação Android de pouca memória e margem mínima de RAM. O perfil compacto usa como referência pelo menos 3 GB totais e 1 GB disponível.
- O perfil equilibrado permanece somente no modo avançado/variante sem pesos para regressão; não é a base do APK completo. Exige pelo menos 6 GB totais e 2 GB disponíveis. Os arquivos finais/parciais já presentes entram no cálculo do espaço adicional, mas tamanho não substitui SHA-256.
- Contexto, threads e limite de resposta são reduzidos em aparelhos mais modestos. O contexto varia entre 512 e 1.536 tokens, compartilhados por instruções, memórias, conversa e saída.
- Câmera e rastreamento usam resolução menor e intervalos maiores em aparelhos com menos recursos. A análise também observa o custo dos quadros para evitar sobrecarregar a interface.
- Rastreamento de duas mãos e ambiente 3D dependem dos recursos disponíveis. O renderer exige OpenGL ES 2.

Esses valores são uma política inicial de segurança de recursos, não uma garantia de velocidade. RAM livre muda conforme os outros aplicativos. A recomendação é estabilizada para a sessão, evitando alternar modelo a cada flutuação de memória. O usuário pode liberar memória e reabrir o aplicativo para uma nova avaliação.

## Quando falta um recurso

Sem microfone ou serviço de reconhecimento, use texto. Sem voz instalada no idioma escolhido, instale os dados de fala do Android. Sem câmera compatível ou biblioteca de rastreamento, o app deve explicar a indisponibilidade e preservar as funções independentes. Sem suporte à IA local, o provedor personalizado é uma opção explícita; não há envio remoto automático.

A câmera traseira pode ser usada como alternativa onde o módulo permitir, se a frontal estiver ausente. Permissão negada é uma escolha do usuário e não deve iniciar sensor nem serviço. Controle global requer autorização de acessibilidade, câmera e sessão visível com notificação; o fabricante ou um aplicativo protegido pode bloquear interações.

## Conferir no seu aparelho

Abra **Central de Diagnóstico** para consultar o perfil e suas razões. Os testes locais são iniciados por botão e não gravam uma aprovação fictícia. Depois teste separadamente texto/IA, áudio, captura, uma mão, duas mãos e controle global.

O Samsung Galaxy S9+ é o aparelho informado pelo usuário. A versão do Android, os serviços de fala instalados, a RAM livre e o desempenho físico desse exemplar ainda não foram medidos pelo ambiente. A adaptação usa as observações do aparelho ao executar o app.
