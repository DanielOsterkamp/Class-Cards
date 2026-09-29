package com.example.classcardsaplicao.data;

import com.example.classcardsaplicao.model.Deck;
import com.example.classcardsaplicao.model.Flashcard;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DeckRepository {

    private final FirebaseFirestore firestore;

    public DeckRepository() {
        firestore = FirebaseFirestore.getInstance();
    }

    public void buscarDecksDoUsuario(String userId, BuscarDecksCallback callback) {

        // o collection do firebase seria tipo uma tabela do sql, aqui ele pega dos decks do usuario

        firestore.collection("decks")
                .whereEqualTo("ownerId", userId)
                .get()
                .addOnSuccessListener(querySnapshot -> {

                    List<Deck> decks = querySnapshot.toObjects(Deck.class);

                    callback.onSuccess(decks);
                })
                .addOnFailureListener(callback::onError);

        // igual em outros lugares, aqui ele passa a resposta pela interface do callback, que o codigo tá lá em baixo
    }

    public void buscarDeckPorId(String deckId, BuscarDeckCallback callback) {
        firestore.collection("decks")
                .document(deckId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {

                    // a diferença do documentSnapshot e pro querySnapshot é que o query devolve varios de uma informação
                    // já o document ele pega só um, como aqui ele pega só um deck

                    if (!documentSnapshot.exists()) {
                        callback.onError(new Exception("Deck não encontrado."));
                        return;
                    }

                    Deck deck = documentSnapshot.toObject(Deck.class);

                    if (deck == null) {
                        callback.onError(new Exception("Erro ao carregar o deck."));
                        return;
                    }

                    deck.setId(documentSnapshot.getId());

                    callback.onSuccess(deck);
                })
                .addOnFailureListener(callback::onError);
    }

    public void criarDeck(Deck deck, List<Flashcard> flashcards, DeckCallback callback) {


        DocumentReference deckRef = firestore.collection("decks").document();

        // aqui ele tá criando uma referencia na "tabela" de decks

        String deckId = deckRef.getId();

        deck.setId(deckId);

        WriteBatch batch = firestore.batch();

        // o batch ele serve pra juntar varias informações pro firebase e evniar de uma vez, sem precisar vazer um milhão de card1Ref.set(card1)

        // aqui ele salva as o deck e seus atributos nessa referencia
        batch.set(deckRef, deck);

        // aqui salva os flashcards na subcoleção cards

        for (Flashcard flashcard : flashcards) {

            DocumentReference cardRef = deckRef.collection("cards").document();

            flashcard.setId(cardRef.getId());

            batch.set(cardRef, flashcard);
        }

        // aqui o batch tá mandando essas informações

        batch.commit().addOnSuccessListener(unused -> callback.onSuccess(deckId))
                .addOnFailureListener(callback::onError);
    }



    public void buscarFlashcardsDoDeck(String deckId, BuscarFlashcardsCallback callback) {
        firestore.collection("decks")
                .document(deckId)
                .collection("cards")
                .get()
                .addOnSuccessListener(querySnapshot -> {

                    List<Flashcard> flashcards = new ArrayList<>();

                    for (DocumentSnapshot documento : querySnapshot.getDocuments()) {

                        // aqui ele tá transformando os dados referente a um flashcard em um objeto de flashcard em java

                        Flashcard flashcard = documento.toObject(Flashcard.class);

                        if (flashcard != null) {
                            flashcard.setId(documento.getId());
                            flashcards.add(flashcard);
                        }
                    }

                    callback.onSuccess(flashcards);
                })
                .addOnFailureListener(callback::onError);
    }



    public void atualizarDeck(Deck deck, List<Flashcard> flashcards, AtualizarDeckCallback callback) {

        DocumentReference deckRef = firestore.collection("decks").document(deck.getId());

        deckRef.collection("cards")
                .get()
                .addOnSuccessListener(querySnapshot -> {

                    WriteBatch batch = firestore.batch();

                    // atualiza os dados do deck na referencia que passamos
                    batch.update(
                            deckRef,
                            "titulo", deck.getTitulo(),
                            "materia", deck.getMateria(),
                            "quantidadeCards", flashcards.size(),
                            "updatedAt", System.currentTimeMillis()
                    );

                    // aqui salvamos os ids dos cards que vão continuar iguais, fazemos isso pra não perder as informações sobre a repetição espaçada
                    Set<String> idsMantidos = new HashSet<>();

                    for (Flashcard flashcard : flashcards) {

                        String flashcardId = flashcard.getId();

                        // flashcard novo
                        if (flashcardId == null || flashcardId.isEmpty()) {

                            DocumentReference cardRef = deckRef.collection("cards").document();

                            flashcard.setId(cardRef.getId());

                            batch.set(cardRef, flashcard);

                            idsMantidos.add(cardRef.getId());

                        } else {

                            // flashcard que já existe
                            DocumentReference cardRef =
                                    deckRef.collection("cards")
                                            .document(flashcardId);

                            batch.set(cardRef, flashcard);

                            idsMantidos.add(flashcardId);
                        }
                    }

                    // exclui os cards que o usuário removeu

                    for (DocumentSnapshot documento : querySnapshot.getDocuments()) {

                        if (!idsMantidos.contains(documento.getId())) {
                            batch.delete(documento.getReference());
                        }

                    }

                    batch.commit()
                            .addOnSuccessListener(unused ->
                                    callback.onSuccess()
                            )
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    public interface AtualizarDeckCallback {

        void onSuccess();

        void onError(Exception exception);
    }

    public interface BuscarDeckCallback {

        void onSuccess(Deck deck);

        void onError(Exception exception);
    }

    public interface BuscarFlashcardsCallback {
        void onSuccess(List<Flashcard> flashcards);
        void onError(Exception exception);
    }

    public interface DeckCallback {

        void onSuccess(String deckId);

        void onError(Exception exception);
    }

    public interface BuscarDecksCallback {

        void onSuccess(List<Deck> decks);

        void onError(Exception exception);
    }
}

