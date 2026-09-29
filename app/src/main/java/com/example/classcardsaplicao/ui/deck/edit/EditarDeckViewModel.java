package com.example.classcardsaplicao.ui.deck.edit;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.classcardsaplicao.data.DeckRepository;
import com.example.classcardsaplicao.model.Deck;
import com.example.classcardsaplicao.model.Flashcard;

import java.util.ArrayList;
import java.util.List;

public class EditarDeckViewModel extends ViewModel {

    private final MutableLiveData<Boolean> salvo =
            new MutableLiveData<>(false);

    public LiveData<Boolean> getSalvo() {
        return salvo;
    }

    private final DeckRepository deckRepository = new DeckRepository();

    private final MutableLiveData<Deck> deck = new MutableLiveData<>();

    private final MutableLiveData<List<Flashcard>> flashcards =
            new MutableLiveData<>(new ArrayList<>());

    private final MutableLiveData<String> erro =
            new MutableLiveData<>();

    public LiveData<Deck> getDeck() {
        return deck;
    }

    public LiveData<List<Flashcard>> getFlashcards() {
        return flashcards;
    }

    public LiveData<String> getErro() {
        return erro;
    }

    public void carregarDeck(String deckId) {

        if (deckId == null || deckId.isEmpty()) {
            erro.setValue("Deck inválido.");
            return;
        }

        deckRepository.buscarDeckPorId(
                deckId,
                new DeckRepository.BuscarDeckCallback() {

                    @Override
                    public void onSuccess(Deck deckCarregado) {
                        deck.setValue(deckCarregado);
                    }

                    @Override
                    public void onError(Exception exception) {
                        erro.setValue("Erro ao carregar o deck.");
                    }
                }
        );

        deckRepository.buscarFlashcardsDoDeck(
                deckId,
                new DeckRepository.BuscarFlashcardsCallback() {

                    @Override
                    public void onSuccess(List<Flashcard> lista) {

                        Log.d(
                                "EDITAR_DECK",
                                "Flashcards carregados: " + lista.size()
                        );

                        flashcards.setValue(lista);
                    }

                    @Override
                    public void onError(Exception exception) {
                        erro.setValue("Erro ao carregar os flashcards.");
                    }
                }
        );
    }

    public void adicionarFlashcard(String pergunta, String resposta) {
        List<Flashcard> listaAtual = flashcards.getValue();

        List<Flashcard> novaLista = listaAtual == null
                ? new ArrayList<>()
                : new ArrayList<>(listaAtual);

        novaLista.add(
                new Flashcard(
                        null,
                        pergunta.trim(),
                        resposta.trim(),
                        System.currentTimeMillis()
                )
        );

        flashcards.setValue(novaLista);
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

    public void salvarAlteracoes(
            String titulo,
            String materia
    ) {
        Deck deckAtual = deck.getValue();
        List<Flashcard> lista = flashcards.getValue();

        if (deckAtual == null) {
            erro.setValue("Deck não carregado.");
            return;
        }

        if (titulo == null || titulo.trim().isEmpty()) {
            erro.setValue("Informe o nome do deck.");
            return;
        }

        if (materia == null || materia.trim().isEmpty()) {
            erro.setValue("Informe a matéria.");
            return;
        }

        if (lista == null || lista.isEmpty()) {
            erro.setValue("O deck precisa ter pelo menos um flashcard.");
            return;
        }

        deckAtual.setTitulo(titulo.trim());
        deckAtual.setMateria(materia.trim());

        deckRepository.atualizarDeck(
                deckAtual,
                lista,
                new DeckRepository.AtualizarDeckCallback() {

                    @Override
                    public void onSuccess() {
                        salvo.setValue(true);
                    }

                    @Override
                    public void onError(Exception exception) {
                        erro.setValue("Erro ao salvar alterações.");
                    }
                }
        );
    }

    public void removerFlashcard(int position) {
        List<Flashcard> listaAtual = flashcards.getValue();

        if (listaAtual == null
                || position < 0
                || position >= listaAtual.size()) {
            return;
        }

        List<Flashcard> novaLista = new ArrayList<>(listaAtual);
        novaLista.remove(position);

        flashcards.setValue(novaLista);
    }
}