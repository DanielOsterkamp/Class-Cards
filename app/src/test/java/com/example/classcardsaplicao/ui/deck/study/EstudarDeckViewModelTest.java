package com.example.classcardsaplicao.ui.deck.study;

import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;
import androidx.lifecycle.SavedStateHandle;
import com.example.classcardsaplicao.data.DeckRepository;
import com.example.classcardsaplicao.data.EstatisticasRepository;
import com.example.classcardsaplicao.data.ProgressoRepository;
import com.example.classcardsaplicao.model.Flashcard;
import com.example.classcardsaplicao.model.Rating;
import com.example.classcardsaplicao.model.ResultadoOfensiva;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.*;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class EstudarDeckViewModelTest {
    private MockedStatic<FirebaseAuth> auth;
    private MockedConstruction<DeckRepository> decks;
    private MockedConstruction<ProgressoRepository> progressos;
    private MockedConstruction<EstatisticasRepository> estatisticas;
    private final List<OnSuccessListener<ResultadoOfensiva>> sucessos = new ArrayList<>();
    private final List<OnFailureListener> falhas = new ArrayList<>();
    private SavedStateHandle estado;
    private EstudarDeckViewModel vm;
    private final List<Flashcard> cards = new ArrayList<>();

    @Before @SuppressWarnings("unchecked") public void preparar() {
        ArchTaskExecutor.getInstance().setDelegate(new TaskExecutor() {
            @Override public void executeOnDiskIO(Runnable task) { task.run(); }
            @Override public void postToMainThread(Runnable task) { task.run(); }
            @Override public boolean isMainThread() { return true; }
        });
        FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
        when(firebaseAuth.getUid()).thenReturn("user");
        auth = mockStatic(FirebaseAuth.class);
        auth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
        for (String id : Arrays.asList("a", "b")) {
            Flashcard card = mock(Flashcard.class);
            when(card.getId()).thenReturn(id);
            cards.add(card);
        }
        decks = mockConstruction(DeckRepository.class, (repo, context) ->
                doAnswer(i -> {
                    ((DeckRepository.BuscarFlashcardsCallback) i.getArgument(1)).onSuccess(cards);
                    return null;
                }).when(repo).buscarFlashcardsDoDeck(anyString(), any()));
        progressos = mockConstruction(ProgressoRepository.class, (repo, context) ->
                doAnswer(i -> {
                    ((ProgressoRepository.BuscarProgressosDeckCallback) i.getArgument(2))
                            .onSuccess(Collections.emptyList());
                    return null;
                }).when(repo).buscarProgressosDoDeck(anyString(), anyString(), any()));
        estatisticas = mockConstruction(EstatisticasRepository.class, (repo, context) ->
                when(repo.registrarRevisaoTradicional(anyString(), anyString(), anyString(),
                        anyList(), anyString(), any())).thenAnswer(i -> {
                    Task<ResultadoOfensiva> task = mock(Task.class);
                    when(task.addOnSuccessListener(any(OnSuccessListener.class))).thenAnswer(call -> {
                        sucessos.add(call.getArgument(0));
                        return task;
                    });
                    when(task.addOnFailureListener(any(OnFailureListener.class))).thenAnswer(call -> {
                        falhas.add(call.getArgument(0));
                        return task;
                    });
                    return task;
                }));
        estado = new SavedStateHandle();
        vm = new EstudarDeckViewModel(estado);
        vm.carregarDeck("deck");
    }

    @After public void limpar() {
        if (estatisticas != null) estatisticas.close();
        if (progressos != null) progressos.close();
        if (decks != null) decks.close();
        if (auth != null) auth.close();
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    private SavedStateHandle copiarEstado() {
        Map<String, Object> copia = new HashMap<>();
        for (String key : estado.keys()) copia.put(key, estado.get(key));
        return new SavedStateHandle(copia);
    }

    @Test public void recriarViewNaoRecarregaFilaNemReiniciaSessao() {
        vm.carregarDeck("deck");
        verify(decks.constructed().get(0), times(1)).buscarFlashcardsDoDeck(eq("deck"), any());
        assertEquals("a", vm.getFlashcardAtual().getId());
    }

    @Test public void duploToqueDuranteGravacaoEnviaUmaRevisao() {
        vm.alternarLado();
        vm.avaliarFlashcard(Rating.GOOD);
        vm.avaliarFlashcard(Rating.EASY);
        assertEquals(1, sucessos.size());
        assertEquals(0, vm.getIndiceAtual());
        assertNull(vm.getResultado().getValue());
        sucessos.get(0).onSuccess(new ResultadoOfensiva(0, false));
        assertEquals(1, vm.getIndiceAtual());
        assertEquals("b", vm.getFlashcardAtual().getId());
        assertFalse(vm.isMostrandoResposta());
        assertNull(vm.getResultado().getValue());
    }

    @Test public void respostaPrecisaSerReveladaAntesDaAvaliacao() {
        vm.avaliarFlashcard(Rating.GOOD);
        assertTrue(sucessos.isEmpty());
    }

    @Test public void estadoRestauradoMantemFilaIndiceELadoDoCard() {
        vm.alternarLado();
        vm.avaliarFlashcard(Rating.GOOD);
        sucessos.get(0).onSuccess(new ResultadoOfensiva(0, false));
        vm.alternarLado();
        EstudarDeckViewModel restaurado = new EstudarDeckViewModel(copiarEstado());
        restaurado.carregarDeck("deck");
        assertEquals("b", restaurado.getFlashcardAtual().getId());
        assertEquals(1, restaurado.getIndiceAtual());
        assertTrue(restaurado.isMostrandoResposta());
        verifyNoInteractions(progressos.constructed().get(1));
    }

    @Test public void gravacaoPendenteAposMorteDoProcessoReutilizaSessaoECard() {
        vm.alternarLado();
        vm.avaliarFlashcard(Rating.GOOD);
        String id = estado.get("sessaoId");
        EstudarDeckViewModel restaurado = new EstudarDeckViewModel(copiarEstado());
        restaurado.carregarDeck("deck");
        verify(estatisticas.constructed().get(1)).registrarRevisaoTradicional(
                eq("user"), eq(id), eq("deck"), eq(Arrays.asList("a", "b")), eq("a"), eq(Rating.GOOD));
        sucessos.get(1).onSuccess(new ResultadoOfensiva(0, false));
        assertEquals(1, restaurado.getIndiceAtual());
        assertEquals("b", restaurado.getFlashcardAtual().getId());
    }

    @Test public void falhaPermiteRepetirMesmaRevisaoSemAvancar() {
        vm.alternarLado();
        vm.avaliarFlashcard(Rating.GOOD);
        falhas.get(0).onFailure(new Exception("Sem rede"));
        assertEquals(0, vm.getIndiceAtual());
        assertFalse(vm.getSalvandoProgresso().getValue());
        assertNotNull(vm.getErro().getValue());
        vm.tentarNovamente("deck");
        assertEquals(2, sucessos.size());
        verify(estatisticas.constructed().get(0), times(2)).registrarRevisaoTradicional(
                eq("user"), anyString(), eq("deck"), anyList(), eq("a"), eq(Rating.GOOD));
        sucessos.get(1).onSuccess(new ResultadoOfensiva(0, false));
        assertEquals(1, vm.getIndiceAtual());
        assertNull(vm.getErro().getValue());
    }

    @Test public void resultadoSomenteAposUltimaConfirmacaoERestauraValorNovo() {
        vm.alternarLado();
        vm.avaliarFlashcard(Rating.GOOD);
        sucessos.get(0).onSuccess(new ResultadoOfensiva(0, false));
        vm.alternarLado();
        vm.avaliarFlashcard(Rating.GOOD);
        assertNull(vm.getResultado().getValue());
        sucessos.get(1).onSuccess(new ResultadoOfensiva(9, true));
        assertEquals(9, vm.getResultado().getValue().getOfensivaAtual());
        assertEquals(2, vm.getIndiceAtual());
        EstudarDeckViewModel restaurado = new EstudarDeckViewModel(copiarEstado());
        restaurado.carregarDeck("deck");
        assertEquals(9, restaurado.getResultado().getValue().getOfensivaAtual());
        assertTrue(restaurado.getResultado().getValue().isOfensivaAlterada());
        assertEquals(2, restaurado.getIndiceAtual());
        verifyNoInteractions(decks.constructed().get(1), estatisticas.constructed().get(1));
    }

    @Test public void sessaoVaziaNaoRegistraEstudoNemResultado() {
        cards.clear();
        EstudarDeckViewModel vazio = new EstudarDeckViewModel(new SavedStateHandle());
        vazio.carregarDeck("deck");
        vazio.avaliarFlashcard(Rating.GOOD);
        assertNull(vazio.getFlashcardAtual());
        assertNull(vazio.getResultado().getValue());
        verifyNoInteractions(estatisticas.constructed().get(1));
    }
}
