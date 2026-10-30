package com.example.classcardsaplicao.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class ResultadoOfensivaTest {
    private ResultadoOfensiva calcular(long atual, String dia, boolean fim) {
        return ResultadoOfensiva.calcular(atual, dia, "2026-10-29", "2026-10-28", fim);
    }
    @Test public void revisaoIntermediariaNaoConquista() {
        ResultadoOfensiva r = calcular(4, "2026-10-28", false);
        assertFalse(r.conquistada); assertEquals(4, r.atual);
    }
    @Test public void primeiroDeckConcluidoIniciaEmUm() {
        ResultadoOfensiva r = calcular(0, null, true);
        assertTrue(r.conquistada); assertEquals(0, r.anterior); assertEquals(1, r.atual);
    }
    @Test public void diaConsecutivoIncrementa() {
        ResultadoOfensiva r = calcular(4, "2026-10-28", true);
        assertTrue(r.conquistada); assertEquals(4, r.anterior); assertEquals(5, r.atual);
    }
    @Test public void segundoDeckNoDiaNaoConquista() {
        ResultadoOfensiva r = calcular(5, "2026-10-29", true);
        assertFalse(r.conquistada); assertEquals(5, r.atual);
    }
    @Test public void ofensivaExpiradaMostraZeroAntesDeReiniciar() {
        ResultadoOfensiva r = calcular(8, "2026-10-20", true);
        assertTrue(r.conquistada); assertEquals(0, r.anterior); assertEquals(1, r.atual);
    }
    @Test public void dataFuturaNaoConcedeOutraConquista() {
        assertFalse(calcular(5, "2026-10-30", true).conquistada);
    }
}
