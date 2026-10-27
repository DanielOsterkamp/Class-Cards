package com.example.classcardsaplicao.ui.profile;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.classcardsaplicao.data.AuthRepository;
import com.example.classcardsaplicao.data.DeckRepository;
import com.example.classcardsaplicao.data.UserRepository;
import com.example.classcardsaplicao.data.RankingRepository;
import com.example.classcardsaplicao.model.Deck;
import com.example.classcardsaplicao.model.TipoRanking;

import java.util.Collections;
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
    private final RankingRepository rankingRepository = new RankingRepository();
    private final MutableLiveData<Boolean> carregando = new MutableLiveData<>(false);
    private int versao;
    private int carregamentosPendentes;

    public LiveData<Boolean> getCarregando() { return carregando; }

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
        carregarPerfil(null);
    }

    /** Sem UID, abre a própria conta; com outro UID, consulta somente os dados públicos. */
    public void carregarPerfil(String uidSelecionado) {
        FirebaseUser usuario = authRepository.getUsuarioAtual();
        int carga = ++versao;

        removerListeners();
        erro.setValue(null);
        nome.setValue("");
        fotoUrl.setValue("");
        decks.setValue(Collections.emptyList());
        xp.setValue(null);
        ofensiva.setValue(null);
        cardsEstudados.setValue(null);
        if (usuario == null) {
            carregando.setValue(false);
            erro.setValue("Usuário não autenticado.");
            return;
        }

        String uidSessao = usuario.getUid();
        String uidPerfil = uidSelecionado == null || uidSelecionado.isEmpty() ? uidSessao : uidSelecionado;
        carregamentosPendentes = 2;
        carregando.setValue(true);

        if (!uidSessao.equals(uidPerfil)) {
            carregarPerfilPublico(uidPerfil, uidSessao, carga);
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
                        if (!cargaValida(carga, uidSessao)) return;
                        decks.setValue(listaDecks);
                        concluirCarregamento();
                    }

                    @Override
                    public void onError(Exception exception) {
                        if (!cargaValida(carga, uidSessao)) return;
                        erro.setValue("Não foi possível carregar os decks. Tente novamente.");
                        concluirCarregamento();
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
                        if (!cargaValida(carga, uidSessao)) return;
                        nome.setValue(nomeUsuario);
                        fotoUrl.setValue(urlFoto);
                        concluirCarregamento();
                    }

                    @Override
                    public void onError(Exception exception) {
                        if (!cargaValida(carga, uidSessao)) return;
                        erro.setValue("Não foi possível carregar o perfil. Tente novamente.");
                        concluirCarregamento();
                    }
                }
        );
    }

    private void carregarPerfilPublico(String uidPerfil, String uidSessao, int carga) {
        // A projeção do ranking já contém nome, foto e estatísticas que podem ser mostrados a outros usuários.
        rankingRepository.buscarUsuario(uidPerfil).addOnSuccessListener(perfil -> {
            if (!cargaValida(carga, uidSessao)) return;
            nome.setValue(perfil.getNome());
            fotoUrl.setValue(perfil.getFotoUrl());
            long agora = System.currentTimeMillis();
            xp.setValue(perfil.valorEm(TipoRanking.PONTOS, agora));
            ofensiva.setValue(perfil.valorEm(TipoRanking.OFENSIVAS, agora));
            cardsEstudados.setValue(perfil.valorEm(TipoRanking.CARDS_ESTUDADOS, agora));
            concluirCarregamento();
        }).addOnFailureListener(exception -> {
            if (!cargaValida(carga, uidSessao)) return;
            erro.setValue("Não foi possível carregar o perfil público. Ele pode ter sido removido. Tente novamente.");
            concluirCarregamento();
        });

        // Os decks são consultados pelo dono selecionado, independentemente da conta conectada.
        deckRepository.buscarDecksDoUsuario(uidPerfil, new DeckRepository.BuscarDecksCallback() {
            @Override
            public void onSuccess(List<Deck> listaDecks) {
                if (!cargaValida(carga, uidSessao)) return;
                decks.setValue(listaDecks);
                concluirCarregamento();
            }

            @Override
            public void onError(Exception exception) {
                if (!cargaValida(carga, uidSessao)) return;
                erro.setValue("Não foi possível carregar os decks deste usuário. Tente novamente.");
                concluirCarregamento();
            }
        });
    }

    private boolean cargaValida(int carga, String uidSessao) {
        FirebaseUser atual = authRepository.getUsuarioAtual();
        return carga == versao && atual != null && uidSessao.equals(atual.getUid());
    }

    private void concluirCarregamento() {
        if (--carregamentosPendentes == 0) carregando.setValue(false);
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
        listenerGamificacao = null;
        listenerCards = null;
    }

    @Override protected void onCleared() {
        versao++;
        removerListeners();
    }
}
