# NEXUS para Android

Assistente Android nativo em Kotlin e Jetpack Compose, com interface escura em azul e ciano. O projeto prioriza uma primeira versão de chat por texto e imagem, voz e dados locais; as funções de mãos e 3D estão em validação.

**Estado real em 23/10/2026:** o código e a configuração de CI foram criados. A rede foi liberada, o JDK 17 e o Gradle 8.11.1 foram instalados com checksums oficiais, e o SDK está em preparação. **Ainda não há resultado de compilação Android aprovado nem APK confirmado.** A conexão GitHub foi confirmada; falta um repositório acessível para enviar o projeto. Veja [PROGRESSO.md](PROGRESSO.md) para os resultados e pendências.

## O que está no projeto

- Camadas separadas de interface, ViewModel, dados e integração real com modelos de IA.
- Cliente OpenAI-compatible com contexto limitado, imagens, personalidade e memórias autorizadas. Sem modelo configurado, apresenta erro; não inventa uma resposta de IA.
- Room para conversas e memórias, DataStore para preferências e Android Keystore para proteger o token da sessão.
- SpeechRecognizer e TextToSpeech do Android, com tratamento de disponibilidade, silêncio e interrupção.
- CameraX para captura explícita de imagem; código MediaPipe para rastreamento local de mãos e exercícios de interação dentro do NEXUS.
- Servidor opcional em Python, sem dependências externas, que mantém credenciais de provedores fora do APK e pode usar Ollama gratuitamente no seu computador.
- Workflow para testar, executar lint e produzir APK de depuração no GitHub Actions, quando o repositório for publicado.

A presença do código não comprova funcionamento em um celular. Compilação Android, teste com modelo real e testes em aparelho continuam pendentes. A primeira fase só estará concluída depois dessas verificações.

## Começando pelo celular

Você não precisa instalar ferramentas de desenvolvimento no celular. Preserve o projeto completo e consulte [como guardar no GitHub e baixar um APK pelo Actions](docs/GITHUB.md). Para a compilação automática funcionar, os arquivos precisam estar extraídos na raiz do repositório, incluindo a pasta oculta `.github`.

Não envie senhas, tokens, `.env.local`, `local.properties` ou chaves de assinatura ao GitHub. O build de teste não precisa de uma chave de IA.

## Compilar depois no computador

Requisitos:

| Ferramenta | Versão desta base |
| --- | --- |
| Android mínimo no celular | Android 8.0 / API 26 |
| Java | JDK 17 recomendado; Java 21 disponível neste ambiente |
| Android SDK | Platform 35 e Build Tools 35.0.0 |
| Android Gradle Plugin | 8.9.2 |
| Gradle | 8.11.1 |
| Kotlin | 2.1.20 |
| Python | 3.10 ou superior para scripts e servidor opcional |

Abra a pasta no Android Studio e instale o SDK solicitado. Para apontar um SDK já instalado, crie `local.properties` na raiz com `sdk.dir=/caminho/do/seu/Android/Sdk`. Esse arquivo é local e está ignorado pelo Git. Você também pode configurar `ANDROID_HOME` no seu computador.

Antes de compilar as funções de mãos, baixe o modelo oficial versionado do MediaPipe:

```bash
python3 scripts/fetch_hand_model.py
```

O script verifica o tamanho, a estrutura do pacote e informa o SHA-256; também aceita `--sha256` com um valor esperado obtido por um canal confiável. O modelo não foi baixado neste ambiente. Sem ele, as funções de mãos informam indisponibilidade; não existe rastreamento simulado.

Em Linux/macOS:

