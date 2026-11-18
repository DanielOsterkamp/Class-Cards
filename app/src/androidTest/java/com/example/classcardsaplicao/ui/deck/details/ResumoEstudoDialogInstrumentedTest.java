package com.example.classcardsaplicao.ui.deck.details;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentFactory;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.classcardsaplicao.MainActivity;
import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.model.Deck;
import com.example.classcardsaplicao.model.ResultadoOfensiva;
import com.example.classcardsaplicao.model.ResumoEstudo;
import com.example.classcardsaplicao.ui.deck.study.ConclusaoEstudo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.*;

/** Telas e diálogos reais com apenas o carregamento do deck substituído por uma fixture. */
@RunWith(AndroidJUnit4.class)
public class ResumoEstudoDialogInstrumentedTest {
    private Application app;
    private Fabrica fabrica;

    @Before public void preparar() {
        app = (Application) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        fabrica = new Fabrica();
        app.registerActivityLifecycleCallbacks(fabrica);
    }

    @After public void limpar() { app.unregisterActivityLifecycleCallbacks(fabrica); }

    @Test public void ofensivaAparecePrimeiroEResumoSoDepoisDoFechamento() {
        try (ActivityScenario<MainActivity> scenario = abrir(true)) {
            scenario.onActivity(activity -> {
                Fragment deck = deck(activity);
                assertNotNull(dialog(deck, OfensivaConquistadaDialog.TAG));
                assertNull(dialog(deck, ResumoEstudoDialog.TAG));
                dialog(deck, OfensivaConquistadaDialog.TAG).getDialog()
                        .findViewById(R.id.btnFecharOfensiva).performClick();
            });
            aguardar();
            scenario.onActivity(activity -> {
                Fragment deck = deck(activity);
                assertNull(dialog(deck, OfensivaConquistadaDialog.TAG));
                DialogFragment resumo = dialog(deck, ResumoEstudoDialog.TAG);
                assertNotNull(resumo);
                assertEquals("Antes: 10", texto(resumo, R.id.txtCardsAntesResumo));
                assertEquals("Agora: 12", texto(resumo, R.id.txtCardsAgoraResumo));
                assertEquals("Antes: 100", texto(resumo, R.id.txtPontosAntesResumo));
                assertEquals("Agora: 108", texto(resumo, R.id.txtPontosAgoraResumo));
                salvar(resumo.getDialog().findViewById(android.R.id.content));
            });
        }
    }

    @Test public void semOfensivaMostraResumoMesmoComTotaisIguaisEFecharConsomeResultado() {
        Bundle dados = dados(false);
        dados.putBundle("resumo", ConclusaoEstudo.dadosResumo(new ResumoEstudo(10, 10, 100, 100)));
        try (ActivityScenario<MainActivity> scenario = abrir(dados)) {
            scenario.onActivity(activity -> {
                Fragment deck = deck(activity);
                assertNull(dialog(deck, OfensivaConquistadaDialog.TAG));
                DialogFragment resumo = dialog(deck, ResumoEstudoDialog.TAG);
                assertEquals("Agora: 10", texto(resumo, R.id.txtCardsAgoraResumo));
                assertEquals("Agora: 100", texto(resumo, R.id.txtPontosAgoraResumo));
                resumo.getDialog().findViewById(R.id.btnFecharResumoEstudo).performClick();
            });
            aguardar();
            scenario.recreate(); aguardar();
            scenario.onActivity(activity -> {
                assertNull(dialog(deck(activity), ResumoEstudoDialog.TAG));
                assertNull(host(activity).getNavController().getCurrentBackStackEntry()
                        .getSavedStateHandle().get(ConclusaoEstudo.CHAVE));
            });
        }
    }

    @Test public void rotacionarOfensivaNaoAvancaFilaECancelarAbreResumo() {
        try (ActivityScenario<MainActivity> scenario = abrir(true)) {
            scenario.recreate(); aguardar();
            scenario.onActivity(activity -> {
                Fragment deck = deck(activity);
                assertEquals(1, deck.getChildFragmentManager().getFragments().size());
                assertNotNull(dialog(deck, OfensivaConquistadaDialog.TAG));
                assertNull(dialog(deck, ResumoEstudoDialog.TAG));
                dialog(deck, OfensivaConquistadaDialog.TAG).getDialog().cancel();
            });
            aguardar();
            scenario.onActivity(activity -> assertNotNull(dialog(deck(activity), ResumoEstudoDialog.TAG)));
        }
    }

