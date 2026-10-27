package com.example.classcardsaplicao.ui.deck.edit;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.classcardsaplicao.data.AiRepository;
import com.example.classcardsaplicao.data.DeckRepository;
import com.example.classcardsaplicao.model.Deck;
import com.example.classcardsaplicao.model.Flashcard;
import com.example.classcardsaplicao.ui.deck.GeracaoFlashcards;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class EditarDeckViewModel extends ViewModel {
    private final DeckRepository repository;
    private final Supplier<String> usuarioAtual;
    private final GeracaoFlashcards geracao;
    private final MutableLiveData<Deck> deck = new MutableLiveData<>();
    private final MutableLiveData<List<Flashcard>> flashcards = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> erro = new MutableLiveData<>();
    private final MutableLiveData<Boolean> salvo = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> salvando = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> pronto = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> carregando = new MutableLiveData<>(false);
    private String idCarregado;
    private String uidCarregado;
    private int versao;
    private boolean encerrado;

    public EditarDeckViewModel() {
        this(new DeckRepository(), new AiRepository(), () -> FirebaseAuth.getInstance().getUid());
    }

    // Fontes substituíveis nos testes: nenhuma chamada real à IA ou ao banco é necessária.
    EditarDeckViewModel(DeckRepository repository, AiRepository ia, Supplier<String> usuarioAtual) {
        this.repository = repository;
        this.usuarioAtual = usuarioAtual;
        geracao = new GeracaoFlashcards(ia, novos -> {
            if (!dono()) { erro.setValue("A conta mudou. Abra o deck novamente."); return; }
            List<Flashcard> lista = new ArrayList<>(flashcards.getValue());
            lista.addAll(novos);
            flashcards.setValue(lista);
        }, erro::setValue);
    }

    public LiveData<Deck> getDeck() { return deck; }
    public LiveData<List<Flashcard>> getFlashcards() { return flashcards; }
    public LiveData<String> getErro() { return erro; }
    public LiveData<Boolean> getSalvo() { return salvo; }
    public LiveData<Boolean> getSalvando() { return salvando; }
    public LiveData<Boolean> getPronto() { return pronto; }
    public LiveData<Boolean> getCarregando() { return carregando; }
    public LiveData<Boolean> getGerandoFlashcards() { return geracao.getGerando(); }

    public void carregarDeck(String id) {
        if (encerrado || Boolean.TRUE.equals(salvando.getValue())
                || Boolean.TRUE.equals(getGerandoFlashcards().getValue())) return;
        // Recriar a interface não deve apagar cards adicionados ou editados ainda não salvos.
        if (id != null && id.equals(idCarregado) && java.util.Objects.equals(uidCarregado, usuarioAtual.get())
                && (Boolean.TRUE.equals(pronto.getValue()) || Boolean.TRUE.equals(carregando.getValue()))) return;
        String uid = usuarioAtual.get();
        if (id == null || id.isEmpty() || uid == null) {
            erro.setValue("Deck inválido ou usuário não autenticado.");
            return;
        }
        int carga = ++versao;
        idCarregado = id;
        uidCarregado = uid;
        pronto.setValue(false);
        carregando.setValue(true);
        erro.setValue(null);
        repository.buscarDeckPorId(id, new DeckRepository.BuscarDeckCallback() {
            @Override public void onSuccess(Deck encontrado) {
                if (!valida(carga, uid)) return;
                if (!uid.equals(encontrado.getOwnerId())) {
                    falharCarga("Somente o dono pode editar este deck.");
                    return;
                }
                if (encontrado.isExcluindo()) {
                    falharCarga("Este deck tem uma exclusão pendente. Volte aos detalhes para tentar excluir novamente.");
                    return;
                }
                deck.setValue(encontrado);
                repository.buscarFlashcardsDoDeck(id, new DeckRepository.BuscarFlashcardsCallback() {
                    @Override public void onSuccess(List<Flashcard> lista) {
                        if (!valida(carga, uid)) return;
                        flashcards.setValue(new ArrayList<>(lista));
                        pronto.setValue(true);
                        carregando.setValue(false);
                    }
                    @Override public void onError(Exception exception) {
                        if (valida(carga, uid)) falharCarga("Erro ao carregar os flashcards. Abra o deck novamente.");
                    }
                });
            }
            @Override public void onError(Exception exception) {
                if (valida(carga, uid)) falharCarga("Erro ao carregar o deck. Abra o deck novamente.");
            }
        });
    }

    private void falharCarga(String mensagem) {
        carregando.setValue(false);
        pronto.setValue(false);
        erro.setValue(mensagem);
    }

    public void gerarFlashcards(String materia, String descricao, String quantidade) {
        if (!podeAlterar()) return;
        geracao.gerar(materia, descricao, quantidade);
    }

    public void adicionarFlashcard(String pergunta, String resposta) {
        if (!podeAlterar()) return;
        List<Flashcard> lista = new ArrayList<>(flashcards.getValue());
        lista.add(new Flashcard(null, pergunta.trim(), resposta.trim(), System.currentTimeMillis()));
        flashcards.setValue(lista);
    }

    public void editarFlashcard(int posicao, String pergunta, String resposta) {
        if (!podeAlterar()) return;
        List<Flashcard> lista = new ArrayList<>(flashcards.getValue());
        if (posicao < 0 || posicao >= lista.size()) return;
        Flashcard antigo = lista.get(posicao);
        lista.set(posicao, new Flashcard(antigo.getId(), pergunta.trim(), resposta.trim(), antigo.getCreatedAt()));
        flashcards.setValue(lista);
    }

    public void removerFlashcard(int posicao) {
        if (!podeAlterar()) return;
        List<Flashcard> lista = new ArrayList<>(flashcards.getValue());
        if (posicao < 0 || posicao >= lista.size()) return;
        lista.remove(posicao);
        flashcards.setValue(lista);
    }

    public void salvarAlteracoes(String titulo, String materia) {
        if (!podeAlterar()) return;
        if (titulo == null || titulo.trim().isEmpty()) { erro.setValue("Informe o nome do deck."); return; }
        if (materia == null || materia.trim().isEmpty()) { erro.setValue("Informe a matéria."); return; }
        List<Flashcard> lista = new ArrayList<>(flashcards.getValue());
        if (lista.isEmpty()) { erro.setValue("O deck precisa ter pelo menos um flashcard."); return; }
        Deck atual = deck.getValue();
        Deck atualizado = new Deck(atual.getId(), atual.getOwnerId(), titulo.trim(), materia.trim(),
                atual.getDescricao(), lista.size(), atual.getCreatedAt(), System.currentTimeMillis());
        int carga = versao;
        String uid = usuarioAtual.get();
        salvando.setValue(true);
        repository.atualizarDeck(atualizado, lista, new DeckRepository.AtualizarDeckCallback() {
            @Override public void onSuccess() {
                if (!valida(carga, uid)) return;
                salvando.setValue(false);
                salvo.setValue(true);
            }
            @Override public void onError(Exception exception) {
                if (!valida(carga, uid)) return;
                salvando.setValue(false);
                erro.setValue("Erro ao salvar alterações. Tente novamente.");
            }
        });
    }

    private boolean dono() {
        Deck atual = deck.getValue();
        String uid = usuarioAtual.get();
        return atual != null && uid != null && uid.equals(atual.getOwnerId());
    }

    private boolean podeAlterar() {
        if (encerrado || Boolean.TRUE.equals(salvando.getValue())
                || Boolean.TRUE.equals(getGerandoFlashcards().getValue())) return false;
        if (!Boolean.TRUE.equals(pronto.getValue()) || !dono()) {
            erro.setValue("Aguarde o carregamento e confirme que este deck pertence à sua conta.");
            return false;
        }
        return true;
    }

    private boolean valida(int carga, String uid) {
        return !encerrado && carga == versao && uid != null && uid.equals(usuarioAtual.get());
    }

    @Override protected void onCleared() {
        encerrado = true;
        versao++;
        geracao.encerrar();
    }
}
