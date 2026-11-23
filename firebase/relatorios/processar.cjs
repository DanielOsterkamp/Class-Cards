const { preparar } = require('./calculo.cjs');
/** Recebe dependências para testes contra o mesmo Firestore, sem credenciais reais. */
async function processar(db, auth, snapshot, uid) {
  const revisao = snapshot.data();
  if (!revisao?.relatorio) return false;
  let usuario;
  try { usuario = await auth.getUser(uid); }
  catch (erro) { if (erro.code === 'auth/user-not-found') return false; throw erro; }
  if (usuario.disabled) return false;
  const perfil = await db.doc(`rankings/${uid}`).get();
  const item = preparar(snapshot.ref.path, uid, revisao, usuario.customClaims, perfil.data());
  if (!item) return false;
  const recibo = db.doc(`relatoriosProcessados/${item.recibo}`);
  const resumo = db.doc(`relatoriosCursos/${item.curso}/resumos/${item.chave}`);
  return db.runTransaction(async tx => {
    const [anterior, atual] = await Promise.all([tx.get(recibo), tx.get(resumo)]);
    if (anterior.exists) return false;
    const dados = { ...item.dados };
    for (const campo of ['revisoes','errei','dificil','bom','facil']) dados[campo] += atual.get(campo) || 0;
    dados.ultima = Math.max(dados.ultima, atual.get('ultima') || 0);
    tx.set(resumo,dados);
    tx.set(recibo,{ evento:snapshot.ref.path, curso:item.curso });
    return true;
  });
}
module.exports = { processar };
