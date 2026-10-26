# NEXUS: começar pelo celular

Esta é uma versão de teste em desenvolvimento. O projeto já existe e está salvo em https://github.com/Cazi1n8/nexus-android. Você não precisa instalar ferramentas de programação no celular.

## 1. Baixar o arquivo certo

Abra o [artefato de teste 16 — alpha.2](https://github.com/Cazi1n8/nexus-android/actions/runs/40389844327/artifacts/12682941109) e entre na sua conta do GitHub. Ele baixa um **ZIP grande, aproximadamente 1,65 GB**. No gerenciador de arquivos do celular, extraia o ZIP e localize **app-debug.apk**. O arquivo `SHA256SUMS.txt` contém o resumo do APK gerado nessa compilação.

O botão verde **Code → Download ZIP** do repositório baixa o código-fonte, que não é o aplicativo instalável. Os artefatos do Actions expiram; este arquivo tem retenção de 30 dias. Reserve cerca de 8 GB livres para download, extração, instalação e preparação da IA; uma atualização pode exigir mais espaço.

## 2. Instalação e seus dados

Mantenha o Play Protect ativado. Prossiga somente se a verificação do Android permitir a instalação. Se ele bloquear o arquivo, registre a mensagem e siga a [auditoria e revisão oficial](AUDITORIA_PLAY_PROTECT.md). Não existe aprovação do Google comprovada para esta versão.

O APK usa assinatura de teste. Se você já tem outro NEXUS instalado, o certificado pode ser diferente e o Android pode recusar a atualização. **Não apague a instalação atual para tentar resolver o bloqueio.** Antes de qualquer troca de instalação, exporte seus textos em **Memória e histórico → Exportar**; essa exportação não inclui imagens ou token. A assinatura definitiva e estável ainda precisa ser configurada pelo responsável.

## 3. Preparar e testar a IA

Abra **Chat IA** e aguarde **IA local pronta para conversar**. O aplicativo copia e verifica um modelo que já está no APK. Essa preparação não baixa outro modelo. O perfil automático considera memória e armazenamento do aparelho; não há um APK diferente para cada modelo de celular.

Em **Personalidade**, escreva o seu nome no campo do usuário. O assistente continua se chamando NEXUS.

Experimente:

- `Quanto é 25 vezes 18?` → a calculadora deve retornar 450.
- `10+10÷10×10` → o resultado deve ser 20.
- Anexe uma foto com texto e peça `Leia esse texto` → usa OCR local.
- Em **NEXUS Vision**, inicie a classificação de objetos ao vivo. Ela analisa quadros periódicos e mostra hipóteses; não é compreensão geral de vídeo.

**A qualidade da conversa ainda não está resolvida.** Os testes reais encontraram erros de interpretação e explicação nos modelos pequenos. Perguntas informais e textos precisam ser conferidos. O provedor online opcional exige infraestrutura/configuração real; não existe um serviço NEXUS online público implantado por este projeto.

## 4. Usar a voz fora do aplicativo

Em **Assistente de voz**, teste se o celular consegue falar em Português (Brasil). Se faltarem dados de voz, confira as opções oficiais de síntese de fala do Android.

Para continuar fora da tela, abra **Voz fora do aplicativo**, leia a explicação, autorize a sessão e toque em **Iniciar sessão de voz**. A opção contínua alterna escuta, resposta e fala por até 15 minutos ou 30 perguntas. O modo básico espera novo toque em **OUVIR**. A notificação permite **PARAR** e **ENCERRAR TUDO**.

Ainda não há detector permanente de “Ei, NEXUS”. Você pode começar sua pergunta com essa frase depois de ativar a escuta. O serviço de fala do Android pode precisar de internet. O funcionamento real no S9+ ainda precisa de teste.

## 5. Usar o mouse fora do aplicativo

Abra **Mouse virtual** ou **Central de controle**. O controle global exige consentimento, câmera e ativação manual do serviço NEXUS nas configurações oficiais de acessibilidade. Acessibilidade é opcional para usar o Chat.

Depois de iniciar a sessão global, use o indicador para mover, pinça para selecionar e dois dedos para rolar. Confira a orientação dos gestos na tela. Sensibilidade e calibração ficam na área do mouse. A notificação permite parar a câmera e o cursor. Alguns aplicativos ou versões do Android podem restringir os gestos.

Mouse e voz têm serviços separados. O 3D continua separado do mouse global. Vision, Libras e Mãos Livres ainda usam a câmera vinculada às respectivas telas. Não foram comprovados os 13 cenários físicos de segundo plano nem a ausência de travamentos no seu celular.

## 6. Libras e continuação pelo computador

O treino básico permanece aberto após o acerto. As candidatas L/V/Y são experimentais; o app não reconhece nem certifica o alfabeto inteiro. Não use o resultado como validação completa de Libras.

Quando estiver no computador, siga o [README](README.md) para abrir e compilar o mesmo projeto. O [progresso](PROGRESSO_NEXUS_AI_3.md), os [testes da IA](TESTES_NEXUS_AI.md) e os [testes de segundo plano](TESTES_SEGUNDO_PLANO.md) registram o que foi executado e o que ainda falta.
