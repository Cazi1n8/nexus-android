# Assinatura estável e distribuição do NEXUS

O APK alpha.4 publicado foi bloqueado novamente pelo Play Protect no aparelho do usuário. **Uma assinatura de lançamento, por si só, não libera esse bloqueio.** A assinatura identifica quem pode atualizar o aplicativo; a análise e eventual revisão do Play Protect são uma etapa separada.

## Estado encontrado na auditoria

- O workflow de teste compila `assembleDebug`. Seu certificado é criado no executor temporário e mudou entre entregas. O certificado SHA-256 da alpha.4 publicada é `65297709dfb7a0149397313c1249cf40b3392913a4050f0af0c8a5951c394c1f`.
- O tipo `release` já habilita redução de código e recursos. As regras preservam a ponte JNI do NEXUS e os wrappers JNI do Tesseract; Room e MediaPipe também fornecem regras de consumidor. Isso não substitui compilar e executar o build reduzido.
- Antes da configuração de assinatura desta etapa, `release` não tinha `signingConfig`: um APK de release produzido assim ficaria sem assinatura e não seria instalável. Um AAB também precisa ser assinado para distribuição.
- O manifesto declara câmera, microfone, notificações, serviço de câmera em primeiro plano, rede e o serviço oficial de acessibilidade para gestos autorizados. Não declara leitura de SMS, contatos, localização, instalação de pacotes ou acesso amplo aos arquivos.
- A acessibilidade permite executar gestos, mas não recuperar conteúdo de janelas; seu código não coleta eventos. Essa continua sendo uma capacidade sensível, sujeita às políticas e à análise do Google. Não foi removida ou disfarçada para evitar a análise.

## O que precisa ficar protegido

O arquivo de chaves e as senhas devem pertencer ao responsável pelo aplicativo. Guarde cópias de recuperação fora do repositório, em armazenamento protegido, e registre o alias. Perder a chave pode impedir futuras atualizações de APKs assinados por ela. Não publique `.jks`, `.keystore`, sua representação em Base64 ou senhas em commits, issues, logs ou arquivos do aplicativo.

Não gere uma chave nova a cada compilação. Use a mesma identidade para a linha de distribuição escolhida. Os certificados e seus hashes são públicos; a chave privada não é.

## Criar uma chave no computador

Esta etapa usa o `keytool` incluído no JDK. Não exige instalar ferramentas no celular. Trabalhe em uma pasta privada **fora da cópia do repositório**:

```bash
keytool -genkeypair -v -keystore nexus-release.jks -storetype JKS -alias nexus-release -keyalg RSA -keysize 3072 -validity 10000
```

O comando pede os dados do responsável e as senhas interativamente. Use informações reais e senhas fortes. O alias do exemplo é `nexus-release`; se escolher outro, configure o mesmo valor no build.

Confira o certificado, sem exportar a chave privada:

```bash
keytool -list -v -keystore nexus-release.jks -alias nexus-release
```

Para transportar o arquivo ao campo de segredo do GitHub, crie uma representação Base64 na mesma pasta privada:

```bash
python3 -c "import base64,pathlib; pathlib.Path('nexus-release.base64.txt').write_text(base64.b64encode(pathlib.Path('nexus-release.jks').read_bytes()).decode('ascii'))"
```

**Base64 não é criptografia.** Esse arquivo contém a mesma chave privada e deve receber a mesma proteção. Após salvar o segredo e conferir sua recuperação, remova a cópia temporária de transporte da máquina, preservando o backup seguro do keystore.

## Configurar o GitHub sem colocar chaves no código

No repositório, abra **Settings → Secrets and variables → Actions → New repository secret**. Essa configuração pode ser acessada pelo navegador; não requer Android SDK no celular. A conexão atual usada para editar o projeto não oferece uma operação de escrita nesses segredos.

| Segredo | Conteúdo |
| --- | --- |
| `NEXUS_RELEASE_KEYSTORE_BASE64` | Todo o conteúdo de `nexus-release.base64.txt` |
| `NEXUS_RELEASE_STORE_PASSWORD` | Senha do arquivo de chaves |
| `NEXUS_RELEASE_KEY_ALIAS` | Alias escolhido, por exemplo `nexus-release` |
| `NEXUS_RELEASE_KEY_PASSWORD` | Senha da entrada da chave; pode ser igual à senha do arquivo, se assim foi criada |

O workflow [signed-release-review.yml](../.github/workflows/signed-release-review.yml) aceita somente a branch `main` e é iniciado manualmente em **Actions → NEXUS — pacote assinado para revisão → Run workflow**. Ele ainda não foi executado com uma chave definitiva. Ele reconstrói o keystore em uma pasta temporária do executor e fornece ao Gradle as variáveis abaixo. O arquivo temporário e as senhas não devem entrar no cache ou nos artefatos.

| Variável de ambiente do Gradle | Origem |
| --- | --- |
| `NEXUS_RELEASE_STORE_FILE` | Caminho temporário do keystore decodificado pelo workflow |
| `NEXUS_RELEASE_STORE_PASSWORD` | Segredo de mesmo nome |
| `NEXUS_RELEASE_KEY_ALIAS` | Segredo de mesmo nome |
| `NEXUS_RELEASE_KEY_PASSWORD` | Segredo de mesmo nome |

O script `scripts/prepare_release_keystore.py` recusa valores ausentes, Base64 inválido, caminho relativo e arquivos existentes; grava com permissão `0600`. O workflow remove a chave temporária ao terminar, não a envia a artefatos e mantém o cache somente para leitura. O Gradle só aplica a configuração assinada quando recebe `-PnexusReleaseSigning=true`; sem as variáveis exigidas essa opção falha imediatamente. Sem essa opção, `assembleRelease` pode continuar produzindo um pacote sem assinatura, destinado a desenvolvimento e não a instalação.

A falta de um segredo interrompe a assinatura com uma mensagem compreensível. **Não substituir por uma chave de depuração nem anunciar um APK sem assinatura como instalável.** Senhas devem ser passadas pelo ambiente, sem valores literais na linha de comando ou impressão dos segredos.

## Conferir antes de distribuir

Após compilar o release assinado, confira assinatura e identidade:

```bash
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

O certificado esperado deve ser o da chave escolhida. O modo release precisa ter `debuggable=false`; a compatibilidade de ABI, alinhamento ZIP/ELF, integridade do GGUF/OCR e funcionamento de JNI/OCR/gestos também precisam de verificação. Um resultado de build aprovado não equivale a teste físico ou aprovação do Play Protect.

A compilação futura pode gerar `assembleRelease` para APK e `bundleRelease` para AAB. O AAB é destinado ao Play Console e **não é instalado diretamente** pelo gerenciador de arquivos. O modelo incluído aumenta bastante o pacote; valide os limites atuais de tamanho e a entrega de assets no Play Console antes de declarar o projeto pronto para a loja.

Para Google Play, configure Play App Signing e guarde separadamente a função da chave de upload e da chave de assinatura do aplicativo. Elas podem ter certificados diferentes. Planeje a identidade de assinatura entre a distribuição direta e a loja antes de prometer atualização entre os dois canais. A versão de release também precisa de `versionCode` maior nas próximas atualizações.

## Bloqueio atual e dados existentes

Não desinstale o NEXUS atual enquanto a instalação nova estiver bloqueada. Exporte conversas importantes antes de qualquer remoção; a exportação não contém imagens nem token. Trocar de certificado não permite atualização direta de uma instalação anterior pelo Android.

O material de revisão do aviso está em [PLAY_PROTECT_REVISAO.md](PLAY_PROTECT_REVISAO.md). A solicitação oficial depende da conta e dos dados do responsável e da avaliação do Google. Não foi enviada por este ambiente. Não desative o Play Protect, não oculte permissões nem altere o nome/pacote com o objetivo de contornar a análise.

## Verificações realizadas nesta etapa

Passaram 72 testes dos scripts, incluindo seis novos testes da preparação protegida da chave. O Gradle carregou a configuração; uma solicitação assinada sem credenciais foi rejeitada, e `validateSigningRelease` passou com uma chave temporária de validação, apagada ao final. Nenhum APK novo ou chave de produção foi gerado. O build release reduzido e o workflow assinado ainda precisam de execução e testes com a identidade definitiva. [Evidência local](evidencias/release-signing-validation.json).

A publicação automática de novos APKs debug nas Releases ficou pausada por padrão. O workflow de build ainda compila e disponibiliza artefatos de teste. A variável do repositório `NEXUS_PUBLICAR_APK_DEBUG=true` permite a publicação explícita de debug; isso não oferece assinatura estável nem aprovação do Google.
