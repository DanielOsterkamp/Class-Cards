const { before, beforeEach, after, test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const { initializeTestEnvironment, assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const { doc, collection, query, where, getDoc, getDocs, setDoc, updateDoc, serverTimestamp, writeBatch } = require('firebase/firestore');
const { initializeApp, deleteApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { processar } = require('../relatorios/processar.cjs');
const projectId='demo-classcards-relatorios';
let env, app, admin;
const claims={moodle:{cursos:['site:2'],prof:[]}};
const aluno='moodle-site-3',prof='moodle-site-4';
const db=(uid,claims)=>env.authenticatedContext(uid,claims).firestore();
const professor=()=>db(prof,{moodle:{cursos:['site:2'],prof:['site:2']}});
const metadados={cursoChave:'site:2',materia:'Biologia',assunto:'Células',titulo:'Deck',verificado:false};
const evento=(extra={})=>({deckId:'moodle-deck',flashcardId:'card',rating:'AGAIN',createdAt:serverTimestamp(),relatorio:metadados,...extra});
before(async()=>{
 assert.ok(process.env.FIRESTORE_EMULATOR_HOST?.startsWith('127.0.0.1:'),'Use apenas o emulador local.');
 const [host,port]=process.env.FIRESTORE_EMULATOR_HOST.split(':');
 env=await initializeTestEnvironment({projectId,firestore:{host,port:Number(port),rules:readFileSync(join(__dirname,'../firestore.rules'),'utf8')}});
 app=initializeApp({projectId},'relatorios-testes');admin=getFirestore(app);
});
after(async()=>{await env?.cleanup();await admin?.terminate();if(app)await deleteApp(app);});
beforeEach(async()=>{
 await env.clearFirestore();
 await admin.doc('decksMoodle/moodle-deck').set({ownerId:aluno,cursoChave:'site:2',materia:'Biologia',titulo:'Deck',assunto:'Células',verificadoPorProfessor:false});
 await admin.doc('decksMoodle/moodle-deck/cards/card').set({pergunta:'p',resposta:'r'});
 await admin.doc('relatoriosCursos/site:2/resumos/resumo').set({dia:'2026-11-18',uid:aluno,revisoes:2});
});
test('somente professor da turma lê resumos; aluno, visitante e professor de outro curso são bloqueados',async()=>{
 await assertSucceeds(getDocs(query(collection(professor(),'relatoriosCursos/site:2/resumos'),where('dia','>=','2026-01-01'))));
 await assertFails(getDoc(doc(db(aluno,claims),'relatoriosCursos/site:2/resumos/resumo')));
 await assertFails(getDoc(doc(db('outro',{moodle:{cursos:['site:3'],prof:['site:3']}}),'relatoriosCursos/site:2/resumos/resumo')));
 await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(),'relatoriosCursos/site:2/resumos/resumo')));
});
test('professor não escreve nos resumos, não lê recibos nem histórico privado do aluno',async()=>{
 const d=professor();await assertFails(setDoc(doc(d,'relatoriosCursos/site:2/resumos/falso'),{revisoes:999}));
 await assertFails(getDoc(doc(d,'relatoriosProcessados/recibo')));
 await assertFails(getDoc(doc(d,`users/${aluno}/revisoesTradicionais/1`)));
});
test('metadados da revisão correspondem ao deck e à matrícula',async()=>{
 const d=db(aluno,claims);await assertSucceeds(setDoc(doc(d,`users/${aluno}/revisoesTradicionais/1`),evento()));
 await assertFails(setDoc(doc(d,`users/${aluno}/revisoesTradicionais/2`),evento({relatorio:{...metadados,cursoChave:'site:3'}})));
 await assertFails(setDoc(doc(d,`users/${aluno}/revisoesDissertativas/3`),evento({relatorio:{...metadados,assunto:'Inventado'}})));
 await assertFails(setDoc(doc(d,`users/${aluno}/revisoesTradicionais/4`),evento({relatorio:{...metadados,verificado:true}})));
 await assertFails(setDoc(doc(d,`users/${aluno}/revisoesTradicionais/5`),evento({flashcardId:'inexistente'})));
 await assertFails(setDoc(doc(d,`users/${aluno}/revisoesTradicionais/6`),evento({rating:'INVALIDO'})));
 // O dono que saiu da turma ainda pode revisar seu deck; o servidor não o agrega como matriculado.
 await assertSucceeds(setDoc(doc(db(aluno,{}),`users/${aluno}/revisoesTradicionais/ex-matriculado`),evento()));
});
test('compatibilidade: revisões antigas sem metadados continuam permitidas',async()=>{
 const d=db(aluno,claims);await assertSucceeds(setDoc(doc(d,`users/${aluno}/revisoesTradicionais/antigo`),{rating:'GOOD'}));
});
test('assunto limitado a 120 caracteres; alteração por aluno retira selo',async()=>{
 const d=db(aluno,claims);
 await assertFails(updateDoc(doc(d,'decksMoodle/moodle-deck'),{assunto:'a'.repeat(121)}));
 await admin.doc('decksMoodle/moodle-deck').update({verificadoPorProfessor:true});
 await assertFails(updateDoc(doc(d,'decksMoodle/moodle-deck'),{assunto:'Novo'}));
 await assertSucceeds(updateDoc(doc(d,'decksMoodle/moodle-deck'),{assunto:'Novo',verificadoPorProfessor:false}));
});
async function gravar(id,rating='AGAIN',colecao='revisoesTradicionais') {
 const d=db(aluno,claims),ref=doc(d,`users/${aluno}/${colecao}/${id}`);await setDoc(ref,evento({rating}));return admin.doc(ref.path).get();
}
const auth={getUser:async()=>({customClaims:claims,disabled:false})};
test('transação real: entregas repetidas e concorrentes contam uma única revisão',async()=>{
 await admin.doc('relatoriosCursos/site:2/resumos/resumo').delete();
 const s=await gravar('duplicado');await Promise.all([processar(admin,auth,s,aluno),processar(admin,auth,s,aluno)]);
 const res=await admin.collection('relatoriosCursos/site:2/resumos').get();assert.equal(res.size,1);assert.equal(res.docs[0].get('revisoes'),1);
 assert.equal((await admin.collection('relatoriosProcessados').get()).size,1);
});
test('transação real: repetições do card são somadas com contadores por classificação',async()=>{
 await admin.doc('relatoriosCursos/site:2/resumos/resumo').delete();
 const a=await gravar('a','AGAIN'),b=await gravar('b','HARD');
 await processar(admin,auth,b,aluno);await processar(admin,auth,a,aluno);
 const res=await admin.collection('relatoriosCursos/site:2/resumos').get();assert.equal(res.size,1);
 assert.equal(res.docs[0].get('revisoes'),2);assert.equal(res.docs[0].get('errei'),1);assert.equal(res.docs[0].get('dificil'),1);
});
test('histórico mantém assunto e selo da revisão mesmo após editar ou excluir o deck',async()=>{
 const s=await gravar('historico');await admin.doc('decksMoodle/moodle-deck').delete();
 await processar(admin,auth,s,aluno);
 const res=await admin.collection('relatoriosCursos/site:2/resumos').where('flashcardId','==','card').get();
 assert.equal(res.docs[0].get('assunto'),'Células');assert.equal(res.docs[0].get('verificado'),false);
});
test('revisões do professor não entram como atividade de aluno',async()=>{
 const s=await gravar('prof');assert.equal(await processar(admin,{getUser:async()=>({customClaims:{moodle:{cursos:['site:2'],prof:['site:2']}}})},s,aluno),false);
});
