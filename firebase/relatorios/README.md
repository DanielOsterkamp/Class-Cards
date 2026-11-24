# Relatórios do professor no plano Spark

Os relatórios são calculados pelo aplicativo Android. Não é necessário publicar Cloud Functions, contratar o Blaze ou instalar outro aplicativo.

## Entrega

- Menu lateral Relatórios: acesso por claim autenticada `moodle.prof`, restrito aos cursos em que a pessoa é professora.
- Dashboard com alunos ativos, cards distintos por aluno, revisões e percentual de Errei.
- Listas de alunos com dificuldade, alunos com mais atividade, turmas e assuntos. Cada lista mostra cinco linhas e permite consultar todos os resultados e seus detalhes.
- Filtros de turma, últimos 7/30 dias ou intervalo personalizado de até 93 dias, modo tradicional/dissertativo e decks verificados.
- Fotos, comparação de percentuais, contadores, data da última atividade e detalhes de assuntos/decks.
- Consulta aos matriculados do Moodle para identificar alunos sem revisões nos filtros. Falhas são apresentadas como lista indisponível/incompleta, sem presumir matrícula a partir de atividade.
- Assunto do deck: campo opcional, até 120 caracteres, apenas em decks Moodle. É gravado na criação/edição e exibido nos detalhes. Decks comuns não recebem esse campo.

## Ativar no ambiente real

1. Compare `firebase/firestore.rules` com as regras atualmente publicadas. Preserve regras adicionais que existam no projeto compartilhado. As novas regras de relatório devem ser publicadas junto com as alterações nas regras de criação de revisões; somente abrir a coleção não é suficiente.
2. Publique **apenas as regras do Firestore** pelo Console ou, após conferir o projeto, com `firebase deploy --only firestore:rules --project SEU_PROJECT_ID`. Isso não exige Cloud Functions.
3. Instale esta versão do aplicativo nos aparelhos de aluno e professor. Clientes antigos continuam estudando, mas não publicam os registros novos dos relatórios.
4. Entre pelo Moodle. O token precisa conter `moodle.cursos`; o professor precisa ter a turma em `moodle.prof`. Se papéis ou matrículas mudaram, entre novamente para renovar o token.
5. Como aluno matriculado, revise um deck da coleção `decksMoodle`, com matéria/turma selecionada. Depois de a revisão ser salva, como professor da mesma turma abra Relatórios e toque em atualizar.
6. Para a lista de alunos sem atividade, o serviço Moodle precisa permitir `core_enrol_get_enrolled_users`. Uma falha nessa consulta não transforma ausência de matrícula em ausência de estudo: a tela mostra o aviso existente.

O Moodle continua dependendo da configuração já existente de URL, plugin e login. Não é necessário alterar o plugin para este novo fluxo de relatórios.

## Como os dados são gravados

`GamificacaoRepository` mantém a transação de progresso, FSRS, pontuação, ofensiva e recibo da tentativa. Somente para um aluno matriculado na turma de um deck Moodle, a mesma transação cria uma projeção compacta em:

`relatoriosCursos/{cursoChave}/revisoes/{id}`

O ID é estável para usuário, modo e tentativa. A projeção contém UID, nome/foto já públicos, identificação do deck/card, assunto, título, selo, classificação, modo e horário do servidor. Ela não inclui resposta digitada, pergunta/resposta do flashcard, e-mail, XP nem o progresso privado completo.

Cada projeção representa exatamente uma revisão; não recebe contadores editáveis. O recibo privado aponta para sua projeção e a projeção aponta para o recibo. As regras exigem o vínculo, a classificação, o contexto e a data correspondentes. Novas tentativas são publicadas junto com o recibo; falha em qualquer gravação desfaz toda a transação. Reenviar uma tentativa confirmada recupera o recibo existente, sem outra revisão ou XP.

As projeções são imutáveis para clientes: aluno e professor não podem editar ou apagar. Somente professores da turma leem ou listam esses documentos. O professor continua sem permissão de leitura dos históricos privados em `users/{uid}`.

Contas comuns, professores estudando na própria turma e donos que já saíram da turma não publicam atividade de aluno. O dono mantém o direito anterior de revisar seu deck. Os papéis são conferidos nas claims autenticadas; campos editáveis do perfil não concedem acesso.

