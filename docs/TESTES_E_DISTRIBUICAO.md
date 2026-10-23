# Testes e preparação para distribuição

Este documento é um roteiro de verificação. Não é um relatório de testes aprovados. Os comandos e resultados realmente executados devem ser registrados em [PROGRESSO.md](../PROGRESSO.md), incluindo data, ambiente, falhas e limitações.

## Compilação e verificações locais

Use o Gradle Wrapper do projeto para manter a versão de compilação consistente:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
python3 -m unittest discover -s server/tests -v
```

O primeiro comando exige Java compatível, Android SDK e acesso às dependências. O segundo verifica a ponte de IA com testes locais e não comprova a qualidade de um modelo real. Se o diretório ou comando de testes mudar, siga o README do componente correspondente.

Um build bem-sucedido gera o APK em `app/build/outputs/apk/debug/app-debug.apk`. A presença do arquivo e o resultado do comando devem ser conferidos antes de anunciar a entrega. O APK de depuração permite testes; a assinatura de desenvolvimento não substitui uma preparação para publicação.

## Matriz manual em aparelho

Registre modelo, Android, RAM, versão do APK e resultado de cada teste. Use ao menos um aparelho modesto e um aparelho recente. Itens de fases futuras permanecem **não aplicáveis** até a implementação.

| Área | Como testar | Resultado esperado |
| --- | --- | --- |
| Instalação | Instalar o APK de depuração, abrir e reabrir | Sem travamento; dados preservados quando previsto |
| Navegação | Abrir as 14 áreas e voltar pelo sistema | Telas acessíveis; funções futuras identificadas; nenhum botão promete uma ação inexistente |
| Chat real | Configurar a ponte e enviar mensagens em sequência | Resposta do modelo; contexto da conversa mantido |
| Imagens | Selecionar uma foto e perguntar sobre ela a um modelo visual | Envio explícito; imagem e pergunta recebidas; erro claro se o modelo não oferece visão |
| Rede | Desconectar, usar URL inválida e simular servidor indisponível | Indicador de processamento encerrado e mensagem compreensível; possibilidade de tentar novamente |
| Autenticação | Usar token inválido e depois corrigir | Falha clara sem expor o token em tela ou logs |
| Memória | Criar, editar, excluir e exportar uma memória autorizada | Alterações refletidas e persistidas; nenhum dado memorizado sem autorização |
| Histórico | Criar conversas e excluir uma delas | Somente os dados escolhidos são removidos; lista atualizada |
| Voz | Falar em pt-BR, ouvir resposta, interromper e sair da tela | Estados visíveis; fala e escuta encerradas corretamente |
| Microfone | Negar permissão, conceder depois e testar silêncio | Sem falha; explicação útil; recuperação sem escuta permanente |
| Síntese de voz | Testar serviço ausente, idioma e velocidade | Configuração respeitada ou limitação informada |
| Câmera | Negar permissão, conceder, capturar e sair da tela | Uso visível; recurso liberado ao sair; nenhuma captura escondida |
| Ciclo de vida | Girar a tela, colocar em segundo plano, voltar e encerrar | Sem captura ou escuta inesperada; estado e recursos tratados corretamente |
| Acessibilidade | TalkBack, fonte grande, contraste e uso sem gestos | Rótulos compreensíveis, conteúdo legível, controles acessíveis |
| Desempenho | Conversa longa, foto grande e uso repetido da câmera | Sem falta de memória; interface utilizável; processamento limitado |
| Privacidade | Inspecionar manifesto, backup, logs e exportações | Apenas permissões necessárias; nenhuma chave privada; exportação iniciada pelo usuário |

Gestos, MediaPipe, manipulação 3D e avaliação de Libras exigem testes específicos quando forem implementados. Não marcar esses itens como aprovados apenas porque suas telas abrem.

## Instalar o APK no celular

1. Baixe o APK disponibilizado pelo build ou pelo artefato do GitHub Actions.
2. No Android, autorize temporariamente a instalação pela aplicação usada para abrir o arquivo, quando o sistema solicitar.
3. Abra o APK e confirme a instalação. Não desative proteções gerais do aparelho.
4. Abra o NEXUS e conceda câmera ou microfone somente quando utilizar essas funções.

Com Android Platform Tools no computador, também é possível instalar com `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Uma atualização com assinatura diferente pode ser recusada; desinstalar apaga os dados locais, portanto exporte o que desejar preservar antes.

## Antes da Google Play

- Verifique no Play Console os requisitos vigentes de SDK de destino e compatibilidade. SDK 35 nesta base não é garantia de aceitação futura.
- Defina o identificador final do aplicativo antes da primeira publicação. Após publicar, o identificador não pode ser trocado para atualizar o mesmo aplicativo.
- Gere uma chave de upload em ambiente seguro e utilize Play App Signing. Nunca envie `.jks`, `.keystore`, senhas ou arquivos de credenciais ao Git.
- Para automação de release, armazene credenciais em GitHub Secrets ou em um cofre apropriado. O workflow de APK de teste não exige chaves de provedores de IA.
- Revise política de privacidade, consentimento de envio de texto e imagem, retenção/exclusão, serviço de voz e comportamento do servidor.
- Preencha Data Safety com o comportamento efetivo do aplicativo e dos serviços utilizados. Armazenamento local e envio a provedores são situações diferentes e devem ser descritas com precisão.
- Justifique permissões de câmera e microfone. Só adicione notificações, serviços em primeiro plano, acessibilidade ou outras permissões quando a função correspondente existir e precisar delas.
- Se futuramente usar `AccessibilityService`, implemente divulgação clara, consentimento, indicação de atividade e os requisitos atuais da Google Play. Esse roteiro não constitui aprovação para um uso específico.
- Execute testes em diferentes tamanhos de tela, idiomas e versões do Android, além de análise de falhas e consumo de bateria.
- Produza um Android App Bundle de release assinado e faça distribuição de teste no Play Console antes do lançamento público.

A preparação técnica do repositório não significa que a publicação na loja já foi realizada ou aprovada.
