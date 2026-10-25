package com.example.classcardsaplicao.ui.pesquisa;

import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModelStore;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.classcardsaplicao.data.PesquisaFonte;
import com.example.classcardsaplicao.model.ResultadoDeckPesquisa;
import com.example.classcardsaplicao.model.ResultadoUsuarioPesquisa;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** Exercita respostas assíncronas controladas, sem acessar contas ou dados reais. */
@RunWith(AndroidJUnit4.class)
public class PesquisaViewModelInstrumentedTest {

    private FonteControlada fonte;
    private PesquisaViewModel viewModel;
    private ViewModelStore store;
    private final AtomicReference<String> uid = new AtomicReference<>("conta-teste");
    private final AtomicLong tempo = new AtomicLong(1_000);

    @Before
    public void preparar() {
        principal(() -> {
            fonte = new FonteControlada();
            store = new ViewModelStore();
            viewModel = new PesquisaViewModel(new SavedStateHandle(), fonte, uid::get, tempo::get);
            store.put("pesquisa", viewModel);
        });
    }

    @After
    public void limpar() {
        principal(() -> store.clear());
    }

    @Test
    public void digitacaoRapidaDisparaSomenteAPesquisaFinalNasDuasFontes() throws Exception {
        principal(() -> {
            viewModel.pesquisar("hi");
            viewModel.pesquisar("hist");
            viewModel.pesquisar("História");
            assertEquals(0, fonte.decks.size());
            assertEquals(0, fonte.usuarios.size());
        });
        Thread.sleep(650);
        principal(() -> {
            assertEquals(1, fonte.decks.size());
            assertEquals(1, fonte.usuarios.size());
            assertEquals("historia", fonte.decks.get(0).termo);
            assertEquals("historia", fonte.usuarios.get(0).termo);
        });
    }

    @Test
    public void fontesSaoIniciadasSemEsperarARespostaDaOutraETrocaDeAbasNaoConsultaNovamente() {
        principal(() -> {
            buscar("ana");
            assertEquals(1, fonte.decks.size());
            assertEquals(1, fonte.usuarios.size());
            assertTrue(viewModel.getEstado().getValue().carregando);

            fonte.usuarios.get(0).sucesso(Collections.singletonList(usuario("u1", "Ana")), null, false);
            viewModel.selecionarAba(PesquisaViewModel.Aba.USUARIOS);
            assertEquals("Ana", viewModel.getUsuarios().getValue().get(0).getNome());
            assertFalse(viewModel.getEstado().getValue().carregando);

            viewModel.selecionarAba(PesquisaViewModel.Aba.FLASHCARDS);
            assertTrue(viewModel.getEstado().getValue().carregando);
            fonte.decks.get(0).sucesso(Collections.singletonList(deck("d1", "Anatomia")), null, false);
            assertFalse(viewModel.getEstado().getValue().carregando);
            assertEquals(1, fonte.decks.size());
            assertEquals(1, fonte.usuarios.size());
        });
    }

    @Test
    public void respostaDeUmTermoAntigoNaoSubstituiResultadosDoTermoAtual() {
        principal(() -> {
            buscar("biologia");
            buscar("historia");
            fonte.decks.get(1).sucesso(Collections.singletonList(deck("novo", "História")), null, false);
            fonte.usuarios.get(1).sucesso(Collections.singletonList(usuario("novo", "História")), null, false);
            fonte.decks.get(0).sucesso(Collections.singletonList(deck("antigo", "Biologia")), null, false);
            fonte.usuarios.get(0).sucesso(Collections.singletonList(usuario("antigo", "Biologia")), null, false);
            assertEquals("novo", viewModel.getDecks().getValue().get(0).getId());
            assertEquals("novo", viewModel.getUsuarios().getValue().get(0).getUid());
        });
    }

    @Test
    public void limparCampoRemoveResultadosEIgnoraRespostaPendente() {
        principal(() -> {
            buscar("biologia");
            viewModel.pesquisar("");
            fonte.decks.get(0).sucesso(Collections.singletonList(deck("d1", "Biologia")), null, false);
            fonte.usuarios.get(0).sucesso(Collections.singletonList(usuario("u1", "Biologia")), null, false);
            assertTrue(viewModel.getDecks().getValue().isEmpty());
            assertTrue(viewModel.getUsuarios().getValue().isEmpty());
            assertFalse(viewModel.getEstado().getValue().termoValido);
            assertFalse(viewModel.getEstado().getValue().carregando);
        });
    }

