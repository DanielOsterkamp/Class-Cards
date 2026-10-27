package com.example.classcardsaplicao.ui.deck.details;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.classcardsaplicao.data.DeckRepository;
import com.example.classcardsaplicao.data.RankingRepository;
import com.example.classcardsaplicao.data.UserRepository;
import com.example.classcardsaplicao.model.Deck;
import com.google.firebase.auth.FirebaseAuth;

import java.util.function.Supplier;

public class DeckSelecionadoViewModel extends ViewModel {
    private final DeckRepository repository;
    private final RankingRepository rankings;
    private final Supplier<String> usuarioAtual;
    private final MutableLiveData<Deck> deck = new MutableLiveData<>();
    private final MutableLiveData<String> erro = new MutableLiveData<>();
    private final MutableLiveData<String> nomeCriador = new MutableLiveData<>("Carregando criador...");
    private final MutableLiveData<Boolean> excluindo = new MutableLiveData<>(false);
    private final MutableLiveData<String> excluido = new MutableLiveData<>();
    private int versao;
    private boolean encerrado;

    public DeckSelecionadoViewModel() {
        this(new DeckRepository(), new RankingRepository(), () -> FirebaseAuth.getInstance().getUid());
    }

    DeckSelecionadoViewModel(DeckRepository repository, RankingRepository rankings, Supplier<String> usuarioAtual) {
        this.repository = repository;
        this.rankings = rankings;
        this.usuarioAtual = usuarioAtual;
    }

    public LiveData<Deck> getDeck() { return deck; }
    public LiveData<String> getErro() { return erro; }
    public LiveData<String> getNomeCriador() { return nomeCriador; }
    public LiveData<Boolean> getExcluindo() { return excluindo; }
    public LiveData<String> getExcluido() { return excluido; }

    public boolean usuarioEhDono() {
        Deck atual = deck.getValue();
        String uid = usuarioAtual.get();
        return atual != null && uid != null && uid.equals(atual.getOwnerId());
    }

    public void carregarDeck(String id) {
        if (encerrado || Boolean.TRUE.equals(excluindo.getValue()) || excluido.getValue() != null) return;
        if (id == null || id.isEmpty()) { erro.setValue("Deck inválido."); return; }
        int carga = ++versao;
        erro.setValue(null);
        repository.buscarDeckPorId(id, new DeckRepository.BuscarDeckCallback() {
            @Override public void onSuccess(Deck encontrado) {
                if (encerrado || carga != versao) return;
                deck.setValue(encontrado);
                carregarCriador(encontrado.getOwnerId(), carga);
            }
            @Override public void onError(Exception exception) {
                if (encerrado || carga != versao) return;
                deck.setValue(null);
                erro.setValue(exception.getMessage() == null ? "Não foi possível carregar o deck." : exception.getMessage());
            }
        });
    }

    private void carregarCriador(String uid, int carga) {
        if (uid == null || uid.isEmpty()) { nomeCriador.setValue("Criador indisponível"); return; }
        rankings.buscarUsuario(uid).addOnSuccessListener(criador -> {
            if (!encerrado && carga == versao) nomeCriador.setValue(criador.getNome());
        }).addOnFailureListener(exception -> {
            if (encerrado || carga != versao) return;
            if (!uid.equals(usuarioAtual.get())) {
                nomeCriador.setValue("Criador indisponível");
                return;
            }
            // Para a própria conta antiga, o perfil privado é um fallback permitido pelas regras.
            new UserRepository().buscarUsuario(uid, new UserRepository.BuscarUsuarioCallback() {
                @Override public void onSuccess(String nome, String email, String foto) {
                    if (!encerrado && carga == versao && uid.equals(usuarioAtual.get())) {
                        nomeCriador.setValue(nome == null || nome.trim().isEmpty() ? "Estudante" : nome.trim());
                    }
                }
                @Override public void onError(Exception erro) {
                    if (!encerrado && carga == versao) nomeCriador.setValue("Criador indisponível");
                }
            });
        });
    }

    public void excluirDeck() {
        if (encerrado || Boolean.TRUE.equals(excluindo.getValue()) || excluido.getValue() != null) return;
        if (!usuarioEhDono()) { erro.setValue("Somente o dono pode excluir este deck."); return; }
        String id = deck.getValue().getId();
        String uid = usuarioAtual.get();
        versao++; // Ignora respostas de carregamentos iniciados antes da exclusão.
        erro.setValue(null);
        excluindo.setValue(true);
        repository.excluirDeck(id, uid, new DeckRepository.ExcluirDeckCallback() {
            @Override public void onSuccess() {
                if (encerrado) return;
                excluindo.setValue(false);
                excluido.setValue(id);
            }
            @Override public void onError(Exception exception) {
                if (encerrado) return;
                excluindo.setValue(false);
                carregarDeck(id); // Recupera o marcador caso um lote tenha falhado após iniciar a exclusão.
                erro.setValue("Não foi possível excluir o deck. Verifique sua conexão e tente novamente.");
            }
        });
    }

    @Override protected void onCleared() { encerrado = true; versao++; }
}
