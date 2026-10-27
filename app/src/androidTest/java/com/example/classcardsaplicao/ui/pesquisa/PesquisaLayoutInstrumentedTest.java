package com.example.classcardsaplicao.ui.pesquisa;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.model.ResultadoDeckPesquisa;
import com.example.classcardsaplicao.model.ResultadoUsuarioPesquisa;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Verifica medidas dos layouts reais com dados fictícios; não envia consultas ao Firebase. */
@RunWith(AndroidJUnit4.class)
public class PesquisaLayoutInstrumentedTest {

    @Test
    public void decksCabemEmTresColunasEmTelasEstreitasEComFonteMaior() {
        principal(() -> {
            for (int largura : new int[]{320, 360, 411}) {
                for (float escala : new float[]{1f, 1.3f}) {
                    Context context = contexto(escala, false);
                    View tela = tela(context);
                    RecyclerView recycler = tela.findViewById(R.id.recyclerPesquisa);
                    GridLayoutManager manager = new GridLayoutManager(context, 3);
                    PesquisaDeckAdapter adapter = new PesquisaDeckAdapter();
                    adapter.submitList(decks());
                    recycler.setLayoutManager(manager);
                    recycler.setAdapter(adapter);
                    medir(tela, largura, 800);

                    View primeiro = manager.findViewByPosition(0);
                    View segundo = manager.findViewByPosition(1);
                    View terceiro = manager.findViewByPosition(2);
                    View quarta = manager.findViewByPosition(3);
                    String caso = largura + "dp, fonte " + escala;
                    assertNotNull(caso, primeiro);
                    assertNotNull(caso, segundo);
                    assertNotNull(caso, terceiro);
                    assertNotNull(caso, quarta);
                    assertEquals(caso, primeiro.getTop(), segundo.getTop());
                    assertEquals(caso, primeiro.getTop(), terceiro.getTop());
                    assertTrue(caso, primeiro.getRight() <= segundo.getLeft());
                    assertTrue(caso, segundo.getRight() <= terceiro.getLeft());
                    assertTrue(caso, terceiro.getRight() <= recycler.getWidth());
                    assertTrue(caso, quarta.getTop() >= primeiro.getBottom());
                    for (View item : new View[]{primeiro, segundo, terceiro}) {
                        assertTrue("O deck deve permitir seleção", item.isClickable());
                        TextView titulo = item.findViewById(R.id.txtTituloDeck);
                        TextView quantidade = item.findViewById(R.id.txtQuantidadeCards);
                        assertTrue(caso, titulo.getWidth() > 0);
                        assertTrue(caso, limites(item, titulo).right <= item.getWidth());
                        assertTrue(caso, limites(item, titulo).bottom <= limites(item, quantidade).top);
                        assertTrue(caso, limites(item, quantidade).bottom <= item.getHeight());
                        assertEquals(Color.WHITE, titulo.getCurrentTextColor());
                    }
                    assertTrue("Deve permitir arrastar até os demais resultados", recycler.canScrollVertically(1));
                    if (largura == 360 && escala == 1f) salvar(tela, "pesquisa-decks-preview.png");
                    recycler.setAdapter(null);
                }
            }
        });
    }

    @Test
    public void usuariosOcupamUmaLinhaInteiraComAvatarSemSobreporONome() {
        principal(() -> {
            for (int largura : new int[]{320, 360, 411}) {
                for (float escala : new float[]{1f, 1.3f}) {
                    Context context = contexto(escala, false);
                    View tela = tela(context);
                    ((MaterialButtonToggleGroup) tela.findViewById(R.id.togglePesquisa)).check(R.id.btnPesquisaUsuarios);
                    ((TextView) tela.findViewById(R.id.campoPesquisa)).setText("Estudante");
                    RecyclerView recycler = tela.findViewById(R.id.recyclerPesquisa);
                    LinearLayoutManager manager = new LinearLayoutManager(context);
                    PesquisaUsuarioAdapter adapter = new PesquisaUsuarioAdapter();
                    adapter.submitList(usuarios());
                    recycler.setLayoutManager(manager);
                    recycler.setAdapter(adapter);
                    medir(tela, largura, 800);

                    View primeiro = manager.findViewByPosition(0);
                    View segundo = manager.findViewByPosition(1);
                    assertNotNull(primeiro);
                    assertNotNull(segundo);
                    assertTrue(segundo.getTop() >= primeiro.getBottom());
                    assertTrue(primeiro.getWidth() > recycler.getWidth() / 2);
                    ImageView foto = primeiro.findViewById(R.id.imgUsuarioPesquisa);
                    TextView nome = primeiro.findViewById(R.id.txtNomeUsuarioPesquisa);
                    assertNotNull("Foto ausente deve ter avatar local", foto.getDrawable());
                    assertEquals(foto.getWidth(), foto.getHeight());
                    assertTrue(limites(primeiro, foto).right <= limites(primeiro, nome).left);
                    assertTrue(limites(primeiro, nome).right <= primeiro.getWidth());
                    assertTrue("O usuário deve permitir seleção", primeiro.isClickable());
                    if (largura == 360 && escala == 1f) salvar(tela, "pesquisa-usuarios-preview.png");
                    recycler.setAdapter(null);
                }
            }
        });
    }

