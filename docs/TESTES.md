# Testes — cidades, vias, pontes, túneis e rodovias

Este documento registra:
- como o mod é testado e o que cada teste verifica;
- os problemas encontrados e as correções (seção 3);
- os números das últimas rodadas (seções 5 e 6).

A especificação que os testes conferem está em `CLAUDE.md`.

## 1. Três camadas de teste

| Camada | Onde | O que usa | Quando rodar |
|---|---|---|---|
| Unitária (JUnit) | `src/test/java/.../core` | planejador puro + 7 terrenos sintéticos × 3 seeds | `./gradlew test` (também no `build`; ~2 min) |
| Plano com terreno real | `core/PlanAudit`, no jogo | ruído de terreno do Minecraft (`getBaseHeight`) | `/amcs plan [raio]` e na bateria |
| Blocos gerados | `command/WorldAudit`, no jogo | os chunks realmente gerados pelo servidor | `/amcs audit [raio]` e na bateria |

Para gerar só o jar, sem os testes, use `./gradlew build -x test`.

### Impressão digital (mudanças que não devem alterar o mundo)

Duas conferências guardam o mundo gerado numa revisão do código (desde 12 nov 2026):
- **`FingerprintTest` (unitário):** um SHA-256 por parte do que o planejador entrega à camada do jogo, nos 7
  terrenos sintéticos com seeds fixas. As partes:
  - `layout`: as 1 129 cidades das 7 × 3 combinações e os lotes de cada quarteirão;
  - `highways`: as 2 013 rodovias (pontos, perfil, pontes, túneis, buracos);
  - `village`: a área sem criaturas de vila em volta de cada cidade;
  - `chunks`: o plano de cada coluna de 31 214 chunks, em volta de 2 cidades e ao longo de 1 rodovia por terreno;
  - `lots`: nesses chunks, o código de bloco de cada coluna de lote em todas as alturas, com o acesso do loot e o
    desenho dos banners;
  - `roads`: superfícies gastas, plantas, pedras e muros das pontes, vinhas;
  - `lamps`: as peças dos postes;
  - `crowns`: as folhas das copas completadas;
  - `structures`: as 52 estruturas nas 4 rotações, célula a célula, com aberturas, acesso, banners e folhas de fora;
  - `weathering`: o que o abandono acrescentou a cada estrutura;
  - `benches`: os bancos dos parques do terreno plano.

  Os valores esperados ficam no próprio teste (código do commit `4dd94ae`). Um bloco de estrutura entra pelo texto
  do estado e pela rotação, não pelo índice na lista de estados. O teste também confere uma cobertura mínima, para
  não passar com um plano vazio. Sensibilidade conferida em 12 nov: mudar `StreetLights.LAMP_EVERY` (então `City.LAMP_EVERY`), `RoadDecay.PATCH`,
  `Park.BENCH_EVERY`, `Weathering.VINES`, `Ring.GAP_MIN`, `Highway.DEADBAND` ou a margem das vilas muda as partes
  certas, e cada uma das 11 partes muda com alguma dessas mudanças.
- **`mod blocks:` na bateria:** por alvo, um SHA-256 dos blocos que o mod decide no mundo gerado
  (`command/WorldFingerprint`), nas posições que o `WorldAudit` compara com o plano:
  - nas vias, a superfície e o bloco de baixo, o ar ou as plantas acima, os postes, o teto dos túneis e os pilares;
  - as paredes dos túneis;
  - nos lotes, todo bloco que o plano define, menos os códigos que a auditoria também pula (nada planejado, grama,
    grama baixa e tapete de musgo);
  - as folhas planejadas das copas completadas;
  - os block entities das estruturas, camadas enterradas incluídas (loot table e semente, desenho dos banners). Os
    outros ficam de fora: o baú vazio da fonte do parque, por exemplo, só aparece no chunk depois que ele é salvo e
    carregado de novo (falta num mundo recém-gerado e está lá depois de recarregar).

  O resto do mundo fica de fora, porque o Minecraft vanilla não gera igual de uma rodada para outra (achado 64).
  Algumas propriedades o Minecraft recalcula pelos vizinhos ao terminar um chunk, e o resultado depende de o chunk
  vizinho já existir ou não; elas entram com valor fixo: a distância das folhas, a forma das escadas, as ligações de
  barras, vidraças e cercas, a neve sobre a grama (`snowy`), o lado de muro ligado baixo ou alto (`low`/`tall`; ligado
  ou não continua contando) e as faces das vinhas nas colunas da borda do chunk; `cave_air` conta como ar. O arquivo
  `renders/tNN_blocks.txt`, ao lado do relatório, traz um resumo por chunk (vias, lotes, resto) e por coluna, para
  achar onde duas rodadas diferem.

Numa mudança que não deve alterar o mundo, o teste passa, e as linhas `mod blocks:` e as contagens `stats:` da
bateria ficam iguais às da referência (seção 6). A exceção é `loot_containers_anywhere`, que conta também os baús do
vanilla, que variam. Um commit que mude o mundo de propósito atualiza os valores esperados do teste e a referência
da bateria, e diz isso.

Com o abandono das estruturas desligado (`structureOvergrowth = false`) as estruturas mudam, então a referência é
outra: a da bateria rápida com essa opção (seção 6). Para tirá-la, troque a linha em
`run/config/abandonedmoderncity-common.toml`, rode `-PselfTestScope=quick` e volte a linha depois.

O que a impressão da bateria não cobre: as vinhas penduradas nas pontes (achado 65: o lugar delas depende de uma
árvore do vanilla chegar antes), o aterro sob as vias, os taludes e a vedação de líquidos. Numa mudança que só move
código de lugar (passo 3 da revisão), a prova para essas partes é comparar a listagem de instruções (`javap -c -p`)
de cada função movida, antes e depois: o corpo tem de ser o mesmo.

A **bateria automática** (`tools/selftest.sh`) faz, para cada seed:
1. cria um mundo novo num servidor dedicado de desenvolvimento (porta 25591);
2. escolhe os alvos a partir da própria rede planejada;
3. gera os chunks;
4. roda as duas auditorias em cada alvo e grava PNGs;
5. para o servidor.

Alvos por seed:

| Alvo | `quick` | `full` |
|---|---|---|
| cidade mais próxima do spawn (inteira, com os taludes) | ✓ | ✓ |
| cidade mais alta entre as 16 mais próximas | | ✓ |
| cidade costeira (água a até 96 blocos da área de influência) | | ✓ |
| cidade de terreno mais acidentado | | ✓ |
| o túnel mais longo (com as aproximações) | ✓ | ✓ |
| a ponte mais longa sobre água | ✓ | ✓ |
| o viaduto seco mais longo | | ✓ |
| a rampa contínua mais longa | | ✓ |
| uma rodovia inteira (a mais curta que cabe na área) | | ✓ |

Alvos repetidos (a mesma cidade em dois critérios) entram uma vez só. Seeds fixas: `20261027`,
`-3297019724594858492`, `3478477863671517581` e `1`.

### Terrenos sintéticos (testes unitários)

- `FLAT` (plano), `HILLS` (colinas ±16) e `MOUNTAINS` (montanhas até ~220).
- `ISLANDS` (ilhas e mar) e `MIXED` (cristas + vales + rios).
- `CANYONS` (planalto cortado por cânions estreitos abaixo do nível do mar).
- `RIVERS` (terra ondulada cortada por rios e salpicada de lagos, como o overworld real perto das seeds da bateria).

Cada terreno roda com as seeds `20261104`, `-3297019724594858492` e `42`, numa área de 9 × 9 células de 736
(~18 000 × 18 000 blocos).

## 2. O que é verificado

### Unitários (46 testes)

