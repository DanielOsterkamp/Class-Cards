package com.example.classcardsaplicao.ui.pesquisa;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentFactory;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.classcardsaplicao.MainActivity;
import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.data.PesquisaFonte;
import com.example.classcardsaplicao.model.Deck;
import com.example.classcardsaplicao.model.ResultadoDeckPesquisa;
import com.example.classcardsaplicao.model.ResultadoUsuarioPesquisa;
import com.example.classcardsaplicao.ui.deck.details.DeckSelecionadoFragment;
import com.example.classcardsaplicao.ui.deck.details.DeckSelecionadoViewModel;
import com.example.classcardsaplicao.ui.profile.PerfilFragment;
import com.example.classcardsaplicao.ui.profile.PerfilViewModel;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.firebase.auth.FirebaseAuth;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Usa as telas, adapters e navegação reais, substituindo somente as fontes de dados por fixtures. */
@RunWith(AndroidJUnit4.class)
public class PesquisaResultadosNavegacaoInstrumentedTest {

    @Test
    public void selecionarDeckAbreIdCorretoEVoltarPreservaPesquisaSemNovaConsulta() {
        FonteEstatica fonte = new FonteEstatica();
        try (ActivityScenario<MainActivity> scenario = abrir(fonte)) {
            pesquisar(scenario);
            AtomicReference<View> itemSelecionado = new AtomicReference<>();
            scenario.onActivity(activity -> {
                RecyclerView lista = atual(activity).requireView().findViewById(R.id.recyclerPesquisa);
                View item = lista.findViewHolderForAdapterPosition(1).itemView;
                itemSelecionado.set(item);
                assertTrue(item.performClick());
                // O segundo toque no mesmo item não deve criar outro destino na pilha.
                item.performClick();
            });
            aguardar();
            scenario.onActivity(activity -> {
                assertDestino(activity, R.id.deckSelecionadoFragment, "deckId", "deck-2");
                assertEquals("Deck selecionado deck-2", texto(atual(activity), R.id.txtNomeDeck));
                assertEquals(View.GONE, atual(activity).requireView().findViewById(R.id.btnDissertativo).getVisibility());
                assertNotNull(itemSelecionado.get());
                atual(activity).requireView().findViewById(R.id.btnVoltar).performClick();
            });
            aguardar();
            scenario.onActivity(activity -> {
                assertPesquisaRestaurada(activity, R.id.btnPesquisaFlashcards);
                RecyclerView lista = atual(activity).requireView().findViewById(R.id.recyclerPesquisa);
                assertEquals(2, lista.getAdapter().getItemCount());
                assertEquals(1, fonte.consultasDecks);
                assertEquals(1, fonte.consultasUsuarios);
            });
        }
    }

    @Test
    public void selecionarUsuarioAbrePerfilCorrespondenteESeusDecksComRetornoParaAbaUsuarios() {
        FonteEstatica fonte = new FonteEstatica();
        try (ActivityScenario<MainActivity> scenario = abrir(fonte)) {
            pesquisar(scenario);
            scenario.onActivity(activity -> ((MaterialButtonToggleGroup) atual(activity).requireView()
                    .findViewById(R.id.togglePesquisa)).check(R.id.btnPesquisaUsuarios));
            aguardar();
            scenario.onActivity(activity -> {
                RecyclerView lista = atual(activity).requireView().findViewById(R.id.recyclerPesquisa);
                lista.findViewHolderForAdapterPosition(1).itemView.performClick();
            });
            aguardar();
            scenario.onActivity(activity -> {
                assertDestino(activity, R.id.perfilFragment, "uid", "usuario-2");
                assertEquals("Usuário usuario-2", texto(atual(activity), R.id.txtNomeUsuario));
                assertEquals(activity.getString(R.string.perfil_decks_usuario),
                        texto(atual(activity), R.id.txtTituloDecks));
                assertEquals("12", texto(atual(activity), R.id.txtXp));
                assertEquals("3", texto(atual(activity), R.id.txtOfensiva));
                assertEquals("4", texto(atual(activity), R.id.txtQuantidadeEstudados));
                RecyclerView decks = atual(activity).requireView().findViewById(R.id.recyclerDecksUsuario);
                decks.findViewHolderForAdapterPosition(0).itemView.performClick();
            });
            aguardar();
            scenario.onActivity(activity -> {
                assertDestino(activity, R.id.deckSelecionadoFragment, "deckId", "deck-usuario-2");
                atual(activity).requireView().findViewById(R.id.btnVoltar).performClick();
            });
            aguardar();
            scenario.onActivity(activity -> {
                assertDestino(activity, R.id.perfilFragment, "uid", "usuario-2");
                atual(activity).requireView().findViewById(R.id.btnVoltar).performClick();
            });
            aguardar();
            scenario.onActivity(activity -> {
                assertPesquisaRestaurada(activity, R.id.btnPesquisaUsuarios);
                assertEquals(1, fonte.consultasDecks);
                assertEquals(1, fonte.consultasUsuarios);
            });
        }
    }

    @Test
    public void perfilSemUidContinuaRepresentandoAPropriaConta() {
        try (ActivityScenario<MainActivity> scenario = abrir(new FonteEstatica())) {
            scenario.onActivity(activity -> host(activity).getNavController().navigate(R.id.perfilFragment));
            aguardar();
            scenario.onActivity(activity -> {
                assertDestino(activity, R.id.perfilFragment, "uid", null);
                assertEquals("Minha conta", texto(atual(activity), R.id.txtNomeUsuario));
                assertEquals(activity.getString(R.string.perfil_decks_proprios),
                        texto(atual(activity), R.id.txtTituloDecks));
            });
        }
    }

