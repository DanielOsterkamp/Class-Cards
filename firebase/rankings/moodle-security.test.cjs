// Testes das regras da integração Moodle (Fase 9: "duas bolhas").
// Exclusivamente o emulador local com projeto fictício; nunca utiliza credenciais ou banco reais.
const { before, beforeEach, after, test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const { initializeTestEnvironment, assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const { doc, collection, query, where, getDoc, getDocs, setDoc, updateDoc, deleteDoc, writeBatch, serverTimestamp } = require('firebase/firestore');

const projectId = 'demo-classcards-moodle';
assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Execute pelo Firebase emulators:exec.');

const SITE = 'site01';
const LPI = `${SITE}:2`;
const BDI = `${SITE}:3`;
// Contas como o plugin do Moodle as cria (uid "moodle-<site>-<userid>" + claims com as chaves dos cursos).
const ALUNA_LPI = { uid: `moodle-${SITE}-3`, claims: { moodle: { site: SITE, userid: 3, cursos: [LPI], prof: [] } } };
const PROF_LPI = { uid: `moodle-${SITE}-4`, claims: { moodle: { site: SITE, userid: 4, cursos: [LPI], prof: [LPI] } } };
const ALUNO_BDI = { uid: `moodle-${SITE}-5`, claims: { moodle: { site: SITE, userid: 5, cursos: [BDI], prof: [] } } };
const DE_FORA = { uid: 'contaEmail', claims: {} };

let env;
before(async () => {
  const [host, port] = process.env.FIRESTORE_EMULATOR_HOST.split(':');
  assert.ok(host === '127.0.0.1' || host === 'localhost');
  env = await initializeTestEnvironment({ projectId, firestore: {
    host, port: Number(port), rules: readFileSync(join(__dirname, '..', 'firestore.rules'), 'utf8')
  }});
});
after(async () => { if (env) await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async ctx => {
    const db = ctx.firestore();
    const lote = writeBatch(db);
    // Baralho de curso (LP-I) criado pela aluna, e um baralho de BD-I criado por outro aluno.
    lote.set(doc(db, 'decksMoodle/moodle-d1'), { ownerId: ALUNA_LPI.uid, titulo: 'Laços', tituloBusca: 'lacos', cursoChave: LPI });
    lote.set(doc(db, 'decksMoodle/moodle-d1/cards/c1'), { pergunta: 'P1', resposta: 'R1' });
    lote.set(doc(db, 'decksMoodle/moodle-d2'), { ownerId: ALUNO_BDI.uid, titulo: 'Joins', tituloBusca: 'joins', cursoChave: BDI });
    // Baralho da bolha de fora, como os que já existem hoje.
    lote.set(doc(db, 'decks/publico1'), { ownerId: DE_FORA.uid, titulo: 'Biologia', tituloBusca: 'biologia' });
    // Resumo de gamificação de cada conta (a regra do ranking confere os valores publicados contra ele).
    for (const conta of [ALUNA_LPI, PROF_LPI, ALUNO_BDI, DE_FORA]) lote.set(doc(db, `users/${conta.uid}/gamificacao/resumo`), RESUMO);
    await lote.commit();
  });
});

const db = conta => env.authenticatedContext(conta.uid, conta.claims).firestore();

// Ranking (2c): o campo opcional cursosChave só pode ter cursos da própria carteirinha.
const RESUMO = { xp: 10, ofensiva: 1, cardsEstudados: 2, ultimoDiaEstudo: '2026-11-17', fusoHorario: 'America/Sao_Paulo' };
const ranking = (conta, extra = {}) => setDoc(doc(db(conta), `rankings/${conta.uid}`), {
  uid: conta.uid, nome: 'Teste', nomeBusca: 'teste', fotoUrl: '', ...RESUMO, atualizadoEm: serverTimestamp(), ...extra });

test('ranking: conta Moodle publica os próprios cursos; curso alheio é recusado', async () => {
  await assertSucceeds(ranking(ALUNA_LPI, { cursosChave: [LPI] }));
  await assertFails(ranking(ALUNA_LPI, { cursosChave: [LPI, BDI] }));
  await assertFails(ranking(ALUNA_LPI, { cursosChave: 'nao-e-lista' }));
});

test('ranking: conta de fora publica como antes e não pode inventar cursos', async () => {
  await assertSucceeds(ranking(DE_FORA));
  await assertFails(ranking(DE_FORA, { cursosChave: [LPI] }));
});

test('membro do curso lê o baralho e os cards do curso', async () => {
  await assertSucceeds(getDoc(doc(db(PROF_LPI), 'decksMoodle/moodle-d1')));
  await assertSucceeds(getDocs(collection(db(PROF_LPI), 'decksMoodle/moodle-d1/cards')));
});

test('quem não é do curso não lê o baralho nem os cards', async () => {
  await assertFails(getDoc(doc(db(ALUNO_BDI), 'decksMoodle/moodle-d1')));
  await assertFails(getDocs(collection(db(ALUNO_BDI), 'decksMoodle/moodle-d1/cards')));
  await assertFails(getDoc(doc(db(DE_FORA), 'decksMoodle/moodle-d1')));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), 'decksMoodle/moodle-d1')));
});