```bash
chmod +x gradlew
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Para a verificação completa de build, incluindo outras variantes:

```bash
./gradlew build
```

No Windows, use `gradlew.bat` no lugar de `./gradlew`.

**Gradle Wrapper oficial incluído:** `gradlew`, `gradlew.bat` e `gradle-wrapper.jar` foram gerados pelo Gradle 8.11.1 obtido da distribuição oficial com SHA-256 verificado. O checksum da distribuição está fixado em `gradle-wrapper.properties`; você não precisa instalar Gradle separadamente. O primeiro uso requer rede para baixar a distribuição e as dependências.

Se o build for aprovado, o APK estará em `app/build/outputs/apk/debug/app-debug.apk`. [Instruções de instalação e testes no celular](docs/TESTES_E_DISTRIBUICAO.md).

## Configurar a IA gratuitamente com Ollama

O app ainda não embute um modelo de linguagem no Android. Você escolhe o servidor e o modelo. Uma opção sem cobrança de API é executar [Ollama](https://ollama.com/) no seu próprio computador. O consumo de memória e o tempo de resposta dependem do computador e do modelo; modelos visuais exigem mais recursos.

1. Instale o Ollama pelas instruções oficiais.
2. Baixe um modelo com visão, por exemplo:

```bash
ollama pull gemma3:4b
```

3. Mantenha o Ollama em execução. Se não estiver iniciado pelo aplicativo do sistema, execute `ollama serve` em outro terminal.
4. Na raiz do NEXUS, gere a configuração privada do servidor:

```bash
python3 server/setup_local.py
python3 server/nexus_gateway.py --env-file server/.env.local
```

O primeiro comando cria `server/.env.local` com um token aleatório e permissão de arquivo restrita. Não imprime o token nem sobrescreve um arquivo existente. Abra esse arquivo apenas em seu computador para copiar o token para o NEXUS; não o compartilhe no chat ou em capturas de tela. A sessão expira após oito horas de execução do servidor. Gere outro token e reinicie o servidor para revogar o anterior.

5. Nas configurações do app, informe:

| Campo | Exemplo de desenvolvimento |
| --- | --- |
| URL da API em emulador Android | `http://10.0.2.2:8787/v1` |
| Modelo | `gemma3:4b`, igual a `NEXUS_MODEL` no servidor |
| Token da sessão | Valor de `NEXUS_ACCESS_TOKEN` no seu arquivo privado |
| Autorização de envio | Leia e autorize o envio ao servidor escolhido |

Para testar em celular por USB, uma alternativa local é `adb reverse tcp:8787 tcp:8787` e URL `http://localhost:8787/v1` em um build de depuração. Sem esse encaminhamento, `localhost` no celular é o próprio celular, não seu computador.

Para uso remoto, publique o servidor atrás de um proxy com certificado HTTPS válido e configure, por exemplo, `https://seu-dominio.example/v1`. HTTP só é aceito pelo app em endereços locais específicos e em builds de depuração. Não exponha o servidor Python diretamente à internet. Veja [server/README.md](server/README.md).

Você também pode usar uma API OpenAI-compatible externa: defina `UPSTREAM_BASE_URL`, `UPSTREAM_API_KEY` e `NEXUS_MODEL` **no servidor**. Custos e política de dados dependem do provedor. A chave do provedor não é um campo do aplicativo e nunca deve ser incluída no APK.

## Dados, voz e permissões

Conversas e memórias ficam no armazenamento privado do app; o banco Room não tem criptografia adicional nesta versão. O backup automático está desativado. O usuário cria suas memórias manualmente e pode editá-las e excluí-las. A exportação JSON contém textos e memórias, sem credenciais e sem os arquivos de imagem; proteja o arquivo exportado.

Mensagens, memórias autorizadas e imagens anexadas são enviadas ao servidor escolhido apenas após autorização. O rastreamento de mãos é local. Câmera e microfone têm controles visíveis e permissões solicitadas conforme o uso. O serviço de reconhecimento de fala do Android pode precisar de rede quando não houver reconhecimento local disponível; algumas vozes de síntese também podem utilizar o serviço do fabricante.

Não há escuta permanente, serviço oculto ou controle global de outros aplicativos por acessibilidade nesta base. A frase “Ei, NEXUS!” em segundo plano, o bloqueio do celular e uma avaliação completa de Libras permanecem fora da primeira entrega validada.

## Testes e documentação

```bash
python3 -m unittest discover -s server/tests -v
```

Resultado mais recente neste ambiente, após liberar a rede: **44 testes Python aprovados, sem ignorados**, incluindo o transporte HTTP local. Os upstreams desses testes são controlados; isso não comprova a qualidade de um modelo de IA real. A compilação e os testes Android estão em preparação. [Relatório do servidor](docs/RELATORIO_TESTES_SERVIDOR.md).

- [Estado de cada função e fases](PROGRESSO.md)
- [Arquitetura e decisões técnicas](docs/ARQUITETURA.md)
- [GitHub, Actions e continuidade no computador](docs/GITHUB.md)
- [Testes em aparelho, instalação e preparação para a Google Play](docs/TESTES_E_DISTRIBUICAO.md)

SDK de destino, política de privacidade, Data Safety, assinatura de release e requisitos de acessibilidade precisam ser revistos antes da publicação na Google Play. O workflow gera somente um APK de teste.
