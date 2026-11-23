const { onDocumentCreated } = require('firebase-functions/v2/firestore');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { getAuth } = require('firebase-admin/auth');
const { processar } = require('./processar.cjs');
initializeApp();
// Cada evento pode ser entregue mais de uma vez; o recibo é gravado na mesma transação do resumo.
for (const [nome, colecao] of [['relatorioTradicional','revisoesTradicionais'],['relatorioDissertativo','revisoesDissertativas']]) {
  exports[nome] = onDocumentCreated({document:`users/{uid}/${colecao}/{tentativa}`, region:'us-central1', retry:true},
    event => event.data ? processar(getFirestore(),getAuth(),event.data,event.params.uid) : null);
}
