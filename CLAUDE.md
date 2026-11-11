# Regras de trabalho deste projeto (obrigatórias)

1. **Perguntar no meio do processo**: antes de qualquer mudança relevante, perguntar. Não decidir sozinho design,
   comportamento, prioridades ou estilo. Se houver mais de uma solução, apresentar as opções e perguntar.
2. **Gerar `.jar` a cada mudança pequena ou média** (`./gradlew build` → `build/libs/`). Sempre avisar quando o jar
   estiver pronto e dizer exatamente o que mudou nele. Não acumular mudanças grandes.
3. **GitHub (prioridade máxima)**: versionar todo progresso e enviar ao GitHub usando o GitHub CLI (`gh`). Commits
   frequentes com mensagens claras; após cada jar testável, atualizar o repositório. Histórico limpo.
4. **Iterativo**: passos pequenos e testáveis. Ao fim de cada etapa: resumo do que foi feito, o que mudou no jar e o
   próximo passo recomendado.
5. **Não avançar para a próxima feature sem o usuário testar e aprovar a atual.**
6. **Documentação sempre atualizada**: a cada mudança que altere comportamento, números, blocos, comandos,
   configuração, arquitetura ou testes, atualizar no **mesmo commit**:
   - `CLAUDE.md` (especificação abaixo);
   - `README.md` (visão geral, tabelas de cidades/prédios/vias, arquitetura, comandos);
   - `docs/TESTES.md` (testes e o que verificam, auditorias, problemas encontrados e corrigidos, desempenho e
     resultados das últimas rodadas).
   Antes de cada commit, conferir se algum desses arquivos ficou desatualizado. Os valores vêm do código e dos
   testes, nunca da memória. No resumo da etapa, dizer quais documentos foram atualizados.

## Projeto

Abandoned Modern City (mod id `abandonedmoderncity`, pacote `com.abandonedmoderncity`, repositório GitHub
`RakkuLyc/abandoned-modern-city`, pasta `Projetos Claude/AbandonedModernCity`). Começou em 7 nov 2026 como cópia do
Abandoned Simple City (`RakkuLyc/abandoned-simple-city`, pasta `AbandonedModernCity-Simple`) com o mesmo sistema de
rodovias, túneis, pontes, quarteirões, parques e plantações, **sem os prédios** (e sem as casinhas das cidades
3 × 2) e sem o histórico de commits. Os prédios serão estruturas `.nbt` do usuário, em protótipo (hoje nenhuma: as 33 primeiras foram removidas em 10 nov 2026). O Abandoned Modern City original
(pasta `AbandonedModernCity-Deprecated`) está descontinuado. Ainda em 7 nov 2026 o usuário mandou remover as cidades
octogonais e as cidades 3 × 2 das rodovias (com o padrão rural_highway perto delas e as plantações), e depois trocar
o grid 4 × 4 por 2 × 2 com células de 1024, depois os quarteirões para 105 × 60 com células de 512, e em 8 nov 2026 os quarteirões para 140 × 90 com células de 576
(para caber as estruturas da pasta `structures/`); em 10 nov 2026 ruas e rodovias passaram a ter 33 de largura
(células de 640): hoje só
existem as cidades 2 × 2 de quarteirões retangulares.

## Especificação das ruas e cidades (atual)

- Grid rígido, ruas retas e cantos em ângulo reto: cidade = 2 × 2 quarteirões de 140 × 90 de área livre
  (`Params.GRID` = 2, `Params.BLOCK_LONG_RUN` / `BLOCK_SHORT_RUN`; eixos das ruas a 173 e a 123, `BLOCK_LONG` /
  `BLOCK_SHORT`), 3 × 3 ruas e 9 cruzamentos, ~379 × ~279 blocos com as ruas. Cada cidade sorteia (50%) se o lado
  comprido dos quarteirões fica leste-oeste ou norte-sul (`City.longX`); todos os quarteirões da cidade seguem a mesma
  orientação. Todas as cidades são assim (não há mais variantes).
- Água é aterrada, morro é cortado. Cidades a ~640 blocos umas das outras (célula 640, `Params.DEFAULT`). O
  `maxRadius` padrão é 190 (metade do lado comprido da cidade). A área de influência (`Params.influence`) é a
  meia-diagonal exata do retângulo da cidade (190 × 140 → 236) + 10 + talude de 32 + 4 = ~282; com ela o centro de
  cada cidade varia ~70 blocos dentro da célula.
- Rua com 33 de largura (10 nov 2026): 3 `smooth_stone` (calçada) + 27 `polished_blackstone` (pista) + 3
  `smooth_stone` (`Params.STREET_HW` = 16,5, `STREET_LANE_HW` = 13,5).
- Trecho de rua de 140 ou 90 blocos (o lado do quarteirão) + 33 de cruzamento; quadrado 3 × 3 de `smooth_stone` em cada esquina de cruzamento.
- Faixa central no 17º bloco da largura (o do meio), traços de 11 com intervalo de 6 (quantos couberem, centrados); `white_concrete` na cidade,
  `yellow_terracotta` nas rodovias.
