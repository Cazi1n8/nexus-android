# Libras NEXUS 4.1 — revisão de 29/10/2026

## Escopo real

O módulo continua sendo preparação e estudo introdutório. A avaliação por câmera mede configurações geométricas limitadas. Não reconhece Libras completa e não certifica sinais, tradução, gramática ou fluência.

## Mudanças implementadas

- Três aulas curtas, incluídas no aplicativo e disponíveis offline: Libras como língua; parâmetros a observar; prática e revisão responsável. Cada aula tem pergunta conceitual, explicação e fonte educacional para aprofundar o estudo.
- A leitura/revisão é salva separadamente das contagens de exercícios com câmera. A adição do campo preserva o progresso existente. Exportação e exclusão incluem esse campo.
- Os dois exercícios preparatórios recebem observação por dedo: estendido, flexionado ou intermediário/ambíguo. Uma correção geométrica só aparece quando há evidência observável; mão ausente, cortada, lateral ou ocluída continua sem avaliação.
- É possível repetir o exercício sem encerrar a câmera. A atividade permanece ativa após registrar uma configuração estável, até o usuário parar ou o ciclo de vida do Android pausar a câmera.
- O treino experimental de L/V/Y tem esquemas próprios dos dedos, alvo e hipótese estável na câmera. Os esquemas não demonstram um sinal completo nem foram apresentados como material linguístico validado. As demais letras continuam sem classificação.
- Os controles e processadores de Mouse Virtual, Mãos Livres e 3D permanecem separados.

Arquivos: `libras/LibrasLessons.kt`, `LibrasPractice.kt`, `LibrasProgressRepository.kt`, `NexusLibrasScreen.kt` e testes correspondentes.

## Referências de estudo

- INES: <https://www.gov.br/ines/pt-br>
- Portal de Libras da UFSC: <https://libras.ufsc.br/>
- Quadros, Pizzio e Rezende, *Língua Brasileira de Sinais I* (2009), indicado no catálogo de configurações experimental do aplicativo.

Essas fontes contextualizam a língua e seus parâmetros. Não validam os limiares de geometria do NEXUS nem a qualidade das hipóteses L/V/Y. Ainda faltam demonstrações linguísticas completas com material autorizado e avaliação com pessoas sinalizantes/educadores.

## Verificação realizada

Foram compilados diretamente com Kotlin 2.1.20/JDK 17 e executados com JUnit 4.13.2 **23 testes reais em JVM**, todos aprovados. Cobrem geometria sintética, recusa de evidência insuficiente, feedback por dedo, estabilidade temporal, hipóteses L/V/Y, respostas conceituais e compatibilidade do progresso anterior. Evidência com saída e hashes das fontes: [nexus41-libras-host.json](evidencias/nexus41-libras-host.json).

O conjunto não usa câmera Android nem mãos reais. Geometria sintética aprovada não mede precisão linguística ou desempenho no Galaxy A03. Renderização Compose, persistência no aparelho, câmera e interações ainda dependem da compilação geral e de teste físico.

## Como verificar no aparelho

1. Ler uma aula, responder à revisão e registrar a leitura. Reabrir o aplicativo e conferir o progresso; exportar e excluir quando desejado.
2. Iniciar uma atividade com câmera e manter a configuração mostrada no canto. Conferir o feedback por dedo; retirar/ocluir a mão deve produzir ausência de avaliação.
3. Após o registro, manter a câmera ativa e usar `Repetir este exercício sem parar a câmera`.
4. Testar o modo livre e L/V/Y. Comparar o esquema e observar a estabilidade exigida. Ausência de hipótese não deve ser tratada como erro de Libras.
5. Sair e retornar ao aplicativo; confirmar pausa pelo Android e liberação ao tocar em `Parar câmera`. Este módulo não foi convertido em serviço de câmera de segundo plano.

Não foram adicionados modelos, vídeos ou bibliotecas grandes nesta revisão. O tamanho final do APK deve ser medido após a compilação geral.
