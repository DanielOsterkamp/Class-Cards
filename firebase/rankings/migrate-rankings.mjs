#!/usr/bin/env node

/**
 * Migração administrativa dos usuários legados para rankings/{uid}.
 *
 * Sem --apply, faz somente leituras e mostra totais agregados.
 * As credenciais são obtidas por Application Default Credentials (ADC),
 * nunca pelo google-services.json e nunca por uma chave embutida no código.
 */

const MAX_ATTEMPTS = 3;
const PAGE_SIZE = 200;
const DEFAULT_TIME_ZONE = 'America/Sao_Paulo';

class MigrationError extends Error {
  constructor(code) {
    super(code);
    this.code = code;
  }
}

class ConcurrentReviewError extends MigrationError {
  constructor() {
    super('concurrent-review');
  }
}

function help() {
  process.stdout.write(`Uso:
  node migrate-rankings.mjs --project <project-id> [--timezone <fuso>] [--apply]

Opções:
  --project   Projeto Firebase explícito; obrigatório.
  --timezone  Fuso assumido apenas para usuários sem fuso salvo.
              Padrão: ${DEFAULT_TIME_ZONE}.
  --apply     Autoriza gravar. Sem esta opção, somente simula a migração.
  --help      Mostra esta ajuda sem inicializar o Firebase.

Autenticação: Application Default Credentials de uma conta administrativa.
A saída contém somente contagens agregadas, sem dados dos usuários.
`);
}

function optionsFrom(args) {
  const options = { project: '', timeZone: DEFAULT_TIME_ZONE, apply: false };
  const seen = new Set();

  for (let index = 0; index < args.length; index++) {
    const flag = args[index];
    if (flag === '--help' || flag === '-h') return { help: true };
    if (!['--project', '--timezone', '--apply'].includes(flag) || seen.has(flag)) {
      throw new MigrationError('invalid-arguments');
    }
    seen.add(flag);
    if (flag === '--apply') {
      options.apply = true;
      continue;
    }
    const value = args[++index];
    if (!value || value.startsWith('--') || value.trim() !== value) {
      throw new MigrationError('invalid-arguments');
    }
    if (flag === '--project') options.project = value;
    else options.timeZone = value;
  }

  if (!options.project || !/^[a-z][a-z0-9-]{4,28}[a-z0-9]$/.test(options.project)) {
    throw new MigrationError('project-required-or-invalid');
  }
  validateTimeZone(options.timeZone);
  return options;
}

function validateTimeZone(zone) {
  try {
    new Intl.DateTimeFormat('pt-BR', { timeZone: zone }).format(0);
  } catch {
    throw new MigrationError('invalid-timezone');
  }
  return zone;
}

function counter(value) {
  if (value === undefined || value === null) return 0;
  // Não arredonda silenciosamente um inteiro grande que o JavaScript não consegue representar.
  if (typeof value !== 'number' || !Number.isSafeInteger(value)) {
    throw new MigrationError('invalid-counter');
  }
  return Math.max(0, value);
}

function nonEmptyText(value) {
  return typeof value === 'string' && value.trim() ? value.trim() : '';
}

function publicPhoto(value) {
  const photo = nonEmptyText(value);
  return photo.startsWith('https://') && photo.length <= 4096 ? photo : '';
}

function hasReview(data) {
  return (typeof data.repeticoes === 'number' && data.repeticoes > 0)
    || (typeof data.ultimaRevisao === 'number' && data.ultimaRevisao > 0);
}

function safeErrorCode(error) {
  const code = String(error?.code ?? 'unknown-error');
  // Não imprime mensagens do SDK: elas podem conter caminhos ou informações pessoais.
  return /^[a-zA-Z0-9_/-]{1,80}$/.test(code) ? code : 'unknown-error';
}

async function authFallback(auth, uid, profile) {
  if (nonEmptyText(profile.nome) && nonEmptyText(profile.fotoUrl)) return {};
  try {
    const user = await auth.getUser(uid);
    return { nome: nonEmptyText(user.displayName), fotoUrl: nonEmptyText(user.photoURL) };
  } catch (error) {
    if (error?.code === 'auth/user-not-found') return {};
    // Um erro de conexão/permissão não deve apagar uma foto ou nome com um fallback genérico.
    throw error;
  }
}