| Teste | Verifica |
|---|---|
| `ProfileTest.profilesAreFlatOrRampAndJoinTheCities` | 4 000 perfis aleatórios (com penhascos): começa e termina no nível de cada cidade, 48 blocos planos nas pontas, variação ≤ 1/8 de bloco por bloco, trechos planos sempre em altura inteira, nunca abaixo do mínimo, superfície (meio-bloco) nunca muda mais de 0,5 entre vizinhos |
| `ProfileTest.halfBlockStepsAreEvenlySpaced` | numa subida, os degraus de meia-laje ficam a pelo menos 4 blocos um do outro |
| `ProfileTest.classificationRunsAreClean` | 2 000 perfis: junções sempre no chão, túneis ≥ 16, pontes secas ≥ 8, toda água fora das junções é ponte |
| `WorldPlanTest.villageCreaturesAreRemovedInTheCityAndItsMargin` | em 30 cidades: o centro conta como cidade; andando do centro para fora, a área vale até 32 blocos (±1) depois da última coluna de rua ou lote e não vale mais longe |
| `WorldPlanTest.parkBenchesSitOnTheEdgeOfThePathsFacingThem` | nos 30 parques do terreno plano: todo assento com a trilha na frente e sem trilha atrás; todo braço com o painel encostado num assento; nenhum banco sobre a trilha; nada cresce através do banco (só copa alta); bancos a 8+ um do outro; todo parque com banco; 565 bancos, 1 018 assentos, 1 a cada 18,6 blocos de trilha |
| `WorldPlanTest.streetLightsStandOnTheSidewalksWithTheirArmsOverTheLanes` | em 20 cidades, coluna por coluna: postes só em trechos de rua (nunca em cruzamento nem em faixa de pedestres); coluna inteira de 13 (com tufo de folhas só na variação tomada); vinha de trás sempre virada para a coluna, a 3+ de altura e com coluna ao lado na mesma altura; sobre a pista nada abaixo de 11 além da lâmpada caída; vinha pendurada sempre sob uma laje; braço quebrado só nas 2 primeiras colunas; 129,9 postes por cidade (de 144, 10% faltam), variações 505 / 523 / 533 / 525 / 511; nos planos dos chunks poste e lâmpada caída não trafegáveis |
| `WorldPlanTest.cutCrownsHangOverSidewalksAndCores` | nas cidades do terreno plano (1 237 chunks com copas completadas): 11 622 folhas no bosque e 892 sobre calçadas; 2 144 baixas demais sobre a rua e 13 onde o bosque já planeja algo ficam de fora; nenhuma em outra estrutura; cada uma no chunk pedido |
| `WorldPlanTest.cityStreetsHaveNoDeadEndsAndLotsAreBuildable` | em todas as cidades dos 7 terrenos (357 cidades 2 × 2 de quarteirões 180 × 120, 11 413 lotes; nenhum prédio repetido na cidade, 6,34 prédios por cidade — estruturas, parques e miolos tomados pela natureza; nenhum quarteirão com duas estruturas iguais; em cada cidade no máximo 3 farmácias, 2 oficinas e 2 postos; 204 quarteirões de parque, cada um com o parque de 110 × 64 num canto e 947 estruturas em volta, nenhuma a menos de 4 blocos dele; gera `park_block.png`): nenhum nó de grau 1 (sem becos), nós de grau 2 só nos cantos (com o arco do canto); toda coluna de lote é lote do quarteirão certo e não toca rua |
| `WorldPlanTest.streetWidthIsConstantOnStraightStreets` | toda rua reta de cidade tem exatamente 33 colunas; a zebra fica nas linhas 2 a 4 do trecho, com 14 listras, e não existe nas outras linhas |
| `WorldPlanTest.highwayNetworkIsConsistentAndWellConnected` | 21 combinações terreno × seed: Gabriel simétrico, rodovia começa e termina exatamente na entrada de cada cidade, raio de curva ≥ 60, pontos a ~1 bloco, comprimento máximo, cruzamento de entrada nunca usado duas vezes (um canto tem duas saídas, mas só uma rodovia), no máximo 8 rodovias por cidade, nenhum par de rodovias perto demais (sem cruzamentos); limites: plano/colinas ≥ 85% das ligações e ≤ 5% de cidades sem rodovia, montanhas/rios/cânions ≤ 15%, e pelo menos metade das células com cidade (exceto ilhas); gera `network_*.png` |
| `FloraTest.overgrownBlocksAreConsistent` | 6 miolos de 140 × 90 (o teste usa esse tamanho fixo): mais de 120 árvores cada, copas dentro do quarteirão, toda vinha presa em folha/tronco ou embaixo de outra vinha, toda folha com distância de 1 a 6 e um vizinho um passo mais perto da madeira (regra do leaf decay do vanilla), plantas só a 1 bloco do chão e só em solo (grama, musgo, terra grossa), metades de cima das plantas altas certas, entulho nunca flutua; nenhuma árvore entra numa estrutura; gera `wild_block.png` |
| `FloraTest.parksAreOvergrownAndKeepTheirFountain` | 6 parques: as mesmas regras de plantas e vinhas, todo bloco da fonte no lugar (só a troca baú → pedregulho musgoso, as lajes musgosas do anel e o piso de pedra virado musgoso), cachos de vinha na fonte; gera `wild_park.png` |
| `RoadDecayTest.aboutThreeColumnsInTenAreTakenInPatches` | 1,9 milhão de colunas em 3 seeds: 29,8% tomadas, determinístico, em manchas (73% das tomadas têm a vizinha tomada), os 6 tipos aparecem |
| `RoadDecayTest.tunnelsRampStepsAndWallsStayClean` | túneis, degraus de meia-laje e a coluna do muro nunca são tomados (pontes podem ser); calçadas racham em pedregulho, nunca em asfalto |
| `RoadDecayTest.wornTextureAndMarkings` | 250 000 colunas: textura gasta sempre de uma das variantes da pista, da calçada ou das lajes; `polished_deepslate` é a mais comum (54%) e todas as variantes aparecem; 20% das faixas apagadas; a coluna do muro não muda |
| `WreckTest.holesAreAboutOnePerEightyBlocksOfBridgeInTheMiddleOfTheLanes` | 2 000 perfis de ponte de 60 a 460 blocos: 1 buraco a cada ~85 blocos de ponte, sempre na ponte, longe das pontas, dentro das 27 faixas, do tamanho pedido; nenhum fora das pontes |
| `RoadDecayTest.plantsOnlyOnGrassAndMossAndWithoutCollision` | plantas só sobre grama e musgo, sempre sem colisão (grama baixa, alta, samambaia), metade de cima da grama alta certa |
| `RingTest.withoutStructuresBlocksStayEmpty` | (pulado com estruturas) com `index.txt` vazio: nenhum estado de estrutura e o anel não cria lotes |
| `RingTest.groundLayerAndBuriedLayers` | as estruturas com camada enterrada têm blocos embaixo do chão e nada mais fundo; ar enterrado só no prédio largo e na delegacia (o `cave_air` da garagem e do subsolo, cavado); mansão abandonada, casa grande, torre, azulado e apartamento descem 1, o prédio largo 8 |
| `RingTest.undergroundGarageKeepsItsRampAndStairsOpen` | no prédio largo, as 99 células de ar da camada do chão ficam abertas (a rampa inteira, com o topo, e o poço da escada), com ar cavado ou a própria rampa embaixo (nunca terreno), nas 4 rotações; a delegacia tem 405 (as salas do subsolo atravessando a camada da rua); nenhuma outra estrutura tem abertura; os 249 contêineres dele são alcançáveis (0 fora de alcance) |
| `StructuresCarveTest.caveAirIsDugUnderTheGround` | molde sintético 3 × 3 × 3 com chão 2: abaixo do chão o `cave_air` vira ar (escavado) e o `air` comum continua terreno (`KEEP`); a pedra enterrada e a do nível do chão são colocadas |
| `RingTest.frontsMeetTheSidewalk` | a fileira da frente, no nível do chão, é toda de blocos em 51 dos 52 arquivos (o apartamento tem 22 de 31: as falhas viram o chão da cidade, nivelado) |
| `RingTest.structureTreesHaveRealLeaves` | toda folha não persistente das estruturas tem distância 1 a 6 e um vizinho tronco ou folha mais perto (regra do leaf decay); mais de 1 000 folhas reais |
| `RingTest.housesWithTheirFrontOnPlusZFaceTheStreet` | a casa moderna e a mansão pós-apocalíptica têm giro de 180° no índice e, com a rua ao norte, a fileira encostada na rua é a última fileira (+Z) do molde, bloco a bloco; as outras não têm giro |
| `RingTest.pharmacyVariantsMatchTheirOriginalButTheColor` | nas 8 variações de farmácia, posição por posição contra a original: os mesmos blocos nos mesmos lugares, nenhum bloco neutro (concreto branco, quartzo liso, calcita, diorito polido) pintado e os banners nas mesmas posições; 76 blocos do letreiro F A R M Á C I A devolvidos em cada pequena, 152 em cada grande |
| `RingTest.pharmacyBannersKeepTheirLetters` | nas 4 rotações, cada uma das 10 farmácias (originais e variações) tem 9 banners (pequena) ou 18 (grande) com desenho (listras e borda), todos em blocos de banner; nas pequenas, banner branco com letras na cor da variação; nas grandes, banner na cor da variação com letras pretas |
| `AccessTest.closedRoomsDoorsAndLadders` | salas montadas no teste: baú em sala lacrada é de difícil acesso; com porta de madeira não; com porta de ferro ou cerca no vão sim; baú num pilar de 4 só se alcança com escada de mão; degraus de 1 bloco se sobem pulando |
| `AccessTest.highContainersAreHard` | barril a `Access.HIGH` (15) blocos do chão, alcançável por escada, conta como difícil; baú no chão não |
| `AccessTest.structuresHardContainers` | nos 52 arquivos, com o abandono: 1 382 de 3 106 contêineres de difícil acesso (entre 10% e 50%: os prédios altos novos têm quase todos os seus acima de 15); os mesmos nas 4 rotações; prédios grandes e médios têm contêineres altos; nas casas no máximo 5% (4 de 356) |
| `AccessTest.everyStructureHasItsLootTables` | as 6 tabelas do tema de cada estrutura existem; barril e vaso com 40% de chance, `barril_dificil` 75%, `vaso_dificil` 80%, baús sem chance de vir vazios; nada do Lost Cities ou do OAmP |
| `WeatheringTest.onlyAllowedChangesAndEverythingHolds` | nos 43 arquivos, célula por célula contra o molde salvo: só mudanças permitidas (rachado e musgoso da mesma pedra, com as mesmas propriedades; piso de pedra ou concreto com ar em cima virando solo; tapete sobre bloco inteiro ou solo; grama, samambaia e arbusto seco só sobre solo; vinha com uma face, presa em bloco inteiro ou na vinha de cima com a mesma face, ou sob beiral); banners iguais; caixa da estrutura igual; o mesmo resultado em duas cargas; relatório igual às mudanças. Total: 17 879 vinhas, 6 712 solos, 3 518 tapetes, 3 263 plantas, 1 768 musgosos, 1 469 rachados |
| `WeatheringTest.everyStructureReachesTheMeasuresOfTheApocalypticBuilding` | nenhuma medida cai (rachado e vinhas podem cair até 3 pontos quando piso vira musgo: o bloco sai da família ou da parede contada); rachado a 31% (−3) ou mais; o lado menos coberto de vinhas com 19% ou mais (meta 25%); no prédio apocalíptico só as vinhas mudam |
| `WeatheringTest.wholeNamesAreNotTakenForParts` | `light_gray_concrete`, `smooth_sandstone` e `mud_bricks` são pedra inteira; tábua é inteira mas não pedra; vidro, folhas, laje, baú, banner e o bloco de luz não são inteiros |
| `RingTest.cutCrownsAreCompletedOutsideTheBox` | 189 folhas completam copas cortadas, só na casa com piscina, com garagem, de subúrbio, com torre, de vidro, no prédio esqueleto e no prédio da rampa; nas 4 rotações: todas fora da caixa e a até 4 dela, não persistentes, distância 1 a 6 com um vizinho um passo mais perto do tronco, sem repetir posição; as da casa com piscina ficam na frente e acima de `CLEARANCE` |
| `RingTest.everyStructureHasASpawnCategory` | toda estrutura tem uma das 7 categorias do `/amcs spawn`: casa 12, delegacia 1, especial 1, farmácia 10, oficina 8, posto 10, prédio 10 (52) |
| `RingTest.lightningRodsBecomeRedstoneTorches` | nenhum estado de para-raios sobra nas estruturas; 5 tochas de redstone de pé vindas de para-raios (mais 15 salvas como tochas nas estruturas novas, 3 delas na delegacia: 20) (uma no topo de cada estrutura que tinha para-raios); para-raios de lado vira tocha de parede com a mesma direção |
| `RingTest.noFloatingCobwebs` | nenhuma teia das estruturas fica sem um bloco em que se prender (o chão conta); mais de 2 000 teias conferidas |
| `RingTest.spawnLimitsHoldOverACity` | 300 cidades de 4 quarteirões sorteados em sequência: no máximo 3 farmácias, 2 oficinas, 2 postos e 1 de cada prédio; o contador da cidade bate com o que foi posto |
| `RingTest.structuresHaveSolidBoxesWithAFloorLayer` | toda estrutura tem caixa não vazia e blocos na primeira camada |
| `RingTest.rotationKeepsEveryBlockAndTurnsTheFrontToTheStreet` | as 4 rotações têm o mesmo número de blocos em todas as estruturas; a rotação de 90, 180 e 270 graus confere célula a célula com a fórmula da transformação |
| `RingTest.ringsFitTheirBlockTouchTheSidewalksAndDoNotOverlap` | 6 000 anéis em quarteirões 180 × 120 e 120 × 180: dentro do quarteirão, sem sobreposição, cada estrutura encosta na calçada para onde a frente aponta, nenhuma estrutura repetida no mesmo quarteirão, determinístico, as 21 aparecem e os 4 lados são usados |
| `RingTest.everyStructureFitsNextToTheShallowestInA120Block` | cada estrutura cabe num quarteirão de 180 × 120 junto da mais rasa (20) e do bosque mais largo (30), virada para um lado comprido ou para um lado curto (o prédio da rampa, 102 × 72, só no lado curto) |
| `RingTest.structuresNeverTouchAndLeaveTheForest` | 6 000 anéis em 180 × 120 e 120 × 180 (192 108 pares): duas estruturas quaisquer ficam a 4 ou mais blocos; entre lados opostos, 20 ou mais de bosque (menor medido: 4 e 21) |
| `PlanAuditTest.citiesAndHighwaysPassThePlanAudit` | `PlanAudit` coluna a coluna em 3 cidades inteiras e 2 rodovias inteiras por terreno × seed (105 auditorias); gera `city_*.png` |
| `PlanAuditTest.planIsIndependentOfQueryOrderAndThreads` | 3 721 chunks planejados de novo em ordem embaralhada por 8 threads: resultado idêntico, campo a campo |
| `FingerprintTest.plannerOutputIsUnchanged` | as 11 partes da impressão digital do planejador (seção 1) iguais às do commit `4dd94ae`; cobertura: 1 129 cidades, 2 013 rodovias, 31 214 chunks, 1,47 milhão de colunas de via (44 129 de ponte, 26 783 de túnel), 1,22 milhão de colunas de lote (6,6 milhões de blocos de estrutura nos lotes), 10 198 colunas de poste, 1 741 folhas de copa, 16,8 milhões de células de estrutura, 2 036 peças de banco; 15 a 25 s |

### `PlanAudit` (plano, coluna a coluna, atravessando chunks)

- **Vias:**
  - `step` (degrau > 0,5 entre colunas trafegáveis vizinhas) e `city_level`;
  - `wall_gap` (muro de rodovia que só encosta no vizinho pela quina);
  - `highway_hole` (buraco na faixa da rodovia), `highway_height` e `highway_end` (rodovia que não pousa numa rua
    da cidade, no nível dela);
  - `short_tunnel` e `ramp` (inclinação ou patamar fracionário no perfil).
- **Rede de ruas:**
  - `street_missing` e `dead_end`;
  - `orphan_road` (trecho de via que não chega a nenhuma cidade nem sai da área);
  - `city_split`.
- **Lotes:** `lot_overlap` (dois lotes de um quarteirão se sobrepõem).

### `WorldAudit` (blocos gerados)

- **Superfície das vias:**
  - `hole` (sem superfície), `surface_block` (bloco diferente do planejado) e `no_base`;
  - `height_mismatch` (altura medida diferente da planejada);
  - `step` (medido nos blocos, inclusive entre chunks).
- **Espaço livre e decoração:**
  - `obstacle`, `cover` e `fluid_on_road` (4 blocos livres acima da pista, 7 em túneis);
  - `vegetation_over_road` (até 48 acima; as folhas planejadas que completam copas de estruturas não contam);
  - `street_light` (peça de poste planejada que falta ou mudou: coluna, braço ou vidro; as conferidas contam em
    `street_light_blocks`);
  - `crown_leaf_missing` (folha planejada de copa completada que ficou sem bloco; as conferidas são contadas em
    `crown_leaves`).
- **Estruturas:** `parapet`, `tunnel_ceiling` (`stone` ou `glowstone`), `tunnel_wall` e `pillar_floating`.
- **Lotes:**
  - `lot_hole` (água que a estrutura põe no nível do chão, a piscina da mansão, não conta);
  - `yard_ground` (chão livre de cidade que não é `gray_concrete_powder`; desde 11 nov não há mais chão livre: o
    miolo é um lote `WILD`);
  - `building_mismatch` (nome herdado: bloco de lote — parque, fonte, miolo tomado pela natureza com árvores,
    vinhas, plantas e entulho, estrutura — diferente da definição;
    costuras entre chunks apareceriam aqui).
- **Regras gerais:**
  - `hard_block` (bloco indestrutível em via, ponte, túnel ou lote; qualquer dureza é permitida);
  - `hole_filled` (buraco de ponte com bloco na superfície ou na laje; contados em `bridge_hole_columns`),
    `parapet` (muro de ponte: `stone_brick_wall` ou `mossy_stone_brick_wall`, ou ar num trecho caído; `wall_gap_columns`);
  - `road_plant` (planta planejada numa coluna de via tomada que falta ou mudou; as colunas tomadas são contadas em
    `taken_road_columns`, as plantas em `road_plants`; a superfície tomada é conferida em `surface_block`);
  - `banner_without_design` (banner de estrutura sem o desenho do molde; contados em `structure_banners`);
  - `structure_without_loot` (contêiner de estrutura — baú, barril, dispenser, dropper, vaso decorado — sem loot
    table) e `structure_wrong_loot` (com outra tabela que não a do tema da estrutura, do tipo de contêiner e do acesso;
    os block entities das estruturas são contados em `structure_block_entities`, os contêineres em
    `structure_loot_containers` e os de difícil acesso em `structure_loot_hard`), `loot_in_city` e `container_in_city` (fora das estruturas; baú da fonte vazio,
    contado em `fountain_chests`);
  - `orphan_road` (conectividade medida nos blocos).

## 3. Problemas encontrados e corrigidos

Histórico herdado do Abandoned Simple City (este mod começou como cópia dele em 7 nov 2026). Os achados sobre
prédios (8, 13, 15, 17 a 26) não se aplicam mais aqui: os prédios foram removidos. Os achados 1 a 17 são de versões anteriores; alguns citam regras que mudaram depois (quarteirões de 64,
escadas em "poços" junto às paredes). As correções continuam valendo onde a ideia se aplica: o reparo de vizinhos
e a regra de escada e apoio no mesmo chunk.

