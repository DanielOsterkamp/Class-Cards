'use strict';

// Estes testes usam somente o emulador: nunca conectam ao projeto Firebase real.
const { before, beforeEach, after, test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('@firebase/rules-unit-testing');
const {
  doc,
  collection,
  setDoc,
  getDoc,
  getDocs,
  updateDoc,
  deleteDoc,
  writeBatch,
  runTransaction,
  serverTimestamp,
  Timestamp,
} = require('firebase/firestore');

const PROJECT_ID = 'demo-classcards-ranking';
const EMULATOR_HOST = '127.0.0.1';
const EMULATOR_PORT = 8087;
let environment;

before(async () => {
  // O prefixo demo e host/porta explícitos impedem que o teste use credenciais reais.
  assert.ok(PROJECT_ID.startsWith('demo-'));
  environment = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      host: EMULATOR_HOST,
      port: EMULATOR_PORT,
      rules: fs.readFileSync(path.resolve(__dirname, '../firestore.rules'), 'utf8'),
    },
  });
});

beforeEach(async () => {
  await environment.clearFirestore();
});

after(async () => {
  if (environment) await environment.cleanup();
});

function authenticated(uid = 'ana') {
  return environment.authenticatedContext(uid).firestore();
}

function canonical(overrides = {}) {
  return {
    xp: 40,
    ofensiva: 3,
    cardsEstudados: 8,
    ultimoDiaEstudo: '2026-10-24',
    fusoHorario: 'America/Sao_Paulo',
    ...overrides,
  };
}

function publicRanking(uid = 'ana', overrides = {}) {
  return {
    uid,
    nome: 'Ana',
    fotoUrl: 'https://example.com/avatar-ana.png',
    ...canonical(),
    atualizadoEm: serverTimestamp(),
    ...overrides,
  };
}

function summaryPath(uid = 'ana') {
  return `users/${uid}/gamificacao/resumo`;
}

async function seed(documents) {
  await environment.withSecurityRulesDisabled(async (context) => {
    const database = context.firestore();
    await Promise.all(Object.entries(documents).map(([documentPath, data]) =>
      setDoc(doc(database, documentPath), data)));
  });
}

test('usuário autenticado lê documentos e lista do ranking público', async () => {
  await seed({
    [summaryPath()]: canonical(),
    'rankings/ana': publicRanking(),
  });
  const bob = authenticated('bob');
  const document = await assertSucceeds(getDoc(doc(bob, 'rankings/ana')));
  assert.equal(document.data().nome, 'Ana');
  const list = await assertSucceeds(getDocs(collection(bob, 'rankings')));
  assert.equal(list.size, 1);
});

test('usuário anônimo não pode ler nem listar o ranking', async () => {
  await seed({ 'rankings/ana': publicRanking() });
  const anonymous = environment.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(anonymous, 'rankings/ana')));
  await assertFails(getDocs(collection(anonymous, 'rankings')));
});

test('ranking público não dá acesso aos dados privados de outro usuário', async () => {
  await seed({
    'users/ana': { nome: 'Ana', email: 'privado@example.com' },
    [summaryPath()]: canonical(),
    'users/ana/progressoFlashcards/card-1': { userId: 'ana', repeticoes: 2 },
  });
  const bob = authenticated('bob');
  await assertFails(getDoc(doc(bob, 'users/ana')));
  await assertFails(getDoc(doc(bob, summaryPath())));
  await assertFails(getDoc(doc(bob, 'users/ana/progressoFlashcards/card-1')));
});

test('batch permite criar resumo canônico e ranking do próprio usuário com getAfter', async () => {
  const database = authenticated();
  const batch = writeBatch(database);
  batch.set(doc(database, summaryPath()), canonical());
  batch.set(doc(database, 'rankings/ana'), publicRanking());
  await assertSucceeds(batch.commit());
  const result = await getDoc(doc(database, 'rankings/ana'));
  assert.equal(result.data().xp, 40);
  assert.ok(result.data().atualizadoEm instanceof Timestamp);
});

