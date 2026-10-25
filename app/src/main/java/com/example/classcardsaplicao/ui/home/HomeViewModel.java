package com.example.classcardsaplicao.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.classcardsaplicao.data.AuthRepository;
import com.example.classcardsaplicao.data.DeckRepository;
import com.example.classcardsaplicao.data.RankingRepository;
import com.example.classcardsaplicao.model.Deck;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

public class HomeViewModel extends ViewModel {

    private final AuthRepository authRepository;
    private final DeckRepository deckRepository;

    private final MutableLiveData<List<Deck>> decks = new MutableLiveData<>();
    private final MutableLiveData<String> erro = new MutableLiveData<>();

    public HomeViewModel() {
        authRepository = new AuthRepository();
        deckRepository = new DeckRepository();
    }

    public LiveData<List<Deck>> getDecks() {
        return decks;
    }

    public LiveData<String> getErro() {
        return erro;
    }

    public void carregarDecks() {

        FirebaseUser usuario = authRepository.getUsuarioAtual();

        if (usuario == null) {
            erro.setValue("Usuário não autenticado.");
            return;
        }

        RankingRepository.atualizarEmSegundoPlano();

        deckRepository.buscarDecksDoUsuario(
                usuario.getUid(),
                new DeckRepository.BuscarDecksCallback() {

                    @Override
                    public void onSuccess(List<Deck> listaDecks) {
                        decks.setValue(listaDecks);
                    }

                    @Override
                    public void onError(Exception exception) {
                        erro.setValue(exception.getMessage());
                    }
                }
        );
    }
}