| # | Onde apareceu | Problema | Causa | Correção |
|---|---|---|---|---|
| 1 | `ProfileTest`, `PlanAuditTest` (1 ponto em ~1 de cada 6 rodovias) | patamar de 1 ponto numa altura fracionária | a faixa permitida (cones de rampa das duas cidades) tem um platô de 1 ponto no topo/fundo quando a paridade não fecha; o perfil parava ali | `Profile.build`: bloqueado numa altura fracionária, o perfil inverte o sentido em vez de parar |
| 2 | Bateria (inicialização) | bateria levava minutos para começar | o PNG da rede amostrava o ruído do jogo em ~750 mil colunas (cada `getBaseHeight` monta um `NoiseChunk`, ~3 ms) | render da rede sem terreno na bateria; renders do plano amostram o terreno a cada 8 blocos |
| 3 | Bateria (thread dump) | planejamento lento em áreas com muitas rodovias | `tooClose` (distância mínima entre duas rodovias) comparava cada ponto com ~800 pontos da outra | grade espacial com célula = 21 blocos: linear no comprimento |
| 4 | Bateria, seed 20261027 | `surface_block`, `hard_block`, `tunnel_wall`, `parapet`: minério de cobre/carvão, terra, cascalho, granito, diorito e andesito no lugar da `stone` de calçadas, parapeitos e paredes de túnel | os minérios e bolhas de terra/cascalho/rochas (`ore_*`) de um chunk decorado depois alcançam ~15 blocos dentro do vizinho já construído e substituem `stone` (a `smooth_stone` das pistas não é substituível) | o reparo de vizinhos restaura superfície, base, parapeito, teto e paredes de túnel em todas as colunas de via do vizinho |
| 5 | Bateria, seed 20261027 | `pillar_floating` (194 colunas): ponte sobre o mar sem pilares | o reparo de vizinhos repavimentava "superfície comida" também em vizinhos **ainda não construídos**; quando esse vizinho era construído, o bloco solto era lido como chão e o pilar não descia | o reparo só atua em vizinhos que já passaram pela etapa `FEATURES` (os demais serão construídos do zero) |
| 6 | Bateria, seed 20261027 | `building_mismatch`: folhas e cogumelos dentro de prédios | árvores e cogumelos de chunks decorados depois | o reparo remove decoração no volume dos prédios do vizinho, só onde o prédio quer ar |
| 7 | Bateria (auditoria) | `loot_in_city`: dois baús a y 110 sobre um túnel; `pillar_floating` sob ressaltos de rocha | falsos positivos: a auditoria olhava a coluna inteira | contêineres só no volume construído; pilar "flutuante" só se o ar estiver acima do chão natural do ruído |
| 8 | Bateria, seed 20261027 | `building_mismatch` (1 724): faltavam as faixas das lajes de um estilo de prédio | o reparo de prédios tratava `stripped_oak_log` (faixa do estilo 7) como tronco de árvore | o reparo nunca remove um bloco que o prédio define; o estilo 7 usa tábuas |
| 9 | Bateria, seed 20261027 (PNG da rede) | auditorias OK, mas a rede real era pobre: 5 cidades em ~7 × 7 km, quase todas sem rodovia | o estágio 1 sorteava **um** ponto por célula e recusava a célula se ele caísse perto de água ou em morro; o overworld real tem muito mais rios e lagos que os terrenos sintéticos, então poucas cidades e ligações longas demais | o estágio 1 tenta até 10 posições (as últimas com raio menor); ligações candidatas até 2 200 blocos (rodovia até 3 000); novo terreno sintético `RIVERS` e limites de cidades isoladas no teste de rede, para essa classe de problema ser pega no `./gradlew test` |
| 10 | `WorldPlanTest` (rede) | "nenhuma saída voltada para a outra cidade" era o principal motivo de ligação perdida (até 48 de 126 em terreno misto) | cidades irregulares têm poucos nós em T no contorno, e cada um serve a uma rodovia só | o meio de cada segmento de rua do contorno também é saída (junção em T com a rua externa), com 48 blocos no mínimo entre saídas usadas e saída até ~75° fora do eixo; ligações mantidas subiram de 51–96% para 74–100% e cidades isoladas ficaram entre 0 e 4% |
| 11 | Bateria, seed 3478477863671517581 (cidade acidentada, nível 114) | `lot_hole` (69) e `building_mismatch` (104): `cave_air` e `stone` no piso de lotes e prédios | um lago de lava de um chunk decorado depois cava o espaço acima do líquido (`cave_air`) e põe uma borda de pedra, dentro do vizinho já construído | o reparo de vizinhos também restaura lotes: aterro sob o piso, piso, e a faixa até 8 blocos acima (prédio, árvores do parque, praça), removendo líquidos e vedando os vizinhos |
| 12 | Bateria, seed 1 | `building_mismatch`: cascalho no lugar de granito em paredes | o granito (variação "gasta" do estilo de tijolos) é alvo das bolhas de minério, como a pedra | granito polido; `Palette.validate()` agora recusa em prédios e lotes qualquer bloco das tags que os minérios substituem |
| 13 | Bateria, seed 1 (cidade a 139 de altura) | `building_mismatch`: `dripstone_block` nos andares altos de um prédio | um aglomerado de dripstone de um chunk vizinho tomou as salas fechadas por caverna | nas colunas de prédio perto da borda, tudo onde o prédio quer ar é removido, em toda a altura |
| 14 | PNG da rede (terreno real) | muitas cidades de 1 quarteirão (64 × 64) | a regra de quarteirão exigia 5 de 5 amostras secas e niveladas | o meio do quarteirão precisa estar firme; 1 dos 4 cantos pode não estar (lagoa ou morrinho aterrados/cortados). Nos testes: 17–37 quarteirões por cidade, quase nenhuma de 1–2 |
| 15 | Bateria final, seed 20261027 | `building_mismatch`: `mossy_cobblestone` numa parede, num andar alto | uma pedra de floresta (*forest rock*) de um chunk vizinho, acima da faixa que o reparo de lotes cobre | perto da borda, o reparo de prédios restaura todos os blocos do prédio (não só o ar); alcance do reparo ampliado ao vizinho inteiro |
| 16 | Bateria final, seed 3478477863671517581 | `building_mismatch`: `cave_air` onde o prédio quer ar | falso positivo: `cave_air` é ar | a auditoria aceita qualquer tipo de ar |
| 17 | Bateria final, seed −3297019724594858492 | `building_mismatch`: 2 blocos de escada de mão viraram `air` a 19 blocos do térreo | investigado no mundo gerado com o comando novo `/amcs column` via RCON: a parede de apoio estava intacta, mas em **outro chunk**; o vanilla reavalia posições marcadas para pós-processamento quando o chunk é promovido e a escada, sem apoio naquele momento, quebra | cada prédio escolhe, entre 4 poços possíveis (um por parede, junto a um canto), um em que escada e apoio ficam no mesmo chunk; no caso raríssimo em que os 4 cruzam a borda (0,015%), o prédio fica 1 bloco mais estreito. Teste novo `laddersAreHeldByWallsOfTheSameChunk`: 32 612 prédios, toda escada com parede sólida atrás e nenhuma apoiada em outro chunk |
| 18 | `buildingsHaveTheirSizeRoomsAndLadders` (prédios com salas) | área de um andar inalcançável a partir da escada | o pilar de apoio da escada caía bem na passagem entre duas salas e a fechava | as passagens não usam células do pilar nem células com pilar dos dois lados; o teste percorre todo andar a partir da escada |
| 19 | `buildingsHaveTheirSizeRoomsAndLadders` (teste 3D da escada em zigue-zague) | patamar do 1º andar inalcançável | o último degrau do lance de volta ficava 1 bloco abaixo do patamar seguinte, e a laje de cima impedia o pulo | o último degrau fica embutido no patamar (um stairs no lugar do piso) |
| 20 | Teste no jogo pelo usuário (jar da escada em zigue-zague) | não dava para subir o lance de volta: a cabeça batia | acima dos degraus do lance de volta só havia 2 blocos livres (a laje do lance de cima descia junto); ao subir um degrau o jogador passa 1 bloco mais alto sobre a coluna que deixa, então precisa de 3. O teste 3D só cobrava 2 | o enchimento sob o lance de cima começa 1 bloco mais alto (3 livres acima de cada degrau); o teste 3D agora exige 3 blocos livres sobre a coluna de origem em toda subida |
| 21 | Teste no jogo pelo usuário (escada em zigue-zague) | as caixas fechadas eram grandes demais e deixavam corredores de 1 bloco entre a caixa e as paredes das salas | — | a caixa fechada foi trocada pelas escadas abertas dos blueprints do usuário (lance reto por andar com `iron_bars` em volta das aberturas), 1 por prédio médio e 2 por grande |
| 22 | Teste no jogo pelo usuário (escadas abertas) | buraco em degraus no teto da sala, ao lado da parte de baixo da escada | sob os degraus o vão era aberto inclusive no enchimento do teto | o enchimento continua fechado sob os degraus; só abre sobre eles, onde a cabeça precisa |
| 23 | `buildingsHaveTheirSizeRoomsAndLadders` (sacadas) | coluna ocupada pelas sacadas de dois prédios médios frente a frente com 3 blocos de vão | a face só exigia 3 blocos livres de outros prédios, mas os dois saem 2 | a faixa livre passou para 2 × alcance + 1 = 5 blocos; na viela de 5 as sacadas dos dois lados ficam com 1 bloco entre elas |
| 24 | Teste no jogo pelo usuário (sacadas e pilares) | pilares embutidos não agradaram: queria relevo | — | nas faces livres os pilares saem 1 bloco do 1º andar até o telhado (no térreo e nas laterais coladas no vizinho continuam embutidos) e dividem as sacadas que cruzam |
| 25 | Teste no jogo pelo usuário (pilares em relevo, aprovado) | pediu mais variação: relevo negativo | — | três fachadas sorteadas em terços: lisa, relevo e recuo (faixas das janelas 1 bloco para dentro do 1º andar ao topo) |
| 26 | `buildingsHaveTheirSizeRoomsAndLadders` (fachada com recuo) | andar de prédio médio com 7 salas (máximo 5) | só a coluna atrás de cada faixa recuada virava parede; as colunas atrás dos pilares e dos cantos ficavam como nichos de 1 bloco presos entre as faixas | nos andares com recuo toda a 1ª coluna junto às paredes vira parede |
| 27 | Bateria, 8 nov (estruturas, já removidas) | `palette` derrubava o servidor: camas, fornalhas, fogueiras, sensor de luz e jukebox nas estruturas | block entities fora da lista permitida | `Palette.exempt` passou a aceitá-los (vazios) |
| 28 | Bateria, 8 nov (estruturas, já removidas) | `building_mismatch` (17): diorito e dripstone no lugar de andesito das estruturas, acima de 8 blocos do chão | blobs de minério de chunks decorados depois; o reparo de vizinhos só cobria 8 blocos acima do chão | perto da borda, o reparo cobre a altura inteira do lote |
| 29 | `FloraTest` (natureza, 11 nov) | vinha sem apoio onde duas copas se encontram | a copa de uma árvore corta o cacho de vinhas da outra | o índice de árvores descarta a vinha que não está presa em folha/tronco nem embaixo da mesma vinha |
| 30 | `FloraTest` (natureza, 11 nov) | `ArrayIndexOutOfBounds` em `Park.code` | consulta de vinha da fonte acima da altura do molde (as árvores do parque são mais altas que a fonte) | limite de altura na consulta |
| 31 | `FloraTest` (natureza, 11 nov) | grama sobre o piso da fonte onde falta uma laje do anel | a falha da laje punha grama sem olhar o bloco de baixo | sem grama nas falhas sobre a fonte |
| 32 | `FloraTest` (piso musgoso, 11 nov) | o piso de pedra da fonte virou grama | a tabela de troca era montada antes do índice do `mossy_cobblestone` existir (ordem da inicialização estática: valia 0) | tabela declarada depois do índice |
| 33 | Testes, 12 nov (estruturas) | todas as classes de estrutura falhavam ao carregar (`NullPointerException`) | a lista de blocos que não são piso e a fração mínima de piso eram declaradas depois do bloco estático que carrega as estruturas | o bloco de carga foi para o fim da classe |
| 34 | `RingTest`, 12 nov | os lados curtos dos quarteirões 140 × 90 ficavam quase sempre vazios (9% das estruturas em cada) | as linhas dos lados compridos iam de canto a canto e as estruturas fundas (até 57) não deixavam espaço | os lados curtos vão de canto a canto; agora ~25% por lado |
| 35 | Revisão, 12 nov | loot das estruturas (código de outra sessão) varria o volume inteiro da estrutura a cada coluna e lia o block entity direto do chunk em geração, onde ele ainda não existe | — | o loot é posto quando cada contêiner é colocado, pela região (como as estruturas vanilla); a auditoria passou a exigir loot nos contêineres das estruturas |
| 36 | Bateria, 12 nov | `lot_hole` (até 200 por alvo): água e seagrass no nível do chão | a piscina da mansão abandonada, que desceu 1 bloco, fica no nível do chão | água planejada pela estrutura não conta como buraco |
| 37 | Teste no jogo pelo usuário, 12 nov | a casa moderna abandonada aparecia de costas para a rua | a frente do molde dela é o lado +Z (todas as outras: -Z) | campo `frente` no `index.txt` (180) e giro por estrutura em `Structures.Def` |
| 38 | Teste no jogo pelo usuário, 12 nov | a mansão pós-apocalíptica de costas para a rua; teias flutuando nela | frente no +Z do molde; teias sem apoio no molde | giro de 180° no índice; teias soltas viram ar em todas as estruturas (18) |
| 39 | Teste no jogo pelo usuário, 12 nov | banners das farmácias lisos | o leitor de moldes ignorava os dados de block entity, onde ficam os desenhos | `Template.banners` guarda os padrões, aplicados quando o banner é colocado (geração e `/amcs spawn`); auditoria `banner_without_design` |
| 40 | Bateria, 12 nov (quarteirões 180 × 120) | `building_mismatch` (3): folhas de bétula planejadas viraram ar | a limpeza de folhas órfãs (árvores naturais cortadas pela cidade) considerava toda folha não persistente; as das árvores reais do mod ficaram assim, e com o tronco num chunk ainda não gerado pareciam órfãs | a limpeza nunca remove folhas que o plano coloca (`ChunkBuilder.plannedLeaves`) |
| 41 | `PlanAuditTest`, 12 nov (buracos nas pontes e desabamentos nos túneis, estes depois retirados) | `orphan_road` (18 de 105 auditorias): colunas de pista isoladas | a borda irregular, sorteada coluna a coluna, cercava colunas de pista | contorno irregular mas suave em volta do centro (ondas do ângulo) sem ilhas; 105 de 105 passam |
| 42 | Bateria, 12 nov (pontes velhas) | servidor travado no viaduto e avisos `Detected setBlock in a far chunk` | nos trechos de muro caído, `wallTop` devolvia `Integer.MIN_VALUE + 1` e o laço da altura livre do reparo de vizinhos percorria ~2 bilhões de alturas; o muro também era posto nos trechos caídos (altura do vizinho) | `wallTop` devolve `Integer.MIN_VALUE` quando a coluna não tem muro e a altura livre começa na superfície |
| 43 | Bateria e teste por RCON, 12–13 nov | a loot table `lostcitychest` (do Lost Cities) era rejeitada pelo Minecraft ao carregar | escape a mais em `custom_data` | substituída, a pedido do usuário, pelas tabelas vanilla próprias (`tools/gen_loot.py`); o log da bateria não tem mais erro de loot table |
| 44 | `WorldPlanTest`, 13 nov (copas completadas) | nenhuma folha de copa planejada | `WorldPlan.citiesIn` só devolve cidades cujo centro cai no retângulo pedido, e o retângulo de um chunk quase nunca contém o centro | `Crowns.forChunk` usa `cityNear` |
| 45 | Bateria, 13 nov (copas completadas) | `crown_leaf_missing` (todas, até 324 por alvo) e avisos `Detected setBlock in a far chunk` | o reparo de vizinhos tirava as folhas como decoração solta sobre o lote e a rua e, ao conferir os 4 vizinhos do bloco tirado, chamava `ensureCanWrite` 2 chunks longe (o que loga o aviso) | o reparo pula as folhas planejadas (`Overhangs.forChunk` do vizinho) e só confere vizinhos dentro da área de escrita (`canWrite`) |
| 46 | Revisão, 13 nov (abandono) | a lista de blocos que não são inteiros, por trecho do nome, pegava `light_gray_concrete` (`light`), `sandstone` (`sand`), `mud_bricks` (`mud`), e tábuas e lã ficavam sem tapete de musgo | comparação por trecho em nomes curtos | esses nomes passam a ser comparados inteiros (`NOT_FULL_EXACT`); `WeatheringTest.wholeNamesAreNotTakenForParts` |
| 47 | `WeatheringTest`, 13 nov | relatório com mais mudanças que as células mudadas (oficina média: 327 / 321) | uma mancha de musgo caía sobre um bloco que já tinha rachado | uma célula já mudada não recebe outra mudança |
| 48 | `WorldPlanTest`, 13 nov | 11 812 lotes em vez de 11 818: o anel mudou | vinhas e um tapete fora da caixa dos blocos sólidos aumentavam a caixa (a estrutura afastava da calçada) | nada é posto fora da caixa original (`Editor.set` recusa); teste da caixa igual |
| 49 | Medida, 13 nov | alvo de pedra rachada de 23% | a conta inicial incluía pedras sem versão rachada (`polished_deepslate`) | alvo de 31% da família que racha, a medida do próprio prédio |
| 50 | `WeatheringTest`, 13 nov (vinhas fortes) | mansão abandonada com 15% no lado menos coberto (meta 25%) | os trechos de vinha que descem abaixo do fim da parede contavam para a meta sem cobrir fachada | só as vinhas na fachada contam |
| 51 | `WeatheringTest`, 13 nov (vinhas fortes) | cobertura de vinhas da casa abandonada "caía" | o `moss_block` não contava como bloco inteiro: as vinhas ao lado de um piso que virou musgo saíam da conta | `moss_block` conta como parede (a vinha se prende nele) |
| 52 | Teste no jogo pelo usuário, 13 nov | as variações de farmácia não eram iguais à original | um letreiro F A R M Á C I A em blocos coloridos sobre a parede branca | os blocos neutros da original voltam onde a variação os pintou (`withNeutralFrom`) |
| 53 | Revisão, 13 nov (mesma comparação) | banners D R U G S T O R E das farmácias pequenas coloridas 1 bloco à esquerda | o deslocamento foi medido alinhando as caixas dos blocos sólidos; nas posições do molde ele é 0 | índice com `@0,0`; teste compara as posições dos banners com as da original |
| 54 | Análise das 7 novas | a boca da rampa e o poço da escada do prédio largo seriam tapados pelo chão da cidade (todo ar na camada do chão vira chão) | regra antiga da camada do chão | ar ou `cave_air` com `cave_air` embaixo fica aberto (`Def.opening`); a auditoria aceita esse ar planejado (`lot_hole`) |
| 55 | `RingTest`, 7 novas | folha com distância 7 na casa moderna com garagem | folhas salvas como não persistentes longe de qualquer tronco (cairiam no mundo) | viram persistentes na carga |
| 56 | Revisão, 7 novas | `/amcs spawn` não cavaria a garagem nem abriria a rampa (ignorava todo ar no chão e abaixo) | regra do comando | o comando cava onde o plano tem ar abaixo do chão (só vem do `cave_air`) e abre as aberturas; conferido por RCON |
| 57 | Teste no jogo pelo usuário, 13 nov | a escada da casa de vidro começava colada na parede de vidro da frente: só se subia entrando de lado | o molde | molde trocado (commit `7e0b2a9`): escada em L a partir do hall, patamar 2 × 2 no canto e corrimão contínuo de barras de ferro do pé da escada até a galeria |
| 58 | Bateria, 13 nov (postes) | `parapet` (até 134 por alvo) e depois `height_mismatch` nas colunas dos postes | a auditoria tratava toda coluna de via não trafegável como muro de rodovia e media a altura do piso em todas | a coluna do poste fica fora dessas duas checagens e é conferida inteira por `street_light` |
| 59 | `WorldPlanTest`, 13 nov (bancos) | vinha de árvore "pendurada no nada" sobre um banco | a coluna do banco devolvia vazio em todas as alturas, apagando a copa acima | acima do banco a árvore continua; só plantas do chão não nascem através dele |
| 60 | `WorldPlanTest`, 13 nov (bancos) | 1 banco a cada 22,2 blocos de trilha (pedido: 15) | distância mínima de 12 entre bancos e poucos trechos retos de 4 blocos nas espirais | distância mínima de 8: 1 a cada 18,6; o resto é a forma das trilhas |
| 61 | Teste no jogo pelo usuário, 13 nov | duas fileiras de terreno logo antes da rampa da garagem do prédio largo (também com `/amcs spawn`) | só ficava aberto o ar da camada do chão com `cave_air` logo embaixo; no topo da rampa, embaixo está a própria rampa (e na boca da escada, o degrau) | fica aberto também o ar da camada do chão ligado a essas aberturas (`StructureLoader.openings`, então `Structures.openings`): 99 células em vez de 87 |
| 62 | Teste no jogo pelo usuário, 13 nov | aldeões e golem de ferro numa cidade gerada onde havia uma vila | as criaturas da vila nascem com as casas, antes da cidade, e ficavam no chunk quando a cidade era construída por cima | o mod as tira do chunk em geração na cidade e a até 32 blocos dela (aldeões, aldeões zumbis, golens, gatos) |
| 63 | Bateria, 13 nov (postes abandonados) | `street_light` (até 962 por alvo): vinhas dos postes faltando | o reparo de vizinhos recolocava as peças do poste e logo depois a limpeza de plantas penduradas sobre a via apagava as vinhas | a limpeza poupa as peças planejadas dos postes |
| 64 | Revisão do código, 12 nov (impressão digital) | os blocos de cada alvo mudaram entre duas rodadas iguais da bateria (mesmo código, mesma seed), também o número de block entities (1 142 e 1 017 na ponte) | o próprio Minecraft não gera igual quando o servidor gera os chunks em paralelo: features de chunks vizinhos postas em outra ordem mudam minérios, sculk, árvores, colmeias e baús (1 073 de 1 369 chunks diferentes na cidade do spawn, de y −64 para cima); nas posições do mod só mudaram propriedades que o Minecraft recalcula pelos vizinhos ao terminar um chunk: a distância das folhas e as ligações de barras e vidraças (na borda do chunk), a forma de uma escada, a neve sobre a grama, `cave_air` no lugar de ar, um lado de muro baixo ou alto, uma face de vinha presa num bloco do chunk vizinho; e o baú vazio de uma fonte só existia como block entity depois de recarregar o mundo | a impressão da bateria (`mod blocks:`) cobre só as posições que o mod decide, com essas propriedades em valor fixo e só os block entities das estruturas; conferida em mundos gerados à parte, recém-gerados e recarregados do disco (seção 6), e sensível a um bloco trocado na paleta (`smooth_basalt` → `basalt` no asfalto gasto: auditoria passa, impressão muda nos 3 alvos) |
| 65 | Revisão do código, 13 nov (impressão digital) | com as vinhas das pontes na impressão da bateria, 3 dos 8 alvos diferiram entre o mundo de referência e um mundo novo do mesmo código | o lugar da vinha (ao lado do tabuleiro, descendo pelo ar) às vezes já tem folhas de uma árvore do vanilla (bétula, abeto), conforme a ordem em que os chunks vizinhos são decorados; numa coluna havia ar num mundo e vinha no outro | as vinhas das pontes ficaram fora da impressão; num passo que só move código, a prova é a listagem de instruções igual da função movida |
| — | Bateria (infraestrutura) | servidores falhando com "Address already in use" | um servidor de uma bateria interrompida ainda terminava | o script usa a porta 25591 e se recusa a começar com um servidor de desenvolvimento vivo |

