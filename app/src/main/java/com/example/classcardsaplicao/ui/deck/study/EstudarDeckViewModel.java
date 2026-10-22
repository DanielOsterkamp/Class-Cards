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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EstudarDeckViewModel extends ViewModel {

    private static final int LIMITE_NOVOS_POR_SESSAO = 20;

    private final MutableLiveData<Boolean> avaliacaoConcluida = new MutableLiveData<>(false);
    private final DeckRepository deckRepository = new DeckRepository();

    public LiveData<Boolean> getAvaliacaoConcluida() {
        return avaliacaoConcluida;
    }

    private final MutableLiveData<Boolean> sessaoConcluida = new MutableLiveData<>(false);

    private final ProgressoRepository progressoRepository = new ProgressoRepository();

    private final FsrsScheduler fsrsScheduler = new FsrsScheduler();

    private final MutableLiveData<Deck> deck = new MutableLiveData<>();

    private final MutableLiveData<List<Flashcard>> flashcards = new MutableLiveData<>(new ArrayList<>());

    private final MutableLiveData<String> erro = new MutableLiveData<>();


    private final MutableLiveData<ProgressoFlashcard> progressoAtual = new MutableLiveData<>();

    private final MutableLiveData<Boolean> salvandoProgresso = new MutableLiveData<>(false);

    private String deckIdAtual;

    private int indiceAtual = 0;

    private boolean mostrandoResposta = false;


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

    public LiveData<Boolean> getSessaoConcluida() {
        return sessaoConcluida;
    }


    public boolean isMostrandoResposta() {
        return mostrandoResposta;
    }

    // Usado pelo modo dissertativo para preservar a sessão ao recriar a tela.
    public void carregarDeckSeNecessario(String deckId) {
        if (deckId != null && deckId.equals(deckIdAtual)) {
            return;
        }
        carregarDeck(deckId);
    }

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

                        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                        progressoRepository.buscarProgressosDoDeck(
                                userId,
                                deckIdAtual,
                                new ProgressoRepository.BuscarProgressosDeckCallback() {

                                    @Override
                                    public void onSuccess(List<ProgressoFlashcard> progressos) {
                                        montarSessaoDeEstudo(lista, progressos);
                                    }

                                    @Override
                                    public void onError(Exception exception) {
                                        erro.setValue("Erro ao carregar o progresso dos flashcards.");
                                    }
                                }
                        );

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


    public void alternarLado() {
        mostrandoResposta = !mostrandoResposta;
    }

    boolean proximoFlashcard() {

        List<Flashcard> lista = flashcards.getValue();

        if (lista == null || lista.isEmpty()) {
            sessaoConcluida.setValue(true);
            return false;
        }

        if (indiceAtual + 1 >= lista.size()) {
            sessaoConcluida.setValue(true);
            return false;
        }

        indiceAtual++;

        mostrandoResposta = false;

        carregarProgressoAtual();
        return false;
    }

    public void carregarProgressoAtual() {

        Flashcard flashcard =
                getFlashcardAtual();

        if (flashcard == null) {
            Log.e("ESTUDO", "Flashcard atual é null");
            return;
        }


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


        progressoRepository.buscarProgresso(
                userId,
                flashcard.getId(),
                new ProgressoRepository.BuscarProgressoCallback() {

                    @Override
                    public void onSuccess(
                            ProgressoFlashcard progresso
                    ) {

                        if (progresso == null) {
                            progresso = new ProgressoFlashcard(userId, deckIdAtual, flashcard.getId());
                        }

                        progressoAtual.setValue(progresso);
                    }

                    @Override
                    public void onError(Exception exception) {

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

    private void montarSessaoDeEstudo(List<Flashcard> todosFlashcards, List<ProgressoFlashcard> progressos) {

        List<Flashcard> flashcardsVencidos = new ArrayList<>();
        List<Flashcard> flashcardsNovos = new ArrayList<>();

        Map<String, ProgressoFlashcard> mapaProgressos = new HashMap<>();

        for (ProgressoFlashcard progresso : progressos) {
            mapaProgressos.put(
                    progresso.getFlashcardId(),
                    progresso
            );
        }

        long agora = System.currentTimeMillis();

        for (Flashcard flashcard : todosFlashcards) {

            ProgressoFlashcard progresso =
                    mapaProgressos.get(flashcard.getId());

            // Nunca estudado
            if (progresso == null) {
                flashcardsNovos.add(flashcard);
                continue;
            }

            // Revisão já está disponível
            if (progresso.getProximaRevisao() <= agora) {
                flashcardsVencidos.add(flashcard);
            }
        }

        // Mais atrasados primeiro
        flashcardsVencidos.sort(
                Comparator.comparingLong(
                        flashcard ->
                                mapaProgressos
                                        .get(flashcard.getId())
                                        .getProximaRevisao()
                )
        );

        List<Flashcard> sessao = new ArrayList<>();

        // Todas as revisões vencidas entram na sessão
                sessao.addAll(flashcardsVencidos);

        // Limita apenas os cards novos
        int quantidadeNovos = Math.min(
                flashcardsNovos.size(),
                LIMITE_NOVOS_POR_SESSAO
        );

        sessao.addAll(
                flashcardsNovos.subList(0, quantidadeNovos)
        );

        flashcards.setValue(sessao);

        indiceAtual = 0;
        mostrandoResposta = false;
        sessaoConcluida.setValue(false);

        if (!sessao.isEmpty()) {
            carregarProgressoAtual();
        }
    }

    public void consumirAvaliacaoConcluida() {
        avaliacaoConcluida.setValue(false);
    }

    public void avaliarFlashcard(Rating rating) {

        ProgressoFlashcard progresso =
                progressoAtual.getValue();


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

        if (Boolean.TRUE.equals(salvandoProgresso.getValue())) {
            return;
        }

        avaliacaoConcluida.setValue(false);
        salvandoProgresso.setValue(true);

        fsrsScheduler.revisar(progresso, rating);

        progressoRepository.salvarProgresso(
                progresso,
                new ProgressoRepository.SalvarProgressoCallback() {

                    @Override
                    public void onSuccess() {
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