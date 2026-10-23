# APK de teste do NEXUS 0.1.0

Verificação em **23/10/2026**, por volta de **12h42 em São Paulo** (15h42 UTC). O APK existe e a compilação local terminou com sucesso. Instalação e uso em celular físico ainda precisam ser validados.

## Arquivo produzido

| Item | Resultado observado |
| --- | --- |
| Arquivo | `NEXUS-v0.1.0-debug.apk` |
| Local da entrega | `/workspace/NEXUS-v0.1.0-debug.apk` |
| Tamanho | 74.930.679 bytes |
| Identificador Android | `br.com.nexus.assistant` |
| Versão | 0.1.0, build de depuração |
| Android mínimo | Android 8.0 / API 26 |
| Assinatura | Assinatura de depuração; verificação v2 aprovada pelo `apksigner` |

SHA-256 do arquivo entregue:

```text
0dd899e86f2dea5554c4413d75fa646542417c77e243baaac1490fcacbab64e7
```

Esta assinatura serve para testes. A distribuição pela Google Play exige assinatura de release e revisão dos requisitos atuais da loja.

## Compilação e testes

Evidências da execução local, registradas em `/workspace/tooling/build-final.log` e nos relatórios Gradle em `app/build/`:

- `BUILD SUCCESSFUL in 1m 35s`; 85 tarefas, sendo 28 executadas e 57 atualizadas.
- **27 testes JVM aprovados**, sem falhas, erros ou testes ignorados. Abrangem transporte de IA, comandos, fragmentação da fala e geometria de gestos.
- Android Lint: **zero erros, 28 avisos e uma informação**. Os avisos ainda precisam de revisão para estabilidade e distribuição.
- APK dos testes instrumentados compilado. Há seis testes instrumentados escritos, mas **não foram executados**.

A tentativa de inicializar o ambiente Android para instrumentação não completou o boot; o ADB encontrou restrição de escrita em `/home/agent/.android`. Isso não comprova abertura do app, navegação, permissões, câmera, reconhecimento de fala ou desempenho em aparelho. Esses itens permanecem na [matriz de testes manuais](TESTES_E_DISTRIBUICAO.md).

O workflow do [repositório GitHub](https://github.com/Cazi1n8/nexus-android) também foi **aprovado**: [execução 39980316020](https://github.com/Cazi1n8/nexus-android/actions/runs/39980316020), commit `d61a978aeff3d1e9f1a331a4fcb326217b11da15`, `BUILD SUCCESSFUL in 4m 2s`. Testes, lint, APK e publicação dos artefatos terminaram com sucesso.

O [artefato nexus-debug-3](https://github.com/Cazi1n8/nexus-android/actions/runs/39980316020/artifacts/12513442249) contém o APK e `SHA256SUMS.txt`. A cópia extraída está em `/workspace/NEXUS-v0.1.0-GitHub-debug.apk`, com **74.290.775 bytes**, assinatura v2 verificada e SHA-256:

```text
73c9ebdb5f56f8baf6317a8e60e7c5dc7590451e9f391a0bc5ac177ca16a7c22
```

O ZIP baixado foi comparado ao digest publicado pelo GitHub, e o APK foi comparado ao checksum incluído no artefato. Builds locais e do CI usam certificados de depuração diferentes; escolha uma origem para os testes. A atualização entre origens pode exigir desinstalação, que apaga dados locais.

## Instalar no celular

1. Baixe `NEXUS-v0.1.0-GitHub-debug.apk` da entrega ou extraia `app-debug.apk` do artefato do GitHub e abra o arquivo no Android.
2. Caso o sistema peça, autorize a instalação pelo aplicativo que abriu o APK, como navegador ou gerenciador de arquivos.
3. Abra **NEXUS**. Câmera e microfone são solicitados somente ao abrir as funções correspondentes.
4. Em **Configurações**, informe um servidor de IA, modelo e token de sessão válidos; leia a autorização de envio antes de ativá-la.

O APK não embute um LLM nem um serviço gratuito hospedado. Chat e análise de imagens dependem do servidor configurado; imagens também exigem um modelo visual. A prova separada de IA está em [RELATORIO_IA_REAL.md](RELATORIO_IA_REAL.md).
