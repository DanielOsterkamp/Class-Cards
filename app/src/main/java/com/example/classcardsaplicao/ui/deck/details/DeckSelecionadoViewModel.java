package com.example.classcardsaplicao.ui.deck.details;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.classcardsaplicao.data.DeckRepository;
import com.example.classcardsaplicao.model.Deck;

public class DeckSelecionadoViewModel extends ViewModel {

    private final DeckRepository deckRepository;

    private final MutableLiveData<Deck> deck = new MutableLiveData<>();
    private final MutableLiveData<String> erro = new MutableLiveData<>();

    public DeckSelecionadoViewModel() {
        deckRepository = new DeckRepository();
    }

    public LiveData<Deck> getDeck() {
        return deck;
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
                    public void onSuccess(Deck deckEncontrado) {
                        deck.setValue(deckEncontrado);
                    }

                    @Override
                    public void onError(Exception exception) {
                        erro.setValue(exception.getMessage());
                    }
                }
        );
    }
}