## 4. Como reproduzir

```bash
./gradlew test                                   # unitários, ~2 min (renders em build/test-renders/)
./gradlew test --tests '*WorldPlanTest.streetWidthIsConstantOnStraightStreets'   # só um teste
tools/selftest.sh quick 20261027                 # bateria rápida (3 alvos), ~4 min
tools/selftest.sh full 20261027 -3297019724594858492 3478477863671517581 1
cat run/selftest/<seed>/report.txt               # termina com RESULT: PASS ou FAIL
./gradlew test --tests '*FingerprintTest'        # impressão digital do planejador, ~30 s
# comparar duas baterias (mudança que não deve alterar o mundo):
diff <(grep -E 'mod blocks:|stats:' A/report.txt | sed 's/loot_containers_anywhere=[0-9]*, //') \
     <(grep -E 'mod blocks:|stats:' B/report.txt | sed 's/loot_containers_anywhere=[0-9]*, //')
```

O `run/config/abandonedmoderncity-common.toml` do servidor de desenvolvimento tem valores antigos (`cellSize = 512`,
`maxRadius = 131`) que o mod recusa como inconsistentes: o log diz "Invalid config ... using the defaults" e a
bateria usa os padrões (736 e 230), como mostra a primeira linha do relatório.

No jogo (op):
- `/amcs locate city` (ou `highway`, `tunnel`, `bridge`);
- `/amcs info`, que mostra o que o plano quer na coluna;
- `/amcs audit 6` e `/amcs plan 12`;
- `/amcs locate structure <nome|tipo|grupo>` e `/amcs spawn <categoria> <nome>`.