    @Test
    public void erroDeUmaCategoriaPreservaASucessoDaOutraETentarNovamenteRecupera() {
        principal(() -> {
            buscar("ana");
            fonte.decks.get(0).sucesso(Collections.singletonList(deck("d1", "Anatomia")), null, false);
            fonte.usuarios.get(0).falhar();
            assertEquals(1, viewModel.getDecks().getValue().size());
            assertNull(viewModel.getEstado().getValue().erro);

            viewModel.selecionarAba(PesquisaViewModel.Aba.USUARIOS);
            assertNotNull(viewModel.getEstado().getValue().erro);
            viewModel.tentarNovamente();
            assertTrue(viewModel.getEstado().getValue().carregando);
            fonte.usuarios.get(1).sucesso(Collections.singletonList(usuario("u1", "Ana")), null, false);
            assertNull(viewModel.getEstado().getValue().erro);
            assertEquals(1, viewModel.getUsuarios().getValue().size());
            assertEquals(1, viewModel.getDecks().getValue().size());
        });
    }

    @Test
    public void mesmoTermoComMaiusculasEAcentosNaoFazOutraConsulta() {
        principal(() -> {
            buscar("História");
            responderPrimeiraPagina();
            viewModel.pesquisar("  HISTORIA  ");
            assertEquals(1, fonte.decks.size());
            assertEquals(1, fonte.usuarios.size());
            assertEquals("  HISTORIA  ", viewModel.getTermo());
            assertEquals("d1", viewModel.getDecks().getValue().get(0).getId());
        });
    }

    @Test
    public void voltarAUmTermoRecenteReaproveitaResultadosEmCache() {
        principal(() -> {
            buscar("historia");
            responderPrimeiraPagina();
            buscar("biologia");
            fonte.decks.get(1).sucesso(Collections.singletonList(deck("d2", "Biologia")), null, false);
            fonte.usuarios.get(1).sucesso(Collections.emptyList(), null, false);
            viewModel.pesquisar("historia");
            assertEquals(2, fonte.decks.size());
            assertEquals(2, fonte.usuarios.size());
            assertEquals("d1", viewModel.getDecks().getValue().get(0).getId());
            assertFalse(viewModel.getEstado().getValue().carregando);
        });
    }

    @Test
    public void paginacaoUsaCursorDaAbaAtualEAdicionaItensSemDuplicar() {
        principal(() -> {
            buscar("ana");
            Object cursorDeck = new Object();
            Object cursorUsuario = new Object();
            fonte.decks.get(0).sucesso(Collections.singletonList(deck("d1", "Anatomia")), cursorDeck, true);
            fonte.usuarios.get(0).sucesso(Collections.singletonList(usuario("u1", "Ana")), cursorUsuario, true);
            viewModel.carregarMais();
            viewModel.carregarMais();
            assertEquals(2, fonte.decks.size());
            assertEquals(1, fonte.usuarios.size());
            assertSame(cursorDeck, fonte.decks.get(1).cursor);
            assertTrue(viewModel.getEstado().getValue().carregandoMais);
            fonte.decks.get(1).sucesso(Arrays.asList(deck("d1", "Anatomia"), deck("d2", "Análise")), null, false);
            assertEquals(2, viewModel.getDecks().getValue().size());
            assertFalse(viewModel.getEstado().getValue().temMais);

            viewModel.selecionarAba(PesquisaViewModel.Aba.USUARIOS);
            viewModel.carregarMais();
            assertEquals(2, fonte.usuarios.size());
            assertSame(cursorUsuario, fonte.usuarios.get(1).cursor);
            fonte.usuarios.get(1).sucesso(Collections.singletonList(usuario("u2", "Ana Maria")), null, false);
            assertEquals(2, viewModel.getUsuarios().getValue().size());
            assertEquals(2, fonte.decks.size());
        });
    }

