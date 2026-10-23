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
import java.util.TimeZone;
import com.example.classcardsaplicao.data.GamificacaoRepository;
import com.example.classcardsaplicao.model.RegrasGamificacao;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class PerfilViewModel extends ViewModel {

    private final MutableLiveData<Long> xp = new MutableLiveData<>();
    private final MutableLiveData<Long> ofensiva = new MutableLiveData<>();
    private final MutableLiveData<Long> cardsEstudados = new MutableLiveData<>();
    private ListenerRegistration listenerGamificacao;
    private ListenerRegistration listenerCards;

    public LiveData<Long> getXp() { return xp; }
    public LiveData<Long> getOfensiva() { return ofensiva; }
    public LiveData<Long> getCardsEstudados() { return cardsEstudados; }

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

        removerListeners();
        xp.setValue(null);
        ofensiva.setValue(null);
        cardsEstudados.setValue(null);
        if (usuario == null) {
            erro.setValue("Usuário não autenticado.");
            return;
        }

        observarGamificacao(usuario.getUid());
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

    private void observarGamificacao(String uid) {
        com.google.firebase.firestore.DocumentReference usuario = FirebaseFirestore.getInstance()
                .collection("users").document(uid);
        listenerGamificacao = usuario.collection("gamificacao").document("resumo")
                .addSnapshotListener((doc, exception) -> {
                    if (authRepository.getUsuarioAtual() == null
                            || !uid.equals(authRepository.getUsuarioAtual().getUid())) return;
                    if (exception != null) {
                        xp.setValue(null);
                        ofensiva.setValue(null);
                        erro.setValue("Não foi possível carregar XP e ofensiva.");
                        return;
                    }
                    if (doc == null) return;
                    xp.setValue(GamificacaoRepository.numero(doc, "xp"));
                    long agora = System.currentTimeMillis();
                    TimeZone zona = TimeZone.getDefault();
                    ofensiva.setValue(RegrasGamificacao.ofensivaVisivel(
                            GamificacaoRepository.numero(doc, "ofensiva"), doc.getString("ultimoDiaEstudo"),
                            RegrasGamificacao.dia(agora, zona, 0), RegrasGamificacao.dia(agora, zona, -1)));
                });
        // A contagem inclui cards distintos já revisados nos dois modos.
        listenerCards = usuario.collection("progressoFlashcards").addSnapshotListener((snapshot, exception) -> {
            if (authRepository.getUsuarioAtual() == null
                    || !uid.equals(authRepository.getUsuarioAtual().getUid())) return;
            if (exception != null) {
                cardsEstudados.setValue(null);
                erro.setValue("Não foi possível carregar os cards estudados.");
                return;
            }
            if (snapshot == null) return;
            long quantidade = 0;
            for (com.google.firebase.firestore.DocumentSnapshot card : snapshot.getDocuments()) {
                if (GamificacaoRepository.numero(card, "repeticoes") > 0
                        || GamificacaoRepository.numero(card, "ultimaRevisao") > 0) quantidade++;
            }
            cardsEstudados.setValue(quantidade);
        });
    }

    private void removerListeners() {
        if (listenerGamificacao != null) listenerGamificacao.remove();
        if (listenerCards != null) listenerCards.remove();
    }

    @Override protected void onCleared() { removerListeners(); }
}