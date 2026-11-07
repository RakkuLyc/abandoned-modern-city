# Abandoned Modern City — NeoForge 1.21.1

Cidades modernas abandonadas geradas proceduralmente: ruas, rodovias, túneis, pontes, quarteirões e parques.
Por enquanto **sem prédios**: eles serão projetados de novo neste mod.

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
- Nenhuma dependência opcional: sem Immersive Vehicles, Zombie Extreme, Big Lost City, loot tables ou mixins.
- A especificação completa e atual (aprovada pelo usuário) fica em `CLAUDE.md`.

## O que é gerado

### Cidades

| Tipo | Onde | Forma |
|---|---|---|
| Cidade 4 × 4 (`urban_city`) | uma por célula de 2 048 blocos (70% das células) | grid rígido de 4 × 4 quarteirões de 186 × 186 (eixos a 203, ~830 blocos de lado), 4 entradas de rodovia |
| Cidade octogonal (`special_city`) | 25% das cidades de célula | rotatória central (ilha de 96 de diâmetro), 2 anéis octogonais a 80 blocos um do outro, 8 ruas radiais; sem prédios |
| Cidade 3 × 2 (`rural_city`) | 50% das rodovias, perto do meio delas | 3 × 2 quarteirões de 68, atravessada pela rodovia na rua do meio |

- Cada cidade fica numa única altura: água é aterrada, morro é cortado, e um talude 1:1 faz a transição.
- As cidades 4 × 4 perdem de 0 a 7 trechos de rua (sem criar beco sem saída). O espaço vira grama ou une quarteirões.
- **Quarteirões:** cerca de 1 em 8 quarteirões é um parque inteiro; os outros são só chão da cidade
  (`gray_concrete_powder`), à espera dos prédios novos.
- **Parques:**
  - grama, flores e caminhos de `dirt_path` (grade ou cata-vento), escalados com o tamanho do parque;
  - fonte `waterfall.nbt` no centro, com baú vazio em 50% delas.
- **Cidades 3 × 2:**
  - pelo menos 1 quarteirão de plantação abandonada (os outros com 15% de chance);
  - o resto é chão de `dirt_path`.

### Ruas e rodovias

| Via | Largura | Seção |
|---|---|---|
| Rua da cidade | 17 | `smooth_stone` 2 · `polished_blackstone` 13 (faixa `white_concrete` tracejada no 9º bloco) · `smooth_stone` 2 |
| Rodovia | 21 | `smooth_stone` + `stone_brick_wall` 1 · `polished_blackstone` 19 (faixa `yellow_terracotta`) · `smooth_stone` + muro 1 |
| Rodovia perto de cidade 3 × 2 (150 blocos) | 21 | `andesite` 19 (lajes nas rampas) · `stone` + `cobblestone_wall` nas bordas |
| Rua do meio da cidade 3 × 2 | 17 | `andesite` 15 com faixa amarela · `stone` 1 de cada lado |
| Demais ruas da cidade 3 × 2 | 17 | `stone` 2 · `andesite` 13 (faixa branca) · `stone` 2 |

- **Faixa central:** traços de 11 com intervalo de 6. Cada cruzamento tem um quadrado 2 × 2 de `smooth_stone` em
  cada esquina.
- **Rede de rodovias:** grafo de Gabriel entre os centros das cidades. É planar e conexo, e as rodovias nunca se
  cruzam.
  - Cada ligação é combinada pelas duas cidades. A entrada é sorteada entre os cruzamentos de 3 vias da borda voltada
    para a cidade de destino.
  - Perto de cada cidade, a rodovia afunila de 21 para 17, perdendo 1 bloco a cada 8.
- **Perfil "plano ou rampa"** (`core/Profile`): trechos planos em alturas inteiras e rampas de 1 bloco a cada 8, com
  meia-laje a cada 4.
- **Túneis:** só onde há 11+ blocos de terreno acima da pista. Têm 7 blocos de altura livre, revestimento de `stone`
  e `glowstone` no teto a cada 12.
- **Pontes:** sobre água ou 7+ blocos acima do chão, com tabuleiro de 2 blocos, parapeitos e pilares a cada 24 até
  o fundo.

## Regras garantidas

| Regra | Como é garantida |
|---|---|
| Nada indestrutível | Todos os blocos estão em `gen/Palette`. `Palette.validate()` roda na inicialização e derruba o jogo se algum for indestrutível ou tiver block entity (exceção: o baú vazio da fonte). Qualquer dureza é permitida. A auditoria no mundo (`hard_block`) confere os blocos gerados. |
| Sem loot | Nenhum contêiner com loot table; o único contêiner é o baú vazio de metade das fontes. A auditoria (`loot_in_city`, `container_in_city`) confere. |
| Ruas planas e conectadas | Cada cidade tem uma única altura (`City.level`). Ruas cortadas nunca criam beco sem saída nem separam a cidade (`PlanAudit`: `dead_end`, `city_split`). |
| Superfície certa | A auditoria no mundo (`surface_block`) confere bloco a bloco a superfície das vias. |
| Determinismo | Tudo é função pura da semente e do terreno natural: dois chunks vizinhos nunca discordam, em qualquer ordem e thread (`PlanAuditTest.planIsIndependentOfQueryOrderAndThreads`). |
| Sem Immersive Vehicles | Nenhuma referência ao IV no código ou no build. |

