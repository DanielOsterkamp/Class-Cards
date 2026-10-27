package com.example.classcardsaplicao.ui.deck;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.classcardsaplicao.data.AiRepository;
import com.example.classcardsaplicao.model.Flashcard;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Validação e estados da geração por IA compartilhados pela criação e pela edição. */
public final class GeracaoFlashcards {
    private final AiRepository repository;
    private final Consumer<List<Flashcard>> receberCards;
    private final Consumer<String> receberErro;
    private final MutableLiveData<Boolean> gerando = new MutableLiveData<>(false);
    private final Handler principal = new Handler(Looper.getMainLooper());
    private boolean encerrada;
    private int tentativa;

    public GeracaoFlashcards(AiRepository repository, Consumer<List<Flashcard>> receberCards,
            Consumer<String> receberErro) {
        this.repository = repository;
        this.receberCards = receberCards;
        this.receberErro = receberErro;
    }

    public LiveData<Boolean> getGerando() { return gerando; }

    public void gerar(String materia, String descricao, String quantidadeTexto) {
        if (encerrada || Boolean.TRUE.equals(gerando.getValue())) return;
        if (materia == null || materia.trim().isEmpty()) {
            receberErro.accept("Informe a matéria.");
            return;
        }
        if (descricao == null || descricao.trim().isEmpty()) {
            receberErro.accept("Informe a descrição.");
            return;
        }
        int quantidade;
        try {
            quantidade = Integer.parseInt(quantidadeTexto == null ? "" : quantidadeTexto.trim());
        } catch (NumberFormatException erro) {
            receberErro.accept("Informe uma quantidade válida.");
            return;
        }
        if (quantidade < 1 || quantidade > 30) {
            receberErro.accept("A quantidade deve estar entre 1 e 30.");
            return;
        }

        int carga = ++tentativa;
        gerando.setValue(true);
        try {
            repository.gerarFlashcards(materia.trim(), descricao.trim(), quantidade, new AiRepository.AiCallback() {
                @Override public void onSuccess(List<Flashcard> cards) {
                    naPrincipal(() -> {
                        if (!valida(carga)) return;
                        List<Flashcard> novos = new ArrayList<>();
                        if (cards != null) {
                            for (Flashcard card : cards) {
                                if (card == null || card.getPergunta() == null || card.getPergunta().trim().isEmpty()
                                        || card.getResposta() == null || card.getResposta().trim().isEmpty()) {
                                    falhar(carga, "A IA retornou um flashcard inválido. Tente novamente.");
                                    return;
                                }
                                // Cards gerados são novos: nunca reutilizam o ID de um card já estudado.
                                novos.add(new Flashcard(null, card.getPergunta().trim(),
                                        card.getResposta().trim(), System.currentTimeMillis()));
                            }
                        }
                        if (novos.isEmpty()) {
                            falhar(carga, "A IA não retornou flashcards. Tente novamente.");
                            return;
                        }
                        receberCards.accept(novos);
                        gerando.setValue(false);
                    });
                }

                @Override public void onError(Exception erro) {
                    naPrincipal(() -> falhar(carga, erro != null && erro.getMessage() != null
                            ? erro.getMessage() : "Erro ao gerar flashcards."));
                }
            });
        } catch (Exception erro) {
            falhar(carga, "Não foi possível iniciar a criação dos flashcards. Tente novamente.");
        }
    }

    private boolean valida(int carga) {
        return !encerrada && carga == tentativa && Boolean.TRUE.equals(gerando.getValue());
    }

    private void falhar(int carga, String mensagem) {
        if (!valida(carga)) return;
        gerando.setValue(false);
        receberErro.accept(mensagem);
    }

    private void naPrincipal(Runnable acao) {
        if (Looper.myLooper() == Looper.getMainLooper()) acao.run();
        else principal.post(acao);
    }

    public void encerrar() {
        encerrada = true;
        tentativa++;
        principal.removeCallbacksAndMessages(null);
    }
}
