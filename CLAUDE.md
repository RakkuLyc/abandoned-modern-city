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
3 × 2) e sem o histórico de commits. Os prédios são as estruturas `.nbt` do usuário (43 arquivos desde 13 nov 2026: 21 modelos, 6 deles com variações de cor; 14 em 12 nov, mais 7 e as 22 variações em 13 nov; as 33 primeiras foram removidas em 10 nov). O Abandoned Modern City original
(pasta `AbandonedModernCity-Deprecated`) está descontinuado. Ainda em 7 nov 2026 o usuário mandou remover as cidades
octogonais e as cidades 3 × 2 das rodovias (com o padrão rural_highway perto delas e as plantações), e depois trocar
o grid 4 × 4 por 2 × 2 com células de 1024, depois os quarteirões para 105 × 60 com células de 512, e em 8 nov 2026 os quarteirões para 140 × 90 com células de 576
(para caber as estruturas da pasta `structures/`); em 10 nov 2026 ruas e rodovias passaram a ter 33 de largura
(células de 640); em 11 nov 2026 as cidades passaram ao meio de células de 648 e a 4 entradas; em 12 nov 2026 os
quarteirões passaram a 180 × 120 (células de 736), com vãos entre as estruturas: hoje só
existem as cidades 2 × 2 de quarteirões retangulares.

## Especificação das ruas e cidades (atual)

- Grid rígido, ruas retas e cantos em ângulo reto: cidade = 2 × 2 quarteirões de 180 × 120 de área livre
  (`Params.GRID` = 2, `Params.BLOCK_LONG_RUN` / `BLOCK_SHORT_RUN`; eixos das ruas a 213 e a 153, `BLOCK_LONG` /
  `BLOCK_SHORT`), 3 × 3 ruas e 9 cruzamentos, ~459 × ~339 blocos com as ruas. Cada cidade sorteia (50%) se o lado
  comprido dos quarteirões fica leste-oeste ou norte-sul (`City.longX`); todos os quarteirões da cidade seguem a mesma
  orientação. Todas as cidades são assim (não há mais variantes).
- Água é aterrada, morro é cortado. Cidades no meio de células de 736 (`Params.DEFAULT`), 736 blocos entre centros vizinhos;
  o vão entre as bordas (o comprimento da rodovia) fica entre ~277 e ~397, conforme a orientação dos quarteirões
  (pedido do usuário em 11 nov: 276–360; com os quarteirões de 180 × 120 a diferença entre os lados da cidade passou
  a 120). Sem variação de posição: célula com o meio sobre água ou alto demais fica sem cidade. O
  `maxRadius` padrão é 230 (metade do lado comprido da cidade). A área de influência (`Params.influence`) é a
  meia-diagonal exata do retângulo da cidade (230 × 170 → 286) + 10 + talude de 32 + 4 = ~332; com ela a célula
  precisa de pelo menos 2 × 334 + 64 = 732.
- Rua com 33 de largura (10 nov 2026): 3 `smooth_stone` (calçada) + 27 `polished_deepslate` (pista; até 12 nov era
  `polished_blackstone`) + 3
  `smooth_stone` (`Params.STREET_HW` = 16,5, `STREET_LANE_HW` = 13,5).
- Trecho de rua de 180 ou 120 blocos (o lado do quarteirão) + 33 de cruzamento; quadrado 3 × 3 de `smooth_stone` em cada esquina de cruzamento.
- Faixa central no 17º bloco da largura (o do meio), traços de 11 com intervalo de 6 (quantos couberem, centrados); `white_concrete` na cidade,
  `yellow_terracotta` nas rodovias.
- Faixas de pedestres (11 nov 2026, `City.crosswalk`): em cada ponta de todo trecho de rua, 2 linhas de pista depois
  do quadrado do cruzamento, zebra de 3 linhas (`CROSSWALK_GAP` = 2, `CROSSWALK_DEPTH` = 3) com listras de
  `white_concrete` de 1 bloco e intervalo de 1, paralelas à rua, nos 27 de pista (14 listras, as duas de fora
  brancas); zona `ChunkPlan.CROSSWALK`.
- 4 entradas por cidade (`City.EXITS`), uma por lado, sorteadas: cada lado usa o cruzamento de 3 vias do meio ou um
  dos dois cantos daquele lado (nunca o mesmo cruzamento duas vezes). O sorteio de 4 quaisquer das 8 posições foi
  testado e rejeitado (perdia 23% das rodovias por curva fechada). No canto a rodovia continua, para fora, uma das duas ruas da borda (sai reta, nunca a 45°); cada cruzamento recebe no máximo
  uma rodovia. Cada ligação é combinada pelas duas cidades. A entrada fica no lado voltado para a cidade de destino
  (a direção que mais aponta para ela) (a entrada sorteada daquele lado; se ela já serve outra rodovia, a próxima melhor direção). Ligam-se as
  vizinhas de Gabriel que estão entre as 4 mais próximas uma da outra.
- Rodovias com a mesma largura das ruas, 33 (10 nov 2026): 3 `smooth_stone` (o de fora com `stone_brick_wall` em
  cima; `Params.HIGHWAY_SHOULDER` = 2 de acostamento), 27 `polished_deepslate` (faixa `yellow_terracotta` no meio), 3
  `smooth_stone` (o de fora com muro). Muros conectados pelo plano (não pelo vanilla); na junção com a rua não há
  muro. Dentro das cidades os lotes têm prioridade. As rodovias vão direto de uma cidade à outra. O afunilamento
  perto das cidades foi removido (as larguras são iguais).
- Ruas abandonadas (12 nov 2026, `core/RoadDecay`): ~30% das colunas de via tomadas (eram 10%), em manchas (ruído de
  valor, células de 5, acima de 0,66) e rachaduras soltas (6%), função pura da posição e da seed. Valem para ruas,
  faixas, faixas de pedestres, calçadas e acostamentos das cidades, das rodovias e das pontes; ficam limpos os túneis,
  os degraus de meia-laje das rampas e a coluna do muro. As colunas não tomadas têm textura gasta
  (`RoadDecay.texture`): na pista, manchas e blocos soltos de `polished_blackstone`, `cobbled_deepslate`,
  `deepslate_tiles`, `cracked_deepslate_tiles`, `blackstone` e `smooth_basalt` sobre o `polished_deepslate` (que fica
  em ~54%); na calçada, `andesite`, `cracked_stone_bricks`, `stone_bricks` e `cobblestone` sobre o `smooth_stone`; nas
  rampas, lajes das mesmas pedras; 20% das faixas pintadas (central, de pedestres, amarela) apagadas. A coluna tomada fica na mesma altura (sem degrau para os
  veículos) e só recebe plantas sem colisão: grama (`grass_block`) com grama baixa, alta ou samambaia; musgo
  (`moss_block`), às vezes com grama baixa (sem tapete de musgo, que tem colisão); `coarse_dirt` ou `gravel`;
  asfalto rachado (`blackstone` ou `cobbled_deepslate` na pista, `cobblestone` na calçada). O reparo de vizinhos e a
  auditoria usam a mesma regra (`surface_block`, `road_plant`).
- Vinhas das pontes (12 nov 2026, pedido do usuário): em 35% das colunas logo fora da borda de uma ponte (a do muro),
  um cacho de vinha preso na laje desce da superfície até o primeiro bloco que não é ar (chão ou água); feito em
  todo chunk, também nos sem nada planejado (`ChunkBuilder.bridgeVines`).
- Pontes abandonadas (12 nov 2026): pista tomada e gasta como as ruas; a laje (o bloco sob a superfície) e os
  pilares em manchas de `mossy_stone_bricks`, `cracked_stone_bricks`, `mossy_cobblestone` e `stone_bricks`
  (`RoadDecay.bridgeStone`); o muro quebrado em trechos de 1 a 3 blocos (`RoadDecay.wallGap`, ruído de 2,5 acima de
  0,8) e musgoso em trechos (`mossy_stone_brick_wall`, ~metade); buracos atravessando a laje, ~1 a cada 80 blocos de
  ponte (`Wreck.holes`), de 5 a 7 blocos na largura e 4 a 5 no comprimento, contorno irregular e suave (sem ilhas de
  pista), com o meio a até 6 do eixo, a 8+ blocos das pontas da ponte.
- Túneis sem obstáculos: desabamentos (monte de pedra com buraco no teto) foram testados em 12 nov 2026 e retirados
  a pedido do usuário; a pista dos túneis fica livre. As colunas dos buracos das pontes não são trafegáveis
  (`ChunkPlan.drivable`).
