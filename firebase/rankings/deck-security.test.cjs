const { before, beforeEach, after, test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const { initializeTestEnvironment, assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const { doc, collection, query, limit, getDoc, getDocs, setDoc, updateDoc, deleteDoc, writeBatch } = require('firebase/firestore');

// Exclusivamente o emulador local com projeto fictício; nunca utiliza credenciais ou banco reais.
const projectId = 'demo-classcards-decks';
assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Execute pelo Firebase emulators:exec.');
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
    lote.set(doc(db, 'decks/d1'), { ownerId: 'dono', titulo: 'Biologia', quantidadeCards: 2 });
    lote.set(doc(db, 'decks/d1/cards/c1'), { pergunta: 'Pergunta 1', resposta: 'Resposta 1' });
    lote.set(doc(db, 'decks/d1/cards/c2'), { pergunta: 'Pergunta 2', resposta: 'Resposta 2' });
    lote.set(doc(db, 'users/dono/gamificacao/resumo'), { xp: 70, cardsEstudados: 2 });
    lote.set(doc(db, 'users/dono/progressoFlashcards/c1'), { repeticoes: 1 });
    await lote.commit();
  });
});
const dbDe = uid => env.authenticatedContext(uid).firestore();

test('outro usuário pode ler deck e cards para estudar', async () => {
  const db = dbDe('visitante');
  await assertSucceeds(getDoc(doc(db, 'decks/d1')));
  await assertSucceeds(getDocs(collection(db, 'decks/d1/cards')));
});
test('visitante e conta desconectada não podem excluir o deck', async () => {
  await assertFails(deleteDoc(doc(dbDe('visitante'), 'decks/d1')));
  await assertFails(deleteDoc(doc(env.unauthenticatedContext().firestore(), 'decks/d1')));
});
test('visitante não pode apagar cards nem iniciar o marcador de exclusão', async () => {
  const db = dbDe('visitante');
  await assertFails(deleteDoc(doc(db, 'decks/d1/cards/c1')));
  await assertFails(updateDoc(doc(db, 'decks/d1'), { excluindo: true }));
});
test('criar deck e cards no mesmo lote continua funcionando', async () => {
  const db = dbDe('dono');
  const lote = writeBatch(db);
  lote.set(doc(db, 'decks/novo'), { ownerId: 'dono', titulo: 'Novo' });
  lote.set(doc(db, 'decks/novo/cards/novo'), { pergunta: 'Nova', resposta: 'Nova' });
  await assertSucceeds(lote.commit());
});
test('edição do dono preserva IDs e permite remover e adicionar cards', async () => {
  const db = dbDe('dono');
  const lote = writeBatch(db);
  lote.update(doc(db, 'decks/d1'), { titulo: 'Atualizado', quantidadeCards: 2 });
  lote.update(doc(db, 'decks/d1/cards/c1'), { resposta: 'Atualizada' });
  lote.delete(doc(db, 'decks/d1/cards/c2'));
  lote.set(doc(db, 'decks/d1/cards/c3'), { pergunta: 'IA', resposta: 'Resposta IA' });
  await assertSucceeds(lote.commit());
  assert.equal((await getDoc(doc(db, 'decks/d1/cards/c1'))).get('resposta'), 'Atualizada');
  assert.equal((await getDocs(collection(db, 'decks/d1/cards'))).size, 2);
});
test('exclusão do dono apaga os cards e o deck, preservando XP e progresso', async () => {
  const db = dbDe('dono');
  await assertSucceeds(updateDoc(doc(db, 'decks/d1'), { excluindo: true }));
  const lote = writeBatch(db);
  lote.delete(doc(db, 'decks/d1/cards/c1'));
  lote.delete(doc(db, 'decks/d1/cards/c2'));
  lote.delete(doc(db, 'decks/d1'));
  await assertSucceeds(lote.commit());
  assert.equal((await getDoc(doc(db, 'decks/d1'))).exists(), false);
  assert.equal((await getDocs(collection(db, 'decks/d1/cards'))).size, 0);
  assert.equal((await getDoc(doc(db, 'users/dono/gamificacao/resumo'))).get('xp'), 70);
  assert.equal((await getDoc(doc(db, 'users/dono/progressoFlashcards/c1'))).get('repeticoes'), 1);
});
test('marcador impede que uma edição concorrente crie cards durante a exclusão', async () => {
  const db = dbDe('dono');
  await updateDoc(doc(db, 'decks/d1'), { excluindo: true });
  await assertFails(setDoc(doc(db, 'decks/d1/cards/novo'), { pergunta: 'Nova' }));
  await assertFails(updateDoc(doc(db, 'decks/d1/cards/c1'), { resposta: 'Concorrente' }));
  await assertSucceeds(deleteDoc(doc(db, 'decks/d1/cards/c1')));
});
test('não permite recriar cards órfãos depois de excluir o deck', async () => {
  const db = dbDe('dono');
  await deleteDoc(doc(db, 'decks/d1/cards/c1'));
  await deleteDoc(doc(db, 'decks/d1/cards/c2'));
  await deleteDoc(doc(db, 'decks/d1'));
  await assertFails(setDoc(doc(db, 'decks/d1/cards/orfao'), { pergunta: 'Órfão' }));
});
test('lote não autorizado é recusado por inteiro', async () => {
  const db = dbDe('visitante');
  const lote = writeBatch(db);
  lote.delete(doc(db, 'decks/d1/cards/c1'));
  lote.delete(doc(db, 'decks/d1'));
  await assertFails(lote.commit());
  assert.equal((await getDoc(doc(dbDe('dono'), 'decks/d1/cards/c1'))).exists(), true);
});
test('deck grande é excluído em lotes sem deixar cards', async () => {
  await env.withSecurityRulesDisabled(async ctx => {
    const db = ctx.firestore();
    const lote = writeBatch(db);
    for (let i = 0; i < 405; i++) lote.set(doc(db, `decks/d1/cards/extra-${i}`), { pergunta: `P${i}` });
    await lote.commit();
  });
  const db = dbDe('dono');
  await updateDoc(doc(db, 'decks/d1'), { excluindo: true });
  let lotes = 0;
  while (true) {
    const cards = await getDocs(query(collection(db, 'decks/d1/cards'), limit(400)));
    const lote = writeBatch(db);
    cards.forEach(card => lote.delete(card.ref));
    const ultimo = cards.size < 400;
    if (ultimo) lote.delete(doc(db, 'decks/d1'));
    await assertSucceeds(lote.commit());
    lotes++;
    if (ultimo) break;
  }
  assert.equal(lotes, 2);
  assert.equal((await getDocs(collection(db, 'decks/d1/cards'))).size, 0);
  assert.equal((await getDoc(doc(db, 'decks/d1'))).exists(), false);
});
