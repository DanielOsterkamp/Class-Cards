package com.example.classcardsaplicao.data;

import android.util.Log;
import com.example.classcardsaplicao.data.moodle.BolhaMoodle;
import com.example.classcardsaplicao.model.Deck;
import com.example.classcardsaplicao.model.Flashcard;
import com.example.classcardsaplicao.model.TextoPesquisa;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.firestore.Source;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DeckRepository {

    private final FirebaseFirestore firestore;

    public DeckRepository() {
        firestore = FirebaseFirestore.getInstance();
    }

    public void buscarDecksDoUsuario(String userId, BuscarDecksCallback callback) {
        // Bolha Moodle: os baralhos de curso ficam em "decksMoodle" (Fase 9)
        if (BolhaMoodle.ehMoodle()) {
            buscarDecksMoodleDoUsuario(userId, callback);
            return;
        }

        // o collection do firebase seria tipo uma tabela do sql, aqui ele pega dos decks do usuario

        firestore.collection("decks")
                .whereEqualTo("ownerId", userId)
                .get()
                .addOnSuccessListener(querySnapshot -> {

                    List<Deck> decks = new ArrayList<>();
                    for (DocumentSnapshot documento : querySnapshot.getDocuments()) {
                        Deck deck = documento.toObject(Deck.class);
                        if (deck != null) {
                            // O ID do documento também funciona para decks antigos sem o campo id.
                            deck.setId(documento.getId());
                            decks.add(deck);
                        }
                    }

                    callback.onSuccess(decks);
                    indexarDecksAntigos(userId, querySnapshot.getDocuments());
                })
                .addOnFailureListener(callback::onError);

        // igual em outros lugares, aqui ele passa a resposta pela interface do callback, que o codigo tá lá em baixo
    }

    /** Bolha Moodle: os próprios baralhos, ou os de outra pessoa só dos cursos em comum. */
    private void buscarDecksMoodleDoUsuario(String userId, BuscarDecksCallback callback) {
        Query query = firestore.collection(BolhaMoodle.COLECAO_DECKS_MOODLE).whereEqualTo("ownerId", userId);
        if (!userId.equals(FirebaseAuth.getInstance().getUid())) {
            List<String> minhas = BolhaMoodle.minhasChaves();
            if (minhas.isEmpty()) {
                callback.onSuccess(new ArrayList<>());
                return;
            }
            query = query.whereIn("cursoChave", minhas.subList(0, Math.min(30, minhas.size())));
        }
        query.get().addOnSuccessListener(querySnapshot -> {
            List<Deck> decks = new ArrayList<>();
            for (DocumentSnapshot documento : querySnapshot.getDocuments()) {
                Deck deck = documento.toObject(Deck.class);
                if (deck != null) {
                    deck.setId(documento.getId());
                    decks.add(deck);
                }
            }
            callback.onSuccess(decks);
        }).addOnFailureListener(callback::onError);
    }

    public void buscarDeckPorId(String deckId, BuscarDeckCallback callback) {
        firestore.collection(BolhaMoodle.colecaoDoDeck(deckId))
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

                    // Decks antigos do próprio professor ganham o selo ao serem abertos.
                    if (!documentSnapshot.contains("verificadoPorProfessor")
                            && deck.getOwnerId() != null
                            && deck.getOwnerId().equals(FirebaseAuth.getInstance().getUid())
                            && BolhaMoodle.COLECAO_DECKS_MOODLE.equals(BolhaMoodle.colecaoDoDeck(deckId))) {
                        FirebaseAuth.getInstance().getCurrentUser().getIdToken(false)
                                .addOnSuccessListener(token -> {
                                    if (!professorNoCurso(token.getClaims(), deck.getCursoChave())) {
                                        callback.onSuccess(deck); return;
                                    }
                                    verificarDeck(deckId, new AtualizarDeckCallback() {
                                        @Override public void onSuccess() {
                                            deck.setVerificadoPorProfessor(true); callback.onSuccess(deck);
                                        }
                                        @Override public void onError(Exception erro) { callback.onSuccess(deck); }
                                    });
                                }).addOnFailureListener(erro -> callback.onSuccess(deck));
                    } else callback.onSuccess(deck);
                })
                .addOnFailureListener(callback::onError);
    }

    public void criarDeck(Deck deck, List<Flashcard> flashcards, DeckCallback callback) {
        if (!BolhaMoodle.ehMoodle()) {
            criarDeckAutorizado(deck, flashcards, false, callback);
            return;
        }
        com.google.firebase.auth.FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) { callback.onError(new Exception("Entre novamente.")); return; }
        usuario.getIdToken(false).addOnSuccessListener(token -> {
            if (!usuario.getUid().equals(FirebaseAuth.getInstance().getUid())) {
                callback.onError(new Exception("A conta mudou. Entre novamente.")); return;
            }
            criarDeckAutorizado(deck, flashcards, professorNoCurso(token.getClaims(),
                    BolhaMoodle.chaveDaMateria(deck.getMateria())), callback);
        }).addOnFailureListener(callback::onError);
    }

    static boolean professorNoCurso(Map<String, Object> claims, String curso) {
        Object moodle = claims.get("moodle");
        Object professores = moodle instanceof Map ? ((Map<?, ?>) moodle).get("prof") : null;
        return curso != null && professores instanceof List && ((List<?>) professores).contains(curso);
    }

    private void criarDeckAutorizado(Deck deck, List<Flashcard> flashcards,
                                     boolean professor, DeckCallback callback) {


        // Bolha Moodle: o baralho vai para "decksMoodle", preso ao curso da matéria (Fase 9)
        boolean moodle = BolhaMoodle.ehMoodle();
        String cursoChave = moodle ? BolhaMoodle.chaveDaMateria(deck.getMateria()) : null;
        if (moodle && cursoChave == null) {
            callback.onError(new Exception("Escolha a matéria na lista de cursos do Moodle."));
            return;
        }
        DocumentReference deckRef = moodle
                ? firestore.collection(BolhaMoodle.COLECAO_DECKS_MOODLE).document(BolhaMoodle.PREFIXO_DECK
                        + firestore.collection(BolhaMoodle.COLECAO_DECKS_MOODLE).document().getId())
                : firestore.collection("decks").document();

        // aqui ele tá criando uma referencia na "tabela" de decks

        String deckId = deckRef.getId();

        deck.setId(deckId);

        WriteBatch batch = firestore.batch();

        // o batch ele serve pra juntar varias informações pro firebase e evniar de uma vez, sem precisar vazer um milhão de card1Ref.set(card1)

        // aqui ele salva as o deck e seus atributos nessa referencia
        // Metadados e índice usam uma única escrita dentro do mesmo lote dos flashcards.
        Map<String, Object> dados = dadosDeck(deck);
        if (moodle) {
            dados.put("cursoChave", cursoChave);
            dados.put("assunto", deck.getAssunto());
            dados.put("verificadoPorProfessor", professor);
        }
        batch.set(deckRef, dados);

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
        firestore.collection(BolhaMoodle.colecaoDoDeck(deckId))
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
        if (!BolhaMoodle.COLECAO_DECKS_MOODLE.equals(BolhaMoodle.colecaoDoDeck(deck.getId()))) {
            atualizarDeckAutorizado(deck, flashcards, false, callback); return;
        }
        com.google.firebase.auth.FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) { callback.onError(new Exception("Entre novamente.")); return; }
        usuario.getIdToken(false).addOnSuccessListener(token -> {
            if (!usuario.getUid().equals(FirebaseAuth.getInstance().getUid())) {
                callback.onError(new Exception("A conta mudou.")); return;
            }
            atualizarDeckAutorizado(deck, flashcards, professorNoCurso(token.getClaims(),
                    BolhaMoodle.chaveDaMateria(deck.getMateria())), callback);
        }).addOnFailureListener(callback::onError);
    }

    private void atualizarDeckAutorizado(Deck deck, List<Flashcard> flashcards,
                                         boolean professor, AtualizarDeckCallback callback) {

        DocumentReference deckRef = firestore.collection(BolhaMoodle.colecaoDoDeck(deck.getId())).document(deck.getId());

        deckRef.collection("cards")
                .get()
                .addOnSuccessListener(querySnapshot -> {

                    WriteBatch batch = firestore.batch();

                    // atualiza os dados do deck na referencia que passamos
                    batch.update(
                            deckRef,
                            "titulo", deck.getTitulo(),
                            "tituloBusca", TextoPesquisa.normalizar(deck.getTitulo()),
                            "materia", deck.getMateria(),
                            "quantidadeCards", flashcards.size(),
                            "updatedAt", System.currentTimeMillis()
                    );

                    // Bolha Moodle: se a matéria mudou, o baralho passa para o curso novo (Fase 9)
                    if (BolhaMoodle.COLECAO_DECKS_MOODLE.equals(BolhaMoodle.colecaoDoDeck(deck.getId()))) {
                        String cursoChave = BolhaMoodle.chaveDaMateria(deck.getMateria());
                        if (cursoChave != null) batch.update(deckRef, "cursoChave", cursoChave);
                        batch.update(deckRef, "assunto", deck.getAssunto());
                        // Uma edição do conteúdo exige nova validação; o professor pode confirmar novamente.
                        batch.update(deckRef, "verificadoPorProfessor", professor);
                    }

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

    public void excluirDeck(String deckId, String uid, ExcluirDeckCallback callback) {

        DocumentReference referencia = firestore.collection(BolhaMoodle.colecaoDoDeck(deckId)).document(deckId);

        firestore.runTransaction(transaction -> {
            DocumentSnapshot deck = transaction.get(referencia);

            transaction.update(referencia, "excluindo", true);
            return null;
        }).addOnSuccessListener(ignorado -> excluirProximoLote(referencia, uid, callback))
                .addOnFailureListener(callback::onError);
    }
    // o deck é excluido por meio de lotes do firebase, o deck só é exlcluido de fato, quando excluimos todos os cards primeiro
    private void excluirProximoLote(DocumentReference referencia, String uid, ExcluirDeckCallback callback) {

        referencia.get(Source.SERVER).addOnSuccessListener(deck -> {

            referencia.collection("cards").limit(400).get(Source.SERVER).addOnSuccessListener(cards -> {

                if (!uid.equals(FirebaseAuth.getInstance().getUid())) {
                    callback.onError(new IllegalStateException("A conta mudou. Tente novamente."));
                    return;
                }

                WriteBatch lote = firestore.batch();

                for (DocumentSnapshot card : cards.getDocuments())
                    lote.delete(card.getReference());

                boolean ultimoLote = cards.size() < 400;


                if (ultimoLote)
                    lote.delete(referencia);

                lote.commit().addOnSuccessListener(ignorado -> {

                    if (ultimoLote)
                        callback.onSuccess();
                    else
                        excluirProximoLote(referencia, uid, callback);

                }).addOnFailureListener(callback::onError);
            }).addOnFailureListener(callback::onError);
        }).addOnFailureListener(callback::onError);
    }

    public interface ExcluirDeckCallback {
        void onSuccess();
        void onError(Exception exception);
    }

    private static Map<String, Object> dadosDeck(Deck deck) {
        Map<String, Object> dados = new HashMap<>();
        dados.put("id", deck.getId());
        dados.put("ownerId", deck.getOwnerId());
        dados.put("titulo", deck.getTitulo());
        dados.put("tituloBusca", TextoPesquisa.normalizar(deck.getTitulo()));
        dados.put("materia", deck.getMateria());
        dados.put("descricao", deck.getDescricao());
        dados.put("quantidadeCards", deck.getQuantidadeCards());
        dados.put("createdAt", deck.getCreatedAt());
        dados.put("updatedAt", deck.getUpdatedAt());
        return dados;
    }

    /** Aproveita a consulta do próprio usuário e corrige somente os índices que estão desatualizados. */
    private void indexarDecksAntigos(String uid, List<DocumentSnapshot> documentos) {
        if (!uid.equals(FirebaseAuth.getInstance().getUid())) return;
        List<DocumentReference> pendentes = new ArrayList<>();
        for (DocumentSnapshot doc : documentos) {
            String titulo = doc.get("titulo") instanceof String ? (String) doc.get("titulo") : "";
            if (!TextoPesquisa.normalizar(titulo).equals(doc.get("tituloBusca"))) {
                pendentes.add(doc.getReference());
            }
        }
        for (int inicio = 0; inicio < pendentes.size(); inicio += 100) {
            List<DocumentReference> lote = new ArrayList<>(pendentes.subList(inicio,
                    Math.min(inicio + 100, pendentes.size())));
            firestore.runTransaction(transaction -> {
                if (!uid.equals(FirebaseAuth.getInstance().getUid())) return null;
                List<DocumentSnapshot> atuais = new ArrayList<>();
                // Relê os títulos para não publicar uma chave antiga se o deck foi editado nesse intervalo.
                for (DocumentReference referencia : lote) atuais.add(transaction.get(referencia));
                for (DocumentSnapshot atual : atuais) {
                    if (!atual.exists() || !uid.equals(atual.getString("ownerId"))) continue;
                    String titulo = atual.get("titulo") instanceof String ? (String) atual.get("titulo") : "";
                    String chave = TextoPesquisa.normalizar(titulo);
                    if (!chave.equals(atual.get("tituloBusca"))) {
                        transaction.update(atual.getReference(), "tituloBusca", chave);
                    }
                }
                return null;
            }).addOnFailureListener(erro -> Log.w("PESQUISA", "Indexação pendente: "
                    + erro.getClass().getSimpleName()));
        }
    }

    /** Altera somente o selo. As regras conferem a claim de professor no curso do documento. */
    public void verificarDeck(String deckId, AtualizarDeckCallback callback) {
        if (!BolhaMoodle.COLECAO_DECKS_MOODLE.equals(BolhaMoodle.colecaoDoDeck(deckId))) {
            callback.onError(new Exception("A verificação exige um curso do Moodle.")); return;
        }
        DocumentReference referencia = firestore.collection(BolhaMoodle.COLECAO_DECKS_MOODLE).document(deckId);
        firestore.runTransaction(transacao -> {
            DocumentSnapshot atual = transacao.get(referencia);
            if (!atual.exists() || Boolean.TRUE.equals(atual.getBoolean("excluindo")))
                throw new IllegalStateException("Este deck não está disponível para verificação.");
            transacao.update(referencia, "verificadoPorProfessor", true);
            return null;
        }).addOnSuccessListener(ignorado -> callback.onSuccess()).addOnFailureListener(callback::onError);
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