- Até 8 entradas por cidade (`City.EXITS`): os 4 cruzamentos de 3 vias do meio dos lados e os 4 cantos. No canto a
  rodovia continua, para fora, uma das duas ruas da borda (sai reta, nunca a 45°); cada cruzamento recebe no máximo
  uma rodovia. Cada ligação é combinada pelas duas cidades. A entrada fica no lado voltado para a cidade de destino
  (a direção que mais aponta para ela) e, entre os candidatos desse lado (canto, meio, canto), no que está mais
  deslocado na direção dela (vizinha a nordeste → canto NE); a seed sorteia entre os empatados. Ligam-se as vizinhas
  de Gabriel que estão entre as 8 mais próximas uma da outra.
- Rodovias com a mesma largura das ruas, 33 (10 nov 2026): 3 `smooth_stone` (o de fora com `stone_brick_wall` em
  cima; `Params.HIGHWAY_SHOULDER` = 2 de acostamento), 27 `polished_blackstone` (faixa `yellow_terracotta` no meio), 3
  `smooth_stone` (o de fora com muro). Muros conectados pelo plano (não pelo vanilla); na junção com a rua não há
  muro. Dentro das cidades os lotes têm prioridade. As rodovias vão direto de uma cidade à outra. O afunilamento
  perto das cidades foi removido (as larguras são iguais).
- Túneis com 7 blocos de altura livre (`Params.CLEARANCE`); só há túnel com 11+ blocos de terreno acima da pista
  (`Params.TUNNEL_COVER`).
- Ruas cortadas: **desligadas** (`City.MAX_CUTS` = 0, pedido do usuário para o 2 × 2; o código continua). Quando
  ligadas, cada cidade perde de 0 a `MAX_CUTS` trechos de rua (sorteio uniforme),
  internos ou da borda; nunca cria beco sem saída e a cidade continua toda conectada. O espaço vira grama, unindo
  os quarteirões. Entradas não são protegidas (só ficam conectadas).
- Quarteirões: ~1 em 8 quarteirões é um parque
  inteiro (`City.PARK_BLOCK_CHANCE`); os demais recebem um anel de estruturas (ver abaixo; hoje não há nenhuma) e o resto do chão é
  `gray_concrete_powder` (o chão da cidade). Rua cortada = quarteirões juntados: cada um avança até o meio da faixa; na borda da cidade o quarteirão
  cobre a faixa até a beira.
- Parques (`Park`): grama com mato, tall grass e flores (papoula, dente-de-leão, azure bluet, tulipas, oxeye daisy,
  cornflower); caminhos de `dirt_path` de 3 blocos com ~8% de falhas, em duas variantes (50%): grade "jogo da velha"
  (2 + 2 caminhos retos a 16 do centro) ou cata-vento (círculo de raio 16 + 4 braços em espiral); distâncias escaladas
  com o tamanho do parque (base `Park.BASE_SIZE` = 68 no lado menor; escala pelo lado menor: nos quarteirões de 140 × 90 é ×1,32). Centro sem árvores:
  fonte `waterfall.nbt` (do usuário, em `resources/amcs/`) cercada de `smooth_stone_slab` (meio bloco) do anel até a
  cerca de folhas da fonte.
  Baú vazio em 50% das fontes (senão `mossy_cobblestone`).
- Estruturas (protótipo de 8 nov 2026): os `.nbt` listados em `src/main/resources/amcs/buildings/index.txt`
  (`tipo|arquivo`) entram nos quarteirões por `core/Ring` (`Structures` carrega, mede e gira). **Em 10 nov 2026 o
  usuário mandou remover as 33 estruturas atuais (farmácias, oficinas, postos, apartamento, prédio azulado, torre e
  prédio tombado) só do mod**: `index.txt` está vazio, o anel devolve zero lotes e o resto continua pronto; a pasta
  `structures/` da raiz é do usuário e não foi mexida. Regras
  decididas pelo usuário: a frente de toda estrutura é o lado -Z do `.nbt` e fica virada para a rua; sorteio por
  arquivo, sem padrão (a ser ajustado); encostadas na calçada, formando um anel; a caixa usada é a dos blocos
  sólidos (sem margem vazia) e a primeira camada sólida fica no chão da cidade. As linhas norte e sul ocupam o lado
  inteiro, as linhas oeste e leste o que sobra entre elas (`Ring.GAP` = 2 entre estruturas). Tamanho do quarteirão
  escolhido pelo usuário em função das 33 primeiras: 140 × 90 (a mais funda tinha 53 de profundidade, a mais rasa 20).
- Dureza: qualquer dureza é permitida (regra antiga removida pelo usuário); só blocos indestrutíveis são proibidos.
  Os block entities permitidos são o baú vazio da fonte e os containers das estruturas (baús, barris, suportes de poção, vasos decorados, atris, fornalhas, camas, fogueiras, sensores de luz e jukeboxes; `Palette.exempt`).
  **Loot tables:** exceto oficinas usam `lostcities:chests/lostcitychest` (do Apocalypse Loot / Lost Cities); oficinas usam `oficina_vehicle_parts` (baús: rodas, assentos, motores compatíveis com Camaro/Escargot do OAmP) e `oficina_vehicle_tools` (outros containers: jumperpack, blowtorch, repairkit). Baú da fonte do parque continua vazio (50%) ou `mossy_cobblestone` (50%).
- Paleta (`gen/Palette`): os 8 estilos de material dos prédios do Abandoned Simple City (paredes, janelas, pisos,
  acabamento, escadas, sacadas) continuam na paleta, sem uso, para os prédios novos.