- Túneis com 7 blocos de altura livre (`Params.CLEARANCE`); só há túnel com 11+ blocos de terreno acima da pista
  (`Params.TUNNEL_COVER`).
- Ruas cortadas: **desligadas** (`City.MAX_CUTS` = 0, pedido do usuário para o 2 × 2; o código continua). Quando
  ligadas, cada cidade perde de 0 a `MAX_CUTS` trechos de rua (sorteio uniforme),
  internos ou da borda; nunca cria beco sem saída e a cidade continua toda conectada. O espaço vira grama, unindo
  os quarteirões. Entradas não são protegidas (só ficam conectadas).
- Quarteirões: todos recebem um anel de estruturas (ver abaixo) e o resto (o miolo) é tomado pela natureza (ver
  "Natureza"). ~1 em 8 quarteirões (`City.PARK_BLOCK_CHANCE`) tem um parque num canto (13 nov 2026, pedido do
  usuário; antes o parque ocupava o quarteirão inteiro): 110 × 64 (`City.PARK_LONG`/`PARK_SHORT`, o lado comprido
  junto ao lado comprido do quarteirão), canto sorteado; as estruturas ficam nas duas outras ruas, em L, e nunca a
  menos de 4 blocos do parque (`Ring.lots` com `keepOut`); ~5 estruturas por quarteirão de parque. Rua cortada = quarteirões juntados: cada um avança até o meio da faixa; na borda da cidade o quarteirão
  cobre a faixa até a beira.
- Natureza (11 nov 2026, `core/Flora` + `core/Wild`, lote `WILD` que cobre o quarteirão inteiro por baixo das
  estruturas, que ganham onde estão): aspecto de abandonado. Chão em manchas (ruído de valor): grama (`grass_block`)
  na maior parte, `moss_block`, `coarse_dirt` e o concreto antigo (`gray_concrete_powder`) aparecendo. Árvores do
  próprio mod, ~1 a cada 55 colunas com troncos a 5+ blocos: carvalho (38%), bétula (24%), carvalho escuro (18%) e
  abeto (20%), 30% grandes (copa de raio 3–4, abeto cônico até 12 de altura), copas com bordas irregulares.
  Árvores reais (11 nov 2026): folhas **não persistentes**, cada uma com a distância até o tronco (1 a 6, contada
  pelas folhas como no vanilla, `Flora.Tree.dist`); folhas da forma que ficariam a mais de 6 da madeira não são
  colocadas. Cortado o tronco, as folhas caem sozinhas (leaf decay). Arbustos: folhas (distância 1 e 2) sobre um
  tronco escondido no chão. Vinhas penduradas (35% das colunas da borda da copa, 1 a 4 blocos; só ficam se presas em folha ou vinha). Copas e vinhas não saem do quarteirão nem entram nas estruturas. Vegetação no solo (nunca no concreto):
  grama, tall grass, samambaia, samambaia grande, tapete de musgo sobre o musgo, arbustos (folhas sobre tronco enterrado).
  Entulho: `mossy_cobblestone`, `cobblestone`, `cracked_stone_bricks`, `gray_concrete` (1 ou 2 de altura) e laje de
  `mossy_stone_bricks`, 1,2% das colunas na grama e 4 × isso no concreto.
- Parques (`Park`): tomados pela mesma natureza (árvores variadas com vinhas, ~1 a cada 60 colunas, sem tronco em
  caminho e sem copa sobre o anel; samambaias, musgo, arbustos, entulho a 0,6%), com algumas flores restantes
  (papoula, dente-de-leão, azure bluet, tulipas, oxeye daisy, cornflower, 5%); caminhos de `dirt_path` de 3 blocos
  com 30% de falhas (grama, musgo ou terra grossa), em duas variantes (50%): grade "jogo da velha"
  (2 + 2 caminhos retos a 16 do centro) ou cata-vento (círculo de raio 16 + 4 braços em espiral); distâncias escaladas
  com o tamanho do parque (base `Park.BASE_SIZE` = 68 no lado menor; escala pelo lado menor: o parque de 110 × 64 fica em ×1). Centro sem árvores:
  fonte `waterfall.nbt` (do usuário, em `resources/amcs/`) cercada de lajes musgosas (meio bloco) do anel até a
  cerca de folhas da fonte: só `mossy_stone_brick_slab` e `mossy_cobblestone_slab` (metade cada), 10% faltam (11 nov
  2026). Nenhum bloco da fonte é removido: em 55% das colunas com lugar para vinha (ar ao lado das folhas ou pedras
  dela) pende um cacho de 2 a 6 blocos a partir do ponto mais alto (`Park.VINE_MIN`/`VINE_MAX`). O piso de pedra em
  volta da bacia (camada 0 do molde) fica musgoso (11 nov 2026, `Park.FLOOR_SWAP`): `stone` e `cobblestone` →
  `mossy_cobblestone`, `stone_bricks` e `cracked_stone_bricks` → `mossy_stone_bricks`; a bacia, a água, as escadas,
  as lanternas e o baú não mudam.
  Baú vazio em 50% das fontes (senão `mossy_cobblestone`).
- Estruturas (12 nov 2026, `core/Structures` carrega, mede e gira; `core/Ring` monta o anel): as 21 `.nbt` de
  `structures/` (cópias em `src/main/resources/amcs/buildings/`, listadas em `index.txt` como `tipo|arquivo|chão|frente`):
  10 casas (casa abandonada, casa moderna abandonada, mansão abandonada, mansão pós-apocalíptica e, desde 13 nov
  2026, casa com garagem, casa grande, casa com piscina, casa de subúrbio, casa com torre e casa vitoriana, todas
  abandonadas), farmácia, farmácia grande, oficina média, oficina pequena, posto padrão, posto pequeno, prédio
  apartamento, prédio esqueleto (13 nov 2026; o arquivo `prédio_esqueleto_31x29.nbt` vira `predio_esqueleto_31x29`,
  sem acento, porque o argumento de `/amcs spawn` só aceita ASCII), prédio azulado, torre, prédio apocalíptico. A
  mansão pós-apocalíptica foi atualizada em 13 nov com a versão nova da pasta (só o estado de 12 portas mudou). As
  casas são 10 dos 21 modelos e ~43% das estruturas. Variações de cor (13 nov 2026, pedido do usuário; campo `modelo`
  do índice): farmácia (amarelo, azul, roxo, verde além da vermelha), farmácia grande (azul, roxo, verde, vermelho além
  da amarela), oficina média (amarela e azul, amarelo e vermelho além da azul e preto), oficina pequena (amarela,
  azul, roxa, vermelha além da verde), posto (amarelo, azul, roxo, verde além do vermelho), posto pequeno (amarelo,
  azul, roxo, verde além do vermelho): 43 arquivos, 21 modelos, cada variação do tamanho, frente e chão da original
  (99,6 a 100% dos blocos no mesmo lugar). O anel sorteia o modelo (todos com a mesma chance, `Structures.pick`) e
  depois a cor: as variações não mudam as frequências. As variações de farmácia vieram sem os banners; o índice os
  copia da original na carga (`Template.withBannersFrom`, campo `banners` = `fonte@dx,dz:cor=cor`): na farmácia
  pequena as letras ficam na cor da variação sobre fundo branco (como as vermelhas da original), na grande o fundo
  fica na cor da variação com letras pretas (como o amarelo da original); azul-claro, roxo, verde-limão e vermelho
  nas grandes, amarelo, azul, roxo e verde nas pequenas. A coluna de banners da lateral da farmácia grande fica fora
  do molde salvo das variações (25 de largura): o molde cresce 1 bloco. A carga falha se um banner cair sobre um
  bloco ou não tiver parede atrás. As variações de farmácia têm ainda um letreiro F A R M Á C I A em blocos coloridos
  sobre a parede branca (na lateral da pequena, na frente e na lateral da grande) que as originais não têm; a pedido
  do usuário (13 nov 2026) o índice devolve os blocos neutros da original onde a variação os pintou
  (`Template.withNeutralFrom`, campo `neutros`: `white_concrete`, `smooth_quartz`, `calcite`, `polished_diorite`):
  76 blocos em cada pequena, 152 em cada grande; o resto da cor da variação fica. As variações ficam bloco a bloco
  nas mesmas posições da original, com os banners nas mesmas posições (até 13 nov os das pequenas estavam 1 bloco
  à esquerda: o primeiro alinhamento foi pela caixa dos blocos sólidos, não pelas posições do molde). Regras
  decididas pelo usuário:
  - frente = lado -Z do `.nbt`, virada para a rua; o campo `frente` do índice gira o molde quando a frente é
    outro lado: a casa moderna abandonada e a mansão pós-apocalíptica têm a frente no +Z (180°, achado pelo usuário
    no jogo em 12 nov 2026); as 7 de 13 nov têm a frente no -Z (caminho e degraus de entrada no lado norte do
    molde);
  - teias soltas (que só encostam em ar, teia, vinha, planta ou carpete) são removidas de todas as estruturas
    (pedido do usuário para a mansão pós-apocalíptica; 41 no total: 23 na casa grande, 13 na mansão
    pós-apocalíptica, 3 na mansão abandonada, 1 na casa abandonada, 1 no prédio apocalíptico);
  - para-raios viram tochas de redstone acesas (13 nov 2026, pedido do usuário; `Structures.swap`, na carga dos
    moldes, vale para qualquer estrutura): de pé quando o para-raios aponta para cima ou para baixo, na parede quando
    aponta para o lado; hoje são 5, um no topo de cada: casa com torre, mansão pós-apocalíptica, prédio apartamento,
    prédio apocalíptico e torre;
  - banners com o desenho salvo no molde (`Template.banners`, aplicado quando o banner é colocado,
    `Palette.applyBanner`): as letras D R U G S T O R E das farmácias (9 na farmácia, 18 na farmácia grande); encostadas na calçada, formando um anel; sorteio por arquivo,
    sem padrão;
  - limites por cidade (`Structures.LIMITS`): farmácias 3 (as duas), oficinas 2 (as duas), postos 2 (os dois); os
    anéis da cidade inteira são sorteados de uma vez (`City.rings`, quarteirões de norte a sul, oeste a leste), e um
    grupo no limite é sorteado de novo;
  - nunca duas estruturas iguais (o mesmo modelo, em qualquer cor) no mesmo quarteirão (13 nov 2026, pedido do usuário): uma
    estrutura que já está em alguma linha do quarteirão é sorteada de novo (`Ring.row`); antes 80% dos quarteirões
    tinham alguma repetida; em quarteirões vizinhos ela pode aparecer de novo;
  - caixa = a dos blocos sólidos; a camada `chão` do índice fica no nível do chão da cidade e as de baixo ficam
    enterradas: mansão abandonada, torre, prédio azulado e prédio apartamento descem 1 bloco (pedido do usuário; ar
    enterrado vira terra); a casa grande também (13 nov 2026: a camada 0 dela é só terra e a grama do quintal está
    na camada 1); as outras casas novas têm o quintal de grama na camada 0, que fica no nível do chão;
  - sem vão até a calçada: na camada do chão, as fileiras da borda sem piso de verdade (menos de 25% de piso, só
    vinhas, banners ou lajes soltas) recebem o bloco de piso da primeira fileira que tem piso, nos 4 lados
    (`Structures.floorFill`); os enfeites (banners da farmácia, vinhas das oficinas) ficam;
  - árvores das estruturas com leaf decay: folhas a até 6 (pelas folhas) de um tronco do molde viram não
    persistentes com a distância certa; folhas sem tronco perto (sebes, arbustos) continuam persistentes;
  - copas cortadas pela borda do molde são completadas para fora da estrutura (13 nov 2026, pedido do usuário pela
    árvore em frente à casa com piscina; `Structures.crowns`, `core/Crowns`, `gen/Overhangs`): cada folha real de uma
    copa (a até 4 blocos na horizontal de um tronco de 3+ troncos naturais, não descascados, e 2+ acima do pé dele) é
    espelhada em volta do tronco, em x, em z e nos dois; o espelho que cai fora da caixa vira folha não persistente,
    com a distância contada pelas folhas (até 6). São 174 folhas em 5 estruturas: casa com piscina 11 (sobre a
    calçada, a 13–16 do chão), casa com garagem 23, casa de subúrbio 20, casa com torre 15 e prédio esqueleto 105
    (no bosque do miolo). Sobre rua ou calçada só a mais de `Params.CLEARANCE` (7) blocos da superfície; no bosque ou
    parque onde ele não planeja nada; nunca em outra estrutura; só onde há ar. O reparo de vizinhos e a limpeza de
    folhas órfãs as mantêm; a auditoria as confere (`crown_leaves`, `crown_leaf_missing`);
  - as árvores dos miolos não entram nas estruturas (1 bloco de folga) e as estruturas nunca se sobrepõem
    (as árvores de cada estrutura ficam dentro da caixa dela, menos as copas completadas acima);
  - vãos (12 nov 2026, pedido do usuário): as estruturas não se encostam; entre duas vizinhas de uma linha, e entre a
    mais funda de uma linha dos lados curtos e as linhas dos lados compridos, sorteia-se um vão de 4 a 17 blocos
    (`Ring.GAP_MIN`/`GAP_MAX`; eram 10 a 19, reduzido pelo usuário no mesmo dia); entre os fundos de linhas opostas fica sempre o bosque do miolo, com folga sorteada de
    20 a 30 por par de lados (`Ring.CORE_MIN`/`CORE_MAX`);
  - as linhas dos lados curtos do quarteirão vão de canto a canto e as dos lados compridos preenchem o que sobra
    entre elas (o contrário deixava os lados curtos quase sempre vazios); ~8,2 estruturas por quarteirão (as 4
    linhas com ~25% cada; 8,4 antes da regra de não repetir), 51% da área livre coberta. Tamanho do quarteirão: 180 × 120, recomendado depois de
    simular os vãos (140 × 90 caía para ~5 estruturas e torre/azulado/prédio apocalíptico ficavam raros).
  - comandos (12 nov 2026, `command/StructureCommands`): `/amcs locate structure <nome|tipo|grupo>` (a mais
    próxima já planejada; coordenada na calçada em frente) e `/amcs spawn <nome>` (2 blocos à frente de quem
    executa, centralizada, frente virada para ele, chão no bloco em que ele pisa; mesmas regras da cidade, com loot).