    @Test
    public void botaoDeBuscaAtualizaResultadoVazioEDetectaUsuarioQueFoiIndexadoDepois() {
        principal(() -> {
            buscar("dan");
            fonte.decks.get(0).sucesso(Collections.emptyList(), null, false);
            fonte.usuarios.get(0).sucesso(Collections.emptyList(), null, false);
            viewModel.selecionarAba(PesquisaViewModel.Aba.USUARIOS);
            assertTrue(viewModel.getUsuarios().getValue().isEmpty());

            viewModel.buscarAgora();
            assertEquals(2, fonte.decks.size());
            assertEquals(2, fonte.usuarios.size());
            assertNull(fonte.decks.get(1).cursor);
            assertNull(fonte.usuarios.get(1).cursor);
            fonte.decks.get(1).sucesso(Collections.emptyList(), null, false);
            fonte.usuarios.get(1).sucesso(Collections.singletonList(usuario("u-daniel", "Daniel")), null, false);

            assertEquals("Daniel", viewModel.getUsuarios().getValue().get(0).getNome());
            assertFalse(viewModel.getEstado().getValue().carregando);
            assertNull(viewModel.getEstado().getValue().erro);
        });
    }

    @Test
    public void mesmoTermoRefazAConsultaQuandoSeuCacheExpira() throws Exception {
        principal(() -> {
            buscar("historia");
            responderPrimeiraPagina();
            tempo.addAndGet(119_999);
            viewModel.pesquisar("HISTÓRIA");
            assertEquals(1, fonte.decks.size());
            assertEquals(1, fonte.usuarios.size());

            tempo.incrementAndGet();
            viewModel.pesquisar("historia");
            assertTrue(viewModel.getEstado().getValue().carregando);
        });
        // O relógio falso expira o cache; esta espera é apenas a pausa de digitação de 350ms.
        Thread.sleep(650);
        principal(() -> {
            assertEquals(2, fonte.decks.size());
            assertEquals(2, fonte.usuarios.size());
            fonte.decks.get(1).sucesso(Collections.singletonList(deck("atual", "História atualizada")), null, false);
            fonte.usuarios.get(1).sucesso(Collections.emptyList(), null, false);
            assertEquals("atual", viewModel.getDecks().getValue().get(0).getId());
        });
    }

    @Test
    public void retornarAOutroTermoComCacheExpiradoConsultaNovamente() throws Exception {
        principal(() -> {
            buscar("historia");
            responderPrimeiraPagina();
            tempo.set(2_000);
            buscar("biologia");
            fonte.decks.get(1).sucesso(Collections.singletonList(deck("d2", "Biologia")), null, false);
            fonte.usuarios.get(1).sucesso(Collections.emptyList(), null, false);

            tempo.set(121_001);
            viewModel.pesquisar("historia");
            assertTrue(viewModel.getEstado().getValue().carregando);
        });
        Thread.sleep(650);
        principal(() -> {
            assertEquals(3, fonte.decks.size());
            assertEquals(3, fonte.usuarios.size());
            assertEquals("historia", fonte.decks.get(2).termo);
            fonte.decks.get(2).sucesso(Collections.singletonList(deck("novo", "História nova")), null, false);
            fonte.usuarios.get(2).sucesso(Collections.emptyList(), null, false);
            assertEquals("novo", viewModel.getDecks().getValue().get(0).getId());
        });
    }

    @Test
    public void apertarBuscaRepetidamenteDuranteCarregamentoNaoDuplicaConsultas() {
        principal(() -> {
            buscar("ana");
            viewModel.buscarAgora();
            viewModel.buscarAgora();
            assertEquals(1, fonte.decks.size());
            assertEquals(1, fonte.usuarios.size());

            fonte.decks.get(0).sucesso(Collections.singletonList(deck("d1", "Anatomia")), new Object(), true);
            viewModel.buscarAgora();
            assertEquals("A outra categoria ainda está carregando", 1, fonte.decks.size());
            assertEquals(1, fonte.usuarios.size());
            fonte.usuarios.get(0).sucesso(Collections.emptyList(), null, false);

            viewModel.carregarMais();
            viewModel.buscarAgora();
            viewModel.buscarAgora();
            assertEquals("A próxima página não deve iniciar outra pesquisa", 2, fonte.decks.size());
            assertEquals(1, fonte.usuarios.size());
        });
    }

