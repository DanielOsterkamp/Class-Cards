package com.example.classcardsaplicao.ui.profile;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.classcardsaplicao.data.AuthRepository;
import com.example.classcardsaplicao.data.DeckRepository;
import com.example.classcardsaplicao.data.UserRepository;
import com.example.classcardsaplicao.model.Deck;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

public class PerfilViewModel extends ViewModel {

    private final AuthRepository authRepository;
    private final UserRepository userRepository;
    private final DeckRepository deckRepository;

    private final MutableLiveData<List<Deck>> decks =
            new MutableLiveData<>();


    private final MutableLiveData<String> nome = new MutableLiveData<>();
    private final MutableLiveData<String> fotoUrl = new MutableLiveData<>();
    private final MutableLiveData<String> erro = new MutableLiveData<>();

    public PerfilViewModel() {
        authRepository = new AuthRepository();
        userRepository = new UserRepository();
        deckRepository = new DeckRepository();
    }

    public LiveData<String> getNome() {
        return nome;
    }

    public LiveData<List<Deck>> getDecks() {
        return decks;
    }

    public LiveData<String> getFotoUrl() {
        return fotoUrl;
    }

    public LiveData<String> getErro() {
        return erro;
    }

    public void carregarPerfil() {
        FirebaseUser usuario = authRepository.getUsuarioAtual();

        if (usuario == null) {
            erro.setValue("Usuário não autenticado.");
            return;
        }

        if (usuario.getPhotoUrl() != null) {
            fotoUrl.setValue(usuario.getPhotoUrl().toString());
        }

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

        userRepository.buscarUsuario(
                usuario.getUid(),
                new UserRepository.BuscarUsuarioCallback() {

                    @Override
                    public void onSuccess(
                            String nomeUsuario,
                            String email,
                            String urlFoto
                    ) {
                        nome.setValue(nomeUsuario);
                        fotoUrl.setValue(urlFoto);
                    }

                    @Override
                    public void onError(Exception exception) {
                        erro.setValue(exception.getMessage());
                    }
                }
        );
    }
}