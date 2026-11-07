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
3 × 2) e sem o histórico de commits. Os prédios serão projetados de novo aqui. O Abandoned Modern City original
(pasta `AbandonedModernCity-Deprecated`) está descontinuado. Ainda em 7 nov 2026 o usuário mandou remover as cidades
octogonais e as cidades 3 × 2 das rodovias (com o padrão rural_highway perto delas e as plantações), e depois trocar
o grid 4 × 4 por 2 × 2 com células de 1024: hoje só existem as cidades 2 × 2.

## Especificação das ruas e cidades (atual)

- Grid rígido, ruas retas e cantos em ângulo reto: cidade = 2 × 2 quarteirões de 186 × 186 (`Params.GRID` = 2,
  `Params.BLOCK` = 203 entre eixos, ~424 blocos de lado, 3 × 3 ruas e 9 cruzamentos). Todas as cidades são assim
  (não há mais variantes).
- Água é aterrada, morro é cortado. Cidades a ~1 000 blocos umas das outras (célula 1024, `Params.DEFAULT`). Para
  caber na célula, o `maxRadius` padrão passou de 288 para 212 (o raio da cidade 2 × 2); o centro de cada cidade só
  varia ~112 blocos dentro da célula.
- Rua com 17 de largura: 2 `smooth_stone` (calçada) + 13 `polished_blackstone` (pista) + 2 `smooth_stone`.
- Trecho de rua de 186 blocos + 17 de cruzamento; quadrado 2 × 2 de `smooth_stone` em cada esquina de cruzamento.
- Faixa central no 9º bloco da largura, traços de 11 com intervalo de 6 (quantos couberem, centrados); `white_concrete` na cidade,
  `yellow_terracotta` nas rodovias.
- 4 entradas por cidade, em cruzamentos de 3 vias da borda: cada ligação é combinada pelas duas cidades e a seed
  sorteia a entrada entre os cruzamentos do lado voltado para a cidade de destino ("opção 2"). No 2 × 2 cada lado tem
  um só cruzamento de 3 vias (o do meio), então cada lado recebe no máximo uma rodovia.
- Rodovias com 21: 1 `smooth_stone` + `stone_brick_wall` em cima, 19 `polished_blackstone` (faixa `yellow_terracotta`
  no meio), 1 `smooth_stone` + muro. Muros conectados pelo plano (não pelo vanilla). Dentro das cidades os lotes têm
  prioridade: a rodovia estreita para a rua de 17 nas entradas. As rodovias vão direto de uma cidade à outra.
- Afunilamento: perto de cada cidade a rodovia perde 1 bloco de largura a cada 8 (`Highway.TAPER_STEP`), alternando
  os lados, de 21 até 17 (largura de rua), logo depois da junção com a rua.
- Túneis com 7 blocos de altura livre (`Params.CLEARANCE`); só há túnel com 11+ blocos de terreno acima da pista
  (`Params.TUNNEL_COVER`).
- Ruas cortadas: **desligadas** (`City.MAX_CUTS` = 0, pedido do usuário para o 2 × 2; o código continua). Quando
  ligadas, cada cidade perde de 0 a `MAX_CUTS` trechos de rua (sorteio uniforme),
  internos ou da borda; nunca cria beco sem saída e a cidade continua toda conectada. O espaço vira grama, unindo
  os quarteirões. Entradas não são protegidas (só ficam conectadas).
- Quarteirões: sem prédios por enquanto. ~1 em 8 quarteirões é um parque
  inteiro (`City.PARK_BLOCK_CHANCE`); os demais não têm lotes e todo o chão é `gray_concrete_powder` (o chão da
  cidade). Rua cortada = quarteirões juntados: cada um avança até o meio da faixa; na borda da cidade o quarteirão
  cobre a faixa até a beira.
- Parques (`Park`): grama com mato, tall grass e flores (papoula, dente-de-leão, azure bluet, tulipas, oxeye daisy,
  cornflower); caminhos de `dirt_path` de 3 blocos com ~8% de falhas, em duas variantes (50%): grade "jogo da velha"
  (2 + 2 caminhos retos a 16 do centro) ou cata-vento (círculo de raio 16 + 4 braços em espiral); distâncias escaladas
  com o tamanho do parque (base `Park.BASE_SIZE` = 68; 186 → ×2,7). Centro sem árvores:
  fonte `waterfall.nbt` (do usuário, em `resources/amcs/`) cercada de `smooth_stone_slab` (meio bloco) do anel até a
  cerca de folhas da fonte.
  Baú vazio em 50% das fontes (senão `mossy_cobblestone`).
- Dureza: qualquer dureza é permitida (regra antiga removida pelo usuário); só blocos indestrutíveis são proibidos.
  O único block entity permitido é o baú vazio da fonte (`Palette.exempt`).
- Paleta (`gen/Palette`): os 8 estilos de material dos prédios do Abandoned Simple City (paredes, janelas, pisos,
  acabamento, escadas, sacadas) continuam na paleta, sem uso, para os prédios novos.
