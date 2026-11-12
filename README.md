# Abandoned Modern City — NeoForge 1.21.1

Cidades modernas abandonadas geradas proceduralmente: ruas, rodovias, túneis, pontes, quarteirões e parques.
Os prédios são as estruturas `.nbt` do usuário (28 modelos, 50 arquivos com as variações de cor), sorteadas ao acaso num anel em volta de cada quarteirão, com
limites por cidade para farmácias, oficinas e postos.

> Começou em 7 nov 2026 como cópia do Abandoned Simple City (repositório `abandoned-simple-city`), sem os prédios e
> sem o histórico de commits. O Abandoned Modern City original (pasta `AbandonedModernCity-Deprecated`) está
> descontinuado.

- modid `abandonedmoderncity` · pacote `com.abandonedmoderncity` · Java 21 · NeoForge 21.1.219 ·
  ModDevGradle 2.0.140
- Build: `./gradlew build` → `build/libs/abandonedmoderncity-0.1.0.jar` (roda também os testes unitários,
  ~10 min); só o jar: `./gradlew build -x test`
- Testes unitários: `./gradlew test` (renders em `build/test-renders/`)
- Bateria no mundo: `tools/selftest.sh [full|quick] [seed...]` (relatórios em `run/selftest/<seed>/`); detalhes em
  `docs/TESTES.md`
- Nenhuma dependência opcional: sem Immersive Vehicles, Zombie Extreme, Big Lost City ou mixins.
- `libs/` **não é versionada** (está no `.gitignore`) e **não é necessária para compilar**: `./gradlew build` funciona
  sem ela. A pasta guarda jars de mods de terceiros usados só como fonte de consulta (loot tables do Apocalypse Loot,
  peças de veículos do OAmP, Immersive Vehicles e MTS Official Pack). Quem clonar o repositório e quiser refazer esse
  levantamento deve copiar os jars para `libs/` à mão: `ApocalypseLoot-1.1.1.jar`,
  `Immersive Vehicles-1.21.1-24.0.0.jar`, `MTS Official Pack-1.21.1-V29.jar` e `OAmP-1.21.1-V3.jar`.
- A especificação completa e atual (aprovada pelo usuário) fica em `CLAUDE.md`.

## O que é gerado

### Cidades

| Tipo | Onde | Forma |
|---|---|---|
| Cidade 2 × 2 | uma no meio de cada célula de 736 blocos (70% das células) | grid rígido de 2 × 2 quarteirões de 180 × 120 (eixos a 213 e a 153, ~459 × ~339 blocos com as ruas, 3 × 3 ruas; o lado comprido fica leste-oeste ou norte-sul, sorteado por cidade), até 8 entradas de rodovia (o cruzamento do meio de cada lado e os 4 cantos) |

Só existe esse tipo de cidade: as cidades octogonais e as cidades 3 × 2 das rodovias (com o padrão rural_highway e
as plantações) foram removidas em 7 nov 2026; o grid passou de 4 × 4 quarteirões de 186 (células de 2 048) para
2 × 2 quarteirões de 105 × 60 (células de 512) e, em 8 nov 2026, para 140 × 90 (células de 576, para caber as
estruturas de `structures/`); em 10 nov 2026 ruas e rodovias passaram a 33 de largura (células de 640); em 11 nov 2026 as
cidades foram para o meio de células de 648 e passaram a ter 4 entradas; em 12 nov 2026 os quarteirões passaram a
180 × 120 (células de 736, rodovias de ~277 a ~397 blocos), com vãos entre as estruturas.

- Cada cidade fica numa única altura: água é aterrada, morro é cortado, e um talude 1:1 faz a transição.
- Ruas cortadas estão desligadas (`City.MAX_CUTS` = 0); o código que corta trechos sem criar beco sem saída
  continua lá.
- **Quarteirões:** as estruturas formam um anel encostado nas calçadas, com a frente (lado -Z do `.nbt`) virada para
  a rua; o resto (o miolo) é tomado pela natureza. Cerca de 1 em 8 quarteirões tem um parque de 110 × 64 num canto,
  com as estruturas em L nas outras duas ruas.
