package com.example.classcardsaplicao.ui.deck.study;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.example.classcardsaplicao.data.AvaliacaoRespostaRepository;
import com.example.classcardsaplicao.data.DeckRepository;
import com.example.classcardsaplicao.data.GamificacaoRepository;
import com.example.classcardsaplicao.data.ProgressoRepository;
import com.example.classcardsaplicao.model.*;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Coordena a sessão dissertativa sem depender das views da tela.
 * Carrega os dados, solicita a avaliação, confirma a revisão e avança somente após o salvamento.
 */
public class EstudarDeckDissertativoViewModel extends ViewModel {

    // Os estados representam as etapas que o Fragment sabe apresentar.
    public enum Estado {
        CARREGANDO,
        RESPOSTA,
        AVALIANDO,
        RESULTADO,
        SALVANDO,
        CONCLUIDA,
        ERRO
    }

    // Limita o tamanho do texto enviado para avaliação.
    public static final int LIMITE_RESPOSTA = 6000;

    // Guarda rascunho, índice, ordem dos IDs, avaliação e tentativa; não é um banco permanente.
    private final SavedStateHandle salvo;

    // Os repositórios separam a coordenação da sessão do acesso aos serviços externos.
    private final DeckRepository decks = new DeckRepository();
    private final ProgressoRepository progressos = new ProgressoRepository();
    private final GamificacaoRepository gamificacao = new GamificacaoRepository();

    // O ViewModel pode alterar os MutableLiveData; a tela recebe apenas LiveData.
    private final MutableLiveData<Estado> estado = new MutableLiveData<>(Estado.CARREGANDO);
    private final MutableLiveData<String> nome = new MutableLiveData<>();
    private final MutableLiveData<String> erro = new MutableLiveData<>();

    // Os objetos dos cards ficam em memória e são buscados novamente na restauração do processo.
    private List<Flashcard> cards = new ArrayList<>();
    private ListenableFuture<?> requisicao;

    // As flags evitam iniciar buscas repetidas. A versão identifica callbacks de uma carga específica.
    private boolean carregado;
    private boolean carregando;
    private int versao;

    /**
     * Recebe o estado salvo pelo Android.
     * O ViewModel sobrevive às rotações; o SavedStateHandle também permite restaurar os dados
     * quando o Android recria o processo a partir de um estado que conseguiu salvar.
     */
    public EstudarDeckDissertativoViewModel(SavedStateHandle salvo) {
        this.salvo = salvo;
    }

    /**
     * Expõe o estado como LiveData para que o Fragment observe sem modificá-lo diretamente.
     */
    public LiveData<Estado> getEstado() {
        return estado;
    }

    /**
     * Expõe o nome do deck carregado.
     */
    public LiveData<String> getNome() {
        return nome;
    }

    /**
     * Expõe a mensagem de erro apresentada pela tela.
     */
    public LiveData<String> getErro() {
        return erro;
    }

    /**
     * Retorna a posição salva do card atual; a primeira posição é zero.
     */
    public int getIndice() {
        Integer i = salvo.get("indice");
        return i == null ? 0 : i;
    }

    /**
     * Retorna o tamanho da lista da sessão, incluindo posições de cards que foram removidos.
     */
    public int getTotal() {
        return cards.size();
    }

    /**
     * Recupera o rascunho escrito pelo estudante; antes da digitação, retorna texto vazio.
     */
    public String getResposta() {
        String s = salvo.get("resposta");
        return s == null ? "" : s;
    }

    /**
     * Atualiza o rascunho salvo a cada alteração do campo de texto.
     */
    public void setResposta(String resposta) {
        salvo.set("resposta", resposta);
    }

    /**
     * Retorna o card da posição atual ou null quando não há um card nessa posição.
     */
    public Flashcard getAtual() {
        int i = getIndice();
        return i < cards.size() ? cards.get(i) : null;
    }

    /**
     * Reconstrói o resultado a partir da explicação e da sugestão salvas.
     * Sem esses dois campos, ainda não há uma avaliação disponível.
     */
    public AvaliacaoDissertativa getAvaliacao() {
        String explicacao = salvo.get("explicacao");
        String sugestao = salvo.get("sugestao");
        if (explicacao == null || sugestao == null)
            return null;

        return new AvaliacaoDissertativa(explicacao, Rating.valueOf(sugestao));
    }

