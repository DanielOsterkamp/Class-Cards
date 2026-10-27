package com.example.classcardsaplicao.ui.deck.create;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.ui.deck.FormularioDeck;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FormularioDeckLayoutInstrumentedTest {
    @Test public void camposDasDuasTelasTemMesmasMedidasECoresEmTelasEstreitasEFontesMaiores() {
        principal(() -> {
            for (int largura : new int[]{320, 360, 411}) {
                for (float fonte : new float[]{1f, 1.3f}) {
                    Context contexto = contexto(fonte);
                    View criar = inflar(contexto, R.layout.fragment_criar_deck);
                    View editar = inflar(contexto, R.layout.fragment_editar_deck);
                    new FormularioDeck(criar); new FormularioDeck(editar);
                    medir(criar, largura); medir(editar, largura);
                    for (int id : new int[]{R.id.inputNomeDeck, R.id.inputMateriaDeck, R.id.inputPergunta, R.id.inputResposta}) {
                        EditText a = criar.findViewById(id); EditText b = editar.findViewById(id);
                        assertTrue(a.getWidth() > 0);
                        assertEquals(a.getWidth(), b.getWidth());
                        assertEquals(a.getHeight(), b.getHeight());
                        assertEquals(a.getTextSize(), b.getTextSize(), 0f);
                        assertEquals(a.getCurrentTextColor(), b.getCurrentTextColor());
                        assertEquals(a.getCurrentHintTextColor(), b.getCurrentHintTextColor());
                        assertEquals(a.getBackground().getClass(), b.getBackground().getClass());
                    }
                    if (largura == 360 && fonte == 1f) salvar(criar, "deck-criar-manual.png");
                }
            }
        });
    }

    @Test public void edicaoOfereceModoAutomaticoEIndicadorIgualAoDaCriacao() {
        principal(() -> {
            Context contexto = contexto(1f);
            for (int layout : new int[]{R.layout.fragment_criar_deck, R.layout.fragment_editar_deck}) {
                View tela = inflar(contexto, layout);
                FormularioDeck formulario = new FormularioDeck(tela);
                ((SwitchMaterial) tela.findViewById(R.id.switchModoCriacao)).setChecked(true);
                ((EditText) tela.findViewById(R.id.inputNomeDeck)).setText("Biologia Celular");
                ((EditText) tela.findViewById(R.id.inputMateriaDeck)).setText("Biologia");
                ((EditText) tela.findViewById(R.id.inputNumeroCards)).setText("10");
                ((EditText) tela.findViewById(R.id.inputDescricaoIA)).setText("Estruturas celulares e suas funções.");
                if (layout == R.layout.fragment_editar_deck) {
                    ((TextView) tela.findViewById(R.id.btnSalvarDeck)).setText(R.string.deck_salvar_alteracoes);
                }
                medir(tela, 360);
                assertEquals(View.VISIBLE, tela.findViewById(R.id.containerAutomatico).getVisibility());
                assertEquals(View.GONE, tela.findViewById(R.id.containerManual).getVisibility());
                assertTrue(tela.findViewById(R.id.btnGerarFlashcards).isEnabled());
                if (layout == R.layout.fragment_editar_deck) salvar(tela, "deck-editar-ia.png");
                formulario.atualizar(true, false, true);
                assertEquals(View.VISIBLE, tela.findViewById(R.id.containerCarregamentoCriacao).getVisibility());
                assertEquals(dp(contexto, 40), tela.findViewById(R.id.progressGeracaoFlashcards).getLayoutParams().width);
                assertFalse(tela.findViewById(R.id.btnSalvarDeck).isEnabled());
            }
        });
    }

    private Context contexto(float fonte) {
        Context alvo = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration config = new Configuration(alvo.getResources().getConfiguration()); config.fontScale = fonte;
        return new ContextThemeWrapper(alvo.createConfigurationContext(config),
                com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar);
    }
    private View inflar(Context contexto, int layout) { return LayoutInflater.from(contexto).inflate(layout, null, false); }
    private int dp(Context c, int valor) { return Math.round(c.getResources().getDisplayMetrics().density * valor); }
    private void medir(View view, int largura) {
        view.measure(View.MeasureSpec.makeMeasureSpec(dp(view.getContext(), largura), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dp(view.getContext(), 900), View.MeasureSpec.EXACTLY));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }
    private void salvar(View view, String nome) {
        Bitmap imagem = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(imagem));
        File destino = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null), nome);
        try (FileOutputStream arquivo = new FileOutputStream(destino)) { imagem.compress(Bitmap.CompressFormat.PNG, 100, arquivo); }
        catch (Exception erro) { throw new AssertionError(erro); }
        finally { imagem.recycle(); }
    }
    private static void principal(Runnable tarefa) { InstrumentationRegistry.getInstrumentation().runOnMainSync(tarefa); }
}
