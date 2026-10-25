package com.example.classcardsaplicao.ui.rankings;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.model.RankingCalculadora;
import com.example.classcardsaplicao.model.RankingSecao;
import com.example.classcardsaplicao.model.RegrasGamificacao;
import com.example.classcardsaplicao.model.TipoRanking;
import com.example.classcardsaplicao.model.UsuarioRanking;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.Assert.*;

/** Renderiza a interface com dados sintéticos; não cria contas nem consulta serviços externos. */
@RunWith(AndroidJUnit4.class)
public class RankingsInstrumentedTest {

    @Test
    public void usuarioForaDoTopCincoApareceNaUltimaLinhaComSuaPosicaoRealEFotoFallback() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = contexto(1f);
            RankingsAdapter adapter = new RankingsAdapter();
            adapter.atualizar(secoes(12, "u12"));
            RankingsAdapter.SecaoHolder holder = card(adapter, context, TipoRanking.PONTOS);
            medirCard(holder.itemView, 360);

            List<View> linhas = linhas(holder.usuarios);
            assertEquals(6, linhas.size());
            for (int i = 0; i < 5; i++) {
                assertEquals(context.getString(R.string.ranking_posicao, i + 1),
                        texto(linhas.get(i), R.id.txtPosicaoRanking));
            }
            View ultima = linhas.get(linhas.size() - 1);
            assertEquals(context.getString(R.string.ranking_posicao, 12), texto(ultima, R.id.txtPosicaoRanking));
            assertEquals("Estudante 12", texto(ultima, R.id.txtUsuarioRanking));
            assertEquals(View.VISIBLE, ultima.findViewById(R.id.txtVoceRanking).getVisibility());
            assertEquals(1, quantidadeMarcadaComoVoce(linhas));
            assertNotNull(((ImageView) ultima.findViewById(R.id.imgFotoRanking)).getDrawable());
            adapter.onViewRecycled(holder);
        });
    }

    @Test
    public void usuarioQueJaEstaNoTopCincoNaoRecebeUmaLinhaDuplicada() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            RankingsAdapter adapter = new RankingsAdapter();
            adapter.atualizar(secoes(8, "u03"));
            RankingsAdapter.SecaoHolder holder = card(adapter, contexto(1f), TipoRanking.PONTOS);
            medirCard(holder.itemView, 360);

            List<View> linhas = linhas(holder.usuarios);
            assertEquals(5, linhas.size());
            assertEquals(1, quantidadeMarcadaComoVoce(linhas));
            assertEquals("Estudante 3", texto(linhas.get(2), R.id.txtUsuarioRanking));
            assertEquals(View.VISIBLE, linhas.get(2).findViewById(R.id.txtVoceRanking).getVisibility());
            // Sem um usuário extra, também não há o divisor e a legenda de posição adicional.
            assertEquals(5, holder.usuarios.getChildCount());
            adapter.onViewRecycled(holder);
        });
    }

    @Test
    public void listaComMenosDeCincoPessoasMostraSomenteParticipantesReais() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            RankingsAdapter adapter = new RankingsAdapter();
            adapter.atualizar(secoes(3, "u02"));
            RankingsAdapter.SecaoHolder holder = card(adapter, contexto(1f), TipoRanking.PONTOS);
            medirCard(holder.itemView, 320);

            List<View> linhas = linhas(holder.usuarios);
            assertEquals(3, linhas.size());
            assertEquals("Estudante 1", texto(linhas.get(0), R.id.txtUsuarioRanking));
            assertEquals("Estudante 3", texto(linhas.get(2), R.id.txtUsuarioRanking));
            assertEquals(1, quantidadeMarcadaComoVoce(linhas));
            assertEquals(View.GONE, holder.vazio.getVisibility());
            adapter.onViewRecycled(holder);
        });
    }

    @Test
    public void fotosENumerosNaoSeSobrepoemEmTelasEstreitasOuComFonteAmpliada() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int largura : new int[]{320, 360, 411}) {
                for (float escala : new float[]{1f, 1.3f}) {
                    RankingsAdapter adapter = new RankingsAdapter();
                    adapter.atualizar(secoes(12, "u12"));
                    RankingsAdapter.SecaoHolder holder = card(adapter, contexto(escala), TipoRanking.PONTOS);
                    medirCard(holder.itemView, largura);

                    for (View linha : linhas(holder.usuarios)) {
                        View posicao = linha.findViewById(R.id.txtPosicaoRanking);
                        View foto = linha.findViewById(R.id.imgFotoRanking);
                        View nome = linha.findViewById(R.id.txtUsuarioRanking);
                        TextView valor = linha.findViewById(R.id.txtValorRanking);
                        Rect bPosicao = limites(holder.itemView, posicao);
                        Rect bFoto = limites(holder.itemView, foto);
                        Rect bNome = limites(holder.itemView, nome);
                        Rect bValor = limites(holder.itemView, valor);
                        String caso = largura + "dp, fonte " + escala;

                        assertTrue(caso, foto.getWidth() > 0 && foto.getHeight() > 0);
                        assertEquals(caso, foto.getWidth(), foto.getHeight());
                        assertTrue(caso, bPosicao.right <= bFoto.left);
                        assertTrue(caso, bFoto.right <= bNome.left);
                        assertTrue(caso, bNome.right <= bValor.left);
                        assertTrue(caso, bFoto.top >= 0 && bFoto.bottom <= holder.itemView.getHeight());
                        assertTrue(caso, bValor.right <= holder.itemView.getWidth());
                        assertTrue(caso, nome.getWidth() > 0);
                        assertNotNull(caso, valor.getLayout());
                        assertEquals(caso, 0, valor.getLayout().getEllipsisCount(0));
                        assertTrue(caso + ": valor cortado", valor.getLayout().getLineWidth(0)
                                <= valor.getWidth() - valor.getPaddingLeft() - valor.getPaddingRight() + 1);
                    }
                    if (largura == 360 && escala == 1f) salvarPreview(holder.itemView);
                    adapter.onViewRecycled(holder);
                }
            }
        });
    }

    @Test
    public void tresSecoesPermitemArrastarAListaAteAsCategoriasAbaixo() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = contexto(1f);
            View tela = LayoutInflater.from(context).inflate(R.layout.fragment_rankings, null, false);
            RecyclerView recycler = tela.findViewById(R.id.recyclerRankings);
            LinearLayoutManager manager = new LinearLayoutManager(context);
            RankingsAdapter adapter = new RankingsAdapter();
            adapter.atualizar(secoes(12, "u12"));
            recycler.setLayoutManager(manager);
            recycler.setAdapter(adapter);
            tela.findViewById(R.id.containerCarregandoRankings).setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
            medirTela(tela, 360, 720);

            assertTrue(recycler.canScrollVertically(1));
            recycler.scrollBy(0, dp(context, 1000));
            assertTrue("O gesto deve alcançar outro card do ranking", manager.findFirstVisibleItemPosition() > 0);
            recycler.setAdapter(null);
        });
    }

    private List<RankingSecao> secoes(int quantidade, String uidAtual) {
        long agora = System.currentTimeMillis();
        String hoje = RegrasGamificacao.dia(agora, TimeZone.getTimeZone("UTC"), 0);
        List<UsuarioRanking> usuarios = new ArrayList<>();
        for (int i = 1; i <= quantidade; i++) {
            // Fotos vazias exercitam o avatar local; nenhum URL externo é solicitado.
            usuarios.add(new UsuarioRanking(String.format(Locale.ROOT, "u%02d", i),
                    "Estudante " + i, "", (quantidade - i + 1) * 100_000L,
                    quantidade - i + 1, (quantidade - i + 1) * 25L, hoje, "UTC"));
        }
        return RankingCalculadora.montar(usuarios, uidAtual, agora);
    }

    private RankingsAdapter.SecaoHolder card(RankingsAdapter adapter, Context context, TipoRanking tipo) {
        RankingsAdapter.SecaoHolder holder = adapter.onCreateViewHolder(new FrameLayout(context), 0);
        adapter.onBindViewHolder(holder, tipo.ordinal());
        return holder;
    }

    private List<View> linhas(LinearLayout container) {
        List<View> linhas = new ArrayList<>();
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child.findViewById(R.id.imgFotoRanking) != null) linhas.add(child);
        }
        return linhas;
    }

    private int quantidadeMarcadaComoVoce(List<View> linhas) {
        int quantidade = 0;
        for (View linha : linhas) {
            if (linha.findViewById(R.id.txtVoceRanking).getVisibility() == View.VISIBLE) quantidade++;
        }
        return quantidade;
    }

    private String texto(View view, int id) {
        return ((TextView) view.findViewById(id)).getText().toString();
    }

    private Rect limites(View card, View child) {
        Rect rect = new Rect(0, 0, child.getWidth(), child.getHeight());
        ((ViewGroup) card).offsetDescendantRectToMyCoords(child, rect);
        return rect;
    }

    private Context contexto(float escala) {
        Context alvo = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration config = new Configuration(alvo.getResources().getConfiguration());
        config.fontScale = escala;
        return new ContextThemeWrapper(alvo.createConfigurationContext(config),
                com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar);
    }

    private int dp(Context context, int valor) {
        return Math.round(valor * context.getResources().getDisplayMetrics().density);
    }

    private void medirCard(View view, int larguraTelaDp) {
        Context context = view.getContext();
        view.measure(View.MeasureSpec.makeMeasureSpec(dp(context, larguraTelaDp - 32), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private void medirTela(View view, int larguraDp, int alturaDp) {
        view.measure(View.MeasureSpec.makeMeasureSpec(dp(view.getContext(), larguraDp), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dp(view.getContext(), alturaDp), View.MeasureSpec.EXACTLY));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private void salvarPreview(View view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        Context alvo = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (FileOutputStream arquivo = new FileOutputStream(new File(alvo.getExternalFilesDir(null), "rankings-preview.png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, arquivo);
        } catch (Exception erro) {
            throw new AssertionError(erro);
        } finally {
            bitmap.recycle();
        }
    }
}
