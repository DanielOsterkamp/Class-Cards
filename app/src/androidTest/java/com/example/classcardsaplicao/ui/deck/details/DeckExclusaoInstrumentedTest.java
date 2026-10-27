package com.example.classcardsaplicao.ui.deck.details;

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
import com.example.classcardsaplicao.data.DeckRepository;
import com.example.classcardsaplicao.data.RankingRepository;
import com.example.classcardsaplicao.model.Deck;
import com.example.classcardsaplicao.model.UsuarioRanking;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

@RunWith(AndroidJUnit4.class)
public class DeckExclusaoInstrumentedTest {
    @Test public void somenteDonoPodeSolicitarExclusaoESegundoToqueNaoDuplica() {
        principal(() -> {
            Banco banco = new Banco(); AtomicReference<String> uid = new AtomicReference<>("visitante");
            DeckSelecionadoViewModel vm = new DeckSelecionadoViewModel(banco, new Ranking(), uid::get);
            vm.carregarDeck("deck");
            vm.excluirDeck();
            assertEquals(0, banco.exclusoes);
            uid.set("dono");
            vm.excluirDeck(); vm.excluirDeck();
            assertEquals(1, banco.exclusoes);
            assertEquals("deck", banco.idExcluido);
            assertEquals("dono", banco.uidExclusao);
            assertTrue(vm.getExcluindo().getValue());
            banco.callback.onSuccess();
            assertFalse(vm.getExcluindo().getValue());
            assertEquals("deck", vm.getExcluido().getValue());
        });
    }

    @Test public void falhaMantemDeckPermiteTentarNovamenteESemContaNaoExclui() {
        principal(() -> {
            Banco banco = new Banco(); AtomicReference<String> uid = new AtomicReference<>("dono");
            DeckSelecionadoViewModel vm = new DeckSelecionadoViewModel(banco, new Ranking(), uid::get);
            vm.carregarDeck("deck"); vm.excluirDeck();
            banco.callback.onError(new Exception("Sem conexão"));
            assertFalse(vm.getExcluindo().getValue());
            assertNotNull(vm.getDeck().getValue());
            assertNotNull(vm.getErro().getValue());
            assertNull(vm.getExcluido().getValue());
            uid.set(null); vm.excluirDeck();
            assertEquals(1, banco.exclusoes);
            uid.set("dono"); vm.excluirDeck();
            assertEquals(2, banco.exclusoes);
        });
    }

    @Test public void nomeDoCriadorVemDoUidDoDonoDoDeck() {
        AtomicReference<DeckSelecionadoViewModel> atual = new AtomicReference<>();
        Ranking ranking = new Ranking();
        principal(() -> {
            DeckSelecionadoViewModel vm = new DeckSelecionadoViewModel(new Banco(), ranking, () -> "visitante");
            atual.set(vm); vm.carregarDeck("deck");
        });
        aguardar();
        assertEquals("dono", ranking.uidConsultado);
        assertEquals("Ana Silva", atual.get().getNomeCriador().getValue());
    }

    @Test public void visitanteVeCriadorMasNaoAcessaEdicaoOuExclusao() {
        AtomicReference<DeckSelecionadoViewModel> vm = new AtomicReference<>();
        Banco banco = new Banco();
        principal(() -> vm.set(new DeckSelecionadoViewModel(banco, new Ranking(), () -> "visitante")));
        try (ActivityScenario<MainActivity> scenario = abrir(vm.get())) {
            scenario.onActivity(activity -> {
                View view = atual(activity).requireView();
                assertEquals(View.GONE, view.findViewById(R.id.btnExcluirDeck).getVisibility());
                assertEquals(View.GONE, view.findViewById(R.id.btnEditarDeck).getVisibility());
                view.findViewById(R.id.btnExcluirDeck).performClick();
                assertEquals(0, banco.exclusoes);
                TextView criador = view.findViewById(R.id.txtCriadorDeck);
                TextView materia = view.findViewById(R.id.txtMateriaDeck);
                assertEquals("Criado por Ana Silva", criador.getText().toString());
                assertEquals(materia.getCurrentTextColor(), criador.getCurrentTextColor());
                assertEquals(materia.getTextSize(), criador.getTextSize(), 0f);
            });
        }
    }

