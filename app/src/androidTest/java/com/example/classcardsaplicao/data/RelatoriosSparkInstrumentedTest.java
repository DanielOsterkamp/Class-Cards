package com.example.classcardsaplicao.data;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.classcardsaplicao.model.Rating;
import com.example.classcardsaplicao.model.ResumoRelatorio;
import com.example.classcardsaplicao.model.ResultadoRevisao;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.Source;
import org.junit.*;
import org.junit.runner.RunWith;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** App Android real, regras reais e contas fictícias: nunca usa o projeto de produção. */
@RunWith(AndroidJUnit4.class)
public class RelatoriosSparkInstrumentedTest {
    private static FirebaseApp app;
    private static FirebaseAuth auth;
    private static FirebaseFirestore db;
    private String card;

    @BeforeClass public static void iniciar() {
        assumeTrue("Exclusivo dos emuladores locais", "true".equals(
                InstrumentationRegistry.getArguments().getString("relatoriosEmulator")));
        FirebaseOptions opcoes = new FirebaseOptions.Builder().setProjectId("demo-classcards-relatorios-android")
                .setApplicationId("1:123456789:android:relatorios-spark-teste")
                .setApiKey("AIzaSyEmulatorKeyForLocalClassCardsTests123").build();
        app = FirebaseApp.initializeApp(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                opcoes, "relatorios-test-" + UUID.randomUUID());
        auth = FirebaseAuth.getInstance(app); auth.useEmulator("10.0.2.2", 9099);
        db = FirebaseFirestore.getInstance(app);
        db.setFirestoreSettings(new FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build());
        db.useEmulator("10.0.2.2", 8087);
    }

    @Before public void preparar() throws Exception {
        entrar("aluno");
        card = "card-" + UUID.randomUUID();
        aguardar(db.document("decksMoodle/moodle-deck-instrumentado/cards/" + card)
                .set(Map.of("pergunta", "Pergunta", "resposta", "Resposta privada")));
    }

    @After public void sair() { if (auth != null) auth.signOut(); }
    @AfterClass public static void limpar() throws Exception {
        if (db != null) aguardar(db.terminate());
        if (app != null) app.delete();
    }

    @Test public void revisoesDosDoisModosAlimentamRelatorioSemDuplicarTentativa() throws Exception {
        String tentativa = "t-" + UUID.randomUUID();
        ResultadoRevisao tradicional = revisar("moodle-deck-instrumentado", card, tentativa, Rating.AGAIN, true);
        assertEquals(tradicional.resumo.pontosAntes, tradicional.resumo.pontosAgora);
        ResultadoRevisao dissertativa = revisar("moodle-deck-instrumentado", card, tentativa, Rating.GOOD, false);
        assertEquals(3, dissertativa.resumo.pontosAgora - dissertativa.resumo.pontosAntes);
        assertEquals(dissertativa.resumo.cardsAntes, dissertativa.resumo.cardsAgora);
        revisar("moodle-deck-instrumentado", card, tentativa, Rating.GOOD, false);

        entrar("professor");
        List<ResumoRelatorio.Registro> registros = buscarHoje();
        List<ResumoRelatorio.Registro> desteCard = new ArrayList<>();
        for (ResumoRelatorio.Registro r : registros) if (card.equals(r.card)) desteCard.add(r);
        ResumoRelatorio resumo = new ResumoRelatorio(desteCard, Map.of("site:2", "Biologia"));
        assertEquals(2, resumo.total.revisoes);
        assertEquals(1, resumo.total.errei);
        assertEquals(1, resumo.total.cards.size());
        assertEquals(0.5, resumo.total.taxaErro(), 0.00001);
        assertEquals("Aluno Spark", resumo.alunos.get(0).nome);
        assertTrue(desteCard.stream().anyMatch(r -> "tradicional".equals(r.modo)));
        assertTrue(desteCard.stream().anyMatch(r -> "dissertativo".equals(r.modo)));
    }

    @Test public void professorEstudaSemEntrarNoRelatorioDeAlunos() throws Exception {
        entrar("professor");
        revisar("moodle-deck-instrumentado", card, UUID.randomUUID().toString(), Rating.HARD, true);
        assertFalse(buscarHoje().stream().anyMatch(r -> auth.getUid().equals(r.uid)));
    }

    @Test public void tentativasConcorrentesRecuperamMesmoReciboSemDuplicar() throws Exception {
        String tentativa = UUID.randomUUID().toString();
        Task<ResultadoRevisao> a = iniciarRevisao("moodle-deck-instrumentado", card, tentativa, Rating.EASY, false);
        Task<ResultadoRevisao> b = iniciarRevisao("moodle-deck-instrumentado", card, tentativa, Rating.EASY, false);
        aguardar(Tasks.whenAll(a, b));
        assertEquals(a.getResult().resumo.pontosAgora, b.getResult().resumo.pontosAgora);
        entrar("professor");
        assertEquals(1, buscarHoje().stream().filter(r -> card.equals(r.card)).count());
    }

