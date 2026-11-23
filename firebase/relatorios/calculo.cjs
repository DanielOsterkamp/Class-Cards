const { createHash } = require('node:crypto');
const hash = value => createHash('sha256').update(value).digest('hex');
const normalizar = valor => valor.normalize('NFD').replace(/[\u0300-\u036f]/g,'').trim().toLowerCase().replace(/\s+/g,' ');
/** Um agregado por aluno, card, dia, assunto, modo e estado do selo. Repetições não duplicam cards. */
function preparar(caminho, uid, revisao, claims, perfil = {}) {
  const r = revisao.relatorio;
  if (!r || !r.cursoChave || !uid.startsWith('moodle-') || !['AGAIN','HARD','GOOD','EASY'].includes(revisao.rating)) return null;
  if (typeof revisao.deckId !== 'string' || !revisao.deckId.startsWith('moodle-') || typeof revisao.flashcardId !== 'string' || !revisao.flashcardId) return null;
  if (!(claims?.moodle?.cursos || []).includes(r.cursoChave) || (claims?.moodle?.prof || []).includes(r.cursoChave)) return null;
  const quando = revisao.createdAt?.toMillis?.();
  if (!Number.isFinite(quando)) return null;
  const dia = new Date(quando).toISOString().slice(0,10);
  const modo = caminho.includes('/revisoesTradicionais/') ? 'tradicional' : 'dissertativo';
  const assunto = typeof r.assunto === 'string' ? r.assunto.trim().slice(0,120) : '';
  const verificado = r.verificado === true;
  const chave = hash(JSON.stringify([uid,revisao.deckId,revisao.flashcardId,dia,normalizar(assunto),modo,verificado]));
  return { recibo: hash(caminho), curso: r.cursoChave, chave, dados: {
    uid, nome: typeof perfil.nome === 'string' ? perfil.nome.slice(0,120) : 'Estudante',
    fotoUrl: typeof perfil.fotoUrl === 'string' && (perfil.fotoUrl.startsWith('https://') || perfil.fotoUrl.startsWith(`avatar-firestore:${uid}/`)) ? perfil.fotoUrl.slice(0,4096) : '',
    deckId: revisao.deckId, titulo: String(r.titulo || '').slice(0,240), flashcardId: revisao.flashcardId,
    assunto, dia, modo, verificado, ultima: quando,
    revisoes: 1, errei: revisao.rating === 'AGAIN' ? 1 : 0, dificil: revisao.rating === 'HARD' ? 1 : 0,
    bom: revisao.rating === 'GOOD' ? 1 : 0, facil: revisao.rating === 'EASY' ? 1 : 0
  }};
}
module.exports = { preparar };
