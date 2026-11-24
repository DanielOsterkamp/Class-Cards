const { before, beforeEach, after, test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const { createHash } = require('node:crypto');
const { initializeTestEnvironment, assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const { doc, collection, getDoc, getDocs, setDoc, updateDoc, deleteDoc, writeBatch,
        query, where, orderBy, limit, startAfter, Timestamp, serverTimestamp } = require('firebase/firestore');

const projectId = 'demo-classcards-relatorios-spark';
const aluno = 'moodle-site-3', prof = 'moodle-site-4';
const claims = { moodle: { cursos: ['site:2'], prof: [] } };
const metadados = { cursoChave: 'site:2', materia: 'Biologia', assunto: 'Células', titulo: 'Deck', verificado: false };
let env;
const db = (uid = aluno, token = claims) => env.authenticatedContext(uid, token).firestore();
const professor = (curso = 'site:2') => db(prof, { moodle: { cursos: [curso], prof: [curso] } });
const id = (tentativa, modo = 'tradicional') => createHash('sha256').update(`${aluno}:${modo}:${tentativa}`).digest('hex');
const refPublica = (d, identificador) => doc(d, `relatoriosCursos/site:2/revisoes/${identificador}`);
function preparar(d, tentativa = 'a', modo = 'tradicional', rating = 'AGAIN') {
  const publicoId = id(tentativa, modo);
  const colecao = modo === 'tradicional' ? 'revisoesTradicionais' : 'revisoesDissertativas';
  const privado = doc(d, `users/${aluno}/${colecao}/${tentativa}`), publico = refPublica(d, publicoId);
  const evento = { deckId: 'moodle-deck', flashcardId: 'card', rating, createdAt: serverTimestamp(),
                   relatorio: metadados, relatorioPublicoId: publicoId };
  const dados = { uid: aluno, eventoId: tentativa, modo, nome: 'Ana', fotoUrl: '', deckId: evento.deckId,
    flashcardId: 'card', rating, createdAt: serverTimestamp(), assunto: metadados.assunto,
    titulo: 'Deck', verificado: false };
  return { privado, publico, evento, dados };
}
function publicar(d, tentativa, modo, rating, mudar = () => {}) {
  const p = preparar(d, tentativa, modo, rating); mudar(p);
  const lote = writeBatch(d); lote.set(p.privado, p.evento); lote.set(p.publico, p.dados); return lote.commit();
}
before(async () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST?.startsWith('127.0.0.1:'), 'Somente emulador local.');
  const [host, port] = process.env.FIRESTORE_EMULATOR_HOST.split(':');
  env = await initializeTestEnvironment({ projectId, firestore: { host, port: Number(port),
    rules: readFileSync(join(__dirname, '../firestore.rules'), 'utf8') } });
});
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async c => {
    const d = c.firestore();
    await setDoc(doc(d, 'decksMoodle/moodle-deck'), { ownerId: aluno, ...metadados, verificadoPorProfessor: false });
    await setDoc(doc(d, 'decksMoodle/moodle-deck/cards/card'), { pergunta: 'p', resposta: 'privada' });
    await setDoc(doc(d, `rankings/${aluno}`), { nome: 'Ana', fotoUrl: '' });
  });
});
after(async () => { await env?.cleanup(); });

