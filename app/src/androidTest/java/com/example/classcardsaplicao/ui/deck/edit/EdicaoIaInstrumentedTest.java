package com.example.classcardsaplicao.ui.deck.edit;

import androidx.lifecycle.ViewModelStore;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.classcardsaplicao.data.AiRepository;
import com.example.classcardsaplicao.data.DeckRepository;
import com.example.classcardsaplicao.model.Deck;
import com.example.classcardsaplicao.model.Flashcard;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** Estados e preservação de dados com banco e IA substituídos, sem acessar o Firebase real. */
@RunWith(AndroidJUnit4.class)
public class EdicaoIaInstrumentedTest {
    @Test public void iaAcrescentaCardsESalvarPreservaOIdDoCardJaEstudado() {
        principal(() -> {
            Banco banco = new Banco(); Ia ia = new Ia();
            EditarDeckViewModel vm = pronto(banco, ia);
            vm.gerarFlashcards("Biologia", "Células", "1");
            assertTrue(vm.getGerandoFlashcards().getValue());
            vm.gerarFlashcards("Biologia", "Células", "1");
            vm.salvarAlteracoes("Novo título", "Biologia");
            assertEquals(1, ia.chamadas);
            assertEquals(0, banco.salvamentos);
            ia.callback.onSuccess(Collections.singletonList(new Flashcard("id-indevido-da-ia", "Nova", "Resposta", 0)));
            assertFalse(vm.getGerandoFlashcards().getValue());
            assertEquals(2, vm.getFlashcards().getValue().size());
            assertEquals("card-estudado", vm.getFlashcards().getValue().get(0).getId());
            assertNull(vm.getFlashcards().getValue().get(1).getId());
            vm.salvarAlteracoes("Novo título", "Biologia");
            assertTrue(vm.getSalvando().getValue());
            assertEquals(1, banco.salvamentos);
            assertEquals("card-estudado", banco.enviados.get(0).getId());
            assertNull(banco.enviados.get(1).getId());
            vm.gerarFlashcards("Biologia", "Células", "1");
            assertEquals(1, ia.chamadas);
            banco.salvar.onSuccess();
            assertFalse(vm.getSalvando().getValue());
            assertTrue(vm.getSalvo().getValue());
        });
    }

    @Test public void erroDaIaNaoRemoveOsCardsExistentesELiberaNovaTentativa() {
        principal(() -> {
            Banco banco = new Banco(); Ia ia = new Ia(); EditarDeckViewModel vm = pronto(banco, ia);
            vm.gerarFlashcards("Biologia", "Células", "1");
            ia.callback.onError(new Exception("Falha controlada"));
            assertFalse(vm.getGerandoFlashcards().getValue());
            assertEquals(1, vm.getFlashcards().getValue().size());
            assertEquals("Falha controlada", vm.getErro().getValue());
            vm.gerarFlashcards("Biologia", "Células", "1");
            assertEquals(2, ia.chamadas);
        });
    }

    @Test public void falhaSincronaEListaVaziaNaoDeixamCarregamentoPreso() {
        principal(() -> {
            Banco banco = new Banco(); Ia ia = new Ia(); EditarDeckViewModel vm = pronto(banco, ia);
            ia.falhar = true;
            vm.gerarFlashcards("Biologia", "Células", "1");
            assertFalse(vm.getGerandoFlashcards().getValue());
            ia.falhar = false;
            vm.gerarFlashcards("Biologia", "Células", "1");
            ia.callback.onSuccess(Collections.emptyList());
            assertFalse(vm.getGerandoFlashcards().getValue());
            assertEquals(1, vm.getFlashcards().getValue().size());
            assertNotNull(vm.getErro().getValue());
        });
    }

    @Test public void validacoesNaoEnviamRequisicaoAIA() {
        principal(() -> {
            Banco banco = new Banco(); Ia ia = new Ia(); EditarDeckViewModel vm = pronto(banco, ia);
            vm.gerarFlashcards("", "Descrição", "1");
            vm.gerarFlashcards("Matéria", "", "1");
            for (String quantidade : new String[]{"", "texto", "0", "31", "99999999999999"}) {
                vm.gerarFlashcards("Matéria", "Descrição", quantidade);
                assertNotNull(vm.getErro().getValue());
            }
            assertEquals(0, ia.chamadas);
            assertFalse(vm.getGerandoFlashcards().getValue());
        });
    }

