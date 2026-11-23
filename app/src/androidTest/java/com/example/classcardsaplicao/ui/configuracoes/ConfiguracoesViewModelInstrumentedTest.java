package com.example.classcardsaplicao.ui.configuracoes;
import android.app.Application;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.classcardsaplicao.data.FotoPerfilFonte;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.File;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class ConfiguracoesViewModelInstrumentedTest {
    private Application app(){return (Application)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();}
    private void main(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
    static class Fonte implements FotoPerfilFonte {
        String uid="conta";int envios;Callback<File> preparacao;Callback<String> envio;Callback<String> carga;
        public String uidAtual(){return uid;}
        public void carregar(String uid,Callback<String> c){c.sucesso("https://foto-antiga");}
        public void preparar(Uri u,Callback<File> c){preparacao=c;}
        public void salvar(String uid,File f,Callback<String> c){envios++;envio=c;}
        public void cancelar(){}public void encerrar(){}
    }
    private File foto(){try{return File.createTempFile("teste-foto-",".jpg",app().getCacheDir());}catch(Exception e){throw new AssertionError(e);}}
    @Test public void cancelarSeletorPreservaFoto(){main(()->{Fonte f=new Fonte();ConfiguracoesViewModel vm=new ConfiguracoesViewModel(app(),f);vm.carregar();vm.selecionar(null);assertEquals("https://foto-antiga",vm.foto.getValue());assertNull(vm.previa.getValue());assertEquals(0,f.envios);});}
    @Test public void salvarExigePreviaEDeduplicaToques(){main(()->{Fonte f=new Fonte();ConfiguracoesViewModel vm=new ConfiguracoesViewModel(app(),f);vm.carregar();vm.salvar();assertEquals(0,f.envios);vm.selecionar(Uri.parse("content://foto"));File foto=foto();f.preparacao.sucesso(foto);vm.salvar();vm.salvar();assertEquals(1,f.envios);f.envio.sucesso("https://nova");assertEquals("https://nova",vm.foto.getValue());assertNull(vm.previa.getValue());assertFalse(foto.exists());assertTrue(vm.sucesso.getValue());});}
    @Test public void falhaMantemFotoAntigaEPermiteTentarNovamente(){main(()->{Fonte f=new Fonte();ConfiguracoesViewModel vm=new ConfiguracoesViewModel(app(),f);vm.carregar();vm.selecionar(Uri.parse("content://foto"));File foto=foto();f.preparacao.sucesso(foto);vm.salvar();f.envio.erro(new Exception());assertEquals("https://foto-antiga",vm.foto.getValue());assertEquals(foto,vm.previa.getValue());assertFalse(vm.salvando.getValue());vm.salvar();assertEquals(2,f.envios);f.envio.erro(new Exception());vm.descartar();assertFalse(foto.exists());});}
    @Test public void trocaDeContaDescartaResultadoAntigo(){main(()->{Fonte f=new Fonte();ConfiguracoesViewModel vm=new ConfiguracoesViewModel(app(),f);vm.carregar();vm.selecionar(Uri.parse("content://foto"));f.uid="outra";vm.conferirConta();File foto=foto();f.preparacao.sucesso(foto);assertFalse(foto.exists());assertNull(vm.previa.getValue());assertEquals("",vm.foto.getValue());});}
    @Test public void descartarNaoEnviaFoto(){main(()->{Fonte f=new Fonte();ConfiguracoesViewModel vm=new ConfiguracoesViewModel(app(),f);vm.carregar();vm.selecionar(Uri.parse("content://foto"));File foto=foto();f.preparacao.sucesso(foto);vm.descartar();assertFalse(foto.exists());assertEquals(0,f.envios);assertEquals("https://foto-antiga",vm.foto.getValue());});}
}