test('pesquisa da bolha Moodle filtrada pelos próprios cursos é permitida; sem filtro é recusada', async () => {
  await assertSucceeds(getDocs(query(collection(db(PROF_LPI), 'decksMoodle'), where('cursoChave', 'in', [LPI]))));
  await assertFails(getDocs(query(collection(db(PROF_LPI), 'decksMoodle'), where('cursoChave', 'in', [LPI, BDI]))));
  await assertFails(getDocs(collection(db(PROF_LPI), 'decksMoodle')));
});

test('o dono lista os próprios baralhos e o perfil de outro mostra só os cursos em comum', async () => {
  await assertSucceeds(getDocs(query(collection(db(ALUNA_LPI), 'decksMoodle'), where('ownerId', '==', ALUNA_LPI.uid))));
  await assertSucceeds(getDocs(query(collection(db(PROF_LPI), 'decksMoodle'),
    where('ownerId', '==', ALUNA_LPI.uid), where('cursoChave', 'in', [LPI]))));
  await assertFails(getDocs(query(collection(db(PROF_LPI), 'decksMoodle'), where('ownerId', '==', ALUNO_BDI.uid))));
});

test('cria baralho só no próprio nome, com id moodle-* e num curso em que está inscrito', async () => {
  await assertSucceeds(setDoc(doc(db(ALUNA_LPI), 'decksMoodle/moodle-novo'),
    { ownerId: ALUNA_LPI.uid, titulo: 'Novo', tituloBusca: 'novo', cursoChave: LPI }));
  await assertFails(setDoc(doc(db(ALUNA_LPI), 'decksMoodle/moodle-outro-curso'),
    { ownerId: ALUNA_LPI.uid, titulo: 'X', tituloBusca: 'x', cursoChave: BDI }));
  await assertFails(setDoc(doc(db(ALUNA_LPI), 'decksMoodle/semprefixo'),
    { ownerId: ALUNA_LPI.uid, titulo: 'X', tituloBusca: 'x', cursoChave: LPI }));
  await assertFails(setDoc(doc(db(ALUNA_LPI), 'decksMoodle/moodle-em-nome-de-outro'),
    { ownerId: PROF_LPI.uid, titulo: 'X', tituloBusca: 'x', cursoChave: LPI }));
  await assertFails(setDoc(doc(db(DE_FORA), 'decksMoodle/moodle-de-fora'),
    { ownerId: DE_FORA.uid, titulo: 'X', tituloBusca: 'x', cursoChave: LPI }));
});

test('cria baralho e cards no mesmo lote (como o app faz)', async () => {
  const banco = db(ALUNA_LPI);
  const lote = writeBatch(banco);
  lote.set(doc(banco, 'decksMoodle/moodle-lote'), { ownerId: ALUNA_LPI.uid, titulo: 'Lote', tituloBusca: 'lote', cursoChave: LPI });
  lote.set(doc(banco, 'decksMoodle/moodle-lote/cards/c1'), { pergunta: 'P', resposta: 'R' });
  await assertSucceeds(lote.commit());
});

test('só o dono edita e exclui; não pode mover para um curso que não é dele', async () => {
  await assertSucceeds(updateDoc(doc(db(ALUNA_LPI), 'decksMoodle/moodle-d1'), { titulo: 'Laços 2' }));
  await assertFails(updateDoc(doc(db(PROF_LPI), 'decksMoodle/moodle-d1'), { titulo: 'Invadido' }));
  await assertFails(updateDoc(doc(db(ALUNA_LPI), 'decksMoodle/moodle-d1'), { cursoChave: BDI }));
  await assertFails(deleteDoc(doc(db(PROF_LPI), 'decksMoodle/moodle-d1')));
  await assertSucceeds(deleteDoc(doc(db(ALUNA_LPI), 'decksMoodle/moodle-d1')));
});

test('a bolha de fora continua como antes (regras de decks não mudaram)', async () => {
  await assertSucceeds(getDoc(doc(db(DE_FORA), 'decks/publico1')));
  await assertSucceeds(getDocs(query(collection(db(DE_FORA), 'decks'), where('tituloBusca', '>=', 'bio'))));
  await assertSucceeds(setDoc(doc(db(DE_FORA), 'decks/novoPublico'), { ownerId: DE_FORA.uid, titulo: 'Química' }));
});
