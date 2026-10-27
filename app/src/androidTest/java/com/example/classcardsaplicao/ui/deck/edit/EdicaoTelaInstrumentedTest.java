package com.example.classcardsaplicao.ui.deck.edit;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentFactory;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.classcardsaplicao.MainActivity;
import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.data.AiRepository;
import com.example.classcardsaplicao.data.DeckRepository;
import com.example.classcardsaplicao.model.Deck;
import com.example.classcardsaplicao.model.Flashcard;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

import static org.junit.Assert.*;

/** Exercita o Fragment real e a recriação da Activity, com banco e IA simulados. */
@RunWith(AndroidJUnit4.class)
public class EdicaoTelaInstrumentedTest {
    @Test public void gerarPeloBotaoERotacionarPreservaCamposERecebeCardsNaNovaTela() {
        Banco banco = new Banco(); Ia ia = new Ia();
        Fabrica callbacks = new Fabrica(banco, ia);
        Application app = (Application) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        app.registerActivityLifecycleCallbacks(callbacks);
        try (ActivityScenario<MainActivity> scenario = abrir()) {
            scenario.onActivity(activity -> {
                View tela = atual(activity).requireView();
                ((TextView) tela.findViewById(R.id.inputNomeDeck)).setText("Título ainda não salvo");
                ((TextView) tela.findViewById(R.id.inputMateriaDeck)).setText("Matéria alterada");
                ((SwitchMaterial) tela.findViewById(R.id.switchModoCriacao)).setChecked(true);
                ((TextView) tela.findViewById(R.id.inputNumeroCards)).setText("1");
                ((TextView) tela.findViewById(R.id.inputDescricaoIA)).setText("Células e organelas");
                tela.findViewById(R.id.btnGerarFlashcards).performClick();
                assertEquals(1, ia.chamadas);
                assertFalse(tela.findViewById(R.id.btnSalvarDeck).isEnabled());
                assertEquals(View.VISIBLE, tela.findViewById(R.id.containerCarregamentoCriacao).getVisibility());
            });
            scenario.recreate(); aguardar();
            scenario.onActivity(activity -> {
                View tela = atual(activity).requireView();
                assertEquals("Título ainda não salvo", texto(tela, R.id.inputNomeDeck));
                assertEquals("Matéria alterada", texto(tela, R.id.inputMateriaDeck));
                assertEquals("Células e organelas", texto(tela, R.id.inputDescricaoIA));
                assertTrue(((SwitchMaterial) tela.findViewById(R.id.switchModoCriacao)).isChecked());
                assertEquals(View.VISIBLE, tela.findViewById(R.id.containerCarregamentoCriacao).getVisibility());
                ia.callback.onSuccess(Collections.singletonList(new Flashcard(null, "Nova", "Resposta IA", 0)));
                EditarDeckViewModel vm = new ViewModelProvider(atual(activity)).get(EditarDeckViewModel.class);
                assertEquals(2, vm.getFlashcards().getValue().size());
                assertEquals("c1", vm.getFlashcards().getValue().get(0).getId());
                assertTrue(tela.findViewById(R.id.btnSalvarDeck).isEnabled());
                assertEquals(1, banco.consultas);
            });
        } finally { app.unregisterActivityLifecycleCallbacks(callbacks); }
    }

    @Test public void rotacionarDuranteCarregamentoInicialAindaPreencheNomeEMateria() {
        Banco banco = new Banco(); banco.adiar = true;
        Fabrica callbacks = new Fabrica(banco, new Ia());
        Application app = (Application) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        app.registerActivityLifecycleCallbacks(callbacks);
        try (ActivityScenario<MainActivity> scenario = abrir()) {
            scenario.recreate(); aguardar();
            scenario.onActivity(activity -> {
                banco.callback.onSuccess(deck());
                View tela = atual(activity).requireView();
                assertEquals("Biologia", texto(tela, R.id.inputNomeDeck));
                assertEquals("Ciências", texto(tela, R.id.inputMateriaDeck));
                assertTrue(tela.findViewById(R.id.btnSalvarDeck).isEnabled());
                assertEquals(1, banco.consultas);
            });
        } finally { app.unregisterActivityLifecycleCallbacks(callbacks); }
    }

    private ActivityScenario<MainActivity> abrir() {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        aguardar();
        scenario.onActivity(activity -> {
            Bundle argumentos = new Bundle(); argumentos.putString("deckId", "deck");
            host(activity).getNavController().navigate(R.id.editarDeckFragment, argumentos);
        });
        aguardar(); return scenario;
    }
    public static class TelaTeste extends EditarDeckFragment {
        private final Banco banco; private final Ia ia;
        TelaTeste(Banco banco, Ia ia) { this.banco = banco; this.ia = ia; }
        @NonNull @Override public ViewModelProvider.Factory getDefaultViewModelProviderFactory() {
            return new ViewModelProvider.Factory() {
                @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> tipo) {
                    return tipo.cast(new EditarDeckViewModel(banco, ia, () -> "dono"));
                }
            };
        }
    }
    private static class Fabrica implements Application.ActivityLifecycleCallbacks {
        private final Banco banco; private final Ia ia;
        Fabrica(Banco banco, Ia ia) { this.banco = banco; this.ia = ia; }
        @Override public void onActivityPreCreated(Activity activity, Bundle state) {
            if (!(activity instanceof MainActivity)) return;
            ((MainActivity) activity).getSupportFragmentManager().setFragmentFactory(new FragmentFactory() {
                @NonNull @Override public Fragment instantiate(@NonNull ClassLoader loader, @NonNull String nome) {
                    if (nome.equals(EditarDeckFragment.class.getName()) || nome.equals(TelaTeste.class.getName())) {
                        return new TelaTeste(banco, ia);
                    }
                    return super.instantiate(loader, nome);
                }
            });
        }
        @Override public void onActivityCreated(Activity a, Bundle b) { }
        @Override public void onActivityStarted(Activity a) { }
        @Override public void onActivityResumed(Activity a) { }
        @Override public void onActivityPaused(Activity a) { }
        @Override public void onActivityStopped(Activity a) { }
        @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
        @Override public void onActivityDestroyed(Activity a) { }
    }
    private static class Banco extends DeckRepository {
        boolean adiar; int consultas; BuscarDeckCallback callback;
        @Override public void buscarDeckPorId(String id, BuscarDeckCallback callback) {
            consultas++; this.callback = callback;
            if (!adiar) callback.onSuccess(deck());
        }
        @Override public void buscarFlashcardsDoDeck(String id, BuscarFlashcardsCallback callback) {
            callback.onSuccess(Collections.singletonList(new Flashcard("c1", "Antiga", "Resposta", 0)));
        }
    }
    private static class Ia extends AiRepository {
        AiCallback callback; int chamadas;
        @Override public void gerarFlashcards(String materia, String descricao, int quantidade, AiCallback callback) {
            chamadas++; this.callback = callback;
        }
    }
    private static Deck deck() { return new Deck("deck", "dono", "Biologia", "Ciências", "", 1, 0, 0); }
    private String texto(View tela, int id) { return ((TextView) tela.findViewById(id)).getText().toString(); }
    private static void aguardar() { InstrumentationRegistry.getInstrumentation().waitForIdleSync(); }
    private NavHostFragment host(MainActivity a) {
        return (NavHostFragment) a.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
    }
    private Fragment atual(MainActivity a) { return host(a).getChildFragmentManager().getPrimaryNavigationFragment(); }
}