async function migrateUser({ db, auth, FieldValue, userDocument, options, totals }) {
  const uid = userDocument.id;
  const userRef = userDocument.ref;
  const summaryRef = userRef.collection('gamificacao').doc('resumo');
  const publicRef = db.collection('rankings').doc(uid);
  const fallback = await authFallback(auth, uid, userDocument.data() ?? {});

  for (let attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
    // A versão é lida ANTES da consulta dos progressos. O novo cliente incrementa
    // essa versão na mesma transação que confirma uma revisão.
    const before = await summaryRef.get();
    const expectedVersion = counter(before.get('versaoEstudos'));
    const progress = await userRef.collection('progressoFlashcards').get();
    const reviewedIds = new Set();
    for (const document of progress.docs) {
      if (hasReview(document.data())) reviewedIds.add(document.id);
    }
    const distinctCount = reviewedIds.size;

    try {
      const result = await db.runTransaction(async transaction => {
        // Todas as leituras ocorrem antes das gravações.
        const currentSummary = await transaction.get(summaryRef);
        const currentProfile = await transaction.get(userRef);
        const currentPublic = await transaction.get(publicRef);
        const version = counter(currentSummary.get('versaoEstudos'));
        if (version !== expectedVersion) throw new ConcurrentReviewError();

        const summary = currentSummary.data() ?? {};
        const profile = currentProfile.data() ?? {};
        const published = currentPublic.data() ?? {};
        const savedZone = nonEmptyText(summary.fusoHorario);
        const timeZone = savedZone ? validateTimeZone(savedZone) : options.timeZone;
        const lastDay = summary.ultimoDiaEstudo ?? '';
        if (typeof lastDay !== 'string') throw new MigrationError('invalid-study-date');

        const canonical = {
          xp: counter(summary.xp),
          ofensiva: counter(summary.ofensiva),
          cardsEstudados: distinctCount,
          ultimoDiaEstudo: lastDay,
          fusoHorario: timeZone,
          // A migração não é uma revisão: preserva a versão atual.
          versaoEstudos: version,
        };
        const publicData = {
          uid,
          nome: (nonEmptyText(profile.nome) || fallback.nome
            || nonEmptyText(published.nome) || 'Estudante').slice(0, 120),
          fotoUrl: publicPhoto(profile.fotoUrl) || publicPhoto(fallback.fotoUrl)
            || publicPhoto(published.fotoUrl),
          xp: canonical.xp,
          ofensiva: canonical.ofensiva,
          cardsEstudados: canonical.cardsEstudados,
          ultimoDiaEstudo: canonical.ultimoDiaEstudo,
          fusoHorario: canonical.fusoHorario,
          atualizadoEm: FieldValue.serverTimestamp(),
        };

        if (options.apply) {
          // A versão não mudou durante a consulta: o contador corresponde a esse histórico.
          // merge no resumo preserva campos privados adicionais; a projeção usa somente
          // os campos públicos exatos e remove eventual conteúdo indevido anterior.
          transaction.set(summaryRef, canonical, { merge: true });
          transaction.set(publicRef, publicData);
        }
        return { distinctCount };
      });
      totals.analisados++;
      totals.cardsDistintos += result.distinctCount;
      if (options.apply) totals.gravados++;
      return;
    } catch (error) {
      if (error instanceof ConcurrentReviewError || error?.code === 'concurrent-review') {
        totals.conflitos++;
        if (attempt + 1 < MAX_ATTEMPTS) continue;
        throw new MigrationError('concurrent-review-limit');
      }
      throw error;
    }
  }
}

async function main() {
  const options = optionsFrom(process.argv.slice(2));
  if (options.help) {
    help();
    return;
  }

  // Importação após a validação dos argumentos: --help não acessa o Firebase.
  const [{ initializeApp, applicationDefault, deleteApp }, { getFirestore, FieldPath, FieldValue }, { getAuth }] =
    await Promise.all([
      import('firebase-admin/app'),
      import('firebase-admin/firestore'),
      import('firebase-admin/auth'),
    ]);
  const app = initializeApp({ credential: applicationDefault(), projectId: options.project });
  const db = getFirestore(app);
  const auth = getAuth(app);
  const totals = {
    modo: options.apply ? 'apply' : 'dry-run',
    usuariosEncontrados: 0,
    analisados: 0,
    gravados: 0,
    cardsDistintos: 0,
    conflitos: 0,
    falhas: 0,
    falhasPorCodigo: {},
  };

  try {
    let lastDocument;
    while (true) {
      let query = db.collection('users').orderBy(FieldPath.documentId()).limit(PAGE_SIZE);
      if (lastDocument) query = query.startAfter(lastDocument);
      const page = await query.get();
      if (page.empty) break;
      totals.usuariosEncontrados += page.size;
      for (const userDocument of page.docs) {
        try {
          await migrateUser({ db, auth, FieldValue, userDocument, options, totals });
        } catch (error) {
          totals.falhas++;
          const code = safeErrorCode(error);
          totals.falhasPorCodigo[code] = (totals.falhasPorCodigo[code] ?? 0) + 1;
        }
      }
      lastDocument = page.docs.at(-1);
      if (page.size < PAGE_SIZE) break;
    }
    // Saída única e agregada: não inclui UIDs, nomes, fotos, emails nem respostas.
    process.stdout.write(`${JSON.stringify(totals, null, 2)}\n`);
    if (totals.falhas > 0) process.exitCode = 1;
  } finally {
    await db.terminate();
    await deleteApp(app);
  }
}

main().catch(error => {
  process.stderr.write(`Migração não concluída. Código: ${safeErrorCode(error)}.\n`);
  if (error instanceof MigrationError && error.code !== 'concurrent-review-limit') {
    process.stderr.write('Use --help para conferir os argumentos. Nenhum dado pessoal é mostrado.\n');
  }
  process.exitCode = 1;
});
