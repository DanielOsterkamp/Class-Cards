package com.example.classcardsaplicao;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PerfilLayoutInstrumentedTest {
    @Test public void tresCardsFicamLadoALadoEmTelasEstreitasEComFonteMaior() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int largura : new int[]{320, 360, 411}) {
                for (float fonte : new float[]{1f, 1.3f}) {
                    View perfil = inflar(R.layout.fragment_perfil, fonte);
                    ((TextView) perfil.findViewById(R.id.txtOfensiva)).setText("7");
                    ((TextView) perfil.findViewById(R.id.txtQuantidadeEstudados)).setText("102");
                    ((TextView) perfil.findViewById(R.id.txtXp)).setText("1.200");
                    medir(perfil, largura);
                    View ofensiva = perfil.findViewById(R.id.cardOfensiva);
                    View estudados = perfil.findViewById(R.id.cardEstudados);
                    View xp = perfil.findViewById(R.id.cardXp);
                    assertTrue(ofensiva.getWidth() > 0);
                    assertTrue(ofensiva.getRight() <= estudados.getLeft());
                    assertTrue(estudados.getRight() <= xp.getLeft());
                    assertEquals(ofensiva.getTop(), estudados.getTop());
                    assertEquals(estudados.getTop(), xp.getTop());
                    assertTrue(Math.abs(ofensiva.getWidth() - estudados.getWidth()) <= 1);
                    assertTrue(Math.abs(estudados.getWidth() - xp.getWidth()) <= 1);
                    assertEquals(perfil.findViewById(R.id.txtOfensiva).getTop(),
                            perfil.findViewById(R.id.txtQuantidadeEstudados).getTop());
                    assertEquals(perfil.findViewById(R.id.txtOfensiva).getTop(),
                            perfil.findViewById(R.id.txtXp).getTop());
                    for (int id : new int[]{R.id.txtOfensiva, R.id.txtQuantidadeEstudados, R.id.txtXp}) {
                        TextView valor = perfil.findViewById(id);
                        assertNotNull(valor.getLayout());
                        assertTrue(valor.getLayout().getLineWidth(0) <= valor.getWidth());
                    }
                    if (largura == 360 && fonte == 1f) salvarPreview(perfil.findViewById(R.id.layoutGamificacao));
                }
            }
        });
    }

    @Test public void layoutDissertativoPossuiTodosOsControlesDoFluxo() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View estudo = inflar(R.layout.fragment_estudar_deck_dissertativo, 1f);
            for (int id : new int[]{R.id.txtNomeDeck2, R.id.txtPergunta, R.id.editResposta,
                    R.id.btnAvaliarResposta, R.id.txtStatusAvaliacao, R.id.txtErroEstudo,
                    R.id.containerResultado, R.id.containerAvaliacaoFinal, R.id.btnErrei,
                    R.id.btnDificil, R.id.btnBom, R.id.btnFacil, R.id.btnVoltar2}) {
                assertNotNull(estudo.findViewById(id));
            }
            assertEquals(View.GONE, estudo.findViewById(R.id.containerResultado).getVisibility());
            assertEquals(View.GONE, estudo.findViewById(R.id.containerCarregamento).getVisibility());
        });
    }

    private View inflar(int layout, float fonte) {
        Context alvo = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration config = new Configuration(alvo.getResources().getConfiguration());
        config.fontScale = fonte;
        Context tema = new ContextThemeWrapper(alvo.createConfigurationContext(config),
                com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar);
        return LayoutInflater.from(tema).inflate(layout, null, false);
    }
    private void medir(View view, int larguraDp) {
        float densidade = view.getResources().getDisplayMetrics().density;
        view.measure(View.MeasureSpec.makeMeasureSpec(Math.round(larguraDp * densidade), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(Math.round(900 * densidade), View.MeasureSpec.EXACTLY));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }
    private void salvarPreview(View view) {
        Bitmap imagem = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(imagem));
        Context alvo = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (FileOutputStream stream = new FileOutputStream(new File(alvo.getExternalFilesDir(null), "perfil-preview.png"))) {
            imagem.compress(Bitmap.CompressFormat.PNG, 100, stream);
        } catch (Exception e) { throw new AssertionError(e); }
        imagem.recycle();
    }
}