test('projeção pública pode ser atualizada após confirmar a transação privada', async () => {
  const database = authenticated();
  await setDoc(doc(database, summaryPath()), canonical({ xp: 0, cardsEstudados: 0 }));
  const event = doc(database, 'users/ana/revisoesTradicionais/tentativa-1');
  const progress = doc(database, 'users/ana/progressoFlashcards/card-1');
  const summary = doc(database, summaryPath());

  await assertSucceeds(runTransaction(database, async (transaction) => {
    const existingEvent = await transaction.get(event);
    const oldSummary = await transaction.get(summary);
    await transaction.get(progress);
    assert.equal(existingEvent.exists(), false);
    transaction.set(summary, { ...canonical(), xp: oldSummary.data().xp + 3, cardsEstudados: 1 });
    transaction.set(progress, { userId: 'ana', deckId: 'deck-1', repeticoes: 1, ultimaRevisao: 100 });
    transaction.set(event, { deckId: 'deck-1', flashcardId: 'card-1', rating: 'GOOD', xp: 3, createdAt: serverTimestamp() });
  }));

  await assertSucceeds(setDoc(doc(database, 'rankings/ana'), publicRanking('ana', { xp: 3, cardsEstudados: 1 })));
  assert.equal((await getDoc(event)).data().xp, 3);
  assert.equal((await getDoc(doc(database, 'rankings/ana'))).data().cardsEstudados, 1);
});

test('falha de projeção pública não desfaz revisão privada já confirmada', async () => {
  const database = authenticated();
  await setDoc(doc(database, summaryPath()), canonical({ xp: 5 }));
  await setDoc(doc(database, 'users/ana/revisoesDissertativas/tentativa-2'), {
    rating: 'EASY', xp: 5, createdAt: serverTimestamp(),
  });
  await assertFails(setDoc(doc(database, 'rankings/ana'), publicRanking('ana', { xp: 999 })));
  assert.equal((await getDoc(doc(database, summaryPath()))).data().xp, 5);
  assert.equal((await getDoc(doc(database, 'users/ana/revisoesDissertativas/tentativa-2'))).data().rating, 'EASY');
});

test('usuário não pode criar ou alterar projeção de outro UID', async () => {
  await seed({ [summaryPath('bob')]: canonical(), 'rankings/bob': publicRanking('bob') });
  const database = authenticated('ana');
  await assertFails(setDoc(doc(database, 'rankings/bob'), publicRanking('bob')));
  await assertFails(setDoc(doc(database, 'rankings/carla'), publicRanking('carla')));
});

test('campo uid do documento precisa coincidir com o UID do caminho', async () => {
  await seed({ [summaryPath()]: canonical() });
  await assertFails(setDoc(doc(authenticated(), 'rankings/ana'), publicRanking('bob')));
});

test('projeção rejeita e-mail e qualquer campo privado ou extra', async () => {
  await seed({ [summaryPath()]: canonical() });
  const database = authenticated();
  await assertFails(setDoc(doc(database, 'rankings/ana'), publicRanking('ana', { email: 'privado@example.com' })));
  await assertFails(setDoc(doc(database, 'rankings/ana'), publicRanking('ana', { resposta: 'Minha resposta escrita' })));
});

test('projeção exige todos os campos públicos previstos', async () => {
  await seed({ [summaryPath()]: canonical() });
  const data = publicRanking();
  delete data.fotoUrl;
  await assertFails(setDoc(doc(authenticated(), 'rankings/ana'), data));
});

test('XP do ranking não pode divergir do resumo nem em um batch', async () => {
  const database = authenticated();
  const batch = writeBatch(database);
  batch.set(doc(database, summaryPath()), canonical());
  batch.set(doc(database, 'rankings/ana'), publicRanking('ana', { xp: 41 }));
  await assertFails(batch.commit());
  assert.equal((await getDoc(doc(database, summaryPath()))).exists(), false);
});