- **Estruturas (28 modelos, 50 arquivos, `structures/`):** sorteadas ao acaso no anel (cada modelo com a mesma
  chance, depois a cor: as 10 casas dão ~43% das estruturas), com limite por cidade: farmácias 3, oficinas 2, postos 2, e cada prédio (apartamento, esqueleto, azulado, torre,
  apocalíptico, pequeno, brutalista, largo, torre verde, rampa) no máximo 1 vez por cidade.
  Nunca duas estruturas iguais (o mesmo modelo, em qualquer cor) no mesmo quarteirão. Variações de cor: farmácia e
  farmácia grande (5 cores cada, as letras D R U G S T O R E copiadas da original na cor da variação, sem o letreiro
  F A R M Á C I A em blocos que as variações tinham: a parede fica branca como na original), oficina média
  (3), oficina pequena (5), posto e posto pequeno (5 cada).

  | Estrutura | Tipo | Caixa (frente × fundo × altura) | Chão |
  |---|---|---|---|
  | Casa abandonada | casas | 29 × 46 × 20 | camada 0 |
  | Casa moderna abandonada (frente no lado +Z do molde: girada 180°) | casas | 30 × 29 × 15 | camada 0 |
  | Mansão abandonada | casas | 41 × 57 × 21 | camada 1 (desce 1) |
  | Mansão pós-apocalíptica (frente no lado +Z do molde: girada 180°) | casas | 37 × 41 × 18 | camada 0 |
  | Casa com garagem abandonada | casas | 32 × 28 × 17 | camada 0 |
  | Casa grande abandonada | casas | 41 × 47 × 23 | camada 1 (desce 1) |
  | Casa com piscina abandonada | casas | 34 × 45 × 23 | camada 0 |
  | Casa de subúrbio abandonada | casas | 51 × 43 × 20 | camada 0 |
  | Casa com torre abandonada | casas | 40 × 44 × 24 | camada 0 |
  | Casa vitoriana abandonada | casas | 42 × 47 × 23 | camada 0 |
  | Farmácia (vermelha, amarela, azul, roxa, verde) | farmácia (limite 3 com a grande) | 19 × 28 × 16 | camada 0 |
  | Farmácia grande (amarela, azul, roxa, verde, vermelha) | farmácia | 26 × 40 × 15 | camada 0 |
  | Oficina média (azul e preto, amarela e azul, amarelo e vermelho) | oficina (limite 2 com a pequena) | 35 × 26 × 17 | camada 0 |
  | Oficina pequena (verde, amarela, azul, roxa, vermelha) | oficina | 22 × 33 × 16 | camada 0 |
  | Posto padrão (vermelho, amarelo, azul, roxo, verde) | posto (limite 2 com o pequeno) | 45 × 37 × 18 | camada 0 |
  | Posto pequeno (vermelho, amarelo, azul, roxo, verde) | posto | 25 × 20 × 18 | camada 0 |
  | Prédio apartamento | prédio | 31 × 29 × 37 | camada 1 (desce 1) |
  | Prédio esqueleto (`predio_esqueleto_31x29`) | prédio | 31 × 29 × 43 | camada 0 |
  | Prédio azulado | prédio | 66 × 46 × 106 | camada 1 (desce 1) |
  | Torre | prédio | 62 × 53 × 118 | camada 1 (desce 1) |
  | Prédio apocalíptico | prédio | 57 × 45 × 110 | camada 0 |
  | Casa moderna com garagem | casas | 42 × 36 × 15 | camada 0 |
  | Casa de vidro | casas | 48 × 40 × 15 | camada 0 |
  | Prédio pequeno | prédio | 23 × 31 × 41 | camada 0 |
  | Prédio brutalista em U | prédio | 75 × 58 × 125 | camada 0 |
  | Prédio largo (estacionamento subterrâneo cavado no terreno, rampa e escada abertas) | prédio | 84 × 31 × 80 | camada 8 (8 enterradas) |
  | Torre verde | prédio | 31 × 31 × 143 | camada 0 |
  | Prédio da rampa (pasta Especial) | especial (no máximo 1 por cidade) | 102 × 72 × 134 | camada 0 |

  - Encostadas na calçada sem vão: as fileiras da borda sem piso recebem o piso da primeira fileira que tem.
  - As árvores das estruturas têm leaf decay (folhas ligadas a um tronco); sebes sem tronco continuam persistentes.
  - Copas cortadas pela borda do molde são completadas espelhando a copa em volta do tronco: 189 folhas em 7
    estruturas (casa com piscina, com garagem, de subúrbio, com torre, prédio esqueleto), penduradas sobre o bosque
    ou, a mais de 7 blocos de altura, sobre a calçada.
  - As estruturas não se encostam: vão sorteado de 4 a 17 blocos entre vizinhas, e sempre 20 a 30 blocos de bosque
    entre os fundos de lados opostos. As árvores dos miolos não entram nas estruturas.
  - Os banners mantêm o desenho do molde: as farmácias têm as letras DRUGSTORE.
  - Teias soltas (sem nada em que se prender) são removidas.
  - Natureza tomando conta (`core/Weathering`, no modelo do prédio apocalíptico, desligável com
    `structureOvergrowth = false`): cada estrutura recebe o que falta para chegar às medidas dele, sem mudar de
    tamanho: pedra rachada em manchas (31% dos tijolos e ladrilhos que racham), pontos de musgo, grama e terra nos
    pisos (8% sob teto, 36% a céu aberto) com tapete de musgo, grama, samambaia ou arbusto seco, vinhas longas e
    agressivas nos 4 lados (25% da fachada externa de cada lado, cachos de 3 a 12 blocos em manchas, em todas as
    estruturas) e pedregulho e tijolo de pedra musgosos perto do verde. 59 904 blocos nos 50 arquivos.
  - Para-raios viram tochas de redstone acesas (5: no topo da casa com torre, da mansão pós-apocalíptica, do prédio
    apartamento, do prédio apocalíptico e da torre).

  Os contêineres (baús, barris, dispensers, droppers, vasos decorados) recebem a loot table quando são colocados; os
  banners recebem o desenho do molde; os outros block entities (fornalhas, camas, crânios, estantes entalhadas etc.)
  saem vazios. Loot só com itens vanilla, em 30 tabelas próprias (`tools/gen_loot.py`): 5 temas × 6 níveis.

  | Tema | Estruturas | Itens típicos | Raros |
  |---|---|---|---|
  | `casa` | as 10 casas | comida, linha, papel, tochas, livros, pepitas, esmeraldas | diamante, maçã dourada, livro encantado, discos |
  | `farmacia` | as farmácias | frascos, açúcar, poções de cura, regeneração, visão noturna, resistência ao fogo | poção de cura II, regeneração longa, frascos de experiência |
  | `oficina` | as oficinas | ferro, cobre, carvão, correntes, ferramentas de ferro gastas, trilhos, pistões | diamante, bloco de ferro, ferramentas encantadas, funil, bigorna |
  | `posto` | os postos | lanches, carvão, balde de lava, mapa, bússola, esmeraldas | esmeraldas, ouro, maçã dourada, luneta |
  | `predio` | os prédios | papel, livros, flechas, lápis, redstone, arco e espada gastos | diamante, armadura de ferro encantada, pérolas do ender, discos |

  | Contêiner | Normal | Difícil acesso (`_dificil`) |
  |---|---|---|
  | Baú (`bau`) | sempre: 3 a 6 pilhas (comuns e normais) e 10% de chance de 1 raro | 5 a 8 pilhas e 1 a 2 raros |
  | Barril, dispenser, dropper (`barril`) | 40% têm algo: 1 a 3 pilhas de comuns | 75%: 2 a 4 pilhas de comuns e normais |
  | Vaso decorado (`vaso`) | 40%: 1 pilha comum | 80%: 1 pilha normal ou rara |

  Difícil acesso (`core/Access`): a 15 ou mais blocos acima da rua, ou fora do alcance de quem entra andando (sem
  quebrar blocos: pulando 1 bloco, caindo, subindo escadas de mão, vinhas e água, abrindo portas). São 1 200 dos 2 842 (os prédios altos novos têm quase todos os seus acima de 15)
  contêineres: quase todos nos andares altos da torre, do prédio apocalíptico, do azulado e do esqueleto.
