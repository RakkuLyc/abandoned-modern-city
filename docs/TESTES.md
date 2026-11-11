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

Cada terreno roda com as seeds `20261104`, `-3297019724594858492` e `42`, numa área de 9 × 9 células de 640
(~18 000 × 18 000 blocos).

## 2. O que é verificado

### Unitários (14 testes)

| Teste | Verifica |
|---|---|
| `ProfileTest.profilesAreFlatOrRampAndJoinTheCities` | 4 000 perfis aleatórios (com penhascos): começa e termina no nível de cada cidade, 48 blocos planos nas pontas, variação ≤ 1/8 de bloco por bloco, trechos planos sempre em altura inteira, nunca abaixo do mínimo, superfície (meio-bloco) nunca muda mais de 0,5 entre vizinhos |
| `ProfileTest.halfBlockStepsAreEvenlySpaced` | numa subida, os degraus de meia-laje ficam a pelo menos 4 blocos um do outro |
| `ProfileTest.classificationRunsAreClean` | 2 000 perfis: junções sempre no chão, túneis ≥ 16, pontes secas ≥ 8, toda água fora das junções é ponte |
| `WorldPlanTest.cityStreetsHaveNoDeadEndsAndLotsAreBuildable` | em todas as cidades dos 7 terrenos (359 cidades 2 × 2 de quarteirões 140 × 90, 202 lotes — só parques, sem estruturas): nenhum nó de grau 1 (sem becos), número de trechos cortados igual ao sorteado; com as ruas cortadas desligadas (`MAX_CUTS` = 0) nenhuma cidade tem corte, senão todos os valores de 0 a `MAX_CUTS` aparecem; toda coluna de lote é lote do quarteirão certo e não toca rua |
| `WorldPlanTest.streetWidthIsConstantOnStraightStreets` | toda rua reta de cidade tem exatamente 33 colunas |
| `WorldPlanTest.highwayNetworkIsConsistentAndWellConnected` | 21 combinações terreno × seed: Gabriel simétrico, rodovia começa e termina exatamente na entrada de cada cidade, raio de curva ≥ 60, pontos a ~1 bloco, comprimento máximo, cruzamento de entrada nunca usado duas vezes (um canto tem duas saídas, mas só uma rodovia), no máximo 8 rodovias por cidade, nenhum par de rodovias perto demais (sem cruzamentos); limites: plano/colinas ≥ 85% das ligações e ≤ 5% de cidades sem rodovia, montanhas/rios/cânions ≤ 15%, e pelo menos metade das células com cidade (exceto ilhas); gera `network_*.png` |
| `WorldPlanTest.removedStreetsAreRenderedAndAudited` | uma cidade para cada quantidade de ruas cortadas (hoje só 0): passa no `PlanAudit` e gera `cuts_N.png` |
| `RingTest.withoutStructuresBlocksStayEmpty` | com `index.txt` vazio (hoje): nenhum estado de estrutura e o anel não cria lotes |
| `RingTest.structuresHaveSolidBoxesWithAFloorLayer` | (pulado sem estruturas) toda estrutura tem caixa não vazia e blocos na primeira camada |
| `RingTest.rotationKeepsEveryBlockAndTurnsTheFrontToTheStreet` | (pulado sem estruturas) as 4 rotações têm o mesmo número de blocos em todas as estruturas; a rotação de 90, 180 e 270 graus confere célula a célula com a fórmula da transformação |
| `RingTest.ringsFitTheirBlockTouchTheSidewalksAndDoNotOverlap` | (pulado sem estruturas) 6 000 anéis em quarteirões 140 × 90 e 90 × 140: dentro do quarteirão, sem sobreposição, cada estrutura encosta na calçada para onde a frente aponta, determinístico, as 33 aparecem e os 4 lados são usados |
| `RingTest.everyStructureFitsNextToTheShallowestInA90Block` | (pulado sem estruturas) cada estrutura cabe ao lado da mais rasa (20) num lado de 90, e na largura de 140 |
| `PlanAuditTest.citiesAndHighwaysPassThePlanAudit` | `PlanAudit` coluna a coluna em 3 cidades inteiras e 2 rodovias inteiras por terreno × seed (105 auditorias); gera `city_*.png` |
| `PlanAuditTest.planIsIndependentOfQueryOrderAndThreads` | 3 721 chunks planejados de novo em ordem embaralhada por 8 threads: resultado idêntico, campo a campo |

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
  - `city_split` e `removed_street` (o meio de um trecho de rua cortado não virou chão de lote).
- **Lotes:** `lot_overlap` (dois lotes de um quarteirão se sobrepõem).

### `WorldAudit` (blocos gerados)

- **Superfície das vias:**
  - `hole` (sem superfície), `surface_block` (bloco diferente do planejado) e `no_base`;
  - `height_mismatch` (altura medida diferente da planejada);
  - `step` (medido nos blocos, inclusive entre chunks).
- **Espaço livre e decoração:**
  - `obstacle`, `cover` e `fluid_on_road` (4 blocos livres acima da pista, 7 em túneis);
  - `vegetation_over_road` (até 48 acima).
