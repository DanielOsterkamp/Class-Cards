package com.example.classcardsaplicao.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class ResumoEstudoTest {
    @Test public void acumulaSessaoInteiraPreservandoInicioDaPrimeiraRevisao() {
        ResumoEstudo resumo = new ResumoEstudo(10, 11, 100, 103)
                .ate(new ResumoEstudo(11, 11, 103, 105))
                .ate(new ResumoEstudo(11, 12, 105, 110));
        assertEquals(10, resumo.cardsAntes);
        assertEquals(12, resumo.cardsAgora);
        assertEquals(100, resumo.pontosAntes);
        assertEquals(110, resumo.pontosAgora);
    }

    @Test public void repetirMesmaTentativaNaoSomaValoresNovamente() {
        ResumoEstudo recibo = new ResumoEstudo(5, 6, 20, 25);
        ResumoEstudo resumo = recibo.ate(recibo);
        assertEquals(5, resumo.cardsAntes);
        assertEquals(6, resumo.cardsAgora);
        assertEquals(20, resumo.pontosAntes);
        assertEquals(25, resumo.pontosAgora);
    }

    @Test public void revisaoSemCardNovoOuSemPontosMantemTotaisIguais() {
        ResumoEstudo resumo = new ResumoEstudo(15, 15, 200, 200);
        assertEquals(resumo.cardsAntes, resumo.cardsAgora);
        assertEquals(resumo.pontosAntes, resumo.pontosAgora);
        assertSame(resumo, resumo.ate(null));
    }
}
