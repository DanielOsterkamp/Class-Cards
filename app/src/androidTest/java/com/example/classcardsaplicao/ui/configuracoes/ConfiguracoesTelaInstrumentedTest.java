package com.example.classcardsaplicao.ui.configuracoes;
import android.app.Activity;
import android.app.Application;
import android.graphics.*;
import android.os.Bundle;
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
import com.google.android.material.navigation.NavigationView;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class ConfiguracoesTelaInstrumentedTest {
    private Application app;private Fabrica fabrica;
    @Before public void inicio(){app=(Application)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();fabrica=new Fabrica();app.registerActivityLifecycleCallbacks(fabrica);}
    @After public void fim(){app.unregisterActivityLifecycleCallbacks(fabrica);}
    private ActivityScenario<MainActivity> abrir(){ActivityScenario<MainActivity> c=ActivityScenario.launch(MainActivity.class);InstrumentationRegistry.getInstrumentation().waitForIdleSync();c.onActivity(a->{NavigationView menu=a.findViewById(R.id.navigationView);assertTrue(menu.getMenu().performIdentifierAction(R.id.menuConfiguracoes,0));});InstrumentationRegistry.getInstrumentation().waitForIdleSync();return c;}
    private Fixture tela(MainActivity a){NavHostFragment h=(NavHostFragment)a.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);assertEquals(R.id.configuracoesFragment,h.getNavController().getCurrentDestination().getId());return (Fixture)h.getChildFragmentManager().getPrimaryNavigationFragment();}
    @Test public void glideRegistraCarregadorDeAvatarEmTodasAsTelas(){
        String ref=com.example.classcardsaplicao.model.ReferenciaAvatar.criar("u","12345678-1234-1234-1234-123456789abc");
        java.util.List<com.bumptech.glide.load.model.ModelLoader<String,?>> modelos=com.bumptech.glide.Glide.get(app).getRegistry().getModelLoaders(ref);
        boolean encontrou=false;for(com.bumptech.glide.load.model.ModelLoader<?,?> modelo:modelos)if(modelo instanceof com.example.classcardsaplicao.ui.imagens.AvatarModelLoader)encontrou=true;
        assertTrue("O carregador do Firestore deve estar registrado no Glide",encontrou);
    }
    @Test public void menuAbreConfiguracoesComFotoEBotoes(){try(ActivityScenario<MainActivity> c=abrir()){c.onActivity(a->{View v=tela(a).requireView();assertNotNull(v.findViewById(R.id.imgFotoConfiguracoes));assertTrue(v.findViewById(R.id.btnEscolherFoto).isEnabled());assertFalse(v.findViewById(R.id.btnSalvarFoto).isEnabled());salvar(v);});}}
    @Test public void previaPermiteSalvarEDescartarSemAlterarFotoAtual(){try(ActivityScenario<MainActivity> c=abrir()){c.onActivity(a->{Fixture f=tela(a);f.vm.selecionar(android.net.Uri.parse("content://foto"));File arquivo=imagem();f.fonte.preparacao.sucesso(arquivo);View v=f.requireView();assertTrue(v.findViewById(R.id.btnSalvarFoto).isEnabled());v.findViewById(R.id.btnDescartarFoto).performClick();assertFalse(v.findViewById(R.id.btnSalvarFoto).isEnabled());assertFalse(arquivo.exists());assertEquals(0,f.fonte.envios);});}}
    @Test public void falhaExibeMensagemEPreservaPreviaParaReenvio(){try(ActivityScenario<MainActivity> c=abrir()){c.onActivity(a->{Fixture f=tela(a);f.vm.selecionar(android.net.Uri.parse("content://foto"));File arquivo=imagem();f.fonte.preparacao.sucesso(arquivo);View v=f.requireView();v.findViewById(R.id.btnSalvarFoto).performClick();assertFalse(v.findViewById(R.id.btnVoltar).isEnabled());assertEquals(View.VISIBLE,v.findViewById(R.id.containerCarregandoFoto).getVisibility());f.fonte.envio.erro(new Exception());assertTrue(v.findViewById(R.id.btnVoltar).isEnabled());assertTrue(v.findViewById(R.id.btnSalvarFoto).isEnabled());assertEquals(View.VISIBLE,v.findViewById(R.id.txtMensagemFoto).getVisibility());f.vm.descartar();});}}
    private File imagem(){try{File f=File.createTempFile("teste-foto-tela-",".jpg",app.getCacheDir());Bitmap b=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);b.eraseColor(Color.rgb(107,53,135));try(FileOutputStream o=new FileOutputStream(f)){b.compress(Bitmap.CompressFormat.JPEG,85,o);}b.recycle();return f;}catch(Exception e){throw new AssertionError(e);}}
    private void salvar(View v){Bitmap b=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));try(FileOutputStream o=new FileOutputStream(new File(app.getExternalFilesDir(null),"configuracoes-preview.png"))){b.compress(Bitmap.CompressFormat.PNG,100,o);}catch(Exception e){throw new AssertionError(e);}finally{b.recycle();}}
    public static class Fixture extends ConfiguracoesFragment {
        ConfiguracoesViewModel vm;ConfiguracoesViewModelInstrumentedTest.Fonte fonte;
        @NonNull @Override public ViewModelProvider.Factory getDefaultViewModelProviderFactory(){return new ViewModelProvider.Factory(){@NonNull @Override public <T extends ViewModel>T create(@NonNull Class<T> tipo){fonte=new ConfiguracoesViewModelInstrumentedTest.Fonte(){@Override public void carregar(String uid,Callback<String> c){c.sucesso("");}};vm=new ConfiguracoesViewModel((Application)requireContext().getApplicationContext(),fonte);return tipo.cast(vm);}};}
    }
    private static class Fabrica implements Application.ActivityLifecycleCallbacks {
        @Override public void onActivityPreCreated(Activity a,Bundle b){if(a instanceof MainActivity)((MainActivity)a).getSupportFragmentManager().setFragmentFactory(new FragmentFactory(){@NonNull @Override public Fragment instantiate(@NonNull ClassLoader l,@NonNull String nome){if(nome.equals(ConfiguracoesFragment.class.getName())||nome.equals(Fixture.class.getName()))return new Fixture();return super.instantiate(l,nome);}});}
        public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityResumed(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
    }
}
