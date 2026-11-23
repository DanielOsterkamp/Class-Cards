# Relatórios do professor

## Entrega

- Menu lateral Relatórios: acesso por claim autenticada `moodle.prof`, restrito aos cursos em que a pessoa é professora.
- Dashboard com alunos ativos, cards distintos por aluno, revisões e percentual de Errei.
- Listas de alunos com dificuldade, alunos com mais atividade, turmas e assuntos. Cada lista mostra cinco linhas e permite consultar todos os resultados e seus detalhes.
- Filtros de turma, últimos 7/30 dias ou intervalo personalizado de até 93 dias, modo tradicional/dissertativo e decks verificados.
- Fotos, comparação de percentuais, contadores, data da última atividade e detalhes de assuntos/decks.
- Consulta aos matriculados do Moodle para identificar alunos sem revisões nos filtros. Falhas são apresentadas como lista indisponível/incompleta, sem presumir matrícula a partir de atividade.
- Assunto do deck: campo opcional, até 120 caracteres, apenas em decks Moodle. É gravado na criação/edição e exibido nos detalhes. Decks comuns não recebem esse campo.

## Ativar no ambiente real

O código está pronto, mas não executa publicação automática nem modifica dados de produção.

1. Na raiz do projeto, execute `npm ci --prefix firebase/relatorios` para instalar as dependências fixadas no lockfile.
2. Confira o projeto Firebase e a região `us-central1` em `firebase/relatorios/index.js`. Confirme as condições de implantação de Cloud Functions no seu projeto (incluindo o plano exigido pelo serviço).
3. Publique as regras e a codebase: `firebase deploy --only "firestore:rules,functions:relatorios" --project SEU_PROJECT_ID`.
4. No Moodle, confirme que o serviço utilizado pelo login mobile contém `core_enrol_get_enrolled_users` e que o professor possui permissão para visualizar os participantes. O aplicativo utiliza o token da própria pessoa; não utiliza conta administrativa.
5. Entre novamente no aplicativo com professor/aluno para renovar cursos e claims, se a configuração de papéis foi alterada.
6. Com um aluno matriculado, estude um deck Moodle; depois consulte o relatório como professor da mesma turma. O processamento é assíncrono: aguarde alguns instantes e toque em atualizar.

O módulo Moodle permanece sujeito à configuração existente do projeto: habilitado em debug e desligado em release. Para um lançamento com Moodle, a URL HTTPS e o interruptor de release precisam ser configurados pelo responsável pelo ambiente.

## Coleta e proteção

`GamificacaoRepository` acrescenta um snapshot `relatorio` somente aos eventos de decks Moodle. Copia curso, matéria, assunto, título e selo dentro da transação da revisão. As regras validam esses valores contra o deck e o card e exigem data do servidor. O dono que saiu da turma mantém o direito de revisar seu próprio deck, mas o processamento não o inclui como aluno matriculado. A pontuação, o FSRS e a conclusão de sessão mantêm as regras existentes.

As funções `relatorioTradicional` e `relatorioDissertativo` processam novos eventos. O servidor confirma a matrícula nas claims do Firebase Auth e exclui do relatório de alunos as revisões de quem tem papel de professor naquele curso. Perfil de apresentação vem de `rankings`; não são publicados e-mail, resposta do estudante ou histórico privado completo.

Os resumos ficam em `relatoriosCursos/{cursoChave}/resumos/{hash}`. Cada resumo reúne repetições de um aluno/card/dia/assunto/modo/selo. O recibo em `relatoriosProcessados` é gravado na mesma transação. Isso evita duplicação em novas entregas do mesmo evento e mantém contadores corretos com concorrência ou entrega fora de ordem.

Somente professores com a claim do curso leem os resumos. Clientes não escrevem resumos ou recibos. O dashboard não abre o histórico privado dos alunos.

## Significado dos indicadores

- Datas e fronteiras dos dias usam UTC, explicitado na tela.
- Cards distintos: cada par aluno/deck/card conta uma vez no intervalo, mesmo se repetido em vários dias ou modos. Ao somar turmas, isso representa cards estudados por aluno, não o tamanho do catálogo.
- Revisões: todas as revisões confirmadas, inclusive repetições.
- Errei: percentual ponderado, soma dos Errei dividida pelo total de revisões.
- Difícil: aparece separado, sem ser convertido automaticamente em erro.
- Mínimo de 10 revisões para incluir um aluno na lista de atenção. Turmas/assuntos com menos dados são identificados como Poucos dados.
- A classificação é a confirmada pelo estudante. Não é uma nota de prova.
- Alunos sem revisões: comparação da lista de matriculados com os filtros selecionados. Usar somente verificados ou um modo específico pode excluir atividade que aconteceu fora desse filtro.
- Matrículas são consultadas no momento da consulta; não representam a composição histórica da turma. A identificação pelo serviço padrão exclui os papéis Moodle `teacher`, `editingteacher` e `manager`; instituições com papéis personalizados devem conferir o retorno desse serviço.

## Histórico e escala

Não é feita reconstrução artificial de revisões antigas sem snapshot. Os dados de relatórios começam com a nova coleta e a ativação das funções. Assunto ausente aparece como Sem assunto informado. Edições posteriores ou exclusão do deck não reescrevem os dados históricos já registrados no evento.

O aplicativo consulta resumos diários paginados em lotes de 250 documentos, por turma, limitados ao período. Não baixa todas as tentativas individuais. Para instituições com grande volume de alunos/cards e períodos longos, o próximo passo de escala é criar agregações adicionais por aluno/assunto e consultas no servidor; a implementação atual preserva a contagem exata de cards distintos em intervalos personalizados.

Referências oficiais: https://firebase.google.com/docs/functions/firestore-events e https://firebase.google.com/docs/functions/get-started

## Validação

- Testes unitários Java para contagem distinta, repetição entre dias, ordenação, amostra mínima, assuntos por turma e detalhes por aluno.
- Testes Node dos metadados, classificações, dia UTC, separação por modo/selo e exclusão de professores.
- Testes de segurança no emulador e testes do processamento real em transações do Firestore, incluindo concorrência, repetição e exclusão do deck.
- Testes Android da tela com uma fonte de dados substituída, sem chamadas reais à IA.

Resultado da execução local: 62 testes unitários Java, 75 testes Android, 7 testes Node de cálculo e 32 testes de segurança/transações passaram. Os 9 testes específicos de relatórios foram repetidos após o ajuste de compatibilidade com o dono que saiu da turma e passaram novamente. Compilação e lint concluídos; lint com 0 erros (avisos existentes e de textos/estilos não impedem a compilação). Nenhuma chamada real à IA nem login real no Moodle foi executado. A tela foi conferida no emulador com dados de teste.
