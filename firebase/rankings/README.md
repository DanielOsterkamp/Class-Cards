# Migração administrativa opcional dos rankings

Este script inclui usuários antigos na coleção pública de ranking sem exigir que cada um abra a nova versão do aplicativo. Ele é uma ferramenta administrativa executada fora do Android.

**O arquivo foi criado para revisão. Não foi executado contra um projeto Firebase.**

## O que ele faz

Para cada documento `users/{uid}`, lê o resumo `gamificacao/resumo` e a coleção `progressoFlashcards`. Um card distinto conta se `repeticoes > 0` **ou** `ultimaRevisao > 0`, usando o ID do documento como identificador.

No modo `--apply`, grava na mesma transação:

- o resumo canônico com `xp`, `ofensiva`, `cardsEstudados`, `ultimoDiaEstudo`, `fusoHorario` e `versaoEstudos`; outros campos privados do resumo são preservados;
- `rankings/{uid}` com **somente** `uid`, `nome`, `fotoUrl`, `xp`, `ofensiva`, `cardsEstudados`, `ultimoDiaEstudo`, `fusoHorario` e `atualizadoEm` do servidor.

O script não cria revisões, não concede XP e não modifica os documentos de progresso. Não publica emails, respostas ou histórico de estudos. O nome e a foto vêm do perfil atual; se estiverem vazios ou ausentes, consulta o Firebase Authentication. Como alternativas finais, preserva valores públicos existentes ou usa `Estudante` e foto vazia.

## Preparação

Use uma versão de Node.js suportada pelo `firebase-admin` instalado. Em um terminal na pasta `firebase/rankings`:

```powershell
npm install --no-save firebase-admin
```

A autenticação usa **Application Default Credentials (ADC)** de uma conta com permissão para ler usuários e progresso no Firestore, consultar usuários no Firebase Authentication e, no modo de gravação, escrever resumo e ranking. Por exemplo, com o Google Cloud CLI instalado:

```powershell
gcloud auth application-default login
```

Entre com uma conta administrativa autorizada do projeto. O parâmetro `--project` é obrigatório: o script não escolhe automaticamente um projeto salvo pelo Firebase CLI.

Não copie credenciais administrativas, chaves de conta de serviço ou esta ferramenta para o aplicativo Android. O `google-services.json` do aplicativo não é uma credencial administrativa. Não coloque arquivos ADC ou chaves privadas no repositório.

## Simular sem gravar

Troque `SEU_PROJECT_ID` pelo ID real do projeto que deseja analisar:

```powershell
node .\migrate-rankings.mjs --project SEU_PROJECT_ID --timezone America/Sao_Paulo
```

Sem `--apply`, o script faz apenas leituras. A saída mostra somente contagens agregadas de usuários analisados, cards distintos, conflitos e falhas; não mostra dados pessoais nem lista contas individualmente. `gravados` deve permanecer `0`.

A simulação também verifica se é possível ler os dados e buscar os perfis necessários. Ela não comprova que a conta possui permissão para escrever, pois não executa gravações.

## Aplicar a migração

Depois de revisar o projeto, o resultado da simulação e o fuso assumido para dados antigos:

```powershell
node .\migrate-rankings.mjs --project SEU_PROJECT_ID --timezone America/Sao_Paulo --apply
```

`--apply` é a autorização explícita de gravação. A ferramenta publica um documento público por usuário e atualiza o contador canônico no resumo privado. O Admin SDK usa IAM e não as regras de acesso do cliente Android.

A aplicação pode ser repetida para sincronizar os dados. Ela recalcula o total de cards a partir do histórico existente e não soma novamente XP. Revise `falhas` e `falhasPorCodigo` no resultado; código de saída `1` significa que a execução não terminou inteiramente com sucesso. Documentos com contadores de tipo inválido ou inteiros que não podem ser representados com segurança não são convertidos silenciosamente.

## Concorrência e limite com clientes antigos

Antes de consultar os progressos, a ferramenta lê `versaoEstudos`. A transação lê novamente essa versão: se uma revisão confirmada pelo novo cliente mudou o valor, a contagem é descartada e refeita, até três tentativas. O script preserva a versão atual; uma migração não é um novo estudo. A transação também relê nome, foto e métricas para não publicar uma cópia anterior do perfil ou dos ganhos.

Isso depende de que os clientes novos incrementem `versaoEstudos` na mesma transação de toda revisão. **Clientes antigos que gravem progresso sem incrementar a versão não podem ser detectados por esse mecanismo**, inclusive gravações que estavam pendentes offline. Para uma migração definitiva, use uma janela sem gravações desses clientes ou exija a atualização e aguarde as gravações pendentes; depois execute novamente a migração.

Usuários sem `fusoHorario` salvo recebem o fuso fornecido por `--timezone` (padrão `America/Sao_Paulo`). Esse fuso é uma hipótese para dados legados e pode afetar o cálculo da ofensiva perto da virada do dia. Se o fuso já existir, ele é preservado. `ultimoDiaEstudo` não é inventado: permanece o existente ou vazio quando ausente.

## Verificação local sem acessar dados reais

```powershell
node --check .\migrate-rankings.mjs
```

Esse comando verifica apenas a sintaxe JavaScript. Para ver a ajuda, sem inicializar o Firebase:

```powershell
node .\migrate-rankings.mjs --help
```

## Referências

- [Configuração do Firebase Admin SDK e ADC](https://firebase.google.com/docs/admin/setup).
- [Consulta administrativa de usuários do Authentication](https://firebase.google.com/docs/auth/admin/manage-users).
- [Transações do Firestore](https://firebase.google.com/docs/firestore/manage-data/transactions).