    @Test
    public void carregamentoMantemIndicadorDeQuarentaDpEAcessoAosFiltros() {
        principal(() -> {
            Context context = contexto(1.3f, false);
            View tela = LayoutInflater.from(context).inflate(R.layout.fragment_pesquisa, null, false);
            tela.findViewById(R.id.containerEstadoPesquisa).setVisibility(View.GONE);
            View carregamento = tela.findViewById(R.id.containerCarregandoPesquisa);
            carregamento.setVisibility(View.VISIBLE);
            medir(tela, 320, 720);
            ProgressBar progress = encontrarProgress(carregamento);
            assertNotNull(progress);
            assertEquals(dp(context, 40), progress.getWidth());
            assertEquals(dp(context, 40), progress.getHeight());
            View filtros = tela.findViewById(R.id.togglePesquisa);
            View campo = tela.findViewById(R.id.layoutCampoPesquisa);
            assertTrue(limites(tela, campo).bottom <= limites(tela, filtros).top);
            assertTrue(limites(tela, filtros).bottom <= limites(tela, carregamento).top);
        });
    }

    @Test
    public void textosDosResultadosPermanecemBrancosNosTemasClaroEEscuro() {
        principal(() -> {
            for (boolean escuro : new boolean[]{false, true}) {
                Context context = contexto(1f, escuro);
                View deck = LayoutInflater.from(context).inflate(R.layout.item_deck, null, false);
                View usuario = LayoutInflater.from(context).inflate(R.layout.item_pesquisa_usuario, null, false);
                assertEquals(Color.WHITE, ((TextView) deck.findViewById(R.id.txtTituloDeck)).getCurrentTextColor());
                assertEquals(Color.WHITE, ((TextView) usuario.findViewById(R.id.txtNomeUsuarioPesquisa)).getCurrentTextColor());
            }
        });
    }

    private View tela(Context context) {
        View tela = LayoutInflater.from(context).inflate(R.layout.fragment_pesquisa, null, false);
        // A captura é estática e não possui a janela de uma Activity para concluir a animação do hint.
        ((TextInputLayout) tela.findViewById(R.id.layoutCampoPesquisa)).setHintAnimationEnabled(false);
        ((TextView) tela.findViewById(R.id.campoPesquisa)).setText("História");
        tela.findViewById(R.id.containerEstadoPesquisa).setVisibility(View.GONE);
        RecyclerView recycler = tela.findViewById(R.id.recyclerPesquisa);
        recycler.setVisibility(View.VISIBLE);
        recycler.setItemAnimator(null);
        return tela;
    }

    private List<ResultadoDeckPesquisa> decks() {
        List<ResultadoDeckPesquisa> decks = new ArrayList<>();
        String[] titulos = {"História do Brasil", "História Antiga", "História da Arte",
                "História Medieval", "História Moderna", "História Contemporânea"};
        for (int i = 0; i < 12; i++) {
            decks.add(new ResultadoDeckPesquisa("d" + i, titulos[i % titulos.length], "História", 12 + i));
        }
        return decks;
    }

    private List<ResultadoUsuarioPesquisa> usuarios() {
        List<ResultadoUsuarioPesquisa> usuarios = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            usuarios.add(new ResultadoUsuarioPesquisa("u" + i, "Estudante de História " + i, ""));
        }
        return usuarios;
    }

    private Context contexto(float escala, boolean escuro) {
        Context alvo = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration config = new Configuration(alvo.getResources().getConfiguration());
        config.fontScale = escala;
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (escuro ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return new ContextThemeWrapper(alvo.createConfigurationContext(config),
                com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar);
    }

    private void medir(View view, int larguraDp, int alturaDp) {
        // TextInputLayout ajusta o espaço dos ícones após o layout global; a segunda medição aplica esse ajuste.
        for (int passo = 0; passo < 3; passo++) {
            view.measure(View.MeasureSpec.makeMeasureSpec(dp(view.getContext(), larguraDp), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(dp(view.getContext(), alturaDp), View.MeasureSpec.EXACTLY));
            view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
            view.getViewTreeObserver().dispatchOnGlobalLayout();
        }
    }

    private Rect limites(View pai, View filho) {
        Rect rect = new Rect(0, 0, filho.getWidth(), filho.getHeight());
        ((ViewGroup) pai).offsetDescendantRectToMyCoords(filho, rect);
        return rect;
    }

    private ProgressBar encontrarProgress(View view) {
        if (view instanceof ProgressBar) return (ProgressBar) view;
        if (view instanceof ViewGroup) {
            ViewGroup grupo = (ViewGroup) view;
            for (int i = 0; i < grupo.getChildCount(); i++) {
                ProgressBar progress = encontrarProgress(grupo.getChildAt(i));
                if (progress != null) return progress;
            }
        }
        return null;
    }

    private int dp(Context context, int valor) {
        return Math.round(valor * context.getResources().getDisplayMetrics().density);
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

    private static void principal(Runnable tarefa) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(tarefa);
    }
}
