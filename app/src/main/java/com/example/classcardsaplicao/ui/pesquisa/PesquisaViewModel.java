package com.example.classcardsaplicao.ui.pesquisa;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.example.classcardsaplicao.data.PesquisaFonte;
import com.example.classcardsaplicao.data.PesquisaRepository;
import com.example.classcardsaplicao.model.ResultadoDeckPesquisa;
import com.example.classcardsaplicao.model.ResultadoUsuarioPesquisa;
import com.example.classcardsaplicao.model.TextoPesquisa;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Mantém as duas buscas independentes e reaproveita seus resultados ao alternar a aba. */
public class PesquisaViewModel extends ViewModel {

    public enum Aba { FLASHCARDS, USUARIOS }

    public static final class EstadoTela {
        public final boolean carregando;
        public final boolean carregandoMais;
        public final boolean temMais;
        public final boolean termoValido;
        public final String erro;

        private EstadoTela(boolean carregando, boolean carregandoMais, boolean temMais,
                boolean termoValido, String erro) {
            this.carregando = carregando;
            this.carregandoMais = carregandoMais;
            this.temMais = temMais;
            this.termoValido = termoValido;
            this.erro = erro;
        }
    }

    private static final long PAUSA_DIGITACAO_MS = 350;
    private static final long VALIDADE_CACHE_MS = 120_000;
    private static final int LIMITE_CACHE = 6;
    private static final String CHAVE_TERMO = "pesquisaTermo";
    private static final String CHAVE_ABA = "pesquisaAba";

