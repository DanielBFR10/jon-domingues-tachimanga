# Jon Domingues — extensão para Tachimanga

Projeto de uma fonte em português para navegar pelo catálogo público de https://jondomingues.com/ no Tachimanga.

> **Estado atual: protótipo em desenvolvimento.** O código ainda precisa ser integrado a uma versão compatível do framework Mihon/Keiyoushi, compilado e validado no Tachimanga. Ainda não existe JAR instalável nem índice de repositório publicado.

## Escopo planejado

- Catálogo, últimas publicações e paginação.
- Pesquisa por título.
- Categorias/editoras observadas: DC Comics, Dynamite Entertainment, IDW Publishing, Image Comics, Mangás, Marvel Comics.
- Página de detalhes: capa, sinopse e metadados.
- Leitor online: capítulos e páginas/imagens sequenciais.
- Distribuição futura por repositório GitHub compatível com Tachimanga.

## Estrutura

```text
src/pt/jondomingues/
├── build.gradle
└── src/eu/kanade/tachiyomi/extension/pt/jondomingues/
    └── JonDomingues.kt
```

## Desenvolvimento

O módulo foi preparado para integração no ecossistema `keiyoushi/extensions-source`. Consulte [`site-analysis.md`](site-analysis.md) para o mapeamento inicial do site e [`TESTING.md`](TESTING.md) para o roteiro de validação.

## Próximas etapas

1. Fixar uma revisão compatível do framework de extensões.
2. Ajustar os seletores aos HTMLs reais recebidos pelo cliente HTTP.
3. Compilar e corrigir incompatibilidades de API.
4. Testar busca, detalhes, capítulos e leitor no Tachimanga.
5. Assinar o artefato e gerar/publicar o índice do repositório.

## Direitos autorais

A extensão funciona como cliente do site de origem. Este projeto não hospeda nem redistribui o conteúdo do catálogo. O uso deve respeitar os direitos autorais, os termos do site e a legislação aplicável.