O processamento anterior em `firebase/relatorios/*.cjs` foi preservado como legado e para regressão. A entrada `functions` foi retirada de `firebase.json`. O processador antigo ignora eventos com `relatorioPublicoId`. Não execute deploy das Functions para ativar esta versão.

## Cálculo e consultas

`RelatoriosRepository` consulta as projeções das turmas do professor por horário do servidor, em páginas de 250 documentos, limitadas ao intervalo escolhido. A consulta usa apenas `createdAt`, sem exigir um índice composto adicional. O último dia é incluído inteiro por um limite final exclusivo em UTC. O cursor usa o documento completo para não perder registros com o mesmo horário.

O aplicativo monta os indicadores com `ResumoRelatorio`: quantidade de revisões, percentual ponderado de Errei, cards distintos por aluno, alunos com dificuldade, atividade, turmas e assuntos. As listas, filtros de modo/selo, detalhes e visual atual são preservados. Filtros de modo e selo reaproveitam os dados já consultados. O período máximo continua sendo 93 dias.

- Cards distintos: um par aluno/deck/card conta uma vez no período, mesmo com repetições e nos dois modos.
- Revisões: cada tentativa confirmada conta uma vez.
- Errei: soma dos Errei dividida pelo total de revisões; Difícil aparece separado.
- Atenção: exige pelo menos 10 revisões e ocorrência de Errei ou Difícil.
- Assuntos: somente os dos decks vinculados a uma turma Moodle; o mesmo assunto de turmas diferentes permanece separado.
- Dias e períodos: UTC, conforme indicado na tela.
- As classificações são as confirmadas pelo estudante, não uma nota de prova.

## Histórico, limites e custo

Não é feita reconstrução automática de revisões anteriores à atualização. Estudar novamente com a nova versão produz novos registros. Os agregados antigos em `resumos` e recibos em `relatoriosProcessados` não são apagados, mas não são somados aos registros novos para evitar duplicidade.

O Spark continua sujeito às cotas do Firestore. Cada revisão elegível acrescenta uma gravação compacta, leitura do perfil público e as leituras de validação das regras. Consultar relatórios lê as projeções correspondentes ao período; a paginação evita consultas sem limite de página, mas o total de leituras cresce com as revisões. Em turmas grandes ou períodos extensos, prefira períodos menores e acompanhe o uso no Console. Esta implementação não promete capacidade ilimitada nem migração automática para o Blaze.

## Validação local

- Testes Java da elegibilidade, identidade da tentativa, projeção sem dados privados e cálculos dos indicadores.
- Testes de regras reais no Firestore Emulator para atomicidade, repetição/concorrência, acesso por curso, imutabilidade, perfil, datas e bloqueio de campos privados ou contadores forjados.
- Testes Android contra Auth/Firestore locais: gravação real nos dois modos, leitura real pelo professor, paginação, exclusão de professor/ex-aluno/estudo comum e cancelamento de consultas.
- Os testes usam projetos `demo-*`, sem contas reais, IA paga ou Functions.

Para executar a integração Android, suba Auth e Firestore com as regras atuais e com `--project demo-classcards-relatorios-android`. Execute `node firebase/rankings/test-relatorios-spark-android.cjs` com `FIRESTORE_EMULATOR_HOST` e `FIREBASE_AUTH_EMULATOR_HOST` apontando para `127.0.0.1`. O teste cria somente contas fictícias do emulador. O Android Emulator precisa estar em execução e os APKs debug e androidTest instalados. O projeto do emulador Auth precisa corresponder ao configurado no teste; o script recusa um projeto diferente.

Resultado em 23/11/2026: 73 testes Java, 89 testes Android de regressão, 61 testes de regras/transações, 8 testes Node de cálculo e 9 integrações Android reais com Auth/Firestore locais passaram (240 testes no total). Compilação debug e APK de testes passaram; lint sem erros. As integrações incluem duas confirmações concorrentes da mesma tentativa, recuperação do recibo sem XP duplicado e uma falha de permissão que não pode ser convertida em sucesso.

Referências: https://firebase.google.com/docs/firestore/manage-data/transactions e https://firebase.google.com/docs/firestore/security/rules-fields