test('os quatro ratings publicam em ambos os modos na mesma operação', async () => {
  for (const modo of ['tradicional', 'dissertativo']) for (const rating of ['AGAIN', 'HARD', 'GOOD', 'EASY'])
    await assertSucceeds(publicar(db(), `${modo}-${rating}`, modo, rating));
  const linhas = await getDocs(collection(professor(), 'relatoriosCursos/site:2/revisoes'));
  assert.equal(linhas.size, 8);
  assert.equal(linhas.docs.filter(d => d.get('rating') === 'AGAIN').length, 2);
});
test('somente professor da turma consulta projeções; nenhum acesso ao histórico privado', async () => {
  await publicar(db(), 'a');
  await assertSucceeds(getDocs(collection(professor(), 'relatoriosCursos/site:2/revisoes')));
  for (const d of [db(), professor('site:3'), env.unauthenticatedContext().firestore()]) {
    await assertFails(getDoc(refPublica(d, id('a'))));
    await assertFails(getDocs(collection(d, 'relatoriosCursos/site:2/revisoes')));
  }
  await assertFails(getDoc(doc(professor(), `users/${aluno}/revisoesTradicionais/a`)));
  await assertFails(getDocs(collection(professor(), `users/${aluno}/progressoFlashcards`)));
});
test('aluno não publica uma revisão de outro usuário', async () => {
  await assertFails(publicar(db('moodle-site-99'), 'a'));
});
test('professor não publica atividades como aluno na própria turma', async () => {
  await assertFails(publicar(db(aluno, { moodle: { cursos: ['site:2'], prof: ['site:2'] } }), 'a'));
});
test('professor em outra turma ainda pode publicar como aluno nesta turma', async () => {
  await assertSucceeds(publicar(db(aluno, { moodle: { cursos: ['site:2', 'site:3'], prof: ['site:3'] } }), 'a'));
});
test('quem saiu da turma pode revisar o próprio deck, mas não publica no relatório', async () => {
  await assertFails(publicar(db(aluno, {}), 'a'));
  const p = preparar(db(aluno, {}), 'sem-matricula'); delete p.evento.relatorioPublicoId;
  await assertSucceeds(setDoc(p.privado, p.evento));
});
test('projeção isolada e revisão com vínculo isolada são recusadas', async () => {
  const p = preparar(db());
  await assertFails(setDoc(p.publico, p.dados));
  await assertFails(setDoc(p.privado, p.evento));
});
test('metadados, card, turma, modo, classificação e perfil não podem ser forjados', async () => {
  const mudancas = [p => p.dados.assunto = 'Inventado', p => p.dados.titulo = 'Outro',
    p => p.dados.verificado = true, p => p.dados.flashcardId = 'outro', p => p.dados.rating = 'EASY',
    p => p.dados.modo = 'dissertativo', p => p.dados.nome = 'Outra pessoa', p => p.dados.fotoUrl = 'https://inventada',
    p => p.evento.relatorio = { ...metadados, cursoChave: 'site:3' },
    p => p.evento.relatorio = { ...metadados, assunto: 'Inventado' },
    p => { p.dados.flashcardId = p.evento.flashcardId = 'inexistente'; },
    p => { p.dados.rating = p.evento.rating = 'INVALIDO'; }];
  for (let i = 0; i < mudancas.length; i++) await assertFails(publicar(db(), `falso-${i}`, undefined, undefined, mudancas[i]));
});
test('dados privados, contadores arbitrários e campos extras são recusados', async () => {
  for (const campo of ['resposta', 'email', 'xp', 'revisoes', 'errei'])
    await assertFails(publicar(db(), campo, undefined, undefined, p => p.dados[campo] = 999));
});
test('data da projeção vem do servidor e não aceita retroagir nem adiantar', async () => {
  for (const data of [new Date('2020-01-01'), new Date('2099-01-01')]) {
    await assertFails(publicar(db(), data.getFullYear().toString(), undefined, undefined,
      p => { p.dados.createdAt = p.evento.createdAt = Timestamp.fromDate(data); }));
  }
});
test('revisão antiga não pode ser republicada, nem com outro identificador', async () => {
  const p = preparar(db()); delete p.evento.relatorioPublicoId;
  await setDoc(p.privado, p.evento);
  await assertFails(setDoc(p.publico, p.dados));
  await assertFails(updateDoc(p.privado, { relatorioPublicoId: id('a') }));
});
test('uma tentativa só produz uma projeção e o vínculo não pode apontar para outra tentativa', async () => {
  const d = db(), p = preparar(d); const lote = writeBatch(d);
  lote.set(p.privado, p.evento); lote.set(p.publico, p.dados);
  lote.set(refPublica(d, id('copia')), p.dados);
  await assertFails(lote.commit());
  await assertFails(publicar(d, 'outro', undefined, undefined, p => p.dados.eventoId = 'a'));
});
test('projeções são imutáveis para alunos e professores', async () => {
  await publicar(db(), 'a');
  for (const d of [db(), professor()]) {
    await assertFails(updateDoc(refPublica(d, id('a')), { rating: 'EASY' }));
    await assertFails(deleteDoc(refPublica(d, id('a'))));
  }
});
test('sem perfil publicado utiliza nome Estudante e foto vazia', async () => {
  await env.withSecurityRulesDisabled(c => deleteDoc(doc(c.firestore(), `rankings/${aluno}`)));
  await assertSucceeds(publicar(db(), 'a', undefined, undefined, p => p.dados.nome = 'Estudante'));
});
test('falha no relatório desfaz também o progresso e o evento privados', async () => {
  const d = db(), p = preparar(d); p.dados.rating = 'EASY'; const lote = writeBatch(d);
  lote.set(p.privado, p.evento); lote.set(p.publico, p.dados);
  lote.set(doc(d, `users/${aluno}/gamificacao/resumo`), { xp: 999 });
  await assertFails(lote.commit());
  assert.equal((await getDoc(p.privado)).exists(), false);
  assert.equal((await getDoc(doc(d, `users/${aluno}/gamificacao/resumo`))).exists(), false);
});
test('gravações concorrentes e reenvio não criam duas projeções da mesma tentativa', async () => {
  const resultados = await Promise.allSettled([publicar(db(), 'a'), publicar(db(), 'a')]);
  assert.equal(resultados.filter(r => r.status === 'fulfilled').length, 1);
  await assertFails(publicar(db(), 'a'));
  assert.equal((await getDocs(collection(professor(), 'relatoriosCursos/site:2/revisoes'))).size, 1);
});
test('exclusão do deck não apaga nem altera a revisão publicada', async () => {
  await publicar(db(), 'a');
  await env.withSecurityRulesDisabled(c => deleteDoc(doc(c.firestore(), 'decksMoodle/moodle-deck')));
  const dados = (await getDoc(refPublica(professor(), id('a')))).data();
  assert.equal(dados.assunto, 'Células'); assert.equal(dados.verificado, false);
});
test('deck comum não pode alimentar relatório de turma', async () => {
  await assertFails(publicar(db(), 'a', undefined, undefined, p => p.dados.deckId = p.evento.deckId = 'comum'));
});
test('consulta por período com paginação não perde documentos com mesmo timestamp', async () => {
  await env.withSecurityRulesDisabled(async c => {
    const d = c.firestore(), lote = writeBatch(d);
    for (let i = 0; i < 251; i++) lote.set(refPublica(d, String(i).padStart(4, '0')),
      { createdAt: Timestamp.fromDate(new Date('2026-11-22T23:59:59Z')), uid: aluno });
    lote.set(refPublica(d, 'depois'), { createdAt: Timestamp.fromDate(new Date('2026-11-23T00:00:00Z')), uid: aluno });
    await lote.commit();
  });
  const base = query(collection(professor(), 'relatoriosCursos/site:2/revisoes'),
    where('createdAt', '>=', new Date('2026-11-22T00:00:00Z')),
    where('createdAt', '<', new Date('2026-11-23T00:00:00Z')), orderBy('createdAt'));
  const pagina = await getDocs(query(base, limit(250)));
  const seguinte = await getDocs(query(base, startAfter(pagina.docs[249]), limit(250)));
  assert.equal(pagina.size, 250); assert.equal(seguinte.size, 1);
  assert.notEqual(pagina.docs[249].id, seguinte.docs[0].id);
});
