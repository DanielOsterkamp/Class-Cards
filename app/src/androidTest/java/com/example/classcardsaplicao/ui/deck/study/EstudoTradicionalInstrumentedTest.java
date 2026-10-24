package com.example.classcardsaplicao.ui.deck.study;

import androidx.lifecycle.MutableLiveData;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.classcardsaplicao.model.Flashcard;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class EstudoTradicionalInstrumentedTest {
    private static class SessaoTeste extends EstudarDeckViewModel {
        int carregamentos;
        @Override public void carregarProgressoAtual() { carregamentos++; }
    }

    @Test public void tresCardsAvancamSemConcluirAposOPrimeiro() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            SessaoTeste vm = new SessaoTeste();
            List<Flashcard> cards = Arrays.asList(card("a"), card("b"), card("c"));
            ((MutableLiveData<List<Flashcard>>) vm.getFlashcards()).setValue(cards);
            vm.alternarLado();
            assertTrue(vm.proximoFlashcard());
            assertEquals("b", vm.getFlashcardAtual().getId());
            assertFalse(Boolean.TRUE.equals(vm.getSessaoConcluida().getValue()));
            assertFalse(vm.isMostrandoResposta());
            assertTrue(vm.proximoFlashcard());
            assertEquals("c", vm.getFlashcardAtual().getId());
            assertFalse(Boolean.TRUE.equals(vm.getSessaoConcluida().getValue()));
            assertFalse(vm.proximoFlashcard());
            assertTrue(Boolean.TRUE.equals(vm.getSessaoConcluida().getValue()));
            assertEquals(2, vm.carregamentos);
        });
    }

    @Test public void cardUnicoConcluiApenasQuandoEleTermina() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            SessaoTeste vm = new SessaoTeste();
            ((MutableLiveData<List<Flashcard>>) vm.getFlashcards()).setValue(Collections.singletonList(card("a")));
            assertFalse(vm.proximoFlashcard());
            assertTrue(Boolean.TRUE.equals(vm.getSessaoConcluida().getValue()));
        });
    }

    @Test public void sessaoVaziaNaoTentaCarregarOutroCard() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            SessaoTeste vm = new SessaoTeste();
            assertFalse(vm.proximoFlashcard());
            assertEquals(0, vm.carregamentos);
        });
    }

    private Flashcard card(String id) { return new Flashcard(id, "Pergunta", "Resposta", 0); }
}
