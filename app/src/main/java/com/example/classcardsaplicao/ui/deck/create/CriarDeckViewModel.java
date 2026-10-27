package com.example.classcardsaplicao.ui.deck.create;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.classcardsaplicao.data.AiRepository;
import com.example.classcardsaplicao.data.DeckRepository;
import com.example.classcardsaplicao.model.Deck;
import com.example.classcardsaplicao.model.Flashcard;
import com.example.classcardsaplicao.ui.deck.GeracaoFlashcards;

import java.util.ArrayList;
import java.util.List;

public class CriarDeckViewModel extends ViewModel {

    private final DeckRepository deckRepository = new DeckRepository();

    private final MutableLiveData<String> deckCriado = new MutableLiveData<>();
    private final MutableLiveData<String> erro = new MutableLiveData<>();

    private final GeracaoFlashcards geracao;
    private final MutableLiveData<Boolean> salvando = new MutableLiveData<>(false);

    public LiveData<Boolean> getSalvando() { return salvando; }

    public CriarDeckViewModel() {
        this(new AiRepository());
    }

    // Permite testar os estados de carregamento sem enviar requisições à IA.
    CriarDeckViewModel(AiRepository aiRepository) {
        geracao = new GeracaoFlashcards(aiRepository, cards -> {
            List<Flashcard> lista = new ArrayList<>(flashcards.getValue());
            lista.addAll(cards);
            flashcards.setValue(lista);
        }, erro::setValue);
    }

    public LiveData<Boolean> getGerandoFlashcards() {
        return geracao.getGerando();
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
        if (Boolean.TRUE.equals(salvando.getValue())) return;
        if (Boolean.TRUE.equals(getGerandoFlashcards().getValue())) {
            erro.setValue("Aguarde a criação dos flashcards antes de salvar o deck.");
            return;
        }

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

        salvando.setValue(true);
        deckRepository.criarDeck(
                deck,
                lista,
                new DeckRepository.DeckCallback() {
                    @Override
                    public void onSuccess(String deckId) {
                        salvando.setValue(false);
                        deckCriado.setValue(deckId);
                    }

                    @Override
                    public void onError(Exception exception) {
                        salvando.setValue(false);
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
        if (ocupado()) return;
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

    public void gerarFlashcards(String materia, String descricao, String quantidadeTexto) {
        if (Boolean.TRUE.equals(salvando.getValue())) return;
        geracao.gerar(materia, descricao, quantidadeTexto);
    }
    public void adicionarFlashcard(String pergunta, String resposta) {
        if (ocupado()) return;

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
        if (ocupado()) return;

        List<Flashcard> listaAtual =
                new ArrayList<>(flashcards.getValue());

        if (position < 0 || position >= listaAtual.size()) {
            return;
        }

        listaAtual.remove(position);

        flashcards.setValue(listaAtual);
    }

    private boolean ocupado() {
        return Boolean.TRUE.equals(salvando.getValue()) || Boolean.TRUE.equals(getGerandoFlashcards().getValue());
    }

    @Override protected void onCleared() { geracao.encerrar(); }
}
