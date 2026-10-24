package com.example.classcardsaplicao.ui.deck.create;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.data.AiRepository;
import com.example.classcardsaplicao.model.Flashcard;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CriacaoIaInstrumentedTest {
    private static class IaControlada extends AiRepository {
        AiCallback callback;
        int chamadas;
        boolean falhaSincrona;
        @Override public void gerarFlashcards(String materia, String descricao, int quantidade, AiCallback callback) {
            chamadas++;
            if (falhaSincrona) throw new IllegalStateException("Falha controlada");
            this.callback = callback;
        }
    }

    @Test public void sucessoEncerraCarregamentoEAdicionaCardsSemApagarOsExistentes() {
        AtomicReference<CriarDeckViewModel> vm = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            IaControlada ia = new IaControlada();
            vm.set(new CriarDeckViewModel(ia));
            vm.get().adicionarFlashcard("Manual", "Resposta manual");
            vm.get().gerarFlashcards("Matemática", "Soma", "1");
            assertTrue(vm.get().getGerandoFlashcards().getValue());
            vm.get().gerarFlashcards("Matemática", "Soma", "1");
            assertEquals(1, ia.chamadas);
            ia.callback.onSuccess(Collections.singletonList(new Flashcard(null,"IA", "Resposta IA",0)));
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertFalse(vm.get().getGerandoFlashcards().getValue());
        assertEquals(2, vm.get().getFlashcards().getValue().size());
    }

    @Test public void falhaDaIaEncerraCarregamentoEPreservaCards() {
        AtomicReference<CriarDeckViewModel> vm = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            IaControlada ia = new IaControlada();
            vm.set(new CriarDeckViewModel(ia));
            vm.get().adicionarFlashcard("Manual", "Resposta");
            vm.get().gerarFlashcards("Matemática", "Soma", "1");
            ia.callback.onError(new Exception("Falha controlada"));
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertFalse(vm.get().getGerandoFlashcards().getValue());
        assertEquals(1, vm.get().getFlashcards().getValue().size());
        assertEquals("Falha controlada", vm.get().getErro().getValue());
    }

    @Test public void falhaAoIniciarNaoDeixaIndicadorPreso() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            IaControlada ia = new IaControlada(); ia.falhaSincrona = true;
            CriarDeckViewModel vm = new CriarDeckViewModel(ia);
            vm.gerarFlashcards("Matemática", "Soma", "1");
            assertFalse(vm.getGerandoFlashcards().getValue());
            assertNotNull(vm.getErro().getValue());
        });
    }

    @Test public void indicadorTemMesmoTamanhoDoEstudoEIniciaOculto() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context contexto = InstrumentationRegistry.getInstrumentation().getTargetContext();
            Context tema = new ContextThemeWrapper(contexto,
                    com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar);
            View criacao = LayoutInflater.from(tema).inflate(R.layout.fragment_criar_deck, null, false);
            View estudo = LayoutInflater.from(tema).inflate(R.layout.fragment_estudar_deck_dissertativo, null, false);
            ProgressBar novo = criacao.findViewById(R.id.progressGeracaoFlashcards);
            ProgressBar existente = estudo.findViewById(R.id.progressAvaliacao);
            assertEquals(existente.getLayoutParams().width, novo.getLayoutParams().width);
            assertEquals(existente.getLayoutParams().height, novo.getLayoutParams().height);
            assertEquals(View.GONE, criacao.findViewById(R.id.containerCarregamentoCriacao).getVisibility());
        });
    }
}