    private ActivityScenario<MainActivity> abrir(FonteEstatica fonte) {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        aguardar();
        assumeTrue("Execute em um emulador sem conta conectada", FirebaseAuth.getInstance().getCurrentUser() == null);
        scenario.onActivity(activity -> {
            host(activity).getChildFragmentManager().setFragmentFactory(new FragmentFactory() {
                @NonNull @Override
                public Fragment instantiate(@NonNull ClassLoader loader, @NonNull String nome) {
                    if (nome.equals(PesquisaFragment.class.getName())) return new PesquisaTesteFragment(fonte);
                    if (nome.equals(PerfilFragment.class.getName())) return new PerfilTesteFragment();
                    if (nome.equals(DeckSelecionadoFragment.class.getName())) return new DeckTesteFragment();
                    return super.instantiate(loader, nome);
                }
            });
            Bundle argumentos = new Bundle();
            argumentos.putBoolean("focar", false);
            host(activity).getNavController().navigate(R.id.pesquisaFragment, argumentos);
        });
        aguardar();
        return scenario;
    }

    private void pesquisar(ActivityScenario<MainActivity> scenario) {
        scenario.onActivity(activity -> {
            Fragment fragment = atual(activity);
            ((TextView) fragment.requireView().findViewById(R.id.campoPesquisa)).setText("História");
            new ViewModelProvider(fragment).get(PesquisaViewModel.class).buscarAgora();
        });
        aguardar();
    }

    private void assertPesquisaRestaurada(MainActivity activity, int aba) {
        assertEquals(R.id.pesquisaFragment, host(activity).getNavController().getCurrentDestination().getId());
        assertEquals("História", texto(atual(activity), R.id.campoPesquisa));
        assertEquals(aba, ((MaterialButtonToggleGroup) atual(activity).requireView()
                .findViewById(R.id.togglePesquisa)).getCheckedButtonId());
    }

    private void assertDestino(MainActivity activity, int destino, String chave, String valor) {
        assertEquals(destino, host(activity).getNavController().getCurrentDestination().getId());
        assertEquals(valor, atual(activity).requireArguments().getString(chave));
    }

    private String texto(Fragment fragment, int id) {
        return ((TextView) fragment.requireView().findViewById(id)).getText().toString();
    }

    private Fragment atual(MainActivity activity) {
        return host(activity).getChildFragmentManager().getPrimaryNavigationFragment();
    }

    private NavHostFragment host(MainActivity activity) {
        return (NavHostFragment) activity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
    }

    private void aguardar() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    public static class PesquisaTesteFragment extends PesquisaFragment {
        private final FonteEstatica fonte;
        PesquisaTesteFragment(FonteEstatica fonte) { this.fonte = fonte; }
        @NonNull @Override public ViewModelProvider.Factory getDefaultViewModelProviderFactory() {
            return factory(new PesquisaViewModel(new SavedStateHandle(), fonte, () -> "conta-fixture"));
        }
    }

    public static class PerfilTesteFragment extends PerfilFragment {
        @NonNull @Override public ViewModelProvider.Factory getDefaultViewModelProviderFactory() {
            return factory(new PerfilViewModel() {
                @Override public void carregarPerfil(String uid) {
                    definir(getNome(), uid == null ? "Minha conta" : "Usuário " + uid);
                    definir(getFotoUrl(), "");
                    definir(getXp(), 12L);
                    definir(getOfensiva(), 3L);
                    definir(getCardsEstudados(), 4L);
                    definir(getDecks(), Collections.singletonList(deck("deck-" + uid)));
                    definir(getCarregando(), false);
                }
            });
        }
    }

    public static class DeckTesteFragment extends DeckSelecionadoFragment {
        @NonNull @Override public ViewModelProvider.Factory getDefaultViewModelProviderFactory() {
            return factory(new DeckSelecionadoViewModel() {
                @Override public void carregarDeck(String id) { definir(getDeck(), deck(id)); }
            });
        }
    }

    private static Deck deck(String id) {
        return new Deck(id, "outro-usuario", "Deck selecionado " + id, "História", "", 7, 0, 0);
    }

    private static <T> void definir(androidx.lifecycle.LiveData<T> dado, T valor) {
        ((MutableLiveData<T>) dado).setValue(valor);
    }

    private static ViewModelProvider.Factory factory(ViewModel viewModel) {
        return new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> classe) {
                return classe.cast(viewModel);
            }
        };
    }

    private static class FonteEstatica implements PesquisaFonte {
        int consultasDecks;
        int consultasUsuarios;
        @Override public void buscarDecks(String termo, Object cursor, Callback<ResultadoDeckPesquisa> callback) {
            consultasDecks++;
            callback.onSuccess(Arrays.asList(
                    new ResultadoDeckPesquisa("deck-1", "História Antiga", "História", 4),
                    new ResultadoDeckPesquisa("deck-2", "História do Brasil", "História", 7)), null, false);
        }
        @Override public void buscarUsuarios(String termo, Object cursor, Callback<ResultadoUsuarioPesquisa> callback) {
            consultasUsuarios++;
            callback.onSuccess(Arrays.asList(
                    new ResultadoUsuarioPesquisa("usuario-1", "Usuário 1", ""),
                    new ResultadoUsuarioPesquisa("usuario-2", "Usuário 2", "")), null, false);
        }
    }
}
