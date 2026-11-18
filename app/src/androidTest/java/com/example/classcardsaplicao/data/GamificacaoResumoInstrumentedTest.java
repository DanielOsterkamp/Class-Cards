package com.example.classcardsaplicao.data;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.classcardsaplicao.model.Rating;
import com.example.classcardsaplicao.model.ResultadoRevisao;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Transações Java reais, exclusivamente nos emuladores Auth/Firestore de um projeto fictício. */
@RunWith(AndroidJUnit4.class)
public class GamificacaoResumoInstrumentedTest {
    private static FirebaseApp app;
    private static FirebaseAuth auth;
    private static FirebaseFirestore db;
    private GamificacaoRepository repository;
    private String uid;

    @BeforeClass public static void iniciarEmuladores() throws Exception {
        assumeTrue("Exige os emuladores locais: argumento gamificacaoEmulator=true",
                "true".equals(InstrumentationRegistry.getArguments().getString("gamificacaoEmulator")));
        FirebaseOptions options = new FirebaseOptions.Builder()
                .setProjectId("demo-classcards-resumo")
                .setApplicationId("1:123456789:android:classcards-resumo-teste")
                .setApiKey("AIzaSyEmulatorKeyForLocalClassCardsTests123")
                .build();
        app = FirebaseApp.initializeApp(InstrumentationRegistry.getInstrumentation().getTargetContext(), options,
                "resumo-test-" + UUID.randomUUID());
        auth = FirebaseAuth.getInstance(app);
        auth.useEmulator("10.0.2.2", 9099);
        db = FirebaseFirestore.getInstance(app);
        db.setFirestoreSettings(new FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build());
        db.useEmulator("10.0.2.2", 8087);
    }

    @Before public void preparar() throws Exception {
        uid = Tasks.await(auth.signInAnonymously(), 25, TimeUnit.SECONDS).getUser().getUid();
        repository = new GamificacaoRepository(db, () -> { });
        Map<String, Object> inicio = new HashMap<>();
        inicio.put("xp", 100L);
        inicio.put("cardsEstudados", 5L);
        inicio.put("ofensiva", 0L);
        inicio.put("ultimoDiaEstudo", "");
        Tasks.await(db.collection("users").document(uid).collection("gamificacao").document("resumo")
                .set(inicio), 15, TimeUnit.SECONDS);
    }

    @After public void sairDaContaDeTeste() {
        if (auth != null) auth.signOut();
    }

    @AfterClass public static void limpar() throws Exception {
        if (db != null) Tasks.await(db.terminate(), 10, TimeUnit.SECONDS);
        if (app != null) app.delete();
    }

    @Test public void transacaoRetornaAntesEDepoisEReciboRepetidoMantemValoresOriginais() throws Exception {
        ResultadoRevisao primeira = revisar("card-a", "tentativa-1", Rating.GOOD, false, false);
        assertEquals(5, primeira.resumo.cardsAntes);
        assertEquals(6, primeira.resumo.cardsAgora);
        assertEquals(100, primeira.resumo.pontosAntes);
        assertEquals(103, primeira.resumo.pontosAgora);
        assertFalse(primeira.ofensiva.conquistada);

        ResultadoRevisao segunda = revisar("card-a", "tentativa-2", Rating.EASY, true, false);
        assertEquals(6, segunda.resumo.cardsAntes);
        assertEquals(6, segunda.resumo.cardsAgora);
        assertEquals(103, segunda.resumo.pontosAntes);
        assertEquals(108, segunda.resumo.pontosAgora);
        assertTrue(segunda.ofensiva.conquistada);

        ResultadoRevisao repetida = revisar("card-a", "tentativa-1", Rating.GOOD, false, false);
        assertEquals(100, repetida.resumo.pontosAntes);
        assertEquals(103, repetida.resumo.pontosAgora);
        assertEquals(5, repetida.resumo.cardsAntes);
        assertEquals(6, repetida.resumo.cardsAgora);
        assertEquals(Long.valueOf(108), Tasks.await(db.collection("users").document(uid)
                .collection("gamificacao").document("resumo").get(), 15, TimeUnit.SECONDS).getLong("xp"));
    }

    @Test public void modoTradicionalRetornaResumoMesmoQuandoNaoConcedePontos() throws Exception {
        ResultadoRevisao resultado = revisar("card-b", "tradicional-1", Rating.EASY, true, true);
        assertEquals(5, resultado.resumo.cardsAntes);
        assertEquals(6, resultado.resumo.cardsAgora);
        assertEquals(100, resultado.resumo.pontosAntes);
        assertEquals(100, resultado.resumo.pontosAgora);
        assertTrue(resultado.ofensiva.conquistada);
    }

    private ResultadoRevisao revisar(String card, String tentativa, Rating rating, boolean ultima, boolean tradicional) throws Exception {
        TaskCompletionSource<ResultadoRevisao> pronto = new TaskCompletionSource<>();
        GamificacaoRepository.Callback callback = new GamificacaoRepository.Callback() {
            @Override public void onSuccess() { pronto.trySetException(new IllegalStateException("Resultado não retornado")); }
            @Override public void onSuccess(ResultadoRevisao resultado) { pronto.trySetResult(resultado); }
            @Override public void onError(Exception erro) { pronto.trySetException(erro); }
        };
        if (tradicional) repository.confirmarRevisaoTradicional(uid, "deck-teste", card, tentativa, rating, ultima, callback);
        else repository.confirmarRevisao(uid, "deck-teste", card, tentativa, rating, ultima, callback);
        return Tasks.await(pronto.getTask(), 25, TimeUnit.SECONDS);
    }
}
