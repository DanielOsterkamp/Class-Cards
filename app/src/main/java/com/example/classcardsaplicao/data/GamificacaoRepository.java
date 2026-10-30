package com.example.classcardsaplicao.data;

import com.example.classcardsaplicao.model.FsrsScheduler;
import com.example.classcardsaplicao.model.ResultadoOfensiva;
import com.example.classcardsaplicao.model.ProgressoFlashcard;
import com.example.classcardsaplicao.model.Rating;
import com.example.classcardsaplicao.model.RegrasGamificacao;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

// salva os pontos de flashcard, flashcard estudados e ofensivas no firebase
public class GamificacaoRepository {
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public void confirmarRevisao(String uid, String deckId, String flashcardId,
            String tentativaId, Rating rating, Callback callback) {

        confirmarRevisao(uid, deckId, flashcardId, tentativaId, rating, false, callback);
    }

    public void confirmarRevisaoTradicional(String uid, String deckId, String flashcardId,
            String tentativaId, Rating rating, Callback callback) {
        confirmarRevisaoTradicional(uid, deckId, flashcardId, tentativaId, rating, false, callback);
    }

    public void confirmarRevisao(String uid, String deckId, String flashcardId,
            String tentativaId, Rating rating, boolean ultimaRevisao, Callback callback) {
        confirmar(uid, deckId, flashcardId, tentativaId, rating, "revisoesDissertativas", ultimaRevisao, callback);
    }

    public void confirmarRevisaoTradicional(String uid, String deckId, String flashcardId,
            String tentativaId, Rating rating, boolean ultimaRevisao, Callback callback) {
        confirmar(uid, deckId, flashcardId, tentativaId, rating, "revisoesTradicionais", ultimaRevisao, callback);
    }

    private void confirmar(String uid, String deckId, String flashcardId, String tentativaId,
            Rating rating, String colecaoEventos, boolean ultimaRevisao, Callback callback) {
        long xpGanho = "revisoesDissertativas".equals(colecaoEventos) ? RegrasGamificacao.xpPorRevisao(rating) : 0;
        DocumentReference usuario = firestore.collection("users").document(uid);
        DocumentReference evento = usuario.collection(colecaoEventos).document(tentativaId);
        DocumentReference resumo = usuario.collection("gamificacao").document("resumo");
        DocumentReference progresso = usuario.collection("progressoFlashcards").document(flashcardId);


        long agora = System.currentTimeMillis();
        TimeZone zona = TimeZone.getDefault();
        String hoje = RegrasGamificacao.dia(agora, zona, 0);
        String ontem = RegrasGamificacao.dia(agora, zona, -1);


        firestore.runTransaction(transaction -> {

            DocumentSnapshot recibo = transaction.get(evento);
            if (recibo.exists()) {
                return new ResultadoOfensiva(numero(recibo, "ofensivaAnterior"),
                        numero(recibo, "ofensivaAtual"), Boolean.TRUE.equals(recibo.getBoolean("ofensivaConquistada")));
            }

            DocumentSnapshot stats = transaction.get(resumo);
            DocumentSnapshot anterior = transaction.get(progresso);
            ProgressoFlashcard progressoFlashcard = anterior.toObject(ProgressoFlashcard.class);
            if (progressoFlashcard == null)
                progressoFlashcard = new ProgressoFlashcard(uid, deckId, flashcardId);

            boolean primeiroEstudo = progressoFlashcard.getRepeticoes() <= 0 && progressoFlashcard.getUltimaRevisao() <= 0;

            // recalcula a próxima revisão com a classificação confirmada pelo estudante.
            new FsrsScheduler().revisar(progressoFlashcard, rating);
            long xp = numero(stats, "xp");
            ResultadoOfensiva resultado = ResultadoOfensiva.calcular(numero(stats, "ofensiva"),
                    stats.getString("ultimoDiaEstudo"), hoje, ontem, ultimaRevisao);

            Map<String, Object> valores = new HashMap<>();
            valores.put("xp", xp + xpGanho);
            valores.put("fusoHorario", zona.getID());
            valores.put("cardsEstudados", numero(stats, "cardsEstudados") + (primeiroEstudo ? 1 : 0)); // fazemos isso porque o firebase não entende booleano, temos que passar 1 ou 0
            valores.put("versaoEstudos", numero(stats, "versaoEstudos") + 1);

            if (resultado.conquistada) {
                valores.put("ofensiva", resultado.atual);
                valores.put("ultimoDiaEstudo", hoje);
            }

            Map<String, Object> registro = new HashMap<>();
            registro.put("deckId", deckId);
            registro.put("flashcardId", flashcardId);
            registro.put("rating", rating.name());
            registro.put("xp", xpGanho);
            registro.put("sessaoConcluida", ultimaRevisao);
            registro.put("ofensivaAnterior", resultado.anterior);
            registro.put("ofensivaAtual", resultado.atual);
            registro.put("ofensivaConquistada", resultado.conquistada);
            registro.put("createdAt", FieldValue.serverTimestamp());

            transaction.set(progresso, progressoFlashcard);
            transaction.set(resumo, valores, SetOptions.merge());
            transaction.set(evento, registro);
            return resultado;
        }).addOnSuccessListener(resultado -> {
            RankingRepository.atualizarEmSegundoPlano();
            callback.onSuccess(resultado);
        })
                .addOnFailureListener(callback::onError);
    }

    public static long numero(DocumentSnapshot doc, String campo) {
        Long valor = doc.getLong(campo);
        return valor == null ? 0 : Math.max(0, valor);
    }

    public interface Callback {
        void onSuccess();

        default void onSuccess(ResultadoOfensiva resultado) { onSuccess(); }

        void onError(Exception erro);
    }
}
