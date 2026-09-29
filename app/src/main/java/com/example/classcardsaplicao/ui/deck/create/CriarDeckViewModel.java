package com.example.classcardsaplicao.ui.deck.create;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.classcardsaplicao.data.AiRepository;
import com.example.classcardsaplicao.data.DeckRepository;
import com.example.classcardsaplicao.model.Deck;
import com.example.classcardsaplicao.model.Flashcard;

import java.util.ArrayList;
import java.util.List;

public class CriarDeckViewModel extends ViewModel {

    private final DeckRepository deckRepository = new DeckRepository();

    private final MutableLiveData<String> deckCriado = new MutableLiveData<>();
    private final MutableLiveData<String> erro = new MutableLiveData<>();

    private final AiRepository aiRepository = new AiRepository();

    private final MutableLiveData<Boolean> gerandoFlashcards =
            new MutableLiveData<>(false);

    public LiveData<Boolean> getGerandoFlashcards() {
        return gerandoFlashcards;
    }

    public LiveData<String> getDeckCriado() {
        return deckCriado;
    }

    public LiveData<String> getErro() {
        return erro;
    }

    private final MutableLiveData<List<Flashcard>> flashcards =
            new MutableLiveData<>(new ArrayList<>());

    public LiveData<List<Flashcard>> getFlashcards() {
        return flashcards;
    }

    public void salvarDeck(
            String ownerId,
            String nome,
            String materia
    ) {
        if (nome == null || nome.trim().isEmpty()) {
            erro.setValue("Informe o nome do deck.");
            return;
        }

        if (materia == null || materia.trim().isEmpty()) {
            erro.setValue("Informe a matéria.");
            return;
        }

        List<Flashcard> lista = flashcards.getValue();

        if (lista == null || lista.isEmpty()) {
            erro.setValue("Adicione pelo menos um flashcard.");
            return;
        }

        long agora = System.currentTimeMillis();

        Deck deck = new Deck(
                null,
                ownerId,
                nome.trim(),
                materia.trim(),
                null,
                lista.size(),
                agora,
                agora
        );

        deckRepository.criarDeck(
                deck,
                lista,
                new DeckRepository.DeckCallback() {
                    @Override
                    public void onSuccess(String deckId) {
                        deckCriado.setValue(deckId);
                    }

                    @Override
                    public void onError(Exception exception) {
                        erro.setValue(exception.getMessage());
                    }
                }
        );
    }

    public void editarFlashcard(
            int position,
            String pergunta,
            String resposta
    ) {
        List<Flashcard> listaAtual = flashcards.getValue();

        if (listaAtual == null
                || position < 0
                || position >= listaAtual.size()) {
            return;
        }

        List<Flashcard> novaLista = new ArrayList<>(listaAtual);

        Flashcard flashcard = novaLista.get(position);

        flashcard.setPergunta(pergunta.trim());
        flashcard.setResposta(resposta.trim());

        flashcards.setValue(novaLista);
    }

    public void gerarFlashcards(
            String materia,
            String descricao,
            String quantidadeTexto
    ) {
        if (materia == null || materia.trim().isEmpty()) {
            erro.setValue("Informe a matéria.");
            return;
        }

        if (descricao == null || descricao.trim().isEmpty()) {
            erro.setValue("Informe a descrição.");
            return;
        }

        int quantidade;

        try {
            quantidade = Integer.parseInt(quantidadeTexto);
        } catch (NumberFormatException exception) {
            erro.setValue("Informe uma quantidade válida.");
            return;
        }

        if (quantidade < 1 || quantidade > 30) {
            erro.setValue("A quantidade deve estar entre 1 e 30.");
            return;
        }

        gerandoFlashcards.setValue(true);

        aiRepository.gerarFlashcards(
                materia.trim(),
                descricao.trim(),
                quantidade,
                new AiRepository.AiCallback() {

                    @Override
                    public void onSuccess(List<Flashcard> cardsGerados) {

                        List<Flashcard> listaAtual =
                                new ArrayList<>(flashcards.getValue());

                        listaAtual.addAll(cardsGerados);

                        flashcards.postValue(listaAtual);
                        gerandoFlashcards.postValue(false);
                    }

                    @Override
                    public void onError(Exception exception) {
                        gerandoFlashcards.postValue(false);

                        erro.postValue(
                                exception.getMessage() != null
                                        ? exception.getMessage()
                                        : "Erro ao gerar flashcards."
                        );
                    }
                }
        );
    }

    public void adicionarFlashcard(String pergunta, String resposta) {

        List<Flashcard> listaAtual =
                new ArrayList<>(flashcards.getValue());

        Flashcard flashcard = new Flashcard(
                null,
                pergunta,
                resposta,
                System.currentTimeMillis()
        );

        listaAtual.add(flashcard);

        flashcards.setValue(listaAtual);
    }

    public void removerFlashcard(int position) {

        List<Flashcard> listaAtual =
                new ArrayList<>(flashcards.getValue());

        if (position < 0 || position >= listaAtual.size()) {
            return;
        }

        listaAtual.remove(position);

        flashcards.setValue(listaAtual);
    }
}