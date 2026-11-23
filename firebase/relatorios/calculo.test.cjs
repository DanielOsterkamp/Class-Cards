const { test } = require('node:test');
const assert = require('node:assert/strict');
const { preparar } = require('./calculo.cjs');
const claims={moodle:{cursos:['site:2'],prof:[]}};
const evento={deckId:'moodle-deck',flashcardId:'card',rating:'AGAIN',createdAt:{toMillis:()=>Date.parse('2026-11-18T23:59:59Z')},relatorio:{cursoChave:'site:2',assunto:'Divisão celular',titulo:'Deck',verificado:false}};
const prepararTeste=(e=evento,c=claims,path='users/moodle-site-3/revisoesTradicionais/1')=>preparar(path,'moodle-site-3',e,c);
test('revisão gera contagem correta e dia UTC',()=>{ const p=prepararTeste();assert.equal(p.dados.dia,'2026-11-18');assert.equal(p.dados.errei,1);assert.equal(p.dados.dificil,0);assert.equal(p.dados.modo,'tradicional'); });
test('tentativas do mesmo card e dia compartilham resumo, mas têm recibos diferentes',()=>{const a=prepararTeste(),b=prepararTeste(evento,claims,'users/moodle-site-3/revisoesTradicionais/2');assert.equal(a.chave,b.chave);assert.notEqual(a.recibo,b.recibo);});
test('modo e selo têm resumos separados',()=>{assert.notEqual(prepararTeste().chave,prepararTeste({...evento,relatorio:{...evento.relatorio,verificado:true}}).chave);assert.equal(prepararTeste(evento,claims,'users/moodle-site-3/revisoesDissertativas/1').dados.modo,'dissertativo');});
test('assunto normalizado agrupa acentos e espaços sem perder o rótulo',()=>{assert.equal(prepararTeste().chave,prepararTeste({...evento,relatorio:{...evento.relatorio,assunto:'  divisao   celular '}}).chave);});
test('professor e conta fora do curso não entram como alunos',()=>{assert.equal(prepararTeste(evento,{moodle:{cursos:['site:2'],prof:['site:2']}}),null);assert.equal(prepararTeste(evento,{moodle:{cursos:['site:3'],prof:[]}}),null);});
test('eventos antigos, classificação inválida e timestamp ausente são ignorados',()=>{assert.equal(prepararTeste({...evento,relatorio:null}),null);assert.equal(prepararTeste({...evento,rating:'X'}),null);assert.equal(prepararTeste({...evento,createdAt:null}),null);});
test('fotos de HTTP são rejeitadas',()=>{assert.equal(preparar('p','moodle-site-3',evento,claims,{fotoUrl:'http://inseguro'}).dados.fotoUrl,'');});

test('referência pequena do avatar do próprio aluno é preservada nos relatórios',()=>{
 const foto='avatar-firestore:moodle-site-3/12345678-1234-1234-1234-123456789abc';
 assert.equal(preparar('p','moodle-site-3',evento,claims,{fotoUrl:foto}).dados.fotoUrl,foto);
 assert.equal(preparar('p','moodle-site-3',evento,claims,{fotoUrl:foto.replace('site-3','site-4')}).dados.fotoUrl,'');
});
