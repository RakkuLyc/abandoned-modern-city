# Abandoned Modern City — NeoForge 1.21.1

Cidades modernas abandonadas geradas proceduralmente: ruas, rodovias, túneis, pontes, quarteirões e parques.
Os prédios serão estruturas `.nbt` do usuário, sorteadas ao acaso num anel em volta de cada quarteirão
(protótipo). No momento o mod não tem nenhuma estrutura: as 33 primeiras foram removidas em 10 nov 2026.

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
| Cidade 2 × 2 | uma por célula de 640 blocos (70% das células) | grid rígido de 2 × 2 quarteirões de 140 × 90 (eixos a 173 e a 123, ~379 × ~279 blocos com as ruas, 3 × 3 ruas; o lado comprido fica leste-oeste ou norte-sul, sorteado por cidade), até 8 entradas de rodovia (o cruzamento do meio de cada lado e os 4 cantos) |

Só existe esse tipo de cidade: as cidades octogonais e as cidades 3 × 2 das rodovias (com o padrão rural_highway e
as plantações) foram removidas em 7 nov 2026; o grid passou de 4 × 4 quarteirões de 186 (células de 2 048) para
2 × 2 quarteirões de 105 × 60 (células de 512) e, em 8 nov 2026, para 140 × 90 (células de 576, para caber as
estruturas de `structures/`); em 10 nov 2026 ruas e rodovias passaram a 33 de largura (células de 640).

- Cada cidade fica numa única altura: água é aterrada, morro é cortado, e um talude 1:1 faz a transição.
- Ruas cortadas estão desligadas (`City.MAX_CUTS` = 0); o código que corta trechos sem criar beco sem saída
  continua lá.
- **Quarteirões:** cerca de 1 em 8 quarteirões é um parque inteiro; nos outros as estruturas formam um anel
  encostado nas calçadas, com a frente (lado -Z do `.nbt`) virada para a rua; o resto é chão da cidade
  (`gray_concrete_powder`).
- **Estruturas:** nenhuma no momento. As 33 primeiras (farmácias, oficinas, postos, apartamento, prédio azulado,
  torre, prédio tombado) foram removidas do mod em 10 nov 2026, a pedido do usuário; o anel, a rotação e as loot
  tables continuam prontos. Para adicionar: copiar os `.nbt` para `src/main/resources/amcs/buildings/` e listar
  cada um em `index.txt` (`tipo|arquivo`). Sem estruturas, os quarteirões que não são parque ficam só com o chão
  da cidade.

  Containers (baús, barris, suportes de poção, vasos decorados, atris, fornalhas, camas, fogueiras, sensor de luz, jukebox) recebem loot tables:
  - **Todas as estruturas exceto oficinas (tipo que começa com `oficina`):** loot table `lostcities:chests/lostcitychest` (do mod Apocalypse Loot / Lost Cities), com munição TACZ, armas, itens médicos, comida, recursos e raridades.
  - **Oficinas (médias e pequenas):** baús recebem `oficina_vehicle_parts` (rodas, assentos e motores compatíveis com Camaro e Escargot do OAmP); demais containers recebem `oficina_vehicle_tools` (jumperpack, blowtorch, repairkit).
- **Parques:**
  - grama, flores e caminhos de `dirt_path` (grade ou cata-vento), escalados com o tamanho do parque;
  - fonte `waterfall.nbt` no centro, com baú vazio em 50% delas.

### Ruas e rodovias

| Via | Largura | Seção |
|---|---|---|
| Rua da cidade | 33 | `smooth_stone` 3 · `polished_blackstone` 27 (faixa `white_concrete` tracejada no 17º bloco) · `smooth_stone` 3 |
| Rodovia | 33 | `smooth_stone` 3 (o de fora com `stone_brick_wall`) · `polished_blackstone` 27 (faixa `yellow_terracotta`) · `smooth_stone` 3 (o de fora com muro) |

- **Faixa central:** traços de 11 com intervalo de 6. Cada cruzamento tem um quadrado 3 × 3 de `smooth_stone` em
  cada esquina.
- **Rede de rodovias:** grafo de Gabriel entre os centros das cidades. É planar e conexo, e as rodovias nunca se
  cruzam.
  - Cada ligação é combinada pelas duas cidades. A entrada fica no lado voltado para a cidade de destino: no
    cruzamento do meio ou num canto, o que estiver mais deslocado na direção dela (sorteio entre empatados). Do
    canto a rodovia sai reta, continuando uma das ruas da borda. Até 8 rodovias por cidade, uma por cruzamento.
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
| Nada indestrutível | Todos os blocos estão em `gen/Palette`. `Palette.validate()` roda na inicialização e derruba o jogo se algum for indestrutível ou tiver block entity (exceção: o baú da fonte e os containers vazios das estruturas). Qualquer dureza é permitida. A auditoria no mundo (`hard_block`) confere os blocos gerados. |
| Sem loot | Containers das estruturas **têm loot tables**: exceto oficinas usam `lostcities:chests/lostcitychest`; oficinas usam `oficina_vehicle_parts` (baús) e `oficina_vehicle_tools` (outros). Baú da fonte do parque continua vazio (50%) ou `mossy_cobblestone` (50%). A auditoria (`loot_in_city`, `container_in_city`) confere. |
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
  Lot            lotes: parque inteiro de quarteirão ou uma estrutura
  Ring           anel de estruturas em volta do quarteirão (norte e sul inteiros, oeste e leste no que sobra)
  Structures     estruturas de `amcs/buildings/index.txt` (hoje vazio): caixa dos blocos sólidos, rotação, código de cada bloco
  Park           parques com caminhos e fonte
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
command/  /amcs, auditoria dos blocos (WorldAudit), renders do mundo, bateria automática (SelfTest)
```

### Estágios

1. **Existe cidade?**
   - O mundo é dividido em células de `cellSize` (640). Cada célula tem uma cidade com chance `cityChance`
     (70%), longe das bordas.
   - A célula tenta até 10 posições e fica com a primeira em que o terreno não é majoritariamente água e a altura
     (mediana de 9 amostras, mínimo nível do mar + 2) não passa de `maxCityLevel`.
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

`enabled`, `cellSize`, `cityChance`, `minRadius`, `maxRadius`, `maxCityLevel`, `highways`, `highwayMaxLength`.
A geometria (larguras, quarteirões, rampas, túneis, pontes) fica em `core/Params` e as cidades em `core/City`. Mudanças valem para chunks novos e mudam o plano: use um mundo novo.
Padrões: `cellSize` = 640 e `maxRadius` = 190. Um arquivo de config antigo (com 2048/288, 1024/212, 512/131 ou 576/166) mantém os
valores velhos: apague-o ou ajuste os dois valores.

## Comandos (op 2)

- `/amcs info` — o que o plano quer na coluna onde você está (tipo, altura, estrutura, cidade, rodovia, lote).
- `/amcs locate city|highway|tunnel|bridge` — o mais próximo, com coordenadas clicáveis (sugerem um `/tp`).
- `/amcs plan [raio]` — auditoria do plano em volta (não gera nada) + PNG.
- `/amcs audit [raio]` — gera os chunks em volta, audita plano e blocos, PNG do mundo.
- `/amcs network [raio]` — PNG da rede de cidades e rodovias.
- `/amcs column <x> <z> <y0> <y1>` — blocos reais de uma coluna ao lado do que o plano quer ali (diagnóstico).

Os arquivos vão para `amcs_renders/` na pasta do servidor.
