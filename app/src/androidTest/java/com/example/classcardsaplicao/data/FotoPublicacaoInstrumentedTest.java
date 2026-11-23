package com.example.classcardsaplicao.data;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.firebase.*;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import com.google.android.gms.tasks.Tasks;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Transação Java real, exclusivamente contra Auth/Firestore de projeto demo local. */
@RunWith(AndroidJUnit4.class)
public class FotoPublicacaoInstrumentedTest {
    private static FirebaseApp app;
    private static FirebaseAuth auth;
    private static FirebaseFirestore db;
    private String uid;
    @BeforeClass public static void iniciar()throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("fotoEmulator")));
        FirebaseOptions opcoes=new FirebaseOptions.Builder().setProjectId("demo-classcards-foto").setApplicationId("1:123456789:android:foto").setApiKey("AIzaSyEmulatorKeyForLocalClassCardsTests123").build();
        app=FirebaseApp.initializeApp(InstrumentationRegistry.getInstrumentation().getTargetContext(),opcoes,"foto-publicacao-testes");
        auth=FirebaseAuth.getInstance(app);auth.useEmulator("10.0.2.2",9099);
        db=FirebaseFirestore.getInstance(app);db.useEmulator("10.0.2.2",8087);db.setFirestoreSettings(new FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build());
    }
    @Before public void conta()throws Exception {
        uid=Tasks.await(auth.signInAnonymously(),20,TimeUnit.SECONDS).getUser().getUid();
        Tasks.await(db.collection("users").document(uid).set(Map.of("nome","Ana","email","privado@teste.invalid","fotoUrl","https://antiga")),20,TimeUnit.SECONDS);
        Tasks.await(db.collection("users").document(uid).collection("gamificacao").document("resumo").set(Map.of("xp",42L,"cardsEstudados",7L,"ofensiva",3L,"versaoEstudos",9L,"fusoHorario","America/Sao_Paulo","ultimoDiaEstudo","2026-11-18")),20,TimeUnit.SECONDS);
    }
    @After public void sair(){if(auth!=null)auth.signOut();}
    @AfterClass public static void encerrar()throws Exception {if(db!=null)Tasks.await(db.terminate(),10,TimeUnit.SECONDS);if(app!=null)app.delete();}
    private byte[] jpeg(){
        android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(32,32,android.graphics.Bitmap.Config.ARGB_8888);b.eraseColor(android.graphics.Color.RED);
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();b.compress(android.graphics.Bitmap.CompressFormat.JPEG,85,out);b.recycle();return out.toByteArray();
    }
    @Test public void fotoAtualizaPerfilERankingJuntosSemAlterarPontos()throws Exception {
        String url=Tasks.await(new FotoPerfilPublicacao(db,auth::getUid).salvar(uid,jpeg(),"",List.of()),20,TimeUnit.SECONDS);
        DocumentSnapshot privado=Tasks.await(db.collection("users").document(uid).get(Source.SERVER),20,TimeUnit.SECONDS);
        DocumentSnapshot publico=Tasks.await(db.collection("rankings").document(uid).get(Source.SERVER),20,TimeUnit.SECONDS);
        DocumentSnapshot stats=Tasks.await(db.collection("users").document(uid).collection("gamificacao").document("resumo").get(Source.SERVER),20,TimeUnit.SECONDS);
        DocumentSnapshot avatar=Tasks.await(db.collection("avatares").document(uid).get(Source.SERVER),20,TimeUnit.SECONDS);
        assertArrayEquals(jpeg(),avatar.getBlob("conteudo").toBytes());assertFalse(publico.contains("conteudo"));assertFalse(privado.contains("conteudo"));assertTrue(url.startsWith("avatar-firestore:"));
        assertEquals(url,privado.getString("fotoUrl"));assertEquals(url,publico.getString("fotoUrl"));assertEquals("privado@teste.invalid",privado.getString("email"));assertFalse(publico.contains("email"));assertEquals(Long.valueOf(42),publico.getLong("xp"));assertEquals(Long.valueOf(7),publico.getLong("cardsEstudados"));assertEquals(Long.valueOf(9),stats.getLong("versaoEstudos"));
    }
    @Test public void outraContaNaoAlteraFotoEPerfilAntigoPermanece()throws Exception {
        String dono=uid;auth.signOut();Tasks.await(auth.signInAnonymously(),20,TimeUnit.SECONDS);
        try{Tasks.await(new FotoPerfilPublicacao(db,()->dono).salvar(dono,jpeg(),"",List.of()),20,TimeUnit.SECONDS);fail("Deveria negar");}catch(java.util.concurrent.ExecutionException esperado){}
    }
    @Test public void segundaFotoSubstituiSomenteUmDocumentoEMudaVersao()throws Exception {
        FotoPerfilPublicacao repo=new FotoPerfilPublicacao(db,auth::getUid);
        String primeira=Tasks.await(repo.salvar(uid,jpeg(),"",List.of()),20,TimeUnit.SECONDS);
        String segunda=Tasks.await(repo.salvar(uid,jpeg(),"",List.of()),20,TimeUnit.SECONDS);
        assertNotEquals(primeira,segunda);
        DocumentSnapshot avatar=Tasks.await(db.collection("avatares").document(uid).get(Source.SERVER),20,TimeUnit.SECONDS);
        assertEquals(com.example.classcardsaplicao.model.ReferenciaAvatar.ler(segunda).versao,avatar.getString("versao"));
    }
    @Test public void carregadorBuscaImagemEReferenciaAntigaContinuaExibindoAvatarAtual()throws Exception {
        FotoPerfilPublicacao repo=new FotoPerfilPublicacao(db,auth::getUid);
        String primeira=Tasks.await(repo.salvar(uid,jpeg(),"",List.of()),20,TimeUnit.SECONDS);
        Tasks.await(repo.salvar(uid,jpeg(),"",List.of()),20,TimeUnit.SECONDS);
        com.example.classcardsaplicao.ui.imagens.AvatarModelLoader loader=new com.example.classcardsaplicao.ui.imagens.AvatarModelLoader(db);
        assertTrue(loader.handles(primeira));assertFalse(loader.handles("https://foto"));
        com.bumptech.glide.load.model.ModelLoader.LoadData<java.io.InputStream> dados=loader.buildLoadData(primeira,48,48,new com.bumptech.glide.load.Options());
        java.util.concurrent.CountDownLatch fim=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.atomic.AtomicReference<Exception> erro=new java.util.concurrent.atomic.AtomicReference<>();
        dados.fetcher.loadData(com.bumptech.glide.Priority.NORMAL,new com.bumptech.glide.load.data.DataFetcher.DataCallback<java.io.InputStream>(){
            public void onDataReady(java.io.InputStream entrada){try{android.graphics.Bitmap img=android.graphics.BitmapFactory.decodeStream(entrada);assertNotNull(img);assertEquals(32,img.getWidth());img.recycle();}catch(Exception e){erro.set(e);}finally{fim.countDown();}}
            public void onLoadFailed(Exception e){erro.set(e);fim.countDown();}
        });
        assertTrue(fim.await(20,TimeUnit.SECONDS));dados.fetcher.cleanup();assertNull(erro.get());
    }

}
