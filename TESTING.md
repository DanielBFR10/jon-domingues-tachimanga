# Roteiro de validação

## Antes do build
- [ ] Integrar o módulo em um checkout compatível de `keiyoushi/extensions-source`.
- [ ] Confirmar a versão da biblioteca de extensões usada pelo framework.
- [ ] Conferir as assinaturas das funções sobrescritas contra a versão fixada do framework.

## Testes funcionais no Tachimanga
- [ ] Fonte aparece como "Jon Domingues" em pt-BR.
- [ ] Busca por "Zetman" retorna "Zetman Volume 01".
- [ ] Detalhes exibem título, capa e sinopse.
- [ ] Capítulo "Ler online" aparece na edição.
- [ ] Leitor carrega todas as imagens e não inclui logo/ícones.
- [ ] Próxima página e retorno funcionam.
- [ ] Testar ao menos uma edição de HQ ocidental e uma de mangá.
- [ ] Testar paginação de busca e categorias.

## Publicação
- [ ] Gerar JAR compatível com o runtime do Tachimanga.
- [ ] Assinar com chave própria e guardar a chave fora do Git.
- [ ] Gerar `index.pb`, `repo.json` e índices auxiliares com as ferramentas do framework.
- [ ] Publicar artefatos no GitHub Pages ou branch `repo`.
- [ ] Instalar pelo URL do índice em uma instalação limpa do Tachimanga.
