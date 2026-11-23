# Avatar pequeno no Firestore

A foto do perfil foi migrada para o Firestore. Esta funcionalidade não usa Firebase Storage, bucket ou Cloud Functions. Ela utiliza as cotas do Firestore do projeto; não é armazenamento ilimitado.

## Ativar

1. Atualize e execute o aplicativo.
2. No Firebase Console, abra Firestore Database → Regras.
3. Publique o conteúdo atualizado de `firebase/firestore.rules` (também entregue como `outputs/firestore-avatares.rules`). As regras preservam os recursos existentes e acrescentam os avatares.
4. Abra Configurações → Escolher foto → confira a prévia → Salvar foto.

Não é necessário ativar o Storage nem substituir o google-services.json para esta mudança. Nenhuma regra foi publicada nem foram feitas gravações no projeto real durante a implementação.

Opcional: no Firestore Console, crie uma isenção de indexação de campo único para `conteudo` no grupo de coleções `avatares`, pois a aplicação nunca pesquisa pelo conteúdo da imagem. Não foi introduzido um arquivo de índices global vazio que pudesse remover índices existentes.

## Dados

- `avatares/{uid}`: `uid`, `versao`, `conteudo` (Blob binário), `mimeType` e `atualizadoEm`.
- O avatar é JPEG, quadrado, com até 256 × 256 pixels e 50 KiB (51.200 bytes).
- A entrada continua limitada a 20 MB e 40 megapixels; a preparação reduz a memória utilizada, corrige a orientação e remove EXIF/GPS.
- A qualidade JPEG é reduzida gradualmente quando necessário para respeitar o limite.
- `users/{uid}.fotoUrl` e `rankings/{uid}.fotoUrl` guardam apenas uma referência interna pequena, no formato `avatar-firestore:UID/UUID`. Não são URLs HTTP e nunca são enviadas a um servidor de imagens.
- O documento do avatar, o perfil e a projeção do ranking são atualizados na mesma transação. Falhas não deixam uma foto parcialmente publicada.
- Só existe um documento de avatar por usuário. Trocar a imagem substitui seu conteúdo e gera outra versão, sem acumular fotos antigas.
- Pontos, ofensiva, contagem de cards, versões de estudo e dados privados são preservados.
- O antigo campo `fotoStoragePath` é removido do perfil ao salvar um avatar novo. Nenhum objeto do antigo bucket é apagado automaticamente.

## Exibição e cache

`ReferenciaAvatar` valida a referência e sua associação ao UID. O carregador `AvatarModelLoader`, registrado pelo `ClassCardsGlideModule`, busca os bytes apenas quando a imagem é exibida e utiliza o cache do Glide. Uma nova versão altera a chave do cache.

Perfil, configurações, pesquisa, ranking e fotos dos relatórios reconhecem o novo formato. Fotos HTTPS existentes, como as do Google, continuam funcionando. Os documentos das listas não carregam o Blob junto com seus resultados.

Referências guardadas em relatórios antigos podem resolver o avatar atual do usuário. Caso um avatar esteja ausente ou inválido, aparece a imagem padrão. O carregador também verifica o JPEG e suas dimensões antes de decodificar a imagem.

## Permissões

- Apenas o dono cria/atualiza seu avatar.
- Cada substituição exige uma versão nova.
- A regra exige Blob não vazio, até 51.200 bytes, MIME JPEG, campos previstos e timestamp do servidor.
- A publicação deve estar coerente com o perfil e o ranking após a transação.
- Contas autenticadas podem obter um avatar pelo UID para exibi-lo no aplicativo.
- Não há permissão para listar a coleção nem para excluir documentos de avatar pelo cliente.
- Referências a avatares inexistentes ou de outro UID são recusadas na projeção pública.

## Arquivos principais

- `FotoPerfilRepository`: preparo e salvamento no Firestore; sem SDK de Storage.
- `FotoPerfilPublicacao`: transação do Blob e das referências.
- `PreparadorFotoPerfil`: recorte e compressão até os novos limites.
- `ReferenciaAvatar`: formato interno e compatibilidade com HTTPS.
- `AvatarModelLoader` e `ClassCardsGlideModule`: leitura e cache centralizados.
- `RankingRepository`, `ResultadoUsuarioPesquisa`, `UsuarioRanking` e exibição dos relatórios: suporte ao avatar.
- `firebase/firestore.rules`: permissões e limites.

Foram removidos a dependência Android do Firebase Storage, sua configuração em firebase.json e os arquivos de regras/testes de Storage que tinham sido adicionados para a foto.

## Validação

- 68 testes unitários Java passaram.
- 89 testes Android da suíte geral passaram, incluindo compressão de imagem detalhada até 50 KiB e registro do carregador no Glide.
- 4 testes Android adicionais contra Auth/Firestore locais passaram: persistência do Blob e referências, preservação de pontos, negativa para outra conta, substituição/versionamento e leitura real da imagem.
- 41 testes de regras/transações locais passaram, incluindo avatares, decks, Moodle e relatórios.
- 8 testes Node de cálculo dos relatórios passaram, incluindo a nova referência de avatar.
- Compilação e lint concluídos com 0 erros. Nenhuma chamada real à IA foi feita.

Referências técnicas oficiais: https://bumptech.github.io/glide/tut/custom-modelloader.html e https://firebase.google.com/docs/firestore/quotas

Esta migração trata da foto. As funções de processamento dos relatórios continuam sendo um recurso separado, sujeito aos requisitos de implantação de Cloud Functions.
