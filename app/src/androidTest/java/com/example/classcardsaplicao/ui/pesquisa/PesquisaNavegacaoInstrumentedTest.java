package com.example.classcardsaplicao.ui.pesquisa;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.classcardsaplicao.MainActivity;
import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.model.ResultadoDeckPesquisa;
import com.example.classcardsaplicao.model.ResultadoUsuarioPesquisa;
import com.example.classcardsaplicao.ui.home.HomeFragment;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Valida a navegação real em um emulador sem conta, sem fazer login ou consultar dados privados. */
@RunWith(AndroidJUnit4.class)
public class PesquisaNavegacaoInstrumentedTest {

    @Test
    public void campoDaHomeAbrePesquisaComFactoryPadraoEVoltarRetornaAHome() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            aguardar();
            assumeTrue("Execute este teste em um emulador sem conta conectada",
                    FirebaseAuth.getInstance().getCurrentUser() == null);
            abrirPesquisaPelaHome(scenario);
            scenario.onActivity(activity -> {
                NavHostFragment host = host(activity);
                assertEquals(R.id.pesquisaFragment, host.getNavController().getCurrentDestination().getId());
                Fragment atual = host.getChildFragmentManager().getPrimaryNavigationFragment();
                assertTrue(atual instanceof PesquisaFragment);
                assertNotNull(atual.getView());
                PesquisaViewModel viewModel = new ViewModelProvider(atual).get(PesquisaViewModel.class);
                assertNotNull("A factory padrão deve fornecer SavedStateHandle ao construtor", viewModel);
                assertTrue(((RecyclerView) atual.requireView().findViewById(R.id.recyclerPesquisa))
                        .getLayoutManager() instanceof GridLayoutManager);
                atual.requireView().findViewById(R.id.btnVoltarPesquisa).performClick();
            });
            aguardar();
            scenario.onActivity(activity -> {
                NavHostFragment host = host(activity);
                assertEquals(R.id.homeFragment, host.getNavController().getCurrentDestination().getId());
                assertTrue(host.getChildFragmentManager().getPrimaryNavigationFragment() instanceof HomeFragment);
            });
        }
    }

    @Test
    public void recriarActivityPreservaTextoEAbaDeUsuariosComUmaColuna() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            aguardar();
            assumeTrue("Execute este teste em um emulador sem conta conectada",
                    FirebaseAuth.getInstance().getCurrentUser() == null);
            abrirPesquisaPelaHome(scenario);
            scenario.onActivity(activity -> {
                Fragment pesquisa = host(activity).getChildFragmentManager().getPrimaryNavigationFragment();
                assertTrue(pesquisa instanceof PesquisaFragment);
                ((TextView) pesquisa.requireView().findViewById(R.id.campoPesquisa)).setText("História");
                ((MaterialButtonToggleGroup) pesquisa.requireView().findViewById(R.id.togglePesquisa))
                        .check(R.id.btnPesquisaUsuarios);
            });
            scenario.recreate();
            aguardar();
            // Aguarda a animação do hint e a medição dos ícones da janela real.
            Thread.sleep(350);
            aguardar();
            scenario.onActivity(activity -> {
                Fragment pesquisa = host(activity).getChildFragmentManager().getPrimaryNavigationFragment();
                assertTrue(pesquisa instanceof PesquisaFragment);
                assertEquals("História", ((TextView) pesquisa.requireView()
                        .findViewById(R.id.campoPesquisa)).getText().toString());
                PesquisaViewModel viewModel = new ViewModelProvider(pesquisa).get(PesquisaViewModel.class);
                assertEquals("História", viewModel.getTermo());
                assertEquals(PesquisaViewModel.Aba.USUARIOS, viewModel.getAba().getValue());
                RecyclerView recycler = pesquisa.requireView().findViewById(R.id.recyclerPesquisa);
                assertTrue(recycler.getLayoutManager() instanceof LinearLayoutManager);
                assertFalse(recycler.getLayoutManager() instanceof GridLayoutManager);
                assertEquals(R.id.btnPesquisaUsuarios, ((MaterialButtonToggleGroup) pesquisa.requireView()
                        .findViewById(R.id.togglePesquisa)).getCheckedButtonId());
                assertNull(FirebaseAuth.getInstance().getCurrentUser());
                TextInputEditText campo = pesquisa.requireView().findViewById(R.id.campoPesquisa);
                salvar(pesquisa.requireView(), "pesquisa-campo-real.png");
                Log.i("PESQUISA_TESTE", "Campo real: compoundPaddingLeft=" + campo.getCompoundPaddingLeft()
                        + ", paddingLeft=" + campo.getPaddingLeft());
                int minimo = Math.round(40 * campo.getResources().getDisplayMetrics().density);
                assertTrue("O texto deve reservar espaço para a lupa: compoundPaddingLeft="
                                + campo.getCompoundPaddingLeft() + ", paddingLeft=" + campo.getPaddingLeft(),
                        campo.getCompoundPaddingLeft() >= minimo);
            });
            // As prévias são fixtures de teste, desenhadas na janela real após validar a navegação.
            gerarPrevia(scenario, false);
            gerarPrevia(scenario, true);
        }
    }

    private void gerarPrevia(ActivityScenario<MainActivity> scenario, boolean usuarios) {
        scenario.onActivity(activity -> {
            View view = host(activity).getChildFragmentManager().getPrimaryNavigationFragment().requireView();
            ((TextView) view.findViewById(R.id.campoPesquisa)).setText(usuarios ? "Estudante" : "História");
            ((MaterialButtonToggleGroup) view.findViewById(R.id.togglePesquisa)).check(usuarios
                    ? R.id.btnPesquisaUsuarios : R.id.btnPesquisaFlashcards);
            RecyclerView recycler = view.findViewById(R.id.recyclerPesquisa);
            if (usuarios) {
                PesquisaUsuarioAdapter adapter = new PesquisaUsuarioAdapter();
                List<ResultadoUsuarioPesquisa> itens = new ArrayList<>();
                for (int i = 1; i <= 8; i++) {
                    itens.add(new ResultadoUsuarioPesquisa("fixture-u" + i, "Estudante de História " + i, ""));
                }
                adapter.submitList(itens);
                recycler.setLayoutManager(new LinearLayoutManager(activity));
                recycler.setAdapter(adapter);
            } else {
                PesquisaDeckAdapter adapter = new PesquisaDeckAdapter();
                List<ResultadoDeckPesquisa> itens = new ArrayList<>();
                String[] titulos = {"História do Brasil", "História Antiga", "História da Arte",
                        "História Medieval", "História Moderna", "História Contemporânea"};
                for (int i = 0; i < titulos.length; i++) {
                    itens.add(new ResultadoDeckPesquisa("fixture-d" + i, titulos[i], "História", 12 + i));
                }
                adapter.submitList(itens);
                recycler.setLayoutManager(new GridLayoutManager(activity, 3));
                recycler.setAdapter(adapter);
            }
            view.findViewById(R.id.containerEstadoPesquisa).setVisibility(View.GONE);
            view.findViewById(R.id.containerCarregandoPesquisa).setVisibility(View.GONE);
            view.findViewById(R.id.containerRodapePesquisa).setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
            ((TextView) view.findViewById(R.id.txtQuantidadePesquisa)).setText(usuarios
                    ? "8 usuários carregados" : "6 decks carregados");
        });
        aguardar();
        scenario.onActivity(activity -> {
            View view = host(activity).getChildFragmentManager().getPrimaryNavigationFragment().requireView();
            RecyclerView recycler = view.findViewById(R.id.recyclerPesquisa);
            assertTrue("A prévia deve usar os resultados dos adapters reais", recycler.getChildCount() > 0);
            salvar(view, usuarios ? "pesquisa-usuarios-preview.png" : "pesquisa-decks-preview.png");
        });
    }

    private void abrirPesquisaPelaHome(ActivityScenario<MainActivity> scenario) {
        scenario.onActivity(activity -> host(activity).getNavController().navigate(R.id.homeFragment));
        aguardar();
        scenario.onActivity(activity -> {
            assertNotNull(activity.findViewById(R.id.imageView2));
            activity.findViewById(R.id.imageView2).performClick();
        });
        aguardar();
    }

    private NavHostFragment host(MainActivity activity) {
        return (NavHostFragment) activity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
    }

    private void aguardar() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void salvar(View view, String nome) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        Context alvo = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (FileOutputStream arquivo = new FileOutputStream(new File(alvo.getExternalFilesDir(null), nome))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, arquivo);
        } catch (Exception erro) {
            throw new AssertionError(erro);
        } finally {
            bitmap.recycle();
        }
    }
}