    private final SavedStateHandle salvo;
    private final PesquisaFonte fonte;
    private final Supplier<String> usuarioAtual;
    private final LongSupplier tempo;
    private final Handler relogio = new Handler(Looper.getMainLooper());
    private final MutableLiveData<Aba> aba = new MutableLiveData<>(Aba.FLASHCARDS);
    private final MutableLiveData<List<ResultadoDeckPesquisa>> decks = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<ResultadoUsuarioPesquisa>> usuarios = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<EstadoTela> estado = new MutableLiveData<>();
    private final Map<String, Cache> cache = new LinkedHashMap<String, Cache>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Cache> maisAntigo) {
            return size() > LIMITE_CACHE;
        }
    };

    private String termo = "";
    private String normalizado = "";
    private String uidAtual;
    private String erroSessao;
    private Consulta consulta;
    private Runnable pendente;
    private int versao;
    private boolean encerrado;
    private FirebaseAuth auth;
    private FirebaseAuth.AuthStateListener listenerAuth;

    public PesquisaViewModel(SavedStateHandle salvo) {
        this(salvo, new PesquisaRepository(), () -> FirebaseAuth.getInstance().getUid());
        auth = FirebaseAuth.getInstance();
        listenerAuth = firebaseAuth -> verificarConta();
        auth.addAuthStateListener(listenerAuth);
    }

    // A fonte e a conta podem ser substituídas nos testes, sem consultar o Firebase real.
    PesquisaViewModel(SavedStateHandle salvo, PesquisaFonte fonte, Supplier<String> usuarioAtual) {
        this(salvo, fonte, usuarioAtual, SystemClock::elapsedRealtime);
    }

    PesquisaViewModel(SavedStateHandle salvo, PesquisaFonte fonte, Supplier<String> usuarioAtual,
            LongSupplier tempo) {
        this.salvo = salvo;
        this.fonte = fonte;
        this.usuarioAtual = usuarioAtual;
        this.tempo = tempo;
        uidAtual = usuarioAtual.get();
        String abaSalva = salvo.get(CHAVE_ABA);
        if (Aba.USUARIOS.name().equals(abaSalva)) aba.setValue(Aba.USUARIOS);
        String textoSalvo = salvo.get(CHAVE_TERMO);
        publicar();
        if (textoSalvo != null && !textoSalvo.isEmpty()) pesquisar(textoSalvo);
    }

    public LiveData<Aba> getAba() { return aba; }
    public LiveData<List<ResultadoDeckPesquisa>> getDecks() { return decks; }
    public LiveData<List<ResultadoUsuarioPesquisa>> getUsuarios() { return usuarios; }
    public LiveData<EstadoTela> getEstado() { return estado; }
    public String getTermo() { return termo; }

    public void selecionarAba(Aba novaAba) {
        if (novaAba == null || encerrado) return;
        verificarConta();
        salvo.set(CHAVE_ABA, novaAba.name());
        aba.setValue(novaAba);
        publicarEstado();
    }

    public void pesquisar(String texto) {
        if (encerrado) return;
        verificarConta();
        termo = texto == null ? "" : texto.substring(0, Math.min(texto.length(), 80));
        salvo.set(CHAVE_TERMO, termo);
        String novo = TextoPesquisa.normalizar(termo);
        // A mesma consulta só é reaproveitada enquanto seus resultados ainda são recentes.
        if (novo.equals(normalizado) && consulta != null
                && (emAndamento() || tempo.getAsLong() - consulta.atualizadoEm < VALIDADE_CACHE_MS)) return;

        cancelarPendente();
        versao++;
        normalizado = novo;
        consulta = null;
        erroSessao = null;
        if (!termoValido()) {
            publicar();
            return;
        }
        if (uidAtual == null) {
            erroSessao = "Entre na sua conta para pesquisar.";
            publicar();
            return;
        }

        Cache anterior = cache.get(normalizado);
        if (anterior != null && tempo.getAsLong() - anterior.instante < VALIDADE_CACHE_MS) {
            consulta = anterior.consulta.copia();
            publicar();
            return;
        }
        cache.remove(normalizado);
        consulta = new Consulta();
        consulta.atualizadoEm = tempo.getAsLong();
        consulta.decks.carregando = true;
        consulta.usuarios.carregando = true;
        publicar();
        int carga = versao;
        pendente = () -> {
            pendente = null;
            if (carga == versao) iniciar();
        };
        relogio.postDelayed(pendente, PAUSA_DIGITACAO_MS);
    }

    /** A busca explícita antecipa a digitação pendente ou renova resultados já carregados. */
    public void buscarAgora() {
        if (encerrado) return;
        verificarConta();
        cancelarPendente();
        if (consulta == null) {
            tentarNovamente();
        } else if (!consulta.iniciada) {
            iniciar();
        } else if (selecionada().erro != null) {
            tentarNovamente();
        } else if (!emAndamento()) {
            // Uma resposta vazia também pode mudar depois que outros perfis forem indexados.
            cache.remove(normalizado);
            versao++;
            consulta = new Consulta();
            consulta.atualizadoEm = tempo.getAsLong();
            consulta.decks.carregando = true;
            consulta.usuarios.carregando = true;
            publicar();
            iniciar();
        }
    }

    public void tentarNovamente() {
        if (encerrado) return;
        verificarConta();
        if (consulta == null) {
            pesquisar(termo);
            buscarPendenteAgora();
            return;
        }
        Pagina<?> pagina = selecionada();
        if (!pagina.carregando && !pagina.carregandoMais && pagina.erro != null) {
            requisitar(aba.getValue(), pagina.falhouMais);
        }
    }

    public void carregarMais() {
        if (encerrado) return;
        verificarConta();
        if (consulta == null) return;
        Pagina<?> pagina = selecionada();
        if (pagina.carregada && pagina.temMais && !pagina.carregando && !pagina.carregandoMais) {
            requisitar(aba.getValue(), true);
        }
    }

    private void buscarPendenteAgora() {
        cancelarPendente();
        if (consulta != null && !consulta.iniciada) iniciar();
    }

    private void iniciar() {
        verificarConta();
        if (consulta == null || consulta.iniciada || encerrado) return;
        consulta.iniciada = true;
        // Nenhuma fonte espera a outra terminar. Falhar em uma aba não apaga o resultado da outra.
        requisitar(Aba.FLASHCARDS, false);
        requisitar(Aba.USUARIOS, false);
    }

    private void requisitar(Aba categoria, boolean mais) {
        Consulta atual = consulta;
        if (atual == null || uidAtual == null) return;
        int carga = versao;
        String uid = uidAtual;
        if (categoria == Aba.USUARIOS) {
            Pagina<ResultadoUsuarioPesquisa> pagina = atual.usuarios;
            int pedido = preparar(pagina, mais);
            try {
                fonte.buscarUsuarios(normalizado, mais ? pagina.cursor : null,
                        retorno(atual, pagina, carga, pedido, uid, mais));
            } catch (Exception erro) {
                receberErro(atual, pagina, carga, pedido, uid, mais);
            }
        } else {
            Pagina<ResultadoDeckPesquisa> pagina = atual.decks;
            int pedido = preparar(pagina, mais);
            try {
                fonte.buscarDecks(normalizado, mais ? pagina.cursor : null,
                        retorno(atual, pagina, carga, pedido, uid, mais));
            } catch (Exception erro) {
                receberErro(atual, pagina, carga, pedido, uid, mais);
            }
        }
    }

    private int preparar(Pagina<?> pagina, boolean mais) {
        pagina.carregando = !mais;
        pagina.carregandoMais = mais;
        pagina.erro = null;
        pagina.falhouMais = false;
        publicarEstado();
        return ++pagina.pedido;
    }

    private <T> PesquisaFonte.Callback<T> retorno(Consulta atual, Pagina<T> pagina,
            int carga, int pedido, String uid, boolean mais) {
        return new PesquisaFonte.Callback<T>() {
            @Override public void onSuccess(List<T> itens, Object cursor, boolean temMais) {
                naPrincipal(() -> {
                    if (!valida(atual, pagina, carga, pedido, uid)) return;
                    // Uma próxima página precisa de cursor para não repetir a primeira indefinidamente.
                    if (itens == null || (temMais && (cursor == null || itens.isEmpty()))) {
                        receberErro(atual, pagina, carga, pedido, uid, mais);
                        return;
                    }
                    Map<String, T> unicos = new LinkedHashMap<>();
                    if (mais) for (T item : pagina.itens) unicos.put(id(item), item);
                    for (T item : itens) unicos.put(id(item), item);
                    pagina.itens = new ArrayList<>(unicos.values());
                    pagina.cursor = cursor;
                    pagina.temMais = temMais;
                    pagina.carregada = true;
                    pagina.carregando = false;
                    pagina.carregandoMais = false;
                    pagina.erro = null;
                    atual.atualizadoEm = tempo.getAsLong();
                    guardarCache();
                    publicar();
                });
            }

            @Override public void onError(Exception erro) {
                naPrincipal(() -> receberErro(atual, pagina, carga, pedido, uid, mais));
            }
        };
    }

    private void receberErro(Consulta atual, Pagina<?> pagina, int carga, int pedido,
            String uid, boolean mais) {
        if (!valida(atual, pagina, carga, pedido, uid)) return;
        pagina.carregando = false;
        pagina.carregandoMais = false;
        pagina.falhouMais = mais;
        pagina.erro = "Não foi possível carregar os resultados. Tente novamente.";
        publicarEstado();
    }

    private boolean valida(Consulta atual, Pagina<?> pagina, int carga, int pedido, String uid) {
        if (encerrado || carga != versao || consulta != atual || pedido != pagina.pedido) return false;
        if (!Objects.equals(uid, usuarioAtual.get())) {
            verificarConta();
            return false;
        }
        return true;
    }

    private void verificarConta() {
        if (encerrado) return;
        String uid = usuarioAtual.get();
        if (Objects.equals(uid, uidAtual)) return;
        uidAtual = uid;
        versao++;
        cancelarPendente();
        cache.clear();
        consulta = null;
        erroSessao = uid == null ? "Entre na sua conta para pesquisar." : "A conta mudou. Pesquise novamente.";
        publicar();
    }

    private void guardarCache() {
        if (consulta.decks.carregada && consulta.usuarios.carregada
                && consulta.decks.erro == null && consulta.usuarios.erro == null
                && !consulta.decks.carregandoMais && !consulta.usuarios.carregandoMais) {
            cache.put(normalizado, new Cache(consulta.copia(), tempo.getAsLong()));
        }
    }

    private Pagina<?> selecionada() {
        return aba.getValue() == Aba.USUARIOS ? consulta.usuarios : consulta.decks;
    }

    private boolean emAndamento() {
        return consulta != null && (!consulta.iniciada || consulta.decks.carregando
                || consulta.usuarios.carregando || consulta.decks.carregandoMais
                || consulta.usuarios.carregandoMais);
    }

    private void publicar() {
        decks.setValue(consulta == null ? Collections.emptyList() : imutavel(consulta.decks.itens));
        usuarios.setValue(consulta == null ? Collections.emptyList() : imutavel(consulta.usuarios.itens));
        publicarEstado();
    }

    private void publicarEstado() {
        Pagina<?> pagina = consulta == null ? null : selecionada();
        estado.setValue(new EstadoTela(pagina != null && pagina.carregando,
                pagina != null && pagina.carregandoMais, pagina != null && pagina.temMais,
                termoValido(), erroSessao != null ? erroSessao : pagina == null ? null : pagina.erro));
    }

    private boolean termoValido() { return normalizado.length() >= 2 && normalizado.length() <= 80; }

    private static <T> List<T> imutavel(List<T> lista) {
        return Collections.unmodifiableList(new ArrayList<>(lista));
    }

    private static String id(Object item) {
        if (item instanceof ResultadoDeckPesquisa) return ((ResultadoDeckPesquisa) item).getId();
        return ((ResultadoUsuarioPesquisa) item).getUid();
    }

    private void cancelarPendente() {
        if (pendente != null) relogio.removeCallbacks(pendente);
        pendente = null;
    }

    private void naPrincipal(Runnable acao) {
        if (Looper.myLooper() == Looper.getMainLooper()) acao.run();
        else relogio.post(acao);
    }

    @Override protected void onCleared() {
        encerrado = true;
        versao++;
        relogio.removeCallbacksAndMessages(null);
        cache.clear();
        if (auth != null && listenerAuth != null) auth.removeAuthStateListener(listenerAuth);
    }

    private static final class Consulta {
        private final Pagina<ResultadoDeckPesquisa> decks = new Pagina<>();
        private final Pagina<ResultadoUsuarioPesquisa> usuarios = new Pagina<>();
        private boolean iniciada;
        private long atualizadoEm;

        private Consulta copia() {
            Consulta nova = new Consulta();
            nova.iniciada = true;
            nova.atualizadoEm = atualizadoEm;
            decks.copiarPara(nova.decks);
            usuarios.copiarPara(nova.usuarios);
            return nova;
        }
    }

    private static final class Pagina<T> {
        private List<T> itens = new ArrayList<>();
        private Object cursor;
        private boolean carregada;
        private boolean carregando;
        private boolean carregandoMais;
        private boolean temMais;
        private boolean falhouMais;
        private String erro;
        private int pedido;

        private void copiarPara(Pagina<T> destino) {
            destino.itens = new ArrayList<>(itens);
            destino.cursor = cursor;
            destino.carregada = carregada;
            destino.temMais = temMais;
        }
    }

    private static final class Cache {
        private final Consulta consulta;
        private final long instante;

        private Cache(Consulta consulta, long instante) {
            this.consulta = consulta;
            this.instante = instante;
        }
    }
}
