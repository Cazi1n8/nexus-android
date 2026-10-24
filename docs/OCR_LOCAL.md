# Ler texto offline no NEXUS Vision

O NEXUS usa Tesseract4Android 4.9.0, com Tesseract OCR 5.5.1 e modelos `tessdata_fast` de português e inglês. É reconhecimento real de texto impresso em uma foto. Não interpreta objetos, circuitos ou cenas e não é compreensão contínua de vídeo. Textos manuscritos, imagens desfocadas, colunas complexas e letras pequenas podem produzir erros; confira o resultado.

## No celular

1. Abra **NEXUS Vision**.
2. Toque em **Escolher imagem** ou **Abrir câmera** para fotografar uma página. A galeria funciona mesmo sem câmera disponível ou sem permissão de câmera.
3. Toque em **Ler texto no aparelho**. Os modelos já vêm no APK; essa leitura não precisa de internet nem de conta.
4. Confira o resultado antes de copiar, exportar, ouvir ou salvar como memória. **Salvar como memória** abre uma confirmação com edição do trecho; o resultado não vira memória automaticamente.

O ditado preenche uma pergunta editável. A foto só é enviada ao provedor multimodal configurado quando você autoriza esse uso e toca em **Analisar com a IA**. O OCR é independente dessa análise. O reconhecedor de fala e a voz escolhidos no Android podem usar rede; selecione uma voz instalada localmente para ouvir texto offline.

## Preparar a compilação

Antes do Gradle, execute no computador:

```sh
python3 scripts/fetch_ocr_models.py
```

O script baixa somente os dois arquivos oficiais fixados abaixo, valida tamanho e SHA-256 e os cria em `app/src/main/assets/ocr/`. Um arquivo existente com conteúdo diferente causa erro e é preservado. Downloads interrompidos não deixam um modelo parcial instalado. Os arquivos são ignorados pelo Git; os três workflows Android os preparam antes da compilação. O APK contém os modelos completos, sem download durante o uso.

Para preparar sem rede usando uma cópia previamente baixada:

```sh
python3 scripts/fetch_ocr_models.py --source-dir /caminho/dos/modelos
```

O diretório precisa conter os dois arquivos com os mesmos tamanhos e hashes. A origem é o repositório oficial [`tesseract-ocr/tessdata_fast`](https://github.com/tesseract-ocr/tessdata_fast/tree/87416418657359cb625c412a48b6e1d6d41c29bd), revisão imutável `87416418657359cb625c412a48b6e1d6d41c29bd`:

| Arquivo | Bytes | SHA-256 |
| --- | ---: | --- |
| `por.traineddata` | 1.982.756 | `c4932b937207a9514b7514d518b931a99938c02a28a5a5a553f8599ed58b7deb` |
| `eng.traineddata` | 4.113.088 | `7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2` |

São 6.095.844 bytes antes da compressão do APK. Na primeira leitura o aplicativo prepara uma cópia privada para o motor OCR. Os textos de licença são incluídos em `app/src/main/assets/licenses/`.

## Escolha técnica e privacidade

A dependência é `com.github.adaptech-cz.Tesseract4Android:tesseract4android:4.9.0`, publicada no JitPack pelo projeto [Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android/tree/4.9.0). Usamos a variante padrão, de uma thread, para conter o processamento em aparelhos modestos. A biblioteca é de código aberto e a leitura ocorre no dispositivo, sem SDK de análise remota. Não há serviço de OCR pago, chave secreta, download automático de idioma ou upload da foto nessa função.

O AAR verificado oferece `arm64-v8a`, `armeabi-v7a`, `x86_64` e `x86`. As bibliotecas de 64 bits desse AAR têm segmentos ELF com alinhamento de 16 KB. As de 32 bits usam 4 KB; isso não é uma garantia de funcionamento em todo aparelho. A compilação e os testes automatizados precisam ser complementados por testes físicos de memória, velocidade, câmera e qualidade das imagens, inclusive no Galaxy S9+.

O reconhecimento é limitado a uma imagem por solicitação. O tamanho da imagem é reduzido antes de processar para controlar memória. Cancelar, trocar a foto ou sair da tela invalida o resultado pendente. Não há análise periódica da câmera, classificação geral de imagens ou tradução automática nessa função.

## Verificações

```sh
python3 -m unittest discover -s scripts/tests -p test_fetch_ocr_models.py -v
```

Esses testes cobrem o catálogo pinado, reuso offline, corrupção, tamanho incorreto, interrupção, links simbólicos, mudança de HTTPS e instalação concorrente. Eles verificam a preparação dos modelos, não a qualidade de reconhecimento do motor Android. Os resultados de compilação e de execução instrumentada constam no relatório da versão.
