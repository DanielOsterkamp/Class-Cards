package com.example.classcardsaplicao.ui.deck.study;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.classcardsaplicao.data.DeckRepository;
import com.example.classcardsaplicao.data.ProgressoRepository;
import com.example.classcardsaplicao.model.Deck;
import com.example.classcardsaplicao.model.Flashcard;
import com.example.classcardsaplicao.model.ProgressoFlashcard;
import com.example.classcardsaplicao.model.FsrsScheduler;
import com.example.classcardsaplicao.model.Rating;
import com.google.firebase.auth.FirebaseAuth;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class EstudarDeckViewModel extends ViewModel {

    // =========================
    // REPOSITORIES / FSRS
    // =========================

    private final MutableLiveData<Boolean> avaliacaoConcluida =
            new MutableLiveData<>(false);
    private final DeckRepository deckRepository =
            new DeckRepository();

    public LiveData<Boolean> getAvaliacaoConcluida() {
        return avaliacaoConcluida;
    }

    private final ProgressoRepository progressoRepository =
            new ProgressoRepository();

    private final FsrsScheduler fsrsScheduler =
            new FsrsScheduler();


    // =========================
    // DADOS DO DECK
    // =========================

    private final MutableLiveData<Deck> deck =
            new MutableLiveData<>();

    private final MutableLiveData<List<Flashcard>> flashcards =
            new MutableLiveData<>(new ArrayList<>());

    private final MutableLiveData<String> erro =
            new MutableLiveData<>();


    // =========================
    // PROGRESSO FSRS
    // =========================

    private final MutableLiveData<ProgressoFlashcard> progressoAtual =
            new MutableLiveData<>();

    private final MutableLiveData<Boolean> salvandoProgresso =
            new MutableLiveData<>(false);


    // =========================
    // ESTADO DA SESSÃO
    // =========================

    private String deckIdAtual;

    private int indiceAtual = 0;

    private boolean mostrandoResposta = false;


    // =========================
    // GETTERS
    // =========================

    public LiveData<Deck> getDeck() {
        return deck;
    }

    public LiveData<List<Flashcard>> getFlashcards() {
        return flashcards;
    }

    public LiveData<String> getErro() {
        return erro;
    }

    public LiveData<ProgressoFlashcard> getProgressoAtual() {
        return progressoAtual;
    }

    public LiveData<Boolean> getSalvandoProgresso() {
        return salvandoProgresso;
    }

    public int getIndiceAtual() {
        return indiceAtual;
    }



    public boolean isMostrandoResposta() {
        return mostrandoResposta;
    }


    // =========================
    // CARREGAR DECK
    // =========================

    public void carregarDeck(String deckId) {

        if (deckId == null || deckId.isEmpty()) {
            erro.setValue("Deck inválido.");
            return;
        }

        this.deckIdAtual = deckId;

        deckRepository.buscarDeckPorId(
                deckId,
                new DeckRepository.BuscarDeckCallback() {

                    @Override
                    public void onSuccess(Deck deckCarregado) {
                        deck.setValue(deckCarregado);
                    }

                    @Override
                    public void onError(Exception exception) {
                        erro.setValue(
                                "Erro ao carregar o deck."
                        );
                    }
                }
        );

        deckRepository.buscarFlashcardsDoDeck(
                deckId,
                new DeckRepository.BuscarFlashcardsCallback() {

                    @Override
                    public void onSuccess(List<Flashcard> lista) {

                        flashcards.setValue(lista);

                        indiceAtual = 0;
                        mostrandoResposta = false;

                        if (!lista.isEmpty()) {
                            carregarProgressoAtual();
                        }
                    }

                    @Override
                    public void onError(Exception exception) {
                        erro.setValue(
                                "Erro ao carregar os flashcards."
                        );
                    }
                }
        );
    }


    // =========================
    // FLASHCARD ATUAL
    // =========================

    public Flashcard getFlashcardAtual() {

        List<Flashcard> lista =
                flashcards.getValue();

        if (lista == null || lista.isEmpty()) {
            return null;
        }

        if (indiceAtual < 0
                || indiceAtual >= lista.size()) {
            return null;
        }

        return lista.get(indiceAtual);
    }


    // =========================
    // VIRAR FLASHCARD
    // =========================

    public void alternarLado() {
        mostrandoResposta = !mostrandoResposta;
    }


    // =========================
    // PRÓXIMO FLASHCARD
    // =========================

    public boolean proximoFlashcard() {

        List<Flashcard> lista =
                flashcards.getValue();

        if (lista == null || lista.isEmpty()) {
            return false;
        }

        if (indiceAtual + 1 >= lista.size()) {
            return false;
        }

        indiceAtual++;
        mostrandoResposta = false;

        progressoAtual.setValue(null);

        carregarProgressoAtual();

        return true;
    }


    // =========================
    // CARREGAR PROGRESSO FSRS
    // =========================

    public void carregarProgressoAtual() {

        Flashcard flashcard =
                getFlashcardAtual();

        if (flashcard == null) {
            Log.e("ESTUDO", "Flashcard atual é null");
            return;
        }

        Log.d(
                "ESTUDO",
                "Carregando progresso do flashcard: "
                        + flashcard.getId()
        );

        String userId =
                FirebaseAuth.getInstance()
                        .getUid();

        if (userId == null) {
            Log.e("ESTUDO", "userId é null");

            erro.setValue(
                    "Usuário não autenticado."
            );

            return;
        }

        Log.d(
                "ESTUDO",
                "Buscando progresso. userId="
                        + userId
                        + " flashcardId="
                        + flashcard.getId()
        );

        progressoRepository.buscarProgresso(
                userId,
                flashcard.getId(),
                new ProgressoRepository.BuscarProgressoCallback() {

                    @Override
                    public void onSuccess(
                            ProgressoFlashcard progresso
                    ) {

                        Log.d(
                                "ESTUDO",
                                "buscarProgresso onSuccess. Existe? "
                                        + (progresso != null)
                        );

                        if (progresso == null) {

                            Log.d(
                                    "ESTUDO",
                                    "Criando progresso NEW"
                            );

                            progresso =
                                    new ProgressoFlashcard(
                                            userId,
                                            deckIdAtual,
                                            flashcard.getId()
                                    );
                        }

                        progressoAtual.setValue(progresso);

                        Log.d(
                                "ESTUDO",
                                "progressoAtual definido"
                        );
                    }

                    @Override
                    public void onError(
                            Exception exception
                    ) {

                        Log.e(
                                "ESTUDO",
                                "Erro ao buscar progresso",
                                exception
                        );

                        erro.setValue(
                                "Erro ao carregar progresso."
                        );
                    }
                }
        );
    }

    public void consumirAvaliacaoConcluida() {
        avaliacaoConcluida.setValue(false);
    }


    // =========================
    // AVALIAR FLASHCARD
    // =========================

    public void avaliarFlashcard(Rating rating) {

        Log.d("ESTUDO", "avaliarFlashcard chamado: " + rating);

        ProgressoFlashcard progresso =
                progressoAtual.getValue();

        Log.d(
                "ESTUDO",
                "progressoAtual = " + progresso
        );

        if (progresso == null) {

            Log.e(
                    "ESTUDO",
                    "Progresso ainda não foi carregado"
            );

            erro.setValue(
                    "Progresso do flashcard não carregado."
            );

            return;
        }

        if (Boolean.TRUE.equals(
                salvandoProgresso.getValue()
        )) {

            Log.d(
                    "ESTUDO",
                    "Já existe salvamento em andamento"
            );

            return;
        }

        avaliacaoConcluida.setValue(false);
        salvandoProgresso.setValue(true);

        Log.d(
                "ESTUDO",
                "Chamando FsrsScheduler.revisar()"
        );

        fsrsScheduler.revisar(
                progresso,
                rating
        );

        Log.d(
                "ESTUDO",
                "FSRS concluído. Salvando no Firestore..."
        );

        progressoRepository.salvarProgresso(
                progresso,
                new ProgressoRepository.SalvarProgressoCallback() {

                    @Override
                    public void onSuccess() {

                        Log.d(
                                "ESTUDO",
                                "Progresso salvo no Firestore"
                        );

                        progressoAtual.setValue(progresso);
                        salvandoProgresso.setValue(false);
                        avaliacaoConcluida.setValue(true);
                    }

                    @Override
                    public void onError(Exception exception) {

                        Log.e(
                                "ESTUDO",
                                "Erro ao salvar progresso",
                                exception
                        );

                        salvandoProgresso.setValue(false);

                        erro.setValue(
                                "Erro ao salvar a revisão."
                        );
                    }
                }
        );
    }
}