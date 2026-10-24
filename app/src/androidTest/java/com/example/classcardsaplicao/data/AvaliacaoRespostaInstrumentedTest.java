package com.example.classcardsaplicao.data;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.classcardsaplicao.model.Rating;
import java.util.concurrent.TimeoutException;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AvaliacaoRespostaInstrumentedTest {
    @Test public void jsonValidoPreservaExplicacaoEClassificacao() throws Exception {
        assertEquals(Rating.GOOD, AvaliacaoRespostaRepository.interpretarResposta(
                "{\"explicacao\":\"Correto.\",\"sugestao\":\"GOOD\"}").getSugestao());
    }
    @Test(expected=IllegalArgumentException.class) public void ratingDesconhecidoNaoViraAvaliacaoValida() throws Exception {
        AvaliacaoRespostaRepository.interpretarResposta("{\"explicacao\":\"Correto\",\"sugestao\":\"OTIMO\"}");
    }
    @Test(expected=IllegalArgumentException.class) public void respostaVaziaNaoViraSucesso() throws Exception {
        AvaliacaoRespostaRepository.interpretarResposta(" ");
    }
    @Test(expected=IllegalArgumentException.class) public void explicacaoVaziaNaoViraSucesso() throws Exception {
        AvaliacaoRespostaRepository.interpretarResposta(new org.json.JSONObject()
                .put("explicacao", " ").put("sugestao", "GOOD").toString());
    }
    @Test public void falhasDeConfiguracaoECotaSaoDiferenciadas() {
        assertTrue(AvaliacaoRespostaRepository.mensagemFalha(new Exception("App Check attestation failed")).contains("App Check"));
        assertTrue(AvaliacaoRespostaRepository.mensagemFalha(new Exception("RESOURCE_EXHAUSTED 429")).contains("limite"));
        assertTrue(AvaliacaoRespostaRepository.mensagemFalha(new Exception("404 model not found")).contains("modelo"));
        assertTrue(AvaliacaoRespostaRepository.mensagemFalha(new TimeoutException()).contains("demorou"));
    }
}
