package com.example.classcardsaplicao.data;

import com.example.classcardsaplicao.model.ProgressoFlashcard;
import com.example.classcardsaplicao.model.Rating;
import com.example.classcardsaplicao.model.ResultadoOfensiva;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;
import org.junit.Before;
import org.junit.Test;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Testa o código real do repositório com uma transação em memória.
 * Não substitui validação de concorrência e regras de segurança no Firestore. */
public class EstatisticasRepositoryTest {
    private final Map<String, Object> documentos = new HashMap<>();
    private final Map<String, Object> pendentes = new LinkedHashMap<>();
    private final Map<String, DocumentReference> referencias = new HashMap<>();
    private final AtomicReference<LocalDate> hoje = new AtomicReference<>(LocalDate.of(2026, 10, 22));
    private EstatisticasRepository repository;
    private boolean falharCommit;
    private static final String GERAL = "users/user/estatisticas/geral";

    @Before public void preparar() throws Exception {
        FirebaseFirestore db = mock(FirebaseFirestore.class);
        Transaction transaction = mock(Transaction.class);
        when(db.collection(anyString())).thenAnswer(i -> colecao(i.getArgument(0)));
        when(transaction.get(any(DocumentReference.class))).thenAnswer(i -> snapshot(i.getArgument(0)));
        when(transaction.set(any(DocumentReference.class), any())).thenAnswer(i -> {
            pendentes.put(((DocumentReference) i.getArgument(0)).getPath(), i.getArgument(1));
            return transaction;
        });
        when(transaction.set(any(DocumentReference.class), any(), any(SetOptions.class))).thenAnswer(i -> {
            String path = ((DocumentReference) i.getArgument(0)).getPath();
            Map<String, Object> dados = new HashMap<>(mapa(path));
            dados.putAll(i.getArgument(1));
            pendentes.put(path, dados);
            return transaction;
        });
        when(db.runTransaction(org.mockito.ArgumentMatchers.<Transaction.Function<ResultadoOfensiva>>any()))
                .thenAnswer(i -> {
                    pendentes.clear();
                    try {
                        Transaction.Function<ResultadoOfensiva> operation = i.getArgument(0);
                        ResultadoOfensiva resultado = operation.apply(transaction);
                        if (falharCommit) {
                            falharCommit = false;
                            throw new IllegalStateException("Falha de conexão simulada");
                        }
                        documentos.putAll(pendentes);
                        return Tasks.forResult(resultado);
                    } catch (Exception exception) {
                        return Tasks.forException(exception);
                    }
                });
        repository = new EstatisticasRepository(db, hoje::get);
    }

    private CollectionReference colecao(String path) {
        CollectionReference ref = mock(CollectionReference.class);
        when(ref.document(anyString())).thenAnswer(i -> referencia(path + "/" + i.getArgument(0)));
        return ref;
    }