- **Natureza tomando a cidade** (`core/Flora`, `core/Wild`):
  - chão em manchas: grama, musgo, terra grossa e restos do concreto (`gray_concrete_powder`);
  - árvores do mod, ~1 a cada 55 colunas: carvalho, bétula, carvalho escuro e abeto, pequenas e grandes, com vinhas
    penduradas nas copas; são árvores reais: as folhas guardam a distância até o tronco e caem quando o tronco é
    cortado (leaf decay);
  - grama alta, samambaias, tapete de musgo, arbustos (folhas sobre um tronco enterrado) e entulho (pedregulho musgoso, pedregulho, tijolos rachados,
    concreto e lajes musgosas).
- **Parques:**
  - a mesma natureza (árvores ~1 a cada 60 colunas), algumas flores, e caminhos de `dirt_path` (grade ou cata-vento)
    com 30% de falhas, escalados com o tamanho do parque;
  - fonte `waterfall.nbt` no centro, intacta, com cachos de vinha de 2 a 6 blocos e baú vazio em 50% delas; o anel
    é só de lajes musgosas (`mossy_stone_brick_slab` e `mossy_cobblestone_slab`), com 10% de falhas; o piso de
    pedra em volta da bacia é musgoso (`mossy_cobblestone` e `mossy_stone_bricks`).