- **Estruturas:** `parapet`, `tunnel_ceiling` (`stone` ou `glowstone`), `tunnel_wall` e `pillar_floating`.
- **Lotes:**
  - `lot_hole`;
  - `yard_ground` (chão livre de cidade que não é `gray_concrete_powder`; pedras de floresta e buracos são
    reparados);
  - `building_mismatch` (nome herdado: bloco de lote — parque, fonte — diferente da definição;
    costuras entre chunks apareceriam aqui).
- **Regras gerais:**
  - `hard_block` (bloco indestrutível em via, ponte, túnel ou lote; qualquer dureza é permitida);
  - `loot_in_city` e `container_in_city` (agora containers têm loot tables: exceto oficinas usam `lostcities:chests/lostcitychest`; oficinas usam `oficina_vehicle_parts` em baús e `oficina_vehicle_tools` em outros containers; baú da fonte do parque continua vazio 50% / `mossy_cobblestone` 50%, contado em `fountain_chests`);
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
| — | Bateria (infraestrutura) | servidores falhando com "Address already in use" | um servidor de uma bateria interrompida ainda terminava | o script usa a porta 25591 e se recusa a começar com um servidor de desenvolvimento vivo |

## 4. Como reproduzir

```bash
./gradlew test                                   # unitários, ~2 min (renders em build/test-renders/)
./gradlew test --tests '*WorldPlanTest.streetWidthIsConstantOnStraightStreets'   # só um teste
tools/selftest.sh quick 20261027                 # bateria rápida (3 alvos), ~4 min
tools/selftest.sh full 20261027 -3297019724594858492 3478477863671517581 1
cat run/selftest/<seed>/report.txt               # termina com RESULT: PASS ou FAIL
```

No jogo (op):
- `/amcs locate city` (ou `highway`, `tunnel`, `bridge`);
- `/amcs info`, que mostra o que o plano quer na coluna;
- `/amcs audit 6` e `/amcs plan 12`.

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

## 6. Resultados atuais (10 nov 2026, quarteirões 140 × 90, ruas e rodovias de 33, células de 640)

- **Unitários:** 10 passam e 4 são pulados sem estruturas (10 nov 2026, `index.txt` vazio); `./gradlew build` em ~30 s (Linux, JDK 21: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew build -Dorg.gradle.java.installations.paths=$JAVA_HOME`) (a área dos testes encolheu com a célula).
  - **Auditorias de plano:** 105 auditorias coluna a coluna, sem falhas, mais a cidade sem cortes do teste de ruas
    cortadas.
  - **Rede (7 terrenos × 3 seeds, 81 células), rodovias de 33:** 42 a 62 cidades por combinação, 103 a 168 ligações
    candidatas. Rodovias mantidas: 126–150 no plano, nas colinas e nos cânions (89–95%); 116–143 nas montanhas
    (85–89%); 117–133 nos rios (82–89%); 120–131 no terreno misto (82–92%); 74–89 nas ilhas (72–75%). Principais
    descartes: `conflicts with a shorter highway` (até 13 por combinação; a separação mínima subiu para 39 com a
    largura nova), `mostly water` nas ilhas (até 26) e desnível nas montanhas (até 10). Nenhuma cidade sem rodovia.
- **Bateria no mundo:**

  | Seed | Escopo | Data | Versão | Resultado |
  |---|---|---|---|---|
  | `20261027` | padrão (8 alvos) | 10 nov | ruas e rodovias de 33, sem estruturas | PASS: 8 de 8 (cidade do spawn, mais alta, mais acidentada, túnel de 282, ponte de 645, viaduto de 172, rampa de 480, rodovia inteira), 0 erros de geração, 9 096 chunks, 8,49 ms por chunk, 324 s |
  | `20261027` | padrão (8 alvos) | 8 nov | quarteirões 140 × 90 com as 33 estruturas | PASS: 8 de 8, 13,53 ms por chunk (depois de estender o reparo de vizinhos à altura inteira das estruturas) |
  | `20261027` | `quick` | 7 nov | cópia sem prédios | PASS: 4 alvos, uma cidade 4 × 4 com 2 lotes (parques) e 37 765 colunas de chão livre conferidas, 18 953 colunas de via, 2,25 ms por chunk |

  A bateria ainda não rodou depois da remoção das cidades octogonais e 3 × 2 (o alvo "cidade 3 × 2 mais próxima do
  spawn" saiu) nem depois da troca para quarteirões 105 × 60 (células de 512) e 140 × 90 (células de 576). Em 8 nov, no Linux (CachyOS, JDK 21), a bateria `-PselfTest` passou 8 de 8 alvos com os quarteirões 105 × 60 num mundo novo (um mundo de teste velho, gerado antes das mudanças, dá falhas falsas no alvo do spawn: renomeie `run/selftest_world_*`). Vale rodar a bateria completa quando houver mudanças: `tools/selftest.sh full`.
- Em nenhuma rodada houve degrau, buraco, obstáculo, pilar flutuante, rodovia sem conexão, bloco indestrutível nas vias, pontes e túneis depois das correções 4 e 5. Containers das estruturas agora têm loot tables (exceto oficinas: vehicle parts/tools; demais: Lost Cities), auditados por `loot_in_city` / `container_in_city`.