    @Test
    public void atualizacaoExplicitaPreservaAbaEReiniciaListasECursoresNaPrimeiraPagina() {
        principal(() -> {
            buscar("ana");
            fonte.decks.get(0).sucesso(Collections.singletonList(deck("d-antigo", "Anatomia antiga")), null, false);
            Object cursorAntigo = new Object();
            fonte.usuarios.get(0).sucesso(Collections.singletonList(usuario("u-antigo", "Ana antiga")), cursorAntigo, true);
            viewModel.selecionarAba(PesquisaViewModel.Aba.USUARIOS);
            viewModel.carregarMais();
            fonte.usuarios.get(1).sucesso(Collections.singletonList(usuario("u-segunda", "Ana segunda")), null, false);
            assertEquals(2, viewModel.getUsuarios().getValue().size());

            viewModel.buscarAgora();
            assertEquals(PesquisaViewModel.Aba.USUARIOS, viewModel.getAba().getValue());
            assertNull(fonte.decks.get(1).cursor);
            assertNull(fonte.usuarios.get(2).cursor);
            Object cursorNovo = new Object();
            fonte.decks.get(1).sucesso(Collections.singletonList(deck("d-novo", "Anatomia nova")), null, false);
            fonte.usuarios.get(2).sucesso(Collections.singletonList(usuario("u-novo", "Ana nova")), cursorNovo, true);
            assertEquals(1, viewModel.getUsuarios().getValue().size());
            assertEquals("u-novo", viewModel.getUsuarios().getValue().get(0).getUid());
            assertEquals("d-novo", viewModel.getDecks().getValue().get(0).getId());

            viewModel.carregarMais();
            assertSame(cursorNovo, fonte.usuarios.get(3).cursor);
            assertNotSame(cursorAntigo, fonte.usuarios.get(3).cursor);
        });
    }

    @Test
    public void botaoBuscaEmErroDePaginacaoRepeteSoACategoriaECursorQueFalharam() {
        principal(() -> {
            buscar("ana");
            fonte.decks.get(0).sucesso(Collections.singletonList(deck("d1", "Anatomia")), null, false);
            Object cursor = new Object();
            fonte.usuarios.get(0).sucesso(Collections.singletonList(usuario("u1", "Ana")), cursor, true);
            viewModel.selecionarAba(PesquisaViewModel.Aba.USUARIOS);
            viewModel.carregarMais();
            fonte.usuarios.get(1).falhar();

            viewModel.buscarAgora();
            assertEquals(1, fonte.decks.size());
            assertEquals(3, fonte.usuarios.size());
            assertSame(cursor, fonte.usuarios.get(2).cursor);
            fonte.usuarios.get(2).sucesso(Collections.singletonList(usuario("u2", "Ana Maria")), null, false);
            assertEquals(2, viewModel.getUsuarios().getValue().size());
            assertNull(viewModel.getEstado().getValue().erro);
        });
    }

    @Test
    public void trocaDeContaInvalidaRespostasPendentesELimpaAsListas() {
        principal(() -> {
            buscar("ana");
            uid.set("outra-conta");
            fonte.decks.get(0).sucesso(Collections.singletonList(deck("d1", "Anatomia")), null, false);
            fonte.usuarios.get(0).sucesso(Collections.singletonList(usuario("u1", "Ana")), null, false);
            assertTrue(viewModel.getDecks().getValue().isEmpty());
            assertTrue(viewModel.getUsuarios().getValue().isEmpty());
            assertFalse(viewModel.getEstado().getValue().carregando);
            assertNotNull(viewModel.getEstado().getValue().erro);
        });
    }

    @Test
    public void falhaNaProximaPaginaPreservaItensEPermiteRepetirOMesmoCursor() {
        principal(() -> {
            buscar("ana");
            Object cursor = new Object();
            fonte.decks.get(0).sucesso(Collections.singletonList(deck("d1", "Anatomia")), cursor, true);
            fonte.usuarios.get(0).sucesso(Collections.emptyList(), null, false);
            viewModel.carregarMais();
            fonte.decks.get(1).falhar();
            assertEquals(1, viewModel.getDecks().getValue().size());
            assertFalse(viewModel.getEstado().getValue().carregandoMais);
            assertNotNull(viewModel.getEstado().getValue().erro);
            viewModel.tentarNovamente();
            assertSame(cursor, fonte.decks.get(2).cursor);
            fonte.decks.get(2).sucesso(Collections.singletonList(deck("d2", "Análise")), null, false);
            assertEquals(2, viewModel.getDecks().getValue().size());
            assertNull(viewModel.getEstado().getValue().erro);
        });
    }

