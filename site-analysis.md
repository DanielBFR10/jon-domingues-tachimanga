# Mapeamento inicial — jondomingues.com

## Rotas observadas

| Função | Padrão/URL observada |
|---|---|
| Início / catálogo | `/` |
| Busca | formulário de busca WordPress (`?s=termo`) |
| Categorias | DC Comics, Dynamite Entertainment, IDW Publishing, Image Comics, Mangás e Marvel Comics |
| Página de edição | exemplo `/zetman-volume-01/` |
| Leitor online | exemplo `/ler-online-zetman-volume-01/` |

## Campos observados na página de edição

- Título da edição
- Data/categoria
- Capa
- Link "Ler online"
- Link externo "Baixar HQ"
- Sinopse
- Ficha técnica (volume, autoria, editora, publicação original, idioma, classificação, páginas e ISBN)
- Link para edição seguinte e para a série

## Leitor online

A página observada de Zetman Volume 01 apresenta:
- Título do volume
- Contador 1 / 251
- Controles de download, leitura automática e próxima imagem
- 251 imagens sequenciais
- Classificação indicativa
- Link de retorno à edição

## Estratégia de scraping

1. Catálogo: localizar links de páginas de edição em cards do WordPress.
2. Busca: enviar `?s=<termo>` e extrair os cards.
3. Detalhes: extrair título, capa, sinopse e metadados.
4. Capítulo: tratar o leitor online como capítulo da edição.
5. Páginas: coletar URLs das imagens da rota `/ler-online-.../`, priorizando `data-src`, `data-lazy-src`, `data-original` e `src`.

## Pontos que precisam de teste real

- Se o servidor entrega o HTML completo ao OkHttp sem depender de JavaScript.
- Classes CSS exatas dos cards, capa e imagens.
- Se imagens usam lazy loading, `srcset`, token temporário ou proteção anti-hotlink.
- Como a paginação da busca e das categorias é construída.
- Se o leitor usa uma imagem por página ou páginas duplas em alguns títulos.