    private DocumentReference referencia(String path) {
        DocumentReference existente = referencias.get(path);
        if (existente != null) return existente;
        DocumentReference ref = mock(DocumentReference.class);
        referencias.put(path, ref);
        when(ref.getPath()).thenReturn(path);
        when(ref.collection(anyString())).thenAnswer(i -> colecao(path + "/" + i.getArgument(0)));
        return ref;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapa(String path) {
        Object valor = documentos.get(path);
        return valor instanceof Map ? (Map<String, Object>) valor : Collections.emptyMap();
    }

    private DocumentSnapshot snapshot(DocumentReference ref) {
        DocumentSnapshot snapshot = mock(DocumentSnapshot.class);
        String path = ref.getPath();
        Map<String, Object> dados = mapa(path);
        when(snapshot.exists()).thenReturn(documentos.containsKey(path));
        when(snapshot.get(anyString())).thenAnswer(i -> dados.get(i.getArgument(0)));
        when(snapshot.getLong(anyString())).thenAnswer(i -> {
            Number valor = (Number) dados.get(i.getArgument(0));
            return valor == null ? null : valor.longValue();
        });
        when(snapshot.getString(anyString())).thenAnswer(i -> dados.get(i.getArgument(0)));
        when(snapshot.getBoolean(anyString())).thenAnswer(i -> dados.get(i.getArgument(0)));
        Object valor = documentos.get(path);
        if (valor instanceof ProgressoFlashcard) {
            ProgressoFlashcard original = (ProgressoFlashcard) valor;
            ProgressoFlashcard copia = new ProgressoFlashcard(original.getUserId(),
                    original.getDeckId(), original.getFlashcardId());
            copia.setDiasDecorridos(original.getDiasDecorridos());
            copia.setDiasAgendados(original.getDiasAgendados());
            copia.setStability(original.getStability());
            copia.setDifficulty(original.getDifficulty());
            copia.setUltimaRevisao(original.getUltimaRevisao());
            copia.setProximaRevisao(original.getProximaRevisao());
            copia.setRepeticoes(original.getRepeticoes());
            copia.setLapsos(original.getLapsos());
            copia.setEstado(original.getEstado());
            when(snapshot.toObject(ProgressoFlashcard.class)).thenReturn(copia);
        }
        return snapshot;
    }

    private Task<ResultadoOfensiva> revisar(String sessao, List<String> fila, String card) {
        return repository.registrarRevisaoTradicional("user", sessao, "deck", fila, card, Rating.GOOD);
    }

    private ResultadoOfensiva sucesso(Task<ResultadoOfensiva> task) {
        assertTrue(task.isSuccessful());
        return task.getResult();
    }

    private long total() {
        return ((Number) mapa(GERAL).getOrDefault("totalFlashcardsEstudados", 0L)).longValue();
    }

    @Test public void sessaoParcialContaCardsSemConquistarOfensiva() {
        sucesso(revisar("sessao", Arrays.asList("a", "b"), "a"));
        assertEquals(1, total());
        assertFalse(mapa(GERAL).containsKey("ultimaDataEstudo"));
        assertFalse((Boolean) mapa("users/user/sessoesEstudo/sessao").get("concluida"));
    }

    @Test public void ultimaRevisaoConquistaOfensivaComValorNovo() {
        List<String> fila = Arrays.asList("a", "b");
        sucesso(revisar("sessao", fila, "a"));
        ResultadoOfensiva resultado = sucesso(revisar("sessao", fila, "b"));
        assertTrue(resultado.isOfensivaAlterada());
        assertEquals(1, resultado.getOfensivaAtual());
        assertEquals(2, total());
        assertEquals(hoje.get().toString(), mapa(GERAL).get("ultimaDataEstudo"));
        assertTrue((Boolean) mapa("users/user/sessoesEstudo/sessao").get("concluida"));
    }

    @Test public void repetirRecibosNaoDuplicaContagemNemFsrsNemOfensiva() {
        List<String> fila = Arrays.asList("a", "b");
        sucesso(revisar("sessao", fila, "a"));
        sucesso(revisar("sessao", fila, "a"));
        ResultadoOfensiva original = sucesso(revisar("sessao", fila, "b"));
        ResultadoOfensiva repetido = sucesso(revisar("sessao", fila, "b"));
        assertEquals(2, total());
        assertEquals(original.getOfensivaAtual(), repetido.getOfensivaAtual());
        assertEquals(original.isOfensivaAlterada(), repetido.isOfensivaAlterada());
        assertEquals(1, ((ProgressoFlashcard) documentos.get("users/user/progressoFlashcards/a")).getRepeticoes());
        assertEquals(1, ((ProgressoFlashcard) documentos.get("users/user/progressoFlashcards/b")).getRepeticoes());
    }

    @Test public void duasSessoesNoMesmoDiaContamEstudoMasUmaOfensivaSemXp() {
        documentos.put(GERAL, new HashMap<>(Collections.singletonMap("xp", 42L)));
        assertTrue(sucesso(revisar("primeira", Collections.singletonList("a"), "a")).isOfensivaAlterada());
        ResultadoOfensiva segunda = sucesso(revisar("segunda", Collections.singletonList("b"), "b"));
        assertFalse(segunda.isOfensivaAlterada());
        assertEquals(1, segunda.getOfensivaAtual());
        assertEquals(2, total());
        assertEquals(42L, mapa(GERAL).get("xp"));
        assertFalse(mapa(GERAL).containsKey("totalXp"));
    }

    @Test public void diaSeguinteIncrementaSequencia() {
        sucesso(revisar("ontem", Collections.singletonList("a"), "a"));
        hoje.set(hoje.get().plusDays(1));
        assertEquals(2, sucesso(revisar("hoje", Collections.singletonList("b"), "b")).getOfensivaAtual());
        assertEquals(2L, mapa(GERAL).get("maiorOfensiva"));
    }

    @Test public void intervaloSemEstudoReiniciaEmUmPreservandoRecorde() {
        Map<String, Object> anterior = new HashMap<>();
        anterior.put("ofensivaAtual", 8L);
        anterior.put("maiorOfensiva", 12L);
        anterior.put("ultimaDataEstudo", hoje.get().minusDays(3).toString());
        documentos.put(GERAL, anterior);
        assertEquals(1, sucesso(revisar("nova", Collections.singletonList("a"), "a")).getOfensivaAtual());
        assertEquals(12L, mapa(GERAL).get("maiorOfensiva"));
    }

    @Test public void falhaNaoGravaParcialmenteENovaTentativaContaUmaVez() {
        falharCommit = true;
        assertFalse(revisar("sessao", Collections.singletonList("a"), "a").isSuccessful());
        assertTrue(documentos.isEmpty());
        assertTrue(sucesso(revisar("sessao", Collections.singletonList("a"), "a")).isOfensivaAlterada());
        assertEquals(1, total());
        assertEquals(1, ((ProgressoFlashcard) documentos.get("users/user/progressoFlashcards/a")).getRepeticoes());
    }

    @Test public void ultimoCardAntesDoPrimeiroNaoConcluiSessao() {
        assertFalse(revisar("sessao", Arrays.asList("a", "b"), "b").isSuccessful());
        assertTrue(documentos.isEmpty());
    }

    @Test public void filaAlteradaNaoConcluiSessao() {
        sucesso(revisar("sessao", Arrays.asList("a", "b"), "a"));
        assertFalse(revisar("sessao", Arrays.asList("a", "c"), "c").isSuccessful());
        assertEquals(1, total());
        assertFalse(mapa(GERAL).containsKey("ultimaDataEstudo"));
    }

    @Test public void sessaoVaziaNaoPodeGanharOfensiva() {
        assertThrows(IllegalArgumentException.class, () -> revisar("sessao", Collections.emptyList(), "a"));
        assertTrue(documentos.isEmpty());
    }

    @Test public void cardsDuplicadosNaoPodemFormarSessao() {
        assertThrows(IllegalArgumentException.class, () -> revisar("sessao", Arrays.asList("a", "a"), "a"));
        assertTrue(documentos.isEmpty());
    }

    @Test public void relogioRetrocedidoNaoDuplicaOfensivaNemRegrideData() {
        sucesso(revisar("primeira", Collections.singletonList("a"), "a"));
        String ultimaData = hoje.get().toString();
        hoje.set(hoje.get().minusDays(1));
        ResultadoOfensiva segunda = sucesso(revisar("segunda", Collections.singletonList("b"), "b"));
        assertFalse(segunda.isOfensivaAlterada());
        assertEquals(ultimaData, mapa(GERAL).get("ultimaDataEstudo"));
        assertEquals(2, total());
    }
}
