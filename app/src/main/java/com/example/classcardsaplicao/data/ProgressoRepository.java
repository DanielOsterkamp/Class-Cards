package com.example.classcardsaplicao.data;

import com.example.classcardsaplicao.model.ProgressoFlashcard;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class ProgressoRepository {

    private final FirebaseFirestore firestore;

    public ProgressoRepository() {
        firestore = FirebaseFirestore.getInstance();
    }

    private DocumentReference getProgressoRef(String userId, String flashcardId) {
        return firestore
                .collection("users")
                .document(userId)
                .collection("progressoFlashcards")
                .document(flashcardId);
    }

    public void salvarProgresso(ProgressoFlashcard progresso, SalvarProgressoCallback callback) {

        getProgressoRef(progresso.getUserId(), progresso.getFlashcardId())
                .set(progresso)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    public void buscarProgresso(String userId, String flashcardId, BuscarProgressoCallback callback) {

        getProgressoRef(userId, flashcardId)
                .get()
                .addOnSuccessListener(documento -> {

                    if (!documento.exists()) {
                        callback.onSuccess(null);
                        return;
                    }

                    ProgressoFlashcard progresso = documento.toObject(ProgressoFlashcard.class);

                    callback.onSuccess(progresso);
                })
                .addOnFailureListener(
                        callback::onError
                );
    }

    public void buscarProgressosDoDeck(String userId, String deckId, BuscarProgressosDeckCallback callback) {

        firestore.collection("users")
                .document(userId)
                .collection("progressoFlashcards")
                .whereEqualTo("deckId", deckId)
                .get()
                .addOnSuccessListener(querySnapshot -> {

                    List<ProgressoFlashcard> progressos = querySnapshot.toObjects(ProgressoFlashcard.class);

                    callback.onSuccess(progressos);
                })
                .addOnFailureListener(callback::onError);
    }

    public interface BuscarProgressosDeckCallback {

        void onSuccess(List<ProgressoFlashcard> progressos);

        void onError(Exception exception);
    }

    public interface SalvarProgressoCallback {

        void onSuccess();

        void onError(Exception exception);
    }

    public interface BuscarProgressoCallback {

        void onSuccess(ProgressoFlashcard progresso);

        void onError(Exception exception);
    }
}