    @Test public void rotacionarResumoNaoRepeteOfensivaENovaSessaoMostraOutroResumo() {
        try (ActivityScenario<MainActivity> scenario = abrir(true)) {
            scenario.onActivity(activity -> dialog(deck(activity), OfensivaConquistadaDialog.TAG).dismiss());
            aguardar();
            scenario.recreate(); aguardar();
            scenario.onActivity(activity -> {
                Fragment deck = deck(activity);
                assertNull(dialog(deck, OfensivaConquistadaDialog.TAG));
                assertEquals(1, deck.getChildFragmentManager().getFragments().size());
                assertEquals("Agora: 108", texto(dialog(deck, ResumoEstudoDialog.TAG), R.id.txtPontosAgoraResumo));
                dialog(deck, ResumoEstudoDialog.TAG).getDialog().cancel();
            });
            aguardar();
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.onActivity(activity -> {
                Bundle seguinte = dados(false);
                seguinte.putString("sessaoId", "segunda-sessao");
                seguinte.putBundle("resumo", ConclusaoEstudo.dadosResumo(new ResumoEstudo(12, 13, 108, 111)));
                host(activity).getNavController().getCurrentBackStackEntry().getSavedStateHandle()
                        .set(ConclusaoEstudo.CHAVE, seguinte);
            });
            scenario.moveToState(Lifecycle.State.RESUMED); aguardar();
            scenario.onActivity(activity -> assertEquals("Agora: 111",
                    texto(dialog(deck(activity), ResumoEstudoDialog.TAG), R.id.txtPontosAgoraResumo)));
        }
    }

    @Test public void reciboAntigoSemTotaisMostraIndisponivelSemInventarNumeros() {
        Bundle dados = dados(false); dados.remove("resumo");
        try (ActivityScenario<MainActivity> scenario = abrir(dados)) {
            scenario.onActivity(activity -> {
                DialogFragment resumo = dialog(deck(activity), ResumoEstudoDialog.TAG);
                assertEquals("Antes: —", texto(resumo, R.id.txtCardsAntesResumo));
                assertEquals("Agora: —", texto(resumo, R.id.txtPontosAgoraResumo));
            });
        }
    }

    private ActivityScenario<MainActivity> abrir(boolean ofensiva) { return abrir(dados(ofensiva)); }

    private ActivityScenario<MainActivity> abrir(Bundle dados) {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        aguardar();
        scenario.onActivity(activity -> {
            Bundle args = new Bundle(); args.putString("deckId", "deck-fixture");
            host(activity).getNavController().navigate(R.id.deckSelecionadoFragment, args);
            host(activity).getNavController().getCurrentBackStackEntry().getSavedStateHandle()
                    .set(ConclusaoEstudo.CHAVE, dados);
        });
        aguardar(); return scenario;
    }

    private Bundle dados(boolean ofensiva) {
        Bundle dados = ConclusaoEstudo.dados(new ResultadoOfensiva(2, ofensiva ? 3 : 2, ofensiva));
        dados.putString("sessaoId", "sessao-fixture");
        dados.putBundle("resumo", ConclusaoEstudo.dadosResumo(new ResumoEstudo(10, 12, 100, 108)));
        return dados;
    }

    public static class DeckFixture extends DeckSelecionadoFragment {
        @NonNull @Override public ViewModelProvider.Factory getDefaultViewModelProviderFactory() {
            return new ViewModelProvider.Factory() {
                @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> tipo) {
                    return tipo.cast(new DeckSelecionadoViewModel() {
                        @Override public void carregarDeck(String id) {
                            ((MutableLiveData<Deck>) getDeck()).setValue(new Deck(id, "dono", "Biologia", "Ciências", "", 3, 0, 0));
                        }
                    });
                }
            };
        }
    }

    private static class Fabrica implements Application.ActivityLifecycleCallbacks {
        @Override public void onActivityPreCreated(Activity activity, Bundle state) {
            if (!(activity instanceof MainActivity)) return;
            ((MainActivity) activity).getSupportFragmentManager().setFragmentFactory(new FragmentFactory() {
                @NonNull @Override public Fragment instantiate(@NonNull ClassLoader loader, @NonNull String nome) {
                    if (nome.equals(DeckSelecionadoFragment.class.getName()) || nome.equals(DeckFixture.class.getName())) {
                        return new DeckFixture();
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

    private DialogFragment dialog(Fragment deck, String tag) {
        return (DialogFragment) deck.getChildFragmentManager().findFragmentByTag(tag);
    }
    private Fragment deck(MainActivity activity) { return host(activity).getChildFragmentManager().getPrimaryNavigationFragment(); }
    private NavHostFragment host(MainActivity activity) {
        return (NavHostFragment) activity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
    }
    private String texto(DialogFragment dialog, int id) { return ((TextView) dialog.getDialog().findViewById(id)).getText().toString(); }
    private static void aguardar() { InstrumentationRegistry.getInstrumentation().waitForIdleSync(); }
    private void salvar(View view) {
        Bitmap imagem = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(imagem));
        File destino = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null), "resumo-estudo-preview.png");
        try (FileOutputStream arquivo = new FileOutputStream(destino)) { imagem.compress(Bitmap.CompressFormat.PNG, 100, arquivo); }
        catch (Exception erro) { throw new AssertionError(erro); }
        finally { imagem.recycle(); }
    }
}