    @Test public void donoConfirmaExclusaoECancelarNaoApagaDeck() {
        AtomicReference<DeckSelecionadoViewModel> vm = new AtomicReference<>();
        Banco banco = new Banco();
        principal(() -> vm.set(new DeckSelecionadoViewModel(banco, new Ranking(), () -> "dono")));
        try (ActivityScenario<MainActivity> scenario = abrir(vm.get())) {
            scenario.onActivity(activity -> {
                assertEquals(View.VISIBLE, atual(activity).requireView().findViewById(R.id.btnExcluirDeck).getVisibility());
                atual(activity).requireView().findViewById(R.id.btnExcluirDeck).performClick();
            });
            onView(withText(R.string.deck_cancelar)).inRoot(isDialog()).perform(click());
            assertEquals(0, banco.exclusoes);
            scenario.onActivity(activity -> atual(activity).requireView().findViewById(R.id.btnExcluirDeck).performClick());
            onView(withText(R.string.deck_excluir)).inRoot(isDialog()).perform(click());
            assertEquals(1, banco.exclusoes);
            scenario.onActivity(activity -> {
                assertFalse(atual(activity).requireView().findViewById(R.id.btnExcluirDeck).isEnabled());
                assertEquals(View.VISIBLE, atual(activity).requireView().findViewById(R.id.containerExclusaoDeck).getVisibility());
                banco.callback.onSuccess();
            });
            aguardar();
            scenario.onActivity(activity -> assertEquals(R.id.homeFragment,
                    host(activity).getNavController().getCurrentDestination().getId()));
        }
    }

    private ActivityScenario<MainActivity> abrir(DeckSelecionadoViewModel vm) {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        aguardar();
        assumeTrue("Execute em emulador sem conta", FirebaseAuth.getInstance().getCurrentUser() == null);
        scenario.onActivity(activity -> {
            host(activity).getChildFragmentManager().setFragmentFactory(new FragmentFactory() {
                @NonNull @Override public Fragment instantiate(@NonNull ClassLoader loader, @NonNull String nome) {
                    if (nome.equals(DeckSelecionadoFragment.class.getName())) return new DetalhesTesteFragment(vm);
                    return super.instantiate(loader, nome);
                }
            });
            host(activity).getNavController().navigate(R.id.homeFragment);
        });
        aguardar();
        scenario.onActivity(activity -> {
            Bundle argumentos = new Bundle(); argumentos.putString("deckId", "deck");
            host(activity).getNavController().navigate(R.id.deckSelecionadoFragment, argumentos);
        });
        aguardar();
        return scenario;
    }

    public static class DetalhesTesteFragment extends DeckSelecionadoFragment {
        private final DeckSelecionadoViewModel vm;
        DetalhesTesteFragment(DeckSelecionadoViewModel vm) { this.vm = vm; }
        @NonNull @Override public ViewModelProvider.Factory getDefaultViewModelProviderFactory() {
            return new ViewModelProvider.Factory() {
                @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> tipo) { return tipo.cast(vm); }
            };
        }
    }

    private static class Banco extends DeckRepository {
        int exclusoes; String idExcluido, uidExclusao; ExcluirDeckCallback callback;
        @Override public void buscarDeckPorId(String id, BuscarDeckCallback callback) {
            callback.onSuccess(new Deck(id, "dono", "Biologia", "Ciências", "", 2, 0, 0));
        }
        @Override public void excluirDeck(String id, String uid, ExcluirDeckCallback callback) {
            exclusoes++; idExcluido = id; uidExclusao = uid; this.callback = callback;
        }
    }
    private static class Ranking extends RankingRepository {
        String uidConsultado;
        @Override public Task<UsuarioRanking> buscarUsuario(String uid) {
            uidConsultado = uid;
            return Tasks.forResult(new UsuarioRanking(uid, "Ana Silva", "", 0, 0, 0, "", "UTC"));
        }
    }
    private static void principal(Runnable tarefa) { InstrumentationRegistry.getInstrumentation().runOnMainSync(tarefa); }
    private static void aguardar() { InstrumentationRegistry.getInstrumentation().waitForIdleSync(); }
    private NavHostFragment host(MainActivity activity) {
        return (NavHostFragment) activity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
    }
    private Fragment atual(MainActivity activity) { return host(activity).getChildFragmentManager().getPrimaryNavigationFragment(); }
}
