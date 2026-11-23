package com.example.classcardsaplicao.ui.relatorios;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.graphics.*;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.fragment.app.*;
import androidx.lifecycle.*;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.classcardsaplicao.MainActivity;
import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.model.ResumoRelatorio;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import java.io.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RelatoriosInstrumentedTest {
    private Application app;
    private Fabrica fabrica;
    @Before public void preparar() {
        app=(Application)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        fabrica=new Fabrica();app.registerActivityLifecycleCallbacks(fabrica);
    }
    @After public void limpar() {app.unregisterActivityLifecycleCallbacks(fabrica);}
    @Test public void dashboardRealMostraMetricasListasEDetalhes() {
        try(ActivityScenario<MainActivity> c=abrir()) {
            c.onActivity(a -> {
                View view=tela(a).requireView();
                assertEquals(View.VISIBLE,view.findViewById(R.id.conteudoRelatorios).getVisibility());
                assertTrue(((TextView)view.findViewById(R.id.filtroTurmaRelatorios)).getText().toString().contains("Todas"));
                LinearLayout secoes=view.findViewById(R.id.secoesRelatorios);assertTrue(secoes.getChildCount()>4);
                View linha=secoes.getChildAt(1);assertTrue(((TextView)linha.findViewById(R.id.nomeRelatorio)).getText().toString().contains("Ana"));
                assertTrue(((TextView)linha.findViewById(R.id.dadosRelatorio)).getText().toString().contains("60,0%"));
                salvar(view);linha.performClick();
            });
        }
    }
    @Test public void carregamentoErroEVazioNaoExibemDadosAntigos() {
        try(ActivityScenario<MainActivity> c=abrir()) {
            c.onActivity(a -> {
                RelatoriosFixture f=(RelatoriosFixture)tela(a);View v=f.requireView();
                f.modelo.carregando.setValue(true);assertEquals(View.VISIBLE,v.findViewById(R.id.progressRelatorios).getVisibility());assertEquals(View.GONE,v.findViewById(R.id.conteudoRelatorios).getVisibility());
                f.modelo.carregando.setValue(false);f.modelo.erro.setValue("Sem permissão");assertEquals(View.VISIBLE,v.findViewById(R.id.btnTentarRelatorios).getVisibility());assertEquals(View.GONE,v.findViewById(R.id.conteudoRelatorios).getVisibility());
                f.modelo.erro.setValue(null);f.modelo.resumo.setValue(new ResumoRelatorio(List.of(),Map.of()));assertEquals(View.VISIBLE,v.findViewById(R.id.vazioRelatorios).getVisibility());
            });
        }
    }
    @Test public void assuntoTemComponenteIndependenteEOcultoPorPadrao() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            android.content.Context contexto=new android.view.ContextThemeWrapper(app,R.style.Theme_ClassCardsAplicação);
            View form=android.view.LayoutInflater.from(contexto).inflate(R.layout.layout_formulario_deck,null,false);
            View assunto=form.findViewById(R.id.inputAssuntoDeck);assertNotNull(assunto);assertEquals(View.GONE,assunto.getVisibility());
            assertTrue(assunto instanceof EditText);
        });
    }
    private ActivityScenario<MainActivity> abrir() {
        ActivityScenario<MainActivity> c=ActivityScenario.launch(MainActivity.class);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        c.onActivity(a -> host(a).getNavController().navigate(R.id.relatoriosFragment));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();return c;
    }
    private static NavHostFragment host(MainActivity a){return (NavHostFragment)a.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);}
    private static Fragment tela(MainActivity a){return host(a).getChildFragmentManager().getPrimaryNavigationFragment();}
    public static class RelatoriosFixture extends RelatoriosFragment {
        RelatoriosViewModel modelo;
        @NonNull @Override public ViewModelProvider.Factory getDefaultViewModelProviderFactory() {
            return new ViewModelProvider.Factory(){
                @NonNull @Override public <T extends ViewModel>T create(@NonNull Class<T> tipo){
                    modelo=new RelatoriosViewModel((Application)requireContext().getApplicationContext(),new SavedStateHandle()){
                        @Override public void atualizar(){
                            cursos.setValue(Map.of("site:2","Biologia — 2º ano"));
                            ResumoRelatorio.Registro r=new ResumoRelatorio.Registro();r.uid="aluna";r.nome="Ana Silva";r.curso="site:2";r.card="c";r.deck="deck";r.titulo="Divisão celular";r.assunto="Divisão celular";r.dia="2026-11-18";r.revisoes=10;r.errei=6;r.dificil=2;
                            resumo.setValue(new ResumoRelatorio(List.of(r),cursos.getValue()));carregando.setValue(false);
                        }
                    };return tipo.cast(modelo);
                }
            };
        }
    }
    private static class Fabrica implements Application.ActivityLifecycleCallbacks {
        @Override public void onActivityPreCreated(Activity a,Bundle b){if(a instanceof MainActivity)((MainActivity)a).getSupportFragmentManager().setFragmentFactory(new FragmentFactory(){
            @NonNull @Override public Fragment instantiate(@NonNull ClassLoader l,@NonNull String nome){if(nome.equals(RelatoriosFragment.class.getName())||nome.equals(RelatoriosFixture.class.getName()))return new RelatoriosFixture();return super.instantiate(l,nome);}
        });}
        public void onActivityCreated(Activity a,Bundle b){} public void onActivityStarted(Activity a){} public void onActivityResumed(Activity a){} public void onActivityPaused(Activity a){} public void onActivityStopped(Activity a){} public void onActivitySaveInstanceState(Activity a,Bundle b){} public void onActivityDestroyed(Activity a){}
    }
    private void salvar(View v){Bitmap img=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(img));try(FileOutputStream out=new FileOutputStream(new File(app.getExternalFilesDir(null),"relatorios-preview.png"))){img.compress(Bitmap.CompressFormat.PNG,100,out);}catch(Exception e){throw new AssertionError(e);}finally{img.recycle();}}
}