Teste dos comandos de estrutura (12 nov 2026, servidor de desenvolvimento via RCON, seed 20261027, a partir de
0 70 0): `locate structure torre` → 1 707 blocos; `farmacia` (grupo) → `farmacia_vermelho` a 290; `casas` (tipo) →
`casa_abandonada` a 331; nome inválido lista os nomes aceitos. No mundo gerado, a coordenada da farmácia cai na
calçada (`smooth_stone`) e a coluna atrás é o lote da estrutura (`/amcs info`: BUILDING). `spawn torre` olhando
para o sul: caixa 2 blocos à frente, 62 de largura centralizada, chão no bloco do terreno, 311 793 blocos e 178
contêineres com loot; `spawn farmacia_vermelho` olhando para o oeste: frente virada para o leste (para quem
executou). Depois dos banners: `spawn farmacia_vermelho` e `data get block` nos banners das pontas: E (listras esquerda,
topo, meio e base, borda) e D (listras e borda ondulada) — quem olha a fachada lê DRUGSTORE.

Vinhas das pontes (12 nov 2026, RCON): ao redor do viaduto seco mais longo da seed 20261027 (-1965 86 -1382)
há ~700 blocos de vinha descendo das bordas; uma "ponte" de 12 blocos sobre um desnível pequeno não tem vinhas
(as colunas da borda dela são de via no chão, sem muro de ponte).

## 5. Desempenho medido

- **Amostras de terreno:** cada `getBaseHeight` custa ~2,7 ms, porque monta um `NoiseChunk` de uma coluna. O
  planejamento usa ~9 amostras por célula para decidir a cidade, ~5 por quarteirão e 1 a cada 4 blocos de rodovia.
  Tudo fica em cache.
- **Geração por chunk na feature** (inclui planejamento e reparo dos vizinhos):
  - 1,2–1,3 ms antes das cidades de 186;
  - no Abandoned Simple City, com prédios: 2,45 ms com as cidades de 186 (seed 20261027: uma cidade 4 × 4 com 950
    prédios), entre 2,26 e 2,77 ms nas versões seguintes (escadas, fachadas, profundidade elástica, salas);
  - 2,25 ms sem prédios (este mod, 7 nov);
  - para comparar, o próprio Minecraft gasta dezenas de ms por chunk.
- **Testes unitários:** ~2 min no total (sem o teste dos prédios, que levava ~8 min).

## 6. Resultados atuais (12 nov 2026, quarteirões 180 × 120 com anel de estruturas com vãos e miolo tomado pela natureza, ruas e rodovias de 33, cidades no meio de células de 736, 4 entradas)

