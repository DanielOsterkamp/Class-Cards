package com.example.classcardsaplicao.data;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.classcardsaplicao.model.AvaliacaoDissertativa;
import com.example.classcardsaplicao.model.Flashcard;
import com.example.classcardsaplicao.model.Rating;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

// Teste opt-in: usa o serviço real e conteúdo sintético, sem gravar progresso ou XP.
// Executar com o argumento de instrumentação verificarIa=true.
@RunWith(AndroidJUnit4.class)
public class AvaliacaoIaIntegracaoTest {
    @Test public void avaliaUmaRespostaCorretaComServicoReal() throws Exception {
        AvaliacaoDissertativa resultado = avaliar("O resultado é quatro.");
        assertTrue("Resposta correta classificada como " + resultado.getSugestao(),
                resultado.getSugestao() == Rating.GOOD || resultado.getSugestao() == Rating.EASY);
    }

    @Test public void identificaUmaRespostaIncorretaComServicoReal() throws Exception {
        assertEquals(Rating.AGAIN, avaliar("O resultado é treze.").getSugestao());
    }

    private AvaliacaoDissertativa avaliar(String texto) throws Exception {
        Assume.assumeTrue("Teste externo habilitado somente quando solicitado.",
                "true".equals(InstrumentationRegistry.getArguments().getString("verificarIa")));
        FirebaseApp.initializeApp(InstrumentationRegistry.getInstrumentation().getTargetContext());
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance());
        CountDownLatch pronto = new CountDownLatch(1);
        AtomicReference<AvaliacaoDissertativa> resultado = new AtomicReference<>();
        AtomicReference<String> erro = new AtomicReference<>();
        new AvaliacaoRespostaRepository().avaliar(
                new Flashcard("teste-local", "Quanto é dois mais dois?", "Quatro.", 0), texto,
                new AvaliacaoRespostaRepository.Callback() {
                    @Override public void onSuccess(AvaliacaoDissertativa avaliacao) {
                        resultado.set(avaliacao); pronto.countDown();
                    }
                    @Override public void onError(String mensagem) { erro.set(mensagem); pronto.countDown(); }
                });
        assertTrue("O callback não retornou no prazo.", pronto.await(55, TimeUnit.SECONDS));
        assertNull("Falha do serviço: " + erro.get(), erro.get());
        assertNotNull(resultado.get());
        assertFalse(resultado.get().getExplicacao().trim().isEmpty());
        return resultado.get();
    }
}