    /**
     * Confere se a conta conectada ainda é a mesma que iniciou esta sessão.
     */
    private boolean autenticado() {
        String uid = salvo.get("uid");
        return uid != null && uid.equals(FirebaseAuth.getInstance().getUid());
    }

    /**
     * Encerra a indicação de carregamento e libera a tela para apresentar a falha.
     */
    private void falharCarga(String mensagem) {
        carregando = false;
        erro.setValue(mensagem);
        estado.setValue(Estado.ERRO);
    }

    /**
     * Inicia o carregamento uma única vez enquanto os dados já estiverem disponíveis ou em busca.
     * Valida conta e deck antes das consultas e descarta estado salvo de outra conta ou outro deck.
     */
    public void carregar(String deckId) {
        if (carregado || carregando)
            return;

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            falharCarga("Usuário não autenticado.");
            return;
        }
        if (deckId == null || deckId.trim().isEmpty()) {
            falharCarga("Deck inválido.");
            return;
        }

        // Evita reutilizar um rascunho e uma ordem de cards pertencentes a outra sessão.
        String anteriorUid = salvo.get("uid");
        String anteriorDeck = salvo.get("deckId");
        if ((anteriorUid != null && !uid.equals(anteriorUid))
                    || (anteriorDeck != null && !deckId.equals(anteriorDeck))) {
            for (String chave : new ArrayList<>(salvo.keys()))
                salvo.remove(chave);
        }
        salvo.set("uid", uid);
        salvo.set("deckId", deckId);
        carregando = true;
        erro.setValue(null);
        estado.setValue(Estado.CARREGANDO);