### Ruas e rodovias

| Via | Largura | Seção |
|---|---|---|
| Rua da cidade | 33 | `smooth_stone` 3 · `polished_deepslate` 27 (faixa `white_concrete` tracejada no 17º bloco) · `smooth_stone` 3 |
| Rodovia | 33 | `smooth_stone` 3 (o de fora com `stone_brick_wall`) · `polished_deepslate` 27 (faixa `yellow_terracotta`) · `smooth_stone` 3 (o de fora com muro) |

- **Faixa central:** traços de 11 com intervalo de 6. Cada cruzamento tem um quadrado 3 × 3 de `smooth_stone` em
  cada esquina.
- **Ruas abandonadas:** ~30% das colunas de ruas, calçadas, rodovias e pontes são tomadas, em manchas e rachaduras:
  grama com mato, musgo, terra grossa, cascalho ou asfalto rachado, sempre no mesmo nível (sem degraus) e só com
  plantas sem colisão por cima. O resto do asfalto (`polished_deepslate`) e das calçadas tem textura gasta, com
  manchas de outras pedras, e 20% das faixas pintadas estão apagadas. Os túneis ficam limpos; das pontes pendem
  vinhas até o chão.
- **Pontes velhas:** laje e pilares de pedra musgosa e rachada, muro quebrado em trechos e musgoso, e buracos
  irregulares (5 a 7 × 4 a 5) atravessando a laje, ~1 a cada 80 blocos de ponte.
- **Postes:** a cada 24 blocos dos dois lados de toda rua da cidade, frente a frente (7 por lado nos trechos de 180,
  5 nos de 120), iguais ao `poste_exemplo` do usuário: coluna de andesito polido de 13 na calçada, braço de 4 lajes a
  12 de altura sobre a pista e vidro cinza (apagado) na ponta; 10% faltam e 5% perderam o braço.
- **Faixas de pedestres:** zebra de `white_concrete` (listras de 1 com intervalo de 1, 3 linhas) 2 linhas antes de
  cada cruzamento, nas duas pontas de todo trecho de rua.
- **Rede de rodovias:** grafo de Gabriel entre os centros das cidades. É planar e conexo, e as rodovias nunca se
  cruzam.
  - Cada cidade tem 4 entradas, uma por lado, sorteadas entre o cruzamento do meio e os dois cantos do lado. Cada
    ligação é combinada pelas duas cidades e sai pelo lado voltado para a destino. Do canto a rodovia sai reta,
    continuando uma das ruas da borda. Até 4 rodovias por cidade.
  - A rodovia tem a largura da rua e chega a ela sem afunilar; o muro termina na junção.
- **Perfil "plano ou rampa"** (`core/Profile`): trechos planos em alturas inteiras e rampas de 1 bloco a cada 8, com
  meia-laje a cada 4.
