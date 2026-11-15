# Mapeamento do catálogo — jondomingues.com

Este arquivo registra a taxonomia observada no site em 15/11/2026. A extensão usa arquivos canônicos por série e filtra cada arquivo pelo título real da edição, pois o site possui categorias agregadoras e algumas edições estão marcadas em categorias de editoras/séries incorretas.

## Regras importantes observadas

- A categoria `Batman` é agregadora: contém Absolute Batman e Batman: Padrões Sombrios. Não deve aparecer como uma série única.
- `Absolute Batman` tem 23 edições. As edições #21–#23 aparecem com o título traduzido `Batman Absoluto`, mas pertencem à mesma série iniciada em 2024.
- `Universo Absoluto` agrega Absolute Batman e Lanterna Verde Absoluto. Não deve aparecer como série independente.
- `Lanterna Verde` contém edições de Lanterna Verde Absoluto; `Lanterna Verde (2023)` é outra série.
- `Flash (2021)` apresenta Flashpoint #0–#5; o nome de série deve ser Flashpoint.
- `Hellblazer` e `John Constantine` apontam para a mesma edição de Hellblazer: Morto na América. Usar uma única entrada canônica.
- `Doutor Destino` contém duas séries diferentes: Guerra Contra Destino e Um Mundo nas Mãos do Destino.
- `Justiceiro` contém duas fases diferentes: Justiceiro (2022) e O Justiceiro (2026).
- `Wolverine` contém Deadpool e Wolverine e até uma edição de Absolute Batman #5. Não deve ser usada como série; filtrar pelo título real.
- A categoria principal Marvel Comics também contém Absolute Batman #5 indevidamente. Sempre filtrar a série pelo título da edição.
- The Boys possui duas páginas: a segunda apresenta as edições #31–#44. O arquivo deve ser percorrido por paginação numérica.

## Catálogo canônico observado

| Editora | Série | Edições/volumes observados | Arquivo de origem |
|---|---|---:|---|
| DC Comics | Absolute Batman | #1–#23 | `/category/dc-comics/batman/absolute-batman/` |
| DC Comics | Batman: Padrões Sombrios | #1–#12 | `/category/dc-comics/batman/batman-padroes-sombrios/` |
| DC Comics | Coringa: O Homem Que Parou de Rir | #1–#3 | `/category/dc-comics/coringa/` |
| DC Comics | Flashpoint | #0–#5 | `/category/dc-comics/flash/` |
| DC Comics | Flash Absoluto | #1–#12 | `/category/dc-comics/flash-absoluto/` |
| DC Comics | Hellblazer: Morto na América | #1 | `/category/dc-comics/hellblazer/` |
| DC Comics | Liga da Justiça: Odisseia | #1–#12 | `/category/dc-comics/liga-da-justica/` |
| DC Comics | Lanterna Verde Absoluto | #1–#17 | `/category/dc-comics/lanterna-verde/` |
| DC Comics | Lanterna Verde (2023) | #1–#9 | `/category/dc-comics/lanterna-verde-2023/` |
| Dynamite Entertainment | The Boys | #1–#44 | `/category/dynamite-entertainment/the-boys/` |
| Dynamite Entertainment | Thundercats | #1–#2 | `/category/dynamite-entertainment/thundercats/` |
| IDW Publishing | Dungeons & Dragons | #0–#5 | `/category/idw-publishing/` |
| IDW Publishing | Caverna do Dragão: Aventuras de Sábado pela Manhã | #1–#4 | `/category/idw-publishing/caverna-do-dragao/` |
| Image Comics | Invencível | #1–#16 | `/category/image-comics/invencivel/` |
| Image Comics | Savage Dragon | #1–#9 | `/category/image-comics/savage-dragon/` |
| Image Comics | The Walking Dead | Volumes 1–4 | `/category/image-comics/the-walking-dead/` |
| Marvel Comics | Quarteto Fantástico | #1 | `/category/marvel-comics/quarteto-fantastico/` |
| Marvel Comics | Deadpool e Wolverine | #1–#7 | `/category/marvel-comics/deadpool/` |
| Marvel Comics | Guerra Contra Destino | #1–#6 | `/category/marvel-comics/doutor-destino/` |
| Marvel Comics | Um Mundo nas Mãos do Destino | #1–#3 | `/category/marvel-comics/doutor-destino/` |
| Marvel Comics | Miles Morales: Homem-Aranha (2019) | #1–#12 | `/category/marvel-comics/homem-aranha/miles-morales-homem-aranha-2019/` |
| Marvel Comics | O Espetacular Homem-Aranha | #1–#10 | `/category/marvel-comics/homem-aranha/o-espetacular-homem-aranha/` |
| Marvel Comics | Justiceiro (2022) | #1–#12 | `/category/marvel-comics/justiceiro/` |
| Marvel Comics | O Justiceiro (2026) | #1–#6 | `/category/marvel-comics/justiceiro/` |
| Marvel Comics | Os Vingadores (2023) | #1–#4 | `/category/marvel-comics/os-vingadores/` |
| Marvel Comics | Invasão Secreta | #1 | `/category/marvel-comics/` |
| Marvel Comics | Homem-Aranha 2099 | #1 | `/category/marvel-comics/` |
| Mangás | Zetman | Volumes 1–2 | `/category/mangas/` |
| Mangás | CDZ – Saint Seiya | Volumes 1–2 | `/category/mangas/os-cavaleiros-do-zodiaco/` |

Os intervalos acima são a listagem visível nas páginas consultadas; o parser ainda deve ignorar qualquer edição cujo título não corresponda exatamente à série configurada.

## Implementação do parser

- O catálogo usa uma lista explícita de arquivos de série verificados, não categorias-pai.
- Arquivos agregadores são filtrados por título normalizado antes de virar capítulos.
- A ordem de capítulos vem do número real da edição/volume; o nome exibido é sequencial (`Capítulo 1`, `Capítulo 2`, etc.).
- A paginação é seguida por links numéricos do WordPress e a coleta termina quando não há próxima página.
- Duplicatas são removidas pela URL canônica da edição/leitor.
- A página de detalhes da edição fornece o leitor correspondente; links de "Veja também" não são capítulos.

## Leitor online

- Cada capítulo aponta para a rota `/ler-online-...` da edição correspondente.
- A página do leitor deve fornecer as imagens em sequência.
- Links de download, breadcrumbs e recomendações não podem ser usados como páginas de leitura.

## Pendências de validação funcional

- Confirmar no Tachimanga o total de capítulos por série e a ordem exibida.
- Confirmar que nenhuma HQ de outra série aparece em Absolute Batman, Batman: Padrões Sombrios, Justiceiro ou demais agrupamentos.
- Validar todos os leitores e páginas, não apenas a compilação.
