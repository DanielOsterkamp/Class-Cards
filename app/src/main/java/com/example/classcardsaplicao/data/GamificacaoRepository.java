package com.example.classcardsaplicao.data;

import com.example.classcardsaplicao.model.FsrsScheduler;
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

        DocumentReference usuario = firestore.collection("users").document(uid);
        DocumentReference evento = usuario.collection("revisoesDissertativas").document(tentativaId);
        DocumentReference resumo = usuario.collection("gamificacao").document("resumo");
        DocumentReference progresso = usuario.collection("progressoFlashcards").document(flashcardId);


        long agora = System.currentTimeMillis();
        TimeZone zona = TimeZone.getDefault();
        String hoje = RegrasGamificacao.dia(agora, zona, 0);
        String ontem = RegrasGamificacao.dia(agora, zona, -1);


        firestore.runTransaction(transaction -> {

            if (transaction.get(evento).exists())
                return null;

            DocumentSnapshot stats = transaction.get(resumo);
            DocumentSnapshot anterior = transaction.get(progresso);
            ProgressoFlashcard p = anterior.toObject(ProgressoFlashcard.class);
            if (p == null)
                p = new ProgressoFlashcard(uid, deckId, flashcardId);

            // recalcula a próxima revisão com a classificação confirmada pelo estudante.
            new FsrsScheduler().revisar(p, rating);
            long xp = numero(stats, "xp");
            long ofensiva = RegrasGamificacao.proximaOfensiva(numero(stats, "ofensiva"),
                    stats.getString("ultimoDiaEstudo"), hoje, ontem);

            Map<String, Object> valores = new HashMap<>();
            valores.put("xp", xp + RegrasGamificacao.XP_POR_REVISAO);
            valores.put("ofensiva", ofensiva);

            String ultimoDia = stats.getString("ultimoDiaEstudo");
            valores.put("ultimoDiaEstudo", ultimoDia != null && ultimoDia.compareTo(hoje) > 0 ? ultimoDia : hoje);

            Map<String, Object> registro = new HashMap<>();
            registro.put("deckId", deckId);
            registro.put("flashcardId", flashcardId);
            registro.put("rating", rating.name());
            registro.put("xp", RegrasGamificacao.XP_POR_REVISAO);
            registro.put("createdAt", FieldValue.serverTimestamp());

            transaction.set(progresso, p);
            transaction.set(resumo, valores, SetOptions.merge());
            transaction.set(evento, registro);
            return null;
        }).addOnSuccessListener(ignorado -> callback.onSuccess())
        .addOnFailureListener(callback::onError);
    }

    public static long numero(DocumentSnapshot doc, String campo) {
        Long valor = doc.getLong(campo);
        return valor == null ? 0 : Math.max(0, valor);
    }

    public interface Callback {
        void onSuccess();

        void onError(Exception erro);
    }
}
