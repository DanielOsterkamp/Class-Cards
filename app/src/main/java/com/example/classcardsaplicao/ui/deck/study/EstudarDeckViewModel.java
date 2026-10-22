package com.example.classcardsaplicao.ui.deck.study;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.example.classcardsaplicao.data.DeckRepository;
import com.example.classcardsaplicao.data.EstatisticasRepository;
import com.example.classcardsaplicao.data.ProgressoRepository;
import com.example.classcardsaplicao.model.Deck;
import com.example.classcardsaplicao.model.Flashcard;
import com.example.classcardsaplicao.model.ProgressoFlashcard;
import com.example.classcardsaplicao.model.Rating;
import com.example.classcardsaplicao.model.ResultadoOfensiva;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EstudarDeckViewModel extends ViewModel {
    private static final int LIMITE_NOVOS_POR_SESSAO = 20;
    private final SavedStateHandle estado;
    private final DeckRepository decks = new DeckRepository();
    private final ProgressoRepository progressos = new ProgressoRepository();
    private final EstatisticasRepository estatisticas = new EstatisticasRepository();
    private final MutableLiveData<Deck> deck = new MutableLiveData<>();
    private final MutableLiveData<List<Flashcard>> flashcards = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> erro = new MutableLiveData<>();
    private final MutableLiveData<Boolean> salvando = new MutableLiveData<>(false);
    private final MutableLiveData<ResultadoOfensiva> resultado = new MutableLiveData<>();
    private final String sessaoId;
    private final String userId;
    private String deckId;
    private boolean carregando;
    private boolean carregado;

    public EstudarDeckViewModel(SavedStateHandle estado) {
        this.estado = estado;
        if (!estado.contains("sessaoId")) estado.set("sessaoId", UUID.randomUUID().toString());
        if (!estado.contains("userId")) estado.set("userId", FirebaseAuth.getInstance().getUid());
        sessaoId = estado.get("sessaoId");
        userId = estado.get("userId");
        if (Boolean.TRUE.equals(estado.get("concluida"))) {
            Integer valor = estado.get("ofensiva");
            resultado.setValue(new ResultadoOfensiva(valor == null ? 0 : valor,
                    Boolean.TRUE.equals(estado.get("conquistou"))));
        }
    }

    public LiveData<Deck> getDeck() { return deck; }
    public LiveData<List<Flashcard>> getFlashcards() { return flashcards; }
    public LiveData<String> getErro() { return erro; }
    public LiveData<Boolean> getSalvandoProgresso() { return salvando; }
    public LiveData<ResultadoOfensiva> getResultado() { return resultado; }
    public int getIndiceAtual() {
        Integer indice = estado.get("indice");
        return indice == null ? 0 : indice;
    }
    public boolean isMostrandoResposta() { return Boolean.TRUE.equals(estado.get("resposta")); }
    public void alternarLado() {
        if (!Boolean.TRUE.equals(salvando.getValue()) && resultado.getValue() == null) {
            estado.set("resposta", !isMostrandoResposta());
            publicarFila();
        }
    }

    public void carregarDeck(String id) {
        if (carregando || carregado || resultado.getValue() != null) return;
        if (id == null || id.isEmpty() || userId == null
                || !userId.equals(FirebaseAuth.getInstance().getUid())) {
            erro.setValue("Deck inválido ou usuário não autenticado.");
            return;
        }
        deckId = id;
        carregando = true;
        erro.setValue(null);
        decks.buscarDeckPorId(id, new DeckRepository.BuscarDeckCallback() {
            @Override public void onSuccess(Deck valor) { deck.setValue(valor); }
            @Override public void onError(Exception exception) { erro.setValue("Erro ao carregar o deck."); }
        });
        decks.buscarFlashcardsDoDeck(id, new DeckRepository.BuscarFlashcardsCallback() {
            @Override public void onSuccess(List<Flashcard> lista) {
                ArrayList<String> filaSalva = estado.get("fila");
                if (filaSalva != null) {
                    Map<String, Flashcard> porId = new HashMap<>();
                    for (Flashcard card : lista) porId.put(card.getId(), card);
                    List<Flashcard> fila = new ArrayList<>();
                    for (String cardId : filaSalva) {
                        if (!porId.containsKey(cardId)) {
                            falharCarga("Um card desta sessão foi removido. Volte e inicie outra sessão.");
                            return;
                        }
                        fila.add(porId.get(cardId));
                    }
                    iniciarFila(fila);
                    return;
                }
                progressos.buscarProgressosDoDeck(userId, id, new ProgressoRepository.BuscarProgressosDeckCallback() {
                    @Override public void onSuccess(List<ProgressoFlashcard> valores) { montarSessao(lista, valores); }
                    @Override public void onError(Exception exception) { falharCarga("Erro ao carregar as revisões."); }
                });
            }
            @Override public void onError(Exception exception) { falharCarga("Erro ao carregar os flashcards."); }
        });
    }

    private void falharCarga(String mensagem) {
        carregando = false;
        erro.setValue(mensagem);
    }

    private void montarSessao(List<Flashcard> todos, List<ProgressoFlashcard> valores) {
        Map<String, ProgressoFlashcard> porId = new HashMap<>();
        for (ProgressoFlashcard progresso : valores) porId.put(progresso.getFlashcardId(), progresso);
        List<Flashcard> vencidos = new ArrayList<>();
        List<Flashcard> novos = new ArrayList<>();
        long agora = System.currentTimeMillis();
        for (Flashcard card : todos) {
            ProgressoFlashcard progresso = porId.get(card.getId());
            if (progresso == null) novos.add(card);
            else if (progresso.getProximaRevisao() <= agora) vencidos.add(card);
        }
        vencidos.sort(Comparator.comparingLong(card -> porId.get(card.getId()).getProximaRevisao()));
        vencidos.addAll(novos.subList(0, Math.min(novos.size(), LIMITE_NOVOS_POR_SESSAO)));
        ArrayList<String> ids = new ArrayList<>();
        for (Flashcard card : vencidos) ids.add(card.getId());
        estado.set("fila", ids);
        iniciarFila(vencidos);
    }

    private void iniciarFila(List<Flashcard> fila) {
        carregado = true;
        carregando = false;
        flashcards.setValue(fila);
        // Repete o mesmo recibo se o processo morreu entre a gravação e o callback.
        String pendente = estado.get("ratingPendente");
        if (pendente != null) avaliarFlashcard(Rating.valueOf(pendente));
    }

    public Flashcard getFlashcardAtual() {
        List<Flashcard> fila = flashcards.getValue();
        int indice = getIndiceAtual();
        return fila == null || indice >= fila.size() ? null : fila.get(indice);
    }

    private void publicarFila() { flashcards.setValue(flashcards.getValue()); }

    public void tentarNovamente(String id) {
        if (!carregado) carregarDeck(id);
        else {
            String pendente = estado.get("ratingPendente");
            if (pendente != null) avaliarFlashcard(Rating.valueOf(pendente));
            else { carregado = false; carregarDeck(id); }
        }
    }

    public void avaliarFlashcard(Rating rating) {
        Flashcard card = getFlashcardAtual();
        if (card == null || rating == null || resultado.getValue() != null
                || Boolean.TRUE.equals(salvando.getValue())) return;
        if (userId == null || !userId.equals(FirebaseAuth.getInstance().getUid())) {
            erro.setValue("Usuário não autenticado. Entre novamente.");
            return;
        }
        String pendente = estado.get("ratingPendente");
        if (pendente == null && !isMostrandoResposta()) return;
        Rating ratingEfetivo = pendente == null ? rating : Rating.valueOf(pendente);
        estado.set("ratingPendente", ratingEfetivo.name());
        salvando.setValue(true);
        erro.setValue(null);
        ArrayList<String> fila = estado.get("fila");
        estatisticas.registrarRevisaoTradicional(userId, sessaoId, deckId, fila, card.getId(), ratingEfetivo)
                .addOnSuccessListener(valor -> {
                    estado.remove("ratingPendente");
                    estado.set("indice", getIndiceAtual() + 1);
                    estado.set("resposta", false);
                    if (getIndiceAtual() == fila.size()) {
                        estado.set("ofensiva", valor.getOfensivaAtual());
                        estado.set("conquistou", valor.isOfensivaAlterada());
                        estado.set("concluida", true);
                        resultado.setValue(valor);
                    }
                    publicarFila();
                    salvando.setValue(false);
                })
                .addOnFailureListener(exception -> {
                    salvando.setValue(false);
                    erro.setValue("Não foi possível confirmar a revisão. Tente novamente com conexão à internet.");
                });
    }
}