- **Unitários:** 45 passam e 1 é pulado (o teste sem estruturas), 13 nov 2026, depois da retirada das ruas cortadas (e do teste delas); `./gradlew build` em ~2 min (Linux, JDK 21: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew build -Dorg.gradle.java.installations.paths=$JAVA_HOME`) (a área dos testes encolheu com a célula).
  - **Auditorias de plano:** 105 auditorias coluna a coluna, sem falhas.
  - **Rede (7 terrenos × 3 seeds, 81 células), cidades no meio da célula de 736, 4 entradas (uma por lado):** 35 a
    62 cidades por combinação, 76 a 121 ligações candidatas. Rodovias mantidas: 99–119 no plano, nas colinas e nos
    cânions (93–98%); 97–112 nas montanhas (91–93%); 85–96 nos rios (83–86%); 91–110 no terreno misto (85–92%);
    59–64 nas ilhas (76–78%). No máximo 2 cidades sem rodovia por combinação. Com 4 entradas quaisquer das 8
    (rejeitado em 11 nov): só 82 de 106 no plano, 22 por curva fechada, rede partida em 4 grupos.
  - **Anel (6 000 quarteirões), vãos de 4 a 17:** ~8,2 estruturas por quarteirão, ~25% em cada lado, 51% da área
    livre coberta (com vãos de 10 a 19 eram 7,7 e 45%); 13 nov, com 21 estruturas: sem repetir no quarteirão 8,15
    por quarteirão e 50,9% coberto; antes da regra 8,41 e 51,1%, com 80% dos quarteirões tendo alguma repetida;
    simulação prévia dos tamanhos com vãos: 140 × 90 → 5,0 por quarteirão, 160 × 110 → 6,5, 180 × 120 → 7,2,
    200 × 130 → 8,2.
- **Abandono das estruturas (13 nov):** rachado da família que racha / verde sob teto / verde a céu aberto /
  fachada externa com vinhas / lado menos coberto (lados de 40+ células), antes → depois (alvos 31% / 8,2% / 35,8% /
  25% por lado; "-" = sem candidatos):

  | Estrutura | Antes | Depois |
  |---|---|---|
  | `casa_abandonada` | 48% / 0% / 4% / 38% / 28% | 60% / 8% / 35% / 38% / 28% |
  | `casa_moderna_abandonada` | 36% / 19% / 36% / 36% / 35% | 36% / 19% / 36% / 36% / 36% |
  | `mansao_abandonada` | 48% / 6% / 16% / 10% / 7% | 63% / 8% / 35% / 26% / 25% |
  | `mansao_pos_apocaliptica` | 61% / 4% / 5% / 18% / 9% | 70% / 8% / 35% / 26% / 25% |
  | `casa_garagem_abandonada` | 13% / 6% / 38% / 4% / 1% | 31% / 8% / 40% / 26% / 25% |
  | `casa_grande_abandonada` | 30% / 9% / 5% / 29% / 22% | 32% / 9% / 36% / 31% / 26% |
  | `casa_piscina_abandonada` | 19% / 7% / 1% / 11% / 5% | 34% / 8% / 36% / 26% / 25% |
  | `casa_suburbio_abandonada` | 0% / 9% / 48% / 8% / 0% | 31% / 9% / 50% / 26% / 25% |
  | `casa_torre_abandonada` | 31% / 7% / 17% / 18% / 5% | 34% / 8% / 35% / 26% / 25% |
  | `casa_vitoriana_abandonada` | 27% / 8% / 1% / 10% / 8% | 35% / 8% / 36% / 26% / 25% |
  | `farmacia_vermelho` | 100% / 5% / 12% / 20% / 0% | 100% / 8% / 34% / 34% / 26% |
  | `farmacia_amarelo` | 100% / 5% / 12% / 20% / 0% | 100% / 7% / 34% / 34% / 26% |
  | `farmacia_azul` | 100% / 5% / 12% / 20% / 0% | 100% / 8% / 34% / 34% / 25% |
  | `farmacia_roxo` | 100% / 5% / 12% / 20% / 0% | 100% / 8% / 35% / 34% / 25% |
  | `farmacia_verde` | 100% / 5% / 12% / 20% / 0% | 100% / 8% / 35% / 35% / 26% |
  | `farmacia_grande_amarela` | - / 5% / 15% / 0% / 0% | - / 8% / 34% / 27% / 26% |
  | `farmacia_grande_azul` | - / 5% / 15% / 0% / 0% | - / 8% / 35% / 26% / 26% |
  | `farmacia_grande_roxo` | - / 5% / 15% / 0% / 0% | - / 8% / 34% / 26% / 25% |
  | `farmacia_grande_verde` | - / 5% / 15% / 0% / 0% | - / 8% / 35% / 26% / 25% |
  | `farmacia_grande_vermelho` | - / 5% / 15% / 0% / 0% | - / 8% / 35% / 26% / 26% |
  | `oficina_media_azul_preto` | 0% / 5% / 13% / 1% / 0% | 35% / 7% / 33% / 28% / 25% |
  | `oficina_media_amarela_azul` | - / 5% / 13% / 1% / 0% | - / 8% / 35% / 28% / 25% |
  | `oficina_media_amarelo_vermelho` | - / 5% / 13% / 1% / 0% | - / 8% / 35% / 29% / 26% |
  | `oficina_pequena_verde` | 18% / 14% / 15% / 20% / 8% | 35% / 15% / 33% / 28% / 25% |
  | `oficina_pequena_amarela` | 18% / 14% / 15% / 21% / 4% | 36% / 15% / 33% / 29% / 26% |
  | `oficina_pequena_azul` | 18% / 14% / 15% / 20% / 8% | 35% / 14% / 34% / 29% / 25% |
  | `oficina_pequena_roxa` | 18% / 14% / 15% / 20% / 8% | 33% / 16% / 34% / 29% / 25% |
  | `oficina_pequena_vermelha` | 18% / 14% / 15% / 20% / 8% | 34% / 15% / 34% / 29% / 25% |
  | `posto_vermelho` | 34% / 0% / 7% / 22% / 14% | 34% / 7% / 34% / 26% / 25% |
  | `posto_amarelo` | 34% / 0% / 7% / 21% / 14% | 34% / 8% / 34% / 26% / 25% |
  | `posto_azul` | 34% / 0% / 7% / 22% / 14% | 33% / 7% / 35% / 26% / 25% |
  | `posto_roxo` | 34% / 0% / 7% / 21% / 14% | 36% / 8% / 35% / 26% / 25% |
  | `posto_verde` | 34% / 0% / 7% / 22% / 14% | 35% / 7% / 35% / 26% / 25% |
  | `posto_pequeno_vermelho` | 31% / 0% / 2% / 23% / 10% | 33% / 11% / 35% / 29% / 25% |
  | `posto_pequeno_amarelo` | 31% / 0% / 2% / 23% / 10% | 34% / 10% / 35% / 29% / 25% |
  | `posto_pequeno_azul` | 31% / 0% / 2% / 23% / 10% | 32% / 11% / 33% / 29% / 26% |
  | `posto_pequeno_roxo` | 31% / 0% / 2% / 23% / 11% | 30% / 14% / 35% / 29% / 25% |
  | `posto_pequeno_verde` | 31% / 0% / 2% / 23% / 10% | 35% / 10% / 34% / 29% / 26% |
  | `predio_apartamento` | 40% / 6% / 0% / 18% / 15% | 45% / 8% / 35% / 26% / 25% |
  | `predio_esqueleto_31x29` | 39% / 22% / 41% / 51% / 48% | 39% / 22% / 41% / 51% / 48% |
  | `predio_azulado` | 22% / 0% / 7% / 7% / 6% | 31% / 8% / 35% / 26% / 26% |
  | `torre` | 39% / 2% / 4% / 4% / 3% | 49% / 8% / 35% / 26% / 26% |
  | `predio_apocaliptico_57x45` | 31% / 9% / 43% / 11% / 9% | 31% / 9% / 43% / 27% / 27% |

- **Bateria no mundo:**

  | Seed | Escopo | Data | Versão | Resultado |
  |---|---|---|---|---|
  | `20261027` | padrão (8 alvos) | 13 nov | revisão, passo 3c: o reparo de vizinhos sai de `ChunkBuilder` para `gen/NeighbourRepair` | PASS: 8 de 8, 0 erros de geração; `mod blocks:` e contagens iguais à referência; 2,69 ms por chunk, 250 s. Impressão digital do planejador igual; função por função, dos 61 membros de `ChunkBuilder` 54 ficaram com o corpo igual (vinhas das pontes, aterro, taludes, vedação de líquidos incluídos) e 7 foram para `NeighbourRepair` com o corpo igual; nenhuma outra classe mudou |
  | `20261027` | padrão (8 alvos) e rápido com `structureOvergrowth = false` | 13 nov | revisão, passo 3b: o preparo dos moldes sai de `Structures` para `core/StructureLoader` | PASS nos dois: `mod blocks:` e contagens iguais à referência (8 de 8) e à referência com o abandono desligado (3 de 3); 2,71 ms por chunk, 253 s. Impressão digital do planejador igual (com `structures` e `weathering`); função por função: as 14 funções e constantes movidas com o mesmo corpo (a menos de `blockOf`/`isLogName`/`withProps` chamadas agora pelo nome da classe e de `ldc` no lugar de `ldc_w`) |
  | `20261027` | padrão (8 alvos) | 13 nov | revisão, passo 3a: postes saem de `City` para `core/StreetLights` | PASS: 8 de 8, 0 erros de geração; `mod blocks:` e contagens iguais à referência; 2,93 ms por chunk, 263 s. Impressão digital do planejador igual (com `lamps`); função por função: `lampAt`, `lampVine` e as 28 constantes com o corpo igual, `lampParts` só com outra forma de carregar a mesma constante, `lampCandidate` só lendo a semente do argumento em vez do campo da cidade |
  | `20261027` | rápido (3 alvos) | 13 nov | referência com `structureOvergrowth = false` (log "Structure overgrowth off") | PASS: 3 de 3, 0 erros de geração; mundo novo e recarregado com as mesmas 3 linhas `mod blocks:` (a referência acima); 4,59 ms por chunk, 149 s |
  | `20261027` | padrão (8 alvos) | 13 nov | revisão: 3 constantes que ficaram sem uso (`Params.ARC_RADIUS`, `AVENUE_EVERY`, `LOT_SETBACK`) | PASS: 8 de 8, 0 erros de geração; `mod blocks:` e contagens iguais à referência; 142 estados da paleta; 2,79 ms por chunk, 253 s. Impressão digital do planejador igual |
  | `20261027` | padrão (8 alvos) | 13 nov | revisão, passo 2d: ruas cortadas retiradas (`City.MAX_CUTS`, o sorteio dos cortes, `removed_street` e o teste delas) | PASS: 8 de 8, 0 erros de geração; as 8 linhas `mod blocks:` iguais à referência e as contagens iguais (menos `loot_containers_anywhere`); 142 estados da paleta; 2,73 ms por chunk, 248 s. Impressão digital do planejador igual |
  | `20261027` | padrão (8 alvos) | 13 nov | revisão, passo 2c: código morto (estilos de prédio do Simple City, tipos de lote praça e vazio, 26 códigos de bloco, argumento `style` de `Palette.of`, 11 declarações sem uso) | PASS: 8 de 8, 0 erros de geração; as 8 linhas `mod blocks:` iguais à referência e as contagens iguais (menos `loot_containers_anywhere`); 142 estados da paleta validados (eram 218: saíram os dos estilos); 2,71 ms por chunk, 250 s. Impressão digital do planejador igual |
  | `20261027` | padrão (8 alvos) | 13 nov | revisão, passo 2b: imports no lugar de nomes completos (213 nomes, 26 arquivos) | PASS: 8 de 8, 0 erros de geração; `mod blocks:` e contagens iguais à referência; bytecode igual instrução por instrução (`javap -c -p`); 2,74 ms por chunk, 253 s |
  | `20261027` | padrão (8 alvos) | 13 nov | revisão, passo 2a: fim de linha LF (`.gitattributes`, 18 arquivos) | PASS: 8 de 8, 0 erros de geração; `mod blocks:` e contagens iguais à referência; classes compiladas idênticas byte a byte; 2,76 ms por chunk, 252 s |
  | `20261027` | padrão (8 alvos) | 12 nov | revisão do código, passo 1: impressão digital (`FingerprintTest` e `mod blocks:`); geração igual à do commit `4dd94ae` | PASS: 8 de 8, 0 erros de geração, 218 estados da paleta validados, 16 criaturas de vila retiradas; 2,73 ms por chunk, 252 s. Um mundo novo, o mesmo mundo recarregado do disco e outro mundo (gerado antes, recarregado): as 8 linhas `mod blocks:` iguais (a referência abaixo); contagens `stats:` iguais às de outra rodada, menos `loot_containers_anywhere` |
  | — | RCON | 13 nov | `/amcs spawn <categoria> <nome>` (sem bateria de geração: só o comando mudou) | `predio predio_largo_abandonado` e `delegacia delegacia_abandonada` colocados; `casa predio_largo_abandonado` recusado com a lista das casas; categoria `igreja` recusada com a lista das categorias; o formato antigo de um argumento recusado pelo jogo |
  | `20261027` | padrão (8 alvos) | 13 nov | delegacia (1 por cidade) e prédio do choque (52 arquivos) | PASS: 8 de 8, 0 erros de geração, 1 408 estados de estrutura validados; 2,77 ms por chunk, 256 s. Por RCON, `/amcs spawn delegacia_abandonada` sobre pedra: salas do subsolo cavadas nas camadas 1, 2 e na da rua, pedra fora delas intacta, banner de letra com o desenho; 63 contêineres com loot, 13 de difícil acesso |
  | `20261027` | padrão (8 alvos) | 13 nov | postes abandonados em 5 variações | PASS: 8 de 8, 0 erros de geração, ~3 460 blocos de poste conferidos por cidade (com as vinhas), 218 estados da paleta validados; 2,77 ms por chunk, 274 s |
  | `20261027` | padrão (8 alvos) | 13 nov | criaturas de vila retiradas das cidades | PASS: 8 de 8, 0 erros de geração, 16 criaturas de vila retiradas; 2,77 ms por chunk, 259 s. Por RCON, seed 20261027, vila de neve em (−240, 144) numa rua da cidade do spawn: antes 4 aldeões, 1 golem e 1 gato num quadrado de 160 × 160 (um aldeão em y 64, sob a rua de piso 69); depois 1 aldeão, em (−235, 89), ~49 blocos além da rua (fora da margem, de uma casa que ficou de pé fora da cidade) |
  | `20261027` | padrão (8 alvos) | 13 nov | topo da rampa e boca da escada do prédio largo abertos | PASS: 8 de 8, 0 erros de geração; 2,85 ms por chunk, 251 s. Por RCON, `/amcs spawn predio_largo_abandonado` sobre pedra: as duas fileiras do topo da rampa e a boca da escada abertas, a laje e o concreto da rampa no lugar (10 de 10 conferidos) |
  | `20261027` | padrão (8 alvos) | 13 nov | prédios no máximo 1 por cidade, postes a cada 24, bancos nos parques, escada da casa de vidro em L | PASS: 8 de 8, 0 erros de geração; ~2 300 blocos de poste conferidos por cidade; 214 estados de bloco da paleta validados; 2,68 ms por chunk, 266 s |
  | `20261027` | padrão (8 alvos) | 13 nov | 7 estruturas novas (50 arquivos), prédio largo com garagem cavada e rampa aberta, prédio da rampa 1 por cidade | PASS: 8 de 8, 0 erros de geração, 1 385 estados de estrutura validados, 59 904 blocos de abandono; 2,75 ms por chunk, 246 s. Por RCON: `/amcs spawn predio_largo_abandonado` sobre um volume de pedra: boca da rampa aberta, 4 pontos da garagem cavados, piso de `smooth_stone`, pedra fora da garagem intacta (7 de 7 conferidos); 249 contêineres com loot, 191 de difícil acesso |
  | `20261027` | padrão (8 alvos) | 13 nov | letreiro F A R M Á C I A apagado das variações; banners das farmácias pequenas na posição da original | PASS: 8 de 8, 0 erros de geração, banners de farmácia conferidos; 2,68 ms por chunk, 257 s |
  | `20261027` | padrão (8 alvos) | 13 nov | vinhas longas e agressivas nos 4 lados (25% da fachada, cachos de 3 a 12; 34 609 blocos de abandono) | PASS: 8 de 8, 0 erros de geração, nenhum `building_mismatch`; 2,63 ms por chunk, 253 s |
  | `20261027` | padrão (8 alvos) | 13 nov | natureza tomando conta das estruturas (`structureOvergrowth` ligado, 18 341 blocos) | PASS: 8 de 8, 0 erros de geração, nenhum `building_mismatch` (plantas e vinhas ficaram no mundo como no plano); 2,72 ms por chunk, 266 s |
  | `20261027` | rápido (3 alvos) | 13 nov | o mesmo com `structureOvergrowth = false` | PASS: 3 de 3; log "Structure overgrowth off" |
  | `20261027` | padrão (8 alvos) | 13 nov | 22 variações de cor (sorteio por modelo, banners copiados nas farmácias) | PASS: 8 de 8, 0 erros de geração, 1 115 estados de estrutura validados, banners de farmácia conferidos com desenho; 2,70 ms por chunk, 263 s |
  | `20261027` | padrão (8 alvos) | 13 nov | copas cortadas completadas para fora das estruturas | PASS: 8 de 8, 0 erros de geração, 0 avisos de `far chunk`; 151 folhas de copa conferidas na cidade do spawn, 324 na mais alta, 307 na mais acidentada; 2,62 ms por chunk, 250 s |
  | `20261027` | padrão (8 alvos) | 13 nov | nunca duas estruturas iguais no mesmo quarteirão | PASS: 8 de 8, 0 erros de geração; na cidade do spawn 906 contêineres com a tabela certa, 156 de difícil acesso; 2,87 ms por chunk, 247 s |
  | `20261027` | padrão (8 alvos) | 13 nov | loot vanilla por tema, contêiner e acesso | PASS: 8 de 8, 0 erros de geração; na cidade do spawn 902 contêineres com a tabela certa, 105 de difícil acesso; 2,68 ms por chunk, 256 s. Por RCON, 300 sorteios de cada uma das 30 tabelas: barril vazio em 58–62% (0,74–0,85 pilha), `barril_dificil` vazio em 19–30% (2,1–2,4 pilhas), baú 4,5–4,6 pilhas, `bau_dificil` 7,9–8,1, vaso vazio em 58–65%, `vaso_dificil` em 19–22%; poções com efeito, ferramentas gastas e encantadas e livros encantados conferidos; `/amcs spawn` do prédio apocalíptico pôs 73 contêineres, 56 de difícil acesso |
  | `20261027` | padrão (8 alvos) | 13 nov | para-raios trocados por tochas de redstone | PASS: 8 de 8, 0 erros de geração, 2,72 ms por chunk, 261 s |
  | `20261027` | padrão (8 alvos) | 13 nov | 7 estruturas novas (6 casas e o prédio esqueleto, 21 no total), mansão pós-apocalíptica atualizada, estantes entalhadas liberadas | PASS: 8 de 8, 0 erros de geração, 2,71 ms por chunk, 253 s |
  | `20261027` | padrão (8 alvos) | 13 nov | parque de 110 × 64 num canto, estruturas em L | PASS: 8 de 8, 0 erros de geração, 1 fonte conferida, 2,69 ms por chunk, 252 s |
  | `20261027` | padrão (8 alvos) | 12 nov | asfalto gasto (`polished_deepslate`), 30% tomado, pontes velhas (laje e pilares musgosos, muro quebrado e musgoso, buracos), sem desabamentos nos túneis | PASS: 8 de 8, 0 erros de geração; na ponte mais longa 87 colunas de buraco, 53 de muro caído e 5976 tomadas; 2,74 ms por chunk, 268 s |
  | `20261027` | padrão (8 alvos) | 12 nov | ruas abandonadas e vinhas das pontes | PASS: 8 de 8, 0 erros de geração; na cidade do spawn 7 717 de 76 027 colunas de via no chão tomadas (10,2%) e 2 413 plantas conferidas; 2,86 ms por chunk, 279 s |
  | `20261027` | padrão (8 alvos) | 12 nov | vãos de 4 a 17 | PASS: 8 de 8, 0 erros de geração, 2,82 ms por chunk |
  | `20261027` | padrão (8 alvos) | 12 nov | quarteirões 180 × 120, vãos de 10 a 19 e bosque de 20 a 30 | PASS: 8 de 8, 0 erros de geração; na cidade mais acidentada 1 180 contêineres com loot e 45 banners conferidos; 8 346 chunks, 2,74 ms por chunk, 267 s |
  | `20261027` | padrão (8 alvos) | 12 nov | mansão pós-apocalíptica girada, teias soltas removidas, banners com desenho | PASS: 8 de 8, 0 erros de geração, 126 banners de farmácia conferidos com desenho, 3,06 ms por chunk |
  | `20261027` | padrão (8 alvos) | 12 nov | casa moderna girada 180° | PASS: 8 de 8, 0 erros de geração, 2,94 ms por chunk |
  | `20261027` | padrão (8 alvos) | 12 nov | 14 estruturas, limites por cidade, chão alinhado, piso até a calçada, folhas reais nas estruturas | PASS: 8 de 8, 0 erros de geração; só na cidade do spawn 1 261 block entities de estrutura e 744 contêineres com loot table conferidos; 2,93 ms por chunk, 227 s |
  | `20261027` | padrão (8 alvos) | 11 nov | piso da fonte musgoso | PASS: 8 de 8, 0 erros de geração, 1 fonte conferida, 2,93 ms por chunk |
  | `20261027` | padrão (8 alvos) | 11 nov | 4 entradas, células de 648, faixas de pedestres, árvores reais, anel musgoso, vinhas longas | PASS: 8 de 8, 0 erros de geração, 7 948 chunks, 2,91 ms por chunk, 231 s |
  | `20261027` | padrão (8 alvos) | 11 nov | miolos e parques tomados pela natureza | PASS: 8 de 8, 0 erros de geração, 952 433 blocos de lote conferidos só no alvo do spawn, 9 096 chunks, 9,28 ms por chunk, 352 s |
  | `20261027` | padrão (8 alvos) | 10 nov | ruas e rodovias de 33, sem estruturas | PASS: 8 de 8 (cidade do spawn, mais alta, mais acidentada, túnel de 282, ponte de 645, viaduto de 172, rampa de 480, rodovia inteira), 0 erros de geração, 9 096 chunks, 8,49 ms por chunk, 324 s |
  | `20261027` | padrão (8 alvos) | 8 nov | quarteirões 140 × 90 com as 33 estruturas | PASS: 8 de 8, 13,53 ms por chunk (depois de estender o reparo de vizinhos à altura inteira das estruturas) |
  | `20261027` | `quick` | 7 nov | cópia sem prédios | PASS: 4 alvos, uma cidade 4 × 4 com 2 lotes (parques) e 37 765 colunas de chão livre conferidas, 18 953 colunas de via, 2,25 ms por chunk |

  A bateria ainda não rodou depois da remoção das cidades octogonais e 3 × 2 (o alvo "cidade 3 × 2 mais próxima do
  spawn" saiu) nem depois da troca para quarteirões 105 × 60 (células de 512) e 140 × 90 (células de 576). Em 8 nov, no Linux (CachyOS, JDK 21), a bateria `-PselfTest` passou 8 de 8 alvos com os quarteirões 105 × 60 num mundo novo (um mundo de teste velho, gerado antes das mudanças, dá falhas falsas no alvo do spawn: renomeie `run/selftest_world_*`). Vale rodar a bateria completa quando houver mudanças: `tools/selftest.sh full`.
- **Referência da impressão digital da bateria** (`mod blocks:`, seed `20261027`, escopo padrão, 12 nov 2026,
  geração do commit `4dd94ae`). Numa mudança que não deve alterar o mundo, as 8 linhas ficam iguais a estas:

  | Alvo | SHA-256 | Posições | Block entities |
  |---|---|---|---|
  | t01 cidade mais próxima do spawn | `6332a9a1f1ba589565f3e4b3f1d56d626d0c1a188f4296e5301bc206b088cd96` | 1 877 158 | 2 055 |
  | t02 cidade mais alta (nível 103) | `e9c76bce50254bd36d2c0ed650222c61d13abd335320102e474d63e8dbf9cb6a` | 3 290 072 | 3 387 |
  | t03 cidade mais acidentada | `92efbcc166cb03ac38acf6a10c8d481d770f82527047dc2dc5823cb08068e7e1` | 2 506 369 | 2 506 |
  | t04 túnel mais longo (352) | `aa04b3a0c7319245562bf89e7e9dd2419de950fcaad0bb48ea8501329d07d967` | 543 064 | 164 |
  | t05 ponte mais longa sobre água (349) | `e9c275178d0660b93dad36efc4d2f6fdcb80699b555f10274b814eb627c54713` | 452 586 | 605 |
  | t06 viaduto seco mais longo (239) | `806cc76601ec663622586341d73b529780b95b6ed86c2e8b240c764617e5919b` | 581 916 | 381 |
  | t07 rampa mais longa (360) | `f960ad531d4199d12ecf42bb72b87d9a199bc1fccb85235572528b07c3dcc118` | 191 450 | 0 |
  | t08 rodovia inteira | `fe0310e800f1b7b6492e15424037fe9a3fda24c90ad61d3c50e0b35a6c1cfe10` | 1 280 207 | 850 |

  O relatório completo e o mundo dessa rodada ficam (fora do git) em `run/selftest/ref_4dd94ae/` e
  `run/selftest_world_ref_4dd94ae/`, para comparar bloco a bloco se alguma linha mudar.
- **Referência com o abandono desligado** (`structureOvergrowth = false`, escopo rápido, 13 nov 2026, geração igual
  à do commit `4dd94ae`). O mundo novo e o mesmo mundo recarregado deram estes 3 valores:

  | Alvo | SHA-256 | Posições | Block entities |
  |---|---|---|---|
  | t01 cidade mais próxima do spawn | `c7eaaa5b7a0a3f1c866d536c2420716dba9dc13efdc50c7f3fb55730842c73f0` | 1 874 811 | 2 055 |
  | t02 túnel mais longo (352) | `cda3911413a903abc703d9e623e12e08ea508adaf2441cd7c4e8fcd1743a3722` | 541 806 | 164 |
  | t03 ponte mais longa sobre água (349) | `6d7016e75a48abb1314cae4d0762c13913f02d1929cd679b4e581609238c4fa4` | 452 586 | 605 |

  Relatório e mundo em `run/selftest/off_fresh/` e `run/selftest_world_ref_off/` (fora do git).
- Em nenhuma rodada houve degrau, buraco, obstáculo, pilar flutuante, rodovia sem conexão, bloco indestrutível nas vias, pontes e túneis depois das correções 4 e 5. Containers das estruturas têm loot tables vanilla próprias (13 nov 2026: tema da estrutura × tipo de contêiner × acesso), auditados por `structure_without_loot`, `structure_wrong_loot`, `loot_in_city` e `container_in_city`.