## Arquitetura

```
core/   planejador puro (sem classes do Minecraft) — testado com JUnit
  Params         toda a geometria fixa (larguras, quarteirões, rampas, túneis, pontes) + valores da config
  Terrain        interface: altura natural do chão e água por coluna
  City           estágio 1 (existe? centro, altura, octogonal?) e estágio 2 (ruas, quarteirões, lotes);
                 também as cidades 3 × 2 (town)
  Lot            lotes: parque, plantação (e praça / terreno vazio nas cidades ainda não pavimentadas)
  Park           parques com caminhos e fonte
  Template       estruturas .nbt do usuário (fonte waterfall.nbt)
  Highway        curva entre duas entradas de cidade, pontos a cada bloco, projeção de colunas
  Profile        perfil de altura "plano ou rampa" e classificação chão / ponte / túnel
  WorldPlan      cidades, rede de rodovias, cidades 3 × 2, plano de cada chunk (ChunkPlan); caches
  PlanAudit      auditoria do plano (usada nos testes e no jogo)
  PlanRender     PNGs do plano (detalhe, rede, perfil lateral)
gen/    camada do jogo
  CityFeature    feature por chunk (passo top_layer_modification, via biome modifier)
  ChunkBuilder   escreve o plano do chunk em blocos; veda líquidos; limpa vizinhos
  Palette        todos os blocos usados (inclui os 8 estilos de material herdados, ainda sem uso) + validação
  McTerrain      Terrain sobre ChunkGenerator.getBaseHeight (ruído, igual em qualquer thread)
  Plans          um WorldPlan por dimensão (só o overworld)
command/  /amcs, auditoria dos blocos (WorldAudit), renders do mundo, bateria automática (SelfTest)
```

### Estágios

1. **Existe cidade?**
   - O mundo é dividido em células de `cellSize` (2 048). Cada célula tem uma cidade com chance `cityChance`
     (70%), longe das bordas.
   - A célula tenta até 10 posições e fica com a primeira em que o terreno não é majoritariamente água e a altura
     (mediana de 9 amostras, mínimo nível do mar + 2) não passa de `maxCityLevel`.
   - 25% das cidades são octogonais.
2. **Planejar.**
   - Ruas, quarteirões e lotes saem do grid da cidade.
   - Ligações de rodovia: grafo de Gabriel. Cada cidade reserva uma entrada por vizinha, de frente para ela.
   - A rodovia é validada: comprimento até `highwayMaxLength`, raio de curva ≥ 60, distância de outras cidades,
     rampa possível e água. Ela é descartada se chegar perto demais de uma rodovia mais curta.
   - Metade das rodovias ganha uma cidade 3 × 2 no meio.
3. **Construir.**
   - `WorldPlan.chunk(cx, cz)` diz o que cada coluna deve ser: rua, rodovia, ponte, túnel, parede de túnel, lote ou
     talude. O `ChunkBuilder` só executa.
   - Líquidos encostados no ar aberto viram pedra.
   - Árvores e plantas que um chunk vizinho decorado depois coloca sobre a rua são removidas de novo
     (`ChunkBuilder.repairNeighbours`).

## Configuração (`config/abandonedmoderncity-common.toml`)

`enabled`, `cellSize`, `cityChance`, `minRadius`, `maxRadius`, `maxCityLevel`, `highways`, `highwayMaxLength`.
A geometria (larguras, quarteirões, rampas, túneis, pontes) fica em `core/Params` e as cidades em `core/City`. Mudanças valem para chunks novos e mudam o plano: use um mundo novo.

## Comandos (op 2)

- `/amcs info` — o que o plano quer na coluna onde você está (tipo, altura, estrutura, cidade, rodovia, lote).
- `/amcs locate city|urban_city|rural_city|special_city|highway|tunnel|bridge` — o mais próximo, com coordenadas
  clicáveis (sugerem um `/tp`). `urban_city` = cidade 4 × 4, `rural_city` = cidade 3 × 2 de rodovia,
  `special_city` = cidade octogonal.
- `/amcs plan [raio]` — auditoria do plano em volta (não gera nada) + PNG.
- `/amcs audit [raio]` — gera os chunks em volta, audita plano e blocos, PNG do mundo.
- `/amcs network [raio]` — PNG da rede de cidades e rodovias.
- `/amcs column <x> <z> <y0> <y1>` — blocos reais de uma coluna ao lado do que o plano quer ali (diagnóstico).

Os arquivos vão para `amcs_renders/` na pasta do servidor.