test('ofensiva, cards, última data e fuso precisam refletir o resumo', async () => {
  await seed({ [summaryPath()]: canonical() });
  const database = authenticated();
  for (const wrongValue of [
    { ofensiva: 99 }, { cardsEstudados: 99 },
    { ultimoDiaEstudo: '2026-10-23' }, { fusoHorario: 'UTC' },
  ]) {
    await assertFails(setDoc(doc(database, 'rankings/ana'), publicRanking('ana', wrongValue)));
  }
});

test('ranking não pode ser publicado sem resumo canônico', async () => {
  await assertFails(setDoc(doc(authenticated(), 'rankings/ana'), publicRanking()));
});

test('atualização do ranking usa horário do servidor e não horário arbitrário do cliente', async () => {
  await seed({ [summaryPath()]: canonical() });
  const database = authenticated();
  await assertFails(setDoc(doc(database, 'rankings/ana'), publicRanking('ana', { atualizadoEm: Timestamp.fromMillis(0) })));
  await assertSucceeds(setDoc(doc(database, 'rankings/ana'), publicRanking()));
  await assertSucceeds(updateDoc(doc(database, 'rankings/ana'), { nome: 'Ana atualizada', atualizadoEm: serverTimestamp() }));
});

test('somente fotos HTTPS ou avatar vazio podem ser publicados', async () => {
  await seed({ [summaryPath()]: canonical() });
  const database = authenticated();
  await assertFails(setDoc(doc(database, 'rankings/ana'), publicRanking('ana', { fotoUrl: 'http://example.com/avatar.png' })));
  await assertSucceeds(setDoc(doc(database, 'rankings/ana'), publicRanking('ana', { fotoUrl: '' })));
});

test('registros de revisão podem ser criados e lidos, mas não alterados nem excluídos', async () => {
  const database = authenticated();
  for (const name of ['revisoesTradicionais', 'revisoesDissertativas']) {
    const event = doc(database, `users/ana/${name}/tentativa-fixa`);
    await assertSucceeds(setDoc(event, { rating: 'GOOD', xp: 3, createdAt: serverTimestamp() }));
    await assertSucceeds(getDoc(event));
    await assertFails(updateDoc(event, { rating: 'EASY', xp: 5 }));
    await assertFails(deleteDoc(event));
    const duplicate = await assertSucceeds(runTransaction(database, async (transaction) => {
      const existing = await transaction.get(event);
      return existing.exists() ? 'já confirmado' : 'inexistente';
    }));
    assert.equal(duplicate, 'já confirmado');
  }
});

test('usuário não exclui sua projeção pública pelo cliente', async () => {
  await seed({ [summaryPath()]: canonical(), 'rankings/ana': publicRanking() });
  await assertFails(deleteDoc(doc(authenticated(), 'rankings/ana')));
});

test('regras anteriores de decks e progresso do proprietário continuam válidas', async () => {
  const ana = authenticated();
  const bob = authenticated('bob');
  const deck = doc(ana, 'decks/deck-1');
  await assertSucceeds(setDoc(deck, { ownerId: 'ana', titulo: 'Deck de teste' }));
  await assertSucceeds(getDoc(doc(bob, 'decks/deck-1')));
  await assertSucceeds(updateDoc(deck, { titulo: 'Deck editado' }));
  await assertFails(updateDoc(doc(bob, 'decks/deck-1'), { titulo: 'Alteração indevida' }));
  await assertSucceeds(setDoc(doc(ana, 'decks/deck-1/cards/card-1'), { pergunta: 'Pergunta', resposta: 'Resposta' }));
  const progress = doc(ana, 'users/ana/progressoFlashcards/card-1');
  await assertSucceeds(setDoc(progress, { userId: 'ana', deckId: 'deck-1', repeticoes: 1 }));
  await assertSucceeds(updateDoc(progress, { repeticoes: 2 }));
  await assertFails(updateDoc(doc(bob, 'users/ana/progressoFlashcards/card-1'), { repeticoes: 99 }));
  await assertSucceeds(deleteDoc(progress));
  await assertSucceeds(deleteDoc(deck));
});