- **Túneis:** só onde há 11+ blocos de terreno acima da pista. Têm 7 blocos de altura livre, revestimento de `stone`
  e `glowstone` no teto a cada 12.
- **Pontes:** sobre água ou 7+ blocos acima do chão, com tabuleiro de 2 blocos, parapeitos e pilares a cada 24 até
  o fundo.

## Regras garantidas

| Regra | Como é garantida |
|---|---|
| Nada indestrutível | Todos os blocos estão em `gen/Palette`. `Palette.validate()` roda na inicialização e derruba o jogo se algum for indestrutível ou tiver block entity (exceção: o baú da fonte e os block entities das estruturas). Qualquer dureza é permitida. A auditoria no mundo (`hard_block`) confere os blocos gerados. |
| Sem loot | Containers das estruturas **têm loot tables** vanilla próprias por tema (casa, farmácia, oficina, posto, prédio) e por contêiner (baú, barril, vaso), mais generosas nos de difícil acesso. Baú da fonte do parque continua vazio (50%) ou `mossy_cobblestone` (50%). A auditoria confere: contêiner de estrutura sem loot table é `structure_without_loot`, com a tabela errada `structure_wrong_loot`; fora das estruturas, `loot_in_city` e `container_in_city`. |
| Ruas planas e conectadas | Cada cidade tem uma única altura (`City.level`). Ruas cortadas nunca criam beco sem saída nem separam a cidade (`PlanAudit`: `dead_end`, `city_split`). |
| Superfície certa | A auditoria no mundo (`surface_block`) confere bloco a bloco a superfície das vias. |
| Determinismo | Tudo é função pura da semente e do terreno natural: dois chunks vizinhos nunca discordam, em qualquer ordem e thread (`PlanAuditTest.planIsIndependentOfQueryOrderAndThreads`). |
| Sem Immersive Vehicles | Nenhuma referência ao IV no código ou no build. |

## Arquitetura

```
core/   planejador puro (sem classes do Minecraft) — testado com JUnit
  Params         toda a geometria fixa (larguras, quarteirões, rampas, túneis, pontes) + valores da config
  Terrain        interface: altura natural do chão e água por coluna
  City           estágio 1 (existe? centro, altura) e estágio 2 (ruas, quarteirões, lotes)
  Lot            lotes: parque (num canto do quarteirão), uma estrutura ou o miolo tomado pela natureza (WILD)
  Flora          árvores (4 espécies, vinhas), chão em manchas, plantas e entulho; índice de árvores por baldes
  RoadDecay      ruas abandonadas: colunas tomadas, textura gasta, plantas; pedras e muros das pontes velhas
  Wreck          buracos nas pontes, por posição ao longo da rodovia
  Wild           o miolo do quarteirão: chão, árvores e vegetação, sem entrar nas estruturas
  Ring           anel de estruturas em volta do quarteirão (lados curtos inteiros, compridos no que sobra), vãos, limites
  Structures     os 50 arquivos (28 modelos) de `amcs/buildings/index.txt`: caixa, camada do chão, piso até a borda, folhas reais, rotação, escavação com `cave_air`
  Access         contêineres de difícil acesso das estruturas (altos ou fora do alcance andando): loot mais generoso
  Crowns         onde vão as folhas que completam copas cortadas pela borda das estruturas (gen/Overhangs as põe)
  Park           parques tomados pela natureza, com caminhos e fonte
  Template       estruturas .nbt do usuário (fonte waterfall.nbt)
  Highway        curva entre duas entradas de cidade, pontos a cada bloco, projeção de colunas
  Profile        perfil de altura "plano ou rampa" e classificação chão / ponte / túnel
  WorldPlan      cidades, rede de rodovias, plano de cada chunk (ChunkPlan); caches
  PlanAudit      auditoria do plano (usada nos testes e no jogo)
  PlanRender     PNGs do plano (detalhe, rede, perfil lateral)
gen/    camada do jogo
  CityFeature    feature por chunk (passo top_layer_modification, via biome modifier)
  ChunkBuilder   escreve o plano do chunk em blocos; veda líquidos; limpa vizinhos
  Palette        todos os blocos usados (inclui as estruturas, giradas por `BlockState.rotate`, e os 8 estilos de material herdados, sem uso) + validação
  McTerrain      Terrain sobre ChunkGenerator.getBaseHeight (ruído, igual em qualquer thread)
  Plans          um WorldPlan por dimensão (só o overworld)
command/  /amcs (StructureCommands: locate structure e spawn), auditoria dos blocos (WorldAudit), renders, bateria (SelfTest)
```