        // Cada callback compara esta versão com a atual para ignorar respostas de uma carga invalidada.
        int carga = ++versao;
        decks.buscarDeckPorId(deckId, new DeckRepository.BuscarDeckCallback() {
            @Override
            public void onSuccess(Deck deck) {
                if (carga != versao)
                    return;

                if (deck == null) {
                    falharCarga("Deck não encontrado.");
                    return;
                }
                nome.setValue(deck.getTitulo());
                carregarCards(uid, deckId, carga);
            }

            @Override
            public void onError(Exception e) {
                if (carga == versao)
                    falharCarga("Não foi possível carregar o deck. Tente novamente.");
            }
        });
    }

    /**
     * Busca os cards e, depois, o histórico necessário para decidir quais revisões estão vencidas.
     * Se já houver uma ordem salva, restaura essa ordem em vez de montar uma sessão diferente.
     */
    private void carregarCards(String uid, String deckId, int carga) {
        decks.buscarFlashcardsDoDeck(deckId, new DeckRepository.BuscarFlashcardsCallback() {
            @Override
            public void onSuccess(List<Flashcard> lista) {
                if (carga != versao)
                    return;

                progressos.buscarProgressosDoDeck(uid, deckId, new ProgressoRepository.BuscarProgressosDeckCallback() {
                    @Override
                    public void onSuccess(List<ProgressoFlashcard> historico) {
                        if (carga != versao)
                            return;

                        // Sem IDs salvos, seleciona os cards vencidos e novos; com IDs, preserva a sessão original.
                        ArrayList<String> ids = salvo.get("cards");
                        if (ids == null) {
                            cards = SessaoDissertativa.selecionar(lista, historico, System.currentTimeMillis());
                            ids = new ArrayList<>();
                            for (Flashcard c : cards)
                                ids.add(c.getId());
                            salvo.set("cards", ids);
                        } else
                            cards = SessaoDissertativa.restaurar(lista, ids);

                        carregando = false;
                        carregado = true;

                        // Cards removidos durante uma sessão não deslocam o índice salvo.
                        while (getIndice() < cards.size() && getAtual() == null) {
                            salvo.set("indice", getIndice() + 1);
                            limparResposta();
                        }
                        if (!autenticado()) {
                            falharCarga("A conta mudou. Volte e inicie o estudo novamente.");
                            return;
                        }
                        prepararEstado();
                    }

                    @Override
                    public void onError(Exception e) {
                        if (carga == versao)
                            falharCarga("Não foi possível carregar seu progresso. Tente novamente.");
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                if (carga == versao)
                    falharCarga("Não foi possível carregar os flashcards. Tente novamente.");
            }
        });
    }

    /**
     * Escolhe entre encerrar a sessão, pedir uma resposta ou exibir uma avaliação já salva.
     * O identificador da tentativa só é criado se ainda não existir.
     */
    private void prepararEstado() {
        if (getAtual() == null) {
            estado.setValue(Estado.CONCLUIDA);
            return;
        }

        // O UUID permanece o mesmo durante reenvios desta revisão, permitindo detectar duplicações no banco.
        if (!salvo.contains("tentativaId"))
            salvo.set("tentativaId", UUID.randomUUID().toString());
        estado.setValue(getAvaliacao() == null ? Estado.RESPOSTA : Estado.RESULTADO);
    }

    /**
     * Valida o rascunho e pede uma sugestão à IA.
     * Esta etapa não salva a revisão nem concede XP: ainda falta a confirmação do estudante.
     */
    public void avaliar() {
        if (estado.getValue() != Estado.RESPOSTA || getAtual() == null)
            return;

        if (!autenticado()) {
            erro.setValue("Entre novamente na sua conta para continuar.");
            return;
        }
        String resposta = getResposta().trim();
        if (resposta.isEmpty()) {
            erro.setValue("Digite sua resposta antes de continuar.");
            return;
        }
        if (resposta.length() > LIMITE_RESPOSTA) {
            erro.setValue("Sua resposta deve ter até 6000 caracteres.");
            return;
        }
        erro.setValue(null);

        // A mudança de estado faz a tela bloquear edição e mostrar o indicador de avaliação.
        estado.setValue(Estado.AVALIANDO);
        try {
            requisicao = new AvaliacaoRespostaRepository().avaliar(getAtual(), resposta,
                    new AvaliacaoRespostaRepository.Callback() {
                        @Override
                        public void onSuccess(AvaliacaoDissertativa resultado) {
                            if (!autenticado()) {
                                erro.setValue("A conta mudou. Reinicie o estudo.");
                                estado.setValue(Estado.ERRO);
                                return;
                            }

                            // Persiste o resultado para não repetir a chamada à IA ao recriar a tela.
                            salvo.set("explicacao", resultado.getExplicacao());
                            salvo.set("sugestao", resultado.getSugestao().name());
                            estado.setValue(Estado.RESULTADO);
                        }

                        @Override
                        public void onError(String mensagem) {
                            erro.setValue(mensagem);
                            estado.setValue(Estado.RESPOSTA);
                        }
                    });
        } catch (Exception e) {
            erro.setValue("Não foi possível iniciar a avaliação. Tente novamente.");
            estado.setValue(Estado.RESPOSTA);
        }
    }

    /**
     * Envia a classificação escolhida ao repositório de gamificação.
     * Só avança o índice após o salvamento; em caso de erro, mantém a avaliação para novo envio.
     */
    public void confirmar(Rating rating) {
        if (estado.getValue() != Estado.RESULTADO || getAtual() == null || rating == null)
            return;

        if (!autenticado()) {
            erro.setValue("Entre novamente na sua conta para continuar.");
            return;
        }
        erro.setValue(null);

        // Mantém a classificação e o card atual enquanto aguarda a confirmação do Firestore.
        estado.setValue(Estado.SALVANDO);
        gamificacao.confirmarRevisao(salvo.get("uid"), salvo.get("deckId"), getAtual().getId(),
                salvo.get("tentativaId"), rating, new GamificacaoRepository.Callback() {
            @Override
            public void onSuccess() {
                if (!autenticado()) {
                    erro.setValue("A conta mudou. Reinicie o estudo.");
                    estado.setValue(Estado.ERRO);
                    return;
                }

                // O avanço ocorre no sucesso da gravação, nunca apenas por ter clicado em uma classificação.
                salvo.set("indice", getIndice() + 1);
                limparResposta();

                // Ignora posições de cards removidos antes de preparar o próximo card.
                while (getIndice() < cards.size() && getAtual() == null)
                    salvo.set("indice", getIndice() + 1);
                prepararEstado();
            }

            @Override
            public void onError(Exception e) {
                erro.setValue("Não foi possível salvar a revisão. Seu texto foi mantido. Tente confirmar novamente.");
                estado.setValue(Estado.RESULTADO);
            }
        });
    }

    /**
     * Remove os dados pertencentes ao card anterior antes de apresentar o próximo.
     */
    private void limparResposta() {
        salvo.set("resposta", "");
        salvo.remove("explicacao");
        salvo.remove("sugestao");
        salvo.remove("tentativaId");
    }

    /**
     * Ao encerrar o ViewModel, invalida callbacks de carregamento e cancela a requisição de IA pendente.
     */
    @Override
    protected void onCleared() {
        versao++;
        if (requisicao != null)
            requisicao.cancel(true);
    }
}