    @Test public void outroUsuarioNaoPodeCarregarEdicaoGerarOuSalvar() {
        principal(() -> {
            Banco banco = new Banco(); Ia ia = new Ia();
            EditarDeckViewModel vm = new EditarDeckViewModel(banco, ia, () -> "visitante");
            vm.carregarDeck("deck");
            vm.gerarFlashcards("Biologia", "Células", "1");
            vm.salvarAlteracoes("Título", "Matéria");
            assertFalse(vm.getPronto().getValue());
            assertEquals(0, banco.buscasCards);
            assertEquals(0, ia.chamadas);
            assertEquals(0, banco.salvamentos);
        });
    }

    @Test public void recriarTelaNaoRecarregaNemApagaAlteracoesNaoSalvas() {
        principal(() -> {
            Banco banco = new Banco(); Ia ia = new Ia(); EditarDeckViewModel vm = pronto(banco, ia);
            vm.adicionarFlashcard("Manual", "Resposta");
            vm.editarFlashcard(0, "Pergunta alterada", "Resposta alterada");
            vm.carregarDeck("deck");
            assertEquals(1, banco.buscasDeck);
            assertEquals(1, banco.buscasCards);
            assertEquals(2, vm.getFlashcards().getValue().size());
            assertEquals("Pergunta alterada", vm.getFlashcards().getValue().get(0).getPergunta());
            assertEquals("card-estudado", vm.getFlashcards().getValue().get(0).getId());
        });
    }

    @Test public void limparViewModelIgnoraRespostaTardiaDaIa() {
        principal(() -> {
            Banco banco = new Banco(); Ia ia = new Ia(); EditarDeckViewModel vm = pronto(banco, ia);
            ViewModelStore store = new ViewModelStore(); store.put("edicao", vm);
            vm.gerarFlashcards("Biologia", "Células", "1");
            store.clear();
            ia.callback.onSuccess(Collections.singletonList(new Flashcard(null, "Tardia", "Resposta", 0)));
            assertEquals(1, vm.getFlashcards().getValue().size());
        });
    }

    @Test public void trocaDeContaNaoRecebeCardsGeradosParaAContaAnterior() {
        principal(() -> {
            Banco banco = new Banco(); Ia ia = new Ia(); AtomicReference<String> uid = new AtomicReference<>("dono");
            EditarDeckViewModel vm = new EditarDeckViewModel(banco, ia, uid::get);
            vm.carregarDeck("deck");
            vm.gerarFlashcards("Biologia", "Células", "1");
            uid.set("visitante");
            ia.callback.onSuccess(Collections.singletonList(new Flashcard(null, "Outra conta", "Resposta", 0)));
            assertEquals(1, vm.getFlashcards().getValue().size());
            assertFalse(vm.getGerandoFlashcards().getValue());
            assertNotNull(vm.getErro().getValue());
        });
    }

    private EditarDeckViewModel pronto(Banco banco, Ia ia) {
        EditarDeckViewModel vm = new EditarDeckViewModel(banco, ia, () -> "dono");
        vm.carregarDeck("deck");
        assertTrue(vm.getPronto().getValue());
        return vm;
    }
    private static void principal(Runnable tarefa) { InstrumentationRegistry.getInstrumentation().runOnMainSync(tarefa); }

    private static class Banco extends DeckRepository {
        int buscasDeck, buscasCards, salvamentos;
        List<Flashcard> enviados;
        AtualizarDeckCallback salvar;
        @Override public void buscarDeckPorId(String id, BuscarDeckCallback callback) {
            buscasDeck++;
            callback.onSuccess(new Deck(id, "dono", "Biologia", "Biologia", "", 1, 0, 0));
        }
        @Override public void buscarFlashcardsDoDeck(String id, BuscarFlashcardsCallback callback) {
            buscasCards++;
            callback.onSuccess(Collections.singletonList(new Flashcard("card-estudado", "Antiga", "Resposta", 10)));
        }
        @Override public void atualizarDeck(Deck deck, List<Flashcard> cards, AtualizarDeckCallback callback) {
            salvamentos++; enviados = new ArrayList<>(cards); salvar = callback;
        }
    }
    private static class Ia extends AiRepository {
        int chamadas; boolean falhar; AiCallback callback;
        @Override public void gerarFlashcards(String materia, String descricao, int quantidade, AiCallback callback) {
            chamadas++;
            if (falhar) throw new IllegalStateException("Falha controlada");
            this.callback = callback;
        }
    }
}
