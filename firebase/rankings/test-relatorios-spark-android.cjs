// Integração Android + Auth/Firestore exclusivamente locais. Nunca lê credenciais reais.
const { initializeApp, deleteApp } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const { getFirestore, Timestamp } = require('firebase-admin/firestore');
const { spawnSync } = require('node:child_process');
const { writeFileSync } = require('node:fs');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const { initializeTestEnvironment } = require('@firebase/rules-unit-testing');
async function main() {
  if (!process.env.FIRESTORE_EMULATOR_HOST?.startsWith('127.0.0.1:')
      || !process.env.FIREBASE_AUTH_EMULATOR_HOST?.startsWith('127.0.0.1:')) throw Error('Exige emuladores locais.');
  const projectId = 'demo-classcards-relatorios-android';
  if (process.env.GCLOUD_PROJECT !== projectId)
    throw Error('Inicie os emuladores com --project demo-classcards-relatorios-android.');
  const [host, port] = process.env.FIRESTORE_EMULATOR_HOST.split(':');
  const ambiente = await initializeTestEnvironment({ projectId, firestore: { host, port: Number(port),
    rules: readFileSync(join(__dirname, '../firestore.rules'), 'utf8') } });
  await ambiente.clearFirestore();
  const app = initializeApp({ projectId }, 'relatorios-android');
  const db = getFirestore(app), auth = getAuth(app);
  try {
    for (const [conta, uid, cursos, prof] of [
      ['aluno', 'moodle-site-3', ['site:2'], []],
      ['professor', 'moodle-site-4', ['site:2'], ['site:2']],
      ['exaluno', 'moodle-site-5', [], []],
      ['outroprofessor', 'moodle-site-6', ['site:3'], ['site:3']]]) {
      try { await auth.deleteUser(uid); } catch (e) { if (e.code !== 'auth/user-not-found') throw e; }
      await auth.createUser({ uid, email: `${conta}@relatorios.invalid`, password: 'ClassCardsEmulator123!' });
      await auth.setCustomUserClaims(uid, { moodle: { cursos, prof } });
      await db.doc(`rankings/${uid}`).set({ nome: conta === 'aluno' ? 'Aluno Spark' : conta, fotoUrl: '' });
    }
    const deck = { cursoChave: 'site:2', titulo: 'Biologia', materia: 'Biologia', assunto: 'Células', verificadoPorProfessor: false };
    await db.doc('decksMoodle/moodle-deck-instrumentado').set({ ...deck, ownerId: 'moodle-site-3' });
    await db.doc('decksMoodle/moodle-deck-exaluno').set({ ...deck, ownerId: 'moodle-site-5' });
    await db.doc('decksMoodle/moodle-deck-exaluno/cards/card-exaluno').set({ pergunta: 'p', resposta: 'r' });
    const lote = db.batch();
    for (let i = 0; i < 251; i++) lote.set(db.doc(`relatoriosCursos/site:2/revisoes/paginacao-${i}`), {
      uid: 'moodle-site-3', eventoId: `antigo-${i}`, modo: 'tradicional', nome: 'Aluno Spark', fotoUrl: '',
      deckId: 'moodle-deck-instrumentado', flashcardId: `c${i % 10}`, rating: i % 2 === 0 ? 'AGAIN' : 'GOOD',
      createdAt: Timestamp.fromDate(new Date('2026-11-01T23:59:59Z')), assunto: 'Células', titulo: 'Biologia', verificado: false });
    // Não pode aparecer no intervalo do dia 01, nem ser confundida com a página seguinte.
    lote.set(db.doc('relatoriosCursos/site:2/revisoes/fora-do-periodo'), {
      uid: 'moodle-site-3', createdAt: Timestamp.fromDate(new Date('2026-11-02T00:00:00Z')) });
    await lote.commit();
    const adb = process.env.ADB_PATH || 'C:/Users/User/AppData/Local/Android/Sdk/platform-tools/adb.exe';
    const resultado = spawnSync(adb, ['-s', 'emulator-5554', 'shell', 'am', 'instrument', '-w',
      '-e', 'class', 'com.example.classcardsaplicao.data.RelatoriosSparkInstrumentedTest',
      '-e', 'relatoriosEmulator', 'true', 'com.example.classcardsaplicao.test/androidx.test.runner.AndroidJUnitRunner'],
      { encoding: 'utf8', timeout: 240000 });
    const texto = (resultado.stdout || '') + (resultado.stderr || '');
    process.stdout.write(texto);
    if (process.env.RELATORIOS_TEST_LOG) writeFileSync(process.env.RELATORIOS_TEST_LOG, texto);
    if (resultado.status !== 0 || !texto.includes('OK (9 tests)')) throw Error('Falha no teste Android de relatórios.');
  } finally { await db.terminate(); await deleteApp(app); await ambiente.cleanup(); }
}
main().catch(e => { console.error(e); process.exitCode = 1; });
