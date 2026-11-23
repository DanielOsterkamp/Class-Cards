package com.example.classcardsaplicao.data;

import com.example.classcardsaplicao.model.FsrsScheduler;
import com.example.classcardsaplicao.model.ResultadoOfensiva;
import com.example.classcardsaplicao.model.ResultadoRevisao;
import com.example.classcardsaplicao.model.ResumoEstudo;
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
    private final FirebaseFirestore firestore;
    private final Runnable atualizarRanking;

    public GamificacaoRepository() {
        this(FirebaseFirestore.getInstance(), RankingRepository::atualizarEmSegundoPlano);
    }

    // Permite exercitar a transação no emulador, sem publicar dados em um projeto real.
    GamificacaoRepository(FirebaseFirestore firestore, Runnable atualizarRanking) {
        this.firestore = firestore;
        this.atualizarRanking = atualizarRanking;
    }

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
                ResultadoOfensiva ofensiva = new ResultadoOfensiva(numero(recibo, "ofensivaAnterior"),
                        numero(recibo, "ofensivaAtual"), Boolean.TRUE.equals(recibo.getBoolean("ofensivaConquistada")));
                // Reenviar a mesma tentativa recupera os totais originais, sem conceder pontos de novo.
                ResumoEstudo totais = recibo.contains("xpAnterior") && recibo.contains("xpAtual")
                        && recibo.contains("cardsEstudadosAnterior") && recibo.contains("cardsEstudadosAtual")
                        ? new ResumoEstudo(numero(recibo, "cardsEstudadosAnterior"), numero(recibo, "cardsEstudadosAtual"),
                                numero(recibo, "xpAnterior"), numero(recibo, "xpAtual")) : null;
                return new ResultadoRevisao(ofensiva, totais);
            }

            DocumentSnapshot deckMoodle = deckId.startsWith("moodle-")
                    ? transaction.get(firestore.collection("decksMoodle").document(deckId)) : null;
            DocumentSnapshot stats = transaction.get(resumo);
            DocumentSnapshot anterior = transaction.get(progresso);
            ProgressoFlashcard progressoFlashcard = anterior.toObject(ProgressoFlashcard.class);
            if (progressoFlashcard == null)
                progressoFlashcard = new ProgressoFlashcard(uid, deckId, flashcardId);

            boolean primeiroEstudo = progressoFlashcard.getRepeticoes() <= 0 && progressoFlashcard.getUltimaRevisao() <= 0;

            // recalcula a próxima revisão com a classificação confirmada pelo estudante.
            new FsrsScheduler().revisar(progressoFlashcard, rating);
            long xp = numero(stats, "xp");
            long cardsAntes = numero(stats, "cardsEstudados");
            ResumoEstudo totais = new ResumoEstudo(cardsAntes, cardsAntes + (primeiroEstudo ? 1 : 0), xp, xp + xpGanho);
            ResultadoOfensiva resultado = ResultadoOfensiva.calcular(numero(stats, "ofensiva"),
                    stats.getString("ultimoDiaEstudo"), hoje, ontem, ultimaRevisao);

            Map<String, Object> valores = new HashMap<>();
            valores.put("xp", totais.pontosAgora);
            valores.put("fusoHorario", zona.getID());
            valores.put("cardsEstudados", totais.cardsAgora);
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
            registro.put("xpAnterior", totais.pontosAntes);
            registro.put("xpAtual", totais.pontosAgora);
            registro.put("cardsEstudadosAnterior", totais.cardsAntes);
            registro.put("cardsEstudadosAtual", totais.cardsAgora);
            registro.put("sessaoConcluida", ultimaRevisao);
            registro.put("ofensivaAnterior", resultado.anterior);
            registro.put("ofensivaAtual", resultado.atual);
            registro.put("ofensivaConquistada", resultado.conquistada);
            registro.put("createdAt", FieldValue.serverTimestamp());
            if (deckMoodle != null && deckMoodle.exists()) {
                Map<String, Object> relatorio = new HashMap<>();
                relatorio.put("cursoChave", deckMoodle.getString("cursoChave"));
                relatorio.put("materia", deckMoodle.getString("materia") == null ? "" : deckMoodle.getString("materia"));
                relatorio.put("assunto", deckMoodle.getString("assunto") == null ? "" : deckMoodle.getString("assunto"));
                relatorio.put("titulo", deckMoodle.getString("titulo") == null ? "" : deckMoodle.getString("titulo"));
                relatorio.put("verificado", Boolean.TRUE.equals(deckMoodle.getBoolean("verificadoPorProfessor")));
                registro.put("relatorio", relatorio);
            }

            transaction.set(progresso, progressoFlashcard);
            transaction.set(resumo, valores, SetOptions.merge());
            transaction.set(evento, registro);
            return new ResultadoRevisao(resultado, totais);
        }).addOnSuccessListener(resultado -> {
            atualizarRanking.run();
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

        default void onSuccess(ResultadoRevisao resultado) { onSuccess(resultado.ofensiva); }

        void onError(Exception erro);
    }
}