### Estágios

1. **Existe cidade?**
   - O mundo é dividido em células de `cellSize` (736). Cada célula tem uma cidade com chance `cityChance`
     (70%), no meio da célula.
   - A cidade só existe se ali o terreno não é majoritariamente água e a altura (mediana de 9 amostras, mínimo
     nível do mar + 2) não passa de `maxCityLevel`.
2. **Planejar.**
   - Ruas, quarteirões e lotes saem do grid da cidade.
   - Ligações de rodovia: grafo de Gabriel. Cada cidade reserva uma entrada por vizinha, de frente para ela.
   - A rodovia é validada: comprimento até `highwayMaxLength`, raio de curva ≥ 60, distância de outras cidades,
     rampa possível e água. Ela é descartada se chegar perto demais de uma rodovia mais curta.
3. **Construir.**
   - `WorldPlan.chunk(cx, cz)` diz o que cada coluna deve ser: rua, rodovia, ponte, túnel, parede de túnel, lote ou
     talude. O `ChunkBuilder` só executa.
   - Líquidos encostados no ar aberto viram pedra.
   - Árvores e plantas que um chunk vizinho decorado depois coloca sobre a rua são removidas de novo
     (`ChunkBuilder.repairNeighbours`).

## Configuração (`config/abandonedmoderncity-common.toml`)

`enabled`, `cellSize`, `cityChance`, `minRadius`, `maxRadius`, `maxCityLevel`, `highways`, `highwayMaxLength`,
`structureOvergrowth` (padrão `true`: natureza tomando conta das estruturas; `false` as gera como foram salvas; lida na
inicialização, reinicie o jogo depois de mudar).
A geometria (larguras, quarteirões, rampas, túneis, pontes) fica em `core/Params` e as cidades em `core/City`. Mudanças valem para chunks novos e mudam o plano: use um mundo novo.
Padrões: `cellSize` = 736 e `maxRadius` = 230. Um arquivo de config antigo (com 2048/288, 1024/212, 512/131, 576/166, 640/190 ou 648/190) mantém os
valores velhos: apague-o ou ajuste os dois valores.

## Comandos (op 2)

- `/amcs info` — o que o plano quer na coluna onde você está (tipo, altura, estrutura, cidade, rodovia, lote).
- `/amcs locate city|highway|tunnel|bridge` — o mais próximo, com coordenadas clicáveis (sugerem um `/tp`).
- `/amcs locate structure <nome|tipo|grupo>` — a estrutura mais próxima já planejada nas cidades: pelo arquivo
  (`torre`, `farmacia_vermelho`...), pelo tipo (`casas`, `predios_grandes`...) ou pelo grupo (`farmacia`, `oficina`,
  `posto`), com autocompletar. A coordenada clicável fica na calçada, 2 blocos em frente ao meio da fachada.
- `/amcs spawn <nome>` — coloca a estrutura 2 blocos à sua frente, centralizada na direção em que você olha, com a
  frente virada para você e o chão no nível do bloco em que você pisa. Mesmas regras da cidade: camada enterrada,
  piso até a borda, folhas reais e loot tables nos contêineres. O ar do molde limpa o terreno dentro da caixa (acima
  do chão); abaixo do chão só o `cave_air` do molde escava o terreno (subsolos, como garagens subterrâneas).
- `/amcs plan [raio]` — auditoria do plano em volta (não gera nada) + PNG.
- `/amcs audit [raio]` — gera os chunks em volta, audita plano e blocos, PNG do mundo.
- `/amcs network [raio]` — PNG da rede de cidades e rodovias.
- `/amcs column <x> <z> <y0> <y1>` — blocos reais de uma coluna ao lado do que o plano quer ali (diagnóstico).

Os arquivos vão para `amcs_renders/` na pasta do servidor.