    @Test
    public void recriacaoRecuperaTermoEAbaSalvosSemExigirNovaDigitacao() {
        principal(() -> {
            store.clear();
            SavedStateHandle salvo = new SavedStateHandle();
            viewModel = new PesquisaViewModel(salvo, fonte, uid::get);
            store.put("pesquisa", viewModel);
            viewModel.pesquisar("História");
            viewModel.selecionarAba(PesquisaViewModel.Aba.USUARIOS);
            store.clear();

            viewModel = new PesquisaViewModel(salvo, fonte, uid::get);
            store.put("pesquisa", viewModel);
            assertEquals("História", viewModel.getTermo());
            assertEquals(PesquisaViewModel.Aba.USUARIOS, viewModel.getAba().getValue());
            viewModel.buscarAgora();
            assertEquals(1, fonte.decks.size());
            assertEquals(1, fonte.usuarios.size());
            assertEquals("historia", fonte.usuarios.get(0).termo);
        });
    }

    @Test
    public void semContaOuComUmCaractereNaoConsultaOBanco() {
        principal(() -> {
            buscar("a");
            assertEquals(0, fonte.decks.size());
            assertEquals(0, fonte.usuarios.size());
            uid.set(null);
            buscar("ana");
            assertEquals(0, fonte.decks.size());
            assertEquals(0, fonte.usuarios.size());
            assertNotNull(viewModel.getEstado().getValue().erro);
        });
    }

    @Test
    public void descartarViewModelImpedeQueUmaRespostaTardiaAltereSeuEstado() {
        principal(() -> {
            buscar("ana");
            store.clear();
            fonte.decks.get(0).sucesso(Collections.singletonList(deck("d1", "Anatomia")), null, false);
            fonte.usuarios.get(0).sucesso(Collections.singletonList(usuario("u1", "Ana")), null, false);
            assertTrue(viewModel.getDecks().getValue().isEmpty());
            assertTrue(viewModel.getUsuarios().getValue().isEmpty());
        });
    }

    private void buscar(String termo) {
        viewModel.pesquisar(termo);
        viewModel.buscarAgora();
    }

    private void responderPrimeiraPagina() {
        fonte.decks.get(fonte.decks.size() - 1).sucesso(Collections.singletonList(deck("d1", "História")), null, false);
        fonte.usuarios.get(fonte.usuarios.size() - 1).sucesso(Collections.singletonList(usuario("u1", "História")), null, false);
    }

    private static ResultadoDeckPesquisa deck(String id, String titulo) {
        return new ResultadoDeckPesquisa(id, titulo, "Matéria", 12);
    }

    private static ResultadoUsuarioPesquisa usuario(String uid, String nome) {
        return new ResultadoUsuarioPesquisa(uid, nome, "");
    }

    private static void principal(Runnable tarefa) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(tarefa);
    }

    private static final class Pedido<T> {
        final String termo;
        final Object cursor;
        final PesquisaFonte.Callback<T> callback;

        Pedido(String termo, Object cursor, PesquisaFonte.Callback<T> callback) {
            this.termo = termo;
            this.cursor = cursor;
            this.callback = callback;
        }

        void sucesso(List<T> itens, Object cursor, boolean temMais) {
            callback.onSuccess(itens, cursor, temMais);
        }

        void falhar() {
            callback.onError(new IllegalStateException("Falha controlada"));
        }
    }

    private static final class FonteControlada implements PesquisaFonte {
        final List<Pedido<ResultadoDeckPesquisa>> decks = new ArrayList<>();
        final List<Pedido<ResultadoUsuarioPesquisa>> usuarios = new ArrayList<>();

        @Override
        public void buscarDecks(String termo, Object cursor, Callback<ResultadoDeckPesquisa> callback) {
            decks.add(new Pedido<>(termo, cursor, callback));
        }

        @Override
        public void buscarUsuarios(String termo, Object cursor, Callback<ResultadoUsuarioPesquisa> callback) {
            usuarios.add(new Pedido<>(termo, cursor, callback));
        }
    }
}