- Abandono das estruturas (13 nov 2026, pedido do usuário: "natureza tomando conta", no modelo do prédio
  apocalíptico; `core/Weathering`): aplicado a cada molde na carga, antes de tudo que o lê (folhas reais, acesso dos
  contêineres, copas), sem mexer nos `.nbt`; **desliga com `structureOvergrowth = false`** na config (lida na
  inicialização: reiniciar o jogo; o log diz "Structure overgrowth on (N blocks changed)" ou "off"). Só acrescenta o
  que falta para chegar às medidas do prédio apocalíptico (quem já tem mais fica como está; na pedra e nos pisos ele
  mesmo, o prédio esqueleto e a casa moderna não mudam), acima da camada do chão e só dentro da caixa da estrutura
  (tamanho e lugar no anel iguais):
  - pedra rachada: `stone_bricks`, `deepslate_bricks`, `deepslate_tiles`, `polished_blackstone_bricks` e
    `nether_bricks` viram as rachadas em manchas (ruído de valor) até 31% da família (no prédio: 9 863 de 31 332);
  - verde nos pisos: pontos soltos nos blocos inteiros com ar em cima, até 8,2% dos que estão sob teto e 35,8% dos a
    céu aberto; piso de pedra ou concreto vira `moss_block` (63%), `grass_block` (20%), `coarse_dirt` (7%),
    `rooted_dirt` (5%) ou `dirt` (5%), com tapete de musgo, grama baixa, samambaia ou arbusto seco em cima nas
    proporções do prédio; madeira e outros blocos inteiros só recebem `moss_carpet`;
  - vinhas nas fachadas (13 nov 2026, pedido do usuário depois do primeiro teste: "mais longas, agressivas e
    espalhadas por todos os lados"; antes eram cachos de 1 a 3 até a medida do prédio, ~5% da fachada): em cada um
    dos 4 lados, até 25% da fachada externa (`Weathering.VINES`; células de ar junto a um bloco inteiro que veem o
    lado de fora da caixa) coberta, também no prédio apocalíptico, no esqueleto e na casa moderna (quem já tem mais
    fica); cachos de 3 a 12 blocos (`STRAND_MIN`/`STRAND_MAX`, média ~6) que descem até um piso ou o chão,
    começando onde um ruído de valor sobre a fachada é mais alto (manchas que cobrem trechos de parede e deixam
    outros limpos); as células abaixo da primeira se seguram na vinha de cima; 10% das células sob beirais (de fora)
    recebem uma vinha presa pelo topo;
  - `cobblestone` e `stone_bricks` (e suas escadas, lajes e muros) a até 2 blocos do verde novo viram musgosos (50%).
  Nunca mexe em folhas, troncos, vidro, block entities (contêineres, banners, camas...), portas, água ou blocos
  parciais; planta só sobre solo, tapete só sobre bloco inteiro, vinha só presa em bloco inteiro ou na vinha de cima.
  Nos 43 arquivos: 17 879 vinhas, 6 712 pisos viram solo, 3 518 tapetes, 3 263 plantas, 1 768 musgosos, 1 469
  rachados (34 609 blocos). As medidas antes e depois de cada estrutura ficam em `docs/TESTES.md`.
- Dureza: qualquer dureza é permitida (regra antiga removida pelo usuário); só blocos indestrutíveis são proibidos.
  Os block entities permitidos são o baú vazio da fonte e os containers das estruturas (baús, barris, suportes de poção, vasos decorados, atris, fornalhas, camas, fogueiras, sensores de luz, jukeboxes,
  banners, crânios, dispensers, droppers e estantes entalhadas, vazias, desde 13 nov 2026 por causa da casa grande; `Palette.exempt`). Os banners recebem o desenho salvo no molde; placas
  saem sem texto.
  **Loot tables** (13 nov 2026, pedido do usuário; postas quando cada contêiner é colocado, pela região, como nas
  estruturas vanilla; semente por lote e posição; `ChunkBuilder.loot`, `Palette.lootTable`): só itens vanilla, em
  tabelas próprias geradas por `tools/gen_loot.py` em `data/abandonedmoderncity/loot_table/chests/<tema>/<nível>.json`.
  Tema pelo tipo da estrutura (`Structures.Def.lootTheme`): `casa` (comida, utensílios, livros, discos), `farmacia`
  (poções de cura, regeneração, visão noturna, resistência ao fogo, ingredientes de poção), `oficina` (ferro, cobre,
  carvão, ferramentas de ferro gastas, trilhos, pistões; raras: ferramentas encantadas, bigorna, funil), `posto`
  (lanches, carvão, baldes de lava, mapas, esmeraldas) e `predio` (os prédios: papel, livros, flechas, lápis, armas
  gastas; raras: armadura de ferro encantada, pérolas do ender, discos). Nível pelo contêiner: baú `bau` (3 a 6 pilhas
  de itens comuns e normais e 10% de chance de um raro), barril, dispenser e dropper `barril` (40% têm algo, 1 a 3
  pilhas só de comuns), vaso decorado `vaso` (40%, uma pilha comum). Contêiner de difícil acesso (`core/Access`) usa
  a versão `_dificil`, mais generosa: `bau_dificil` 5 a 8 pilhas e 1 a 2 raros, `barril_dificil` 75% com 2 a 4 pilhas
  de comuns e normais, `vaso_dificil` 80% com uma pilha normal ou rara. Difícil acesso = a 15+ blocos acima do chão
  da cidade (`Access.HIGH`) ou fora do alcance de quem entra andando da rua: o percurso pula 1 bloco (nunca sobre
  cerca, muro ou vidraça), cai qualquer altura, sobe escada de mão, vinha, andaime e água, abre portas, alçapões e
  portões (não os de ferro); plantas, teias, tapetes e tochas não bloqueiam; folhas sim; o contêiner precisa estar a
  1 bloco de lado (diagonal vale) de onde o jogador pisa, de 1 abaixo dos pés a 1 acima da cabeça. Hoje 172 de 1 530
  contêineres dos 43 arquivos com o abandono ligado (as vinhas também se escalam; 173 de 882 nos 21 modelos sem ele): 166 pela altura (torre 82, prédio apocalíptico 54, azulado 17, esqueleto 9,
  apartamento 4) e 17 fechados (telhados da casa abandonada e da farmácia grande, um cômodo da mansão abandonada 2
  blocos acima do pátio, salas do prédio apocalíptico e do azulado). As peças de veículo do OAmP e a tabela do Lost
  Cities saíram (a do Lost Cities era rejeitada pelo Minecraft). Baú da fonte do parque continua vazio (50%) ou
  `mossy_cobblestone` (50%).
- Paleta (`gen/Palette`): os 8 estilos de material dos prédios do Abandoned Simple City (paredes, janelas, pisos,
  acabamento, escadas, sacadas) continuam na paleta, sem uso, para os prédios novos.
