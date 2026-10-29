# NEXUS 3D — cenas locais e duas mãos

## Alterações reais

A tela existente usa agora uma cena OpenGL ES 2.0 com até 12 objetos: cubo, esfera, pirâmide, cilindro, cone e toro. Todas as malhas são geradas localmente, sem bibliotecas, modelos ou downloads adicionais.

O usuário pode criar, selecionar, duplicar, excluir e recolorir objetos; alterar rotação e escala manualmente; salvar uma cena no armazenamento privado e abri-la depois. A abertura pausa a câmera, preserva a cena atual se o arquivo estiver corrompido e nunca restaura uma pinça pressionada. O arquivo JSON tem versão, limite de 64 KiB e validação de identificadores, seleção, cores, número de objetos e valores finitos.

A pinça inicia a seleção somente sobre o objeto e manipula apenas o objeto selecionado. Duas mãos podem mover, girar e redimensionar a mesma seleção. Uma pinça iniciada fora do objeto precisa ser solta antes de agarrá-lo. O rastreador pede duas mãos e mantém a adaptação de resolução existente. Perder a mão ou sair da tela libera a seleção. O controlador não injeta toques, não acessa o serviço do mouse e não compartilha seu estado com Mãos Livres.

## Arquivos

- `gestures/SceneState.kt`, `SceneController.kt`, `SceneMesh.kt`: cena, controle e geometria.
- `gestures/SceneCodec.kt`, `SceneStore.kt`: persistência atômica privada.
- `gestures/CubeScene.kt`: renderização GLES e tratamento de falhas.
- `gestures/GestureFeatureScreen.kt`: controles da tela 3D existente.
- `gestures/CubeController.kt`: preserva a API anterior e aceita a transformação do objeto selecionado.

## Verificação executada em 29/10/2026

19 testes JVM isolados passaram, com compilador Kotlin 2.1.20 e JUnit 4.13.2. Foram compilados os arquivos puros reais de controle, malhas e serialização; a declaração `HandFrame` foi extraída do código Android para a compilação isolada. Resultado salvo no ambiente em `/workspace/tooling/n42-scene-tests/junit.txt`.

Os testes cobrem identidade e exclusão independente, limite de objetos, duplicação, seleção por pinça, rejeição de pinças iniciadas fora, transformação por duas mãos, perda/liberação de mãos, rejeição de landmarks não finitos, serialização válida e corrompida e integridade topológica das seis malhas. As malhas têm faces não degeneradas e arestas trianguladas fechadas.

Foi adicionado `SceneRenderingTest.sixShapesAndMultiObjectSceneRenderOnActualEglContext`, ativado por `nexusSceneSmoke=true`. Ele cria um contexto EGL real, compila os shaders e verifica pixels das seis formas e uma cena múltipla. Sua execução Android continua pendente até o relatório de instrumentação. Testes JVM não comprovam renderização ou detecção física.

## Limitações e teste no aparelho

A compilação completa, a renderização no Galaxy A03 e a continuidade física de duas mãos precisam ser verificadas pelo fluxo principal. Não há posicionamento espacial de realidade aumentada, colisões físicas nem seleção simultânea de objetos diferentes por cada mão. Salvar mantém uma cena local; não armazena quadros da câmera. A câmera 3D pausa fora do NEXUS.

1. Abra NEXUS 3D sem ativar câmera. Crie as seis formas, selecione uma por vez, altere cor, rotação e tamanho e confirme que as demais não mudam.
2. Duplique, exclua e reinicie; salve, saia e abra a cena salva.
3. Ative câmera com boa iluminação. Faça uma nova pinça sobre um objeto, mova e solte; tente uma pinça fora e confirme que entrar segurando não o agarra.
4. Comece duas pinças sobre o mesmo objeto e afaste as mãos lentamente; observe escala, rotação e movimento. Registre o tempo de análise exibido e eventuais falhas.
5. Pare a câmera, volte ao mouse e confira clique e rolagem. Os modos são independentes e não podem disputar a mesma câmera.
