# Guardar o projeto no GitHub

**Versão atual 2.0:** o código continua no mesmo repositório. O workflow publica APKs de teste em [Releases](https://github.com/Cazi1n8/nexus-android/releases), com download direto após aprovação. Confira o estado real em [PROGRESSO_NEXUS_2.md](../PROGRESSO_NEXUS_2.md). Os exemplos de build 0.1 abaixo são históricos.

O Git registra versões do código. O GitHub guarda uma cópia remota desse histórico e pode compilar o aplicativo automaticamente. A pasta local do projeto e um repositório publicado no GitHub são entregas diferentes: confira em [PROGRESSO.md](../PROGRESSO.md) se o envio remoto realmente aconteceu.

**Repositório deste projeto:** https://github.com/Cazi1n8/nexus-android. O código já foi enviado e o [build de teste foi aprovado](https://github.com/Cazi1n8/nexus-android/actions/runs/39980316020). Para instalar, baixe o artefato `nexus-debug-3`, extraia o ZIP e abra `app-debug.apk` no Android. As instruções de primeiro envio abaixo servem como referência para outras cópias.

## Começando pelo celular

Você não precisa instalar Android Studio no celular para usar um APK de teste. Depois que o código estiver em um repositório GitHub e o workflow estiver ativo, a compilação pode acontecer no GitHub Actions.

1. Entre no GitHub pelo navegador e crie um repositório vazio chamado, por exemplo, `nexus-android`. Escolha privado se ainda não quiser compartilhar o código.
2. Preserve a estrutura do projeto completo. Para o primeiro envio, use um computador ou um ambiente de desenvolvimento com Git. Se receber um arquivo ZIP, guarde-o e extraia antes de enviar os arquivos; publicar somente o ZIP não permite que o workflow encontre o projeto.
3. No navegador é possível usar **Add file → Upload files** para pequenos conjuntos de arquivos, respeitando os limites do GitHub. Confira se a pasta oculta `.github/workflows` e o Gradle Wrapper também foram enviados. Para um projeto completo, prefira Git.
4. Nunca envie tokens, chaves privadas, arquivos `.env`, `local.properties`, bancos de dados pessoais ou chaves de assinatura. Confira o `.gitignore` antes do primeiro envio.

Se o ambiente não estiver conectado à sua conta, não é possível criar ou enviar um repositório em seu nome sem configurar essa conexão. Não compartilhe senhas ou tokens em mensagens de chat.

## Cópia de segurança desta entrega

O arquivo `NEXUS-projeto.zip` inclui o código na pasta `nexus/`, inclusive `.github`, e uma cópia do histórico chamada `NEXUS-historico.bundle`. Guarde o ZIP no celular ou no seu armazenamento de arquivos para preservar esta entrega enquanto o GitHub não estiver conectado.

Depois, no computador, você pode extrair e abrir `nexus/` diretamente no Android Studio. Para restaurar também os commits Git, use o bundle como origem de um novo clone:

```bash
git clone NEXUS-historico.bundle nexus-restaurado
cd nexus-restaurado
git remote remove origin
git remote add origin https://github.com/SEU_USUARIO/nexus-android.git
git push -u origin main
```

Crie antes o repositório vazio na sua conta e autentique pelo fluxo oficial. Essa cópia de segurança local não significa que o projeto já tenha sido publicado no GitHub.

## Primeiro envio usando Git

Execute dentro da pasta que contém `settings.gradle.kts`. Substitua o endereço pelo seu repositório vazio:

```bash
git init
git add .
git status
git commit -m "Inicia base Android do NEXUS"
git branch -M main
git remote add origin https://github.com/SEU_USUARIO/nexus-android.git
git push -u origin main
```

Revise `git status` e os arquivos preparados antes do commit. Se o projeto já tiver um histórico Git, preserve esse histórico; não é necessário inicializar novamente nem recriar commits existentes. Se já existir um remoto `origin`, use `git remote -v` para conferir o endereço antes de alterar qualquer coisa. Não utilize `push --force` para resolver conflitos sem entender as alterações.

A autenticação deve ser feita pelo mecanismo oficial do GitHub. Em um computador ou ambiente confiável, o GitHub CLI pode auxiliar:

```bash
gh auth login
```

Siga o fluxo oficial do navegador. Essa etapa só é necessária onde a conexão ainda não foi configurada. Tokens não devem aparecer no código, na URL do remoto ou em capturas de tela.

## Baixar o APK pelo GitHub Actions

1. Abra o repositório e selecione **Actions**.
2. Abra o workflow Android e a execução correspondente ao commit desejado. O arquivo do workflow deve estar em `.github/workflows/`.
3. Aguarde o resultado. Se uma etapa falhar, abra o log: não haverá um APK válido para essa execução até o erro ser corrigido.
4. Em uma execução aprovada, localize **Artifacts** e baixe o artefato de depuração disponibilizado pelo workflow. O GitHub normalmente entrega um ZIP; extraia o APK antes de instalar.
5. Anote o commit da execução. Isso ajuda a saber exatamente qual versão está no celular.

Quando o workflow permitir execução manual, use **Run workflow** na página dele. O uso de Actions depende da disponibilidade e dos limites da sua conta. Os artefatos expiram conforme a retenção configurada; o histórico de código continua no repositório.

O workflow de depuração compila e testa código. Ele não configura um modelo de IA, não exige chaves de provedores e não publica o aplicativo na Google Play. A configuração da ponte de IA é feita separadamente conforme o README.

## Continuar depois no computador

```bash
git clone https://github.com/SEU_USUARIO/nexus-android.git
cd nexus-android
```

Abra essa pasta no Android Studio, instale o SDK solicitado e siga o [README](../README.md). Antes de trabalhar em outro dispositivo, envie seus commits; antes de retomar em uma cópia já existente, atualize o código com `git pull` e confira possíveis conflitos.