    @Test public void falhaDePermissaoSemReciboNaoViraSucessoNemSalvaProgresso() throws Exception {
        String uid = auth.getUid(), tentativa = UUID.randomUUID().toString(), inexistente = "inexistente-" + UUID.randomUUID();
        Map<String, Object> antes = aguardar(db.document("users/" + uid + "/gamificacao/resumo").get(Source.SERVER)).getData();
        try {
            revisar("moodle-deck-instrumentado", inexistente, tentativa, Rating.EASY, false);
            fail("Uma revisão de card inexistente deveria ser recusada");
        } catch (java.util.concurrent.ExecutionException esperado) {
            assertTrue(esperado.getCause() instanceof com.google.firebase.firestore.FirebaseFirestoreException);
        }
        assertFalse(aguardar(db.document("users/" + uid + "/revisoesDissertativas/" + tentativa).get(Source.SERVER)).exists());
        assertFalse(aguardar(db.document("users/" + uid + "/progressoFlashcards/" + inexistente).get(Source.SERVER)).exists());
        assertEquals(antes, aguardar(db.document("users/" + uid + "/gamificacao/resumo").get(Source.SERVER)).getData());
    }

    @Test public void donoQueSaiuDaTurmaAindaRevisaSemPublicarAtividade() throws Exception {
        entrar("exaluno");
        String tentativa = UUID.randomUUID().toString();
        revisar("moodle-deck-exaluno", "card-exaluno", tentativa, Rating.EASY, false);
        assertFalse(aguardar(db.document("users/" + auth.getUid() + "/revisoesDissertativas/" + tentativa)
                .get(Source.SERVER)).contains("relatorioPublicoId"));
        entrar("professor");
        assertFalse(buscarHoje().stream().anyMatch(r -> "moodle-site-5".equals(r.uid)));
    }

    @Test public void estudoDeDeckComumContinuaPrivado() throws Exception {
        String tentativa = UUID.randomUUID().toString(), uid = auth.getUid();
        revisar("deck-comum", card, tentativa, Rating.EASY, false);
        assertFalse(aguardar(db.document("users/" + uid + "/revisoesDissertativas/" + tentativa)
                .get(Source.SERVER)).contains("relatorio"));
        entrar("professor");
        assertFalse(buscarHoje().stream().anyMatch(r -> "deck-comum".equals(r.deck)));
    }

    @Test public void repositorioPaginaMesmoHorarioECalculaPeriodoCompletoEmUtc() throws Exception {
        entrar("professor");
        List<ResumoRelatorio.Registro> registros = aguardar(new RelatoriosRepository(auth, db).buscar(
                List.of("site:2"), "2026-11-01", "2026-11-01", auth.getUid(), () -> true));
        assertEquals(251, registros.size());
        ResumoRelatorio resumo = new ResumoRelatorio(registros, Map.of());
        assertEquals(251, resumo.total.revisoes);
        assertEquals(126, resumo.total.errei);
        assertEquals(10, resumo.total.cards.size());
        assertEquals(Set.of("2026-11-01"), resumo.total.dias);
    }

    @Test public void alunoEProfessorDeOutraTurmaNaoConsultamRelatorio() throws Exception {
        for (String conta : List.of("aluno", "outroprofessor")) {
            entrar(conta);
            try { buscarHoje(); fail("Consulta deveria ser recusada"); }
            catch (java.util.concurrent.ExecutionException esperado) {
                assertTrue(esperado.getCause() instanceof com.google.firebase.firestore.FirebaseFirestoreException);
            }
        }
    }

    @Test public void consultaCanceladaNaoEntregaDadosDeUmaContaAnterior() throws Exception {
        entrar("professor");
        try {
            aguardar(new RelatoriosRepository(auth, db).buscar(List.of("site:2"), "2026-11-01",
                    "2026-11-01", auth.getUid(), () -> false));
            fail("Consulta cancelada deveria falhar");
        } catch (java.util.concurrent.ExecutionException esperado) {
            assertTrue(esperado.getCause() instanceof IllegalStateException);
        }
    }

    private static void entrar(String conta) throws Exception {
        auth.signOut();
        aguardar(auth.signInWithEmailAndPassword(conta + "@relatorios.invalid", "ClassCardsEmulator123!"));
    }
    private List<ResumoRelatorio.Registro> buscarHoje() throws Exception {
        SimpleDateFormat formato = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        formato.setTimeZone(TimeZone.getTimeZone("UTC"));
        String hoje = formato.format(new Date());
        return aguardar(new RelatoriosRepository(auth, db).buscar(List.of("site:2"), hoje, hoje, auth.getUid(), () -> true));
    }
    private ResultadoRevisao revisar(String deck, String card, String tentativa, Rating rating, boolean tradicional) throws Exception {
        return aguardar(iniciarRevisao(deck, card, tentativa, rating, tradicional));
    }
    private Task<ResultadoRevisao> iniciarRevisao(String deck, String card, String tentativa, Rating rating, boolean tradicional) {
        TaskCompletionSource<ResultadoRevisao> pronto = new TaskCompletionSource<>();
        GamificacaoRepository.Callback callback = new GamificacaoRepository.Callback() {
            @Override public void onSuccess() { pronto.trySetException(new IllegalStateException("Sem resultado")); }
            @Override public void onSuccess(ResultadoRevisao resultado) { pronto.trySetResult(resultado); }
            @Override public void onError(Exception erro) { pronto.trySetException(erro); }
        };
        GamificacaoRepository repo = new GamificacaoRepository(db, () -> { });
        if (tradicional) repo.confirmarRevisaoTradicional(auth.getUid(), deck, card, tentativa, rating, true, callback);
        else repo.confirmarRevisao(auth.getUid(), deck, card, tentativa, rating, true, callback);
        return pronto.getTask();
    }
    private static <T> T aguardar(Task<T> task) throws Exception { return Tasks.await(task, 30, TimeUnit.SECONDS); }
}
