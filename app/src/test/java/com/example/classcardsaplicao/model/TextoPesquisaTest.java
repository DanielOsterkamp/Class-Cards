package com.example.classcardsaplicao.model;

import org.junit.Test;
import java.util.Locale;
import static org.junit.Assert.*;

public class TextoPesquisaTest {
    @Test public void buscaPortuguesIgnoraAcentosECaixa() {
        assertEquals("historia do brasil", TextoPesquisa.normalizar("  HISTÓRIA   do BRASIL  "));
        assertEquals("joao da conceicao", TextoPesquisa.normalizar("João da Conceição"));
    }

    @Test public void acentoCombinanteEquivaleAoPreComposto() {
        assertEquals(TextoPesquisa.normalizar("Álgebra"), TextoPesquisa.normalizar("A\u0301lgebra"));
    }

    @Test public void normalizaEspacosUnicodeEQuebrasDeLinha() {
        assertEquals("geografia geral", TextoPesquisa.normalizar("\tGeografia\nGeral\u00a0"));
        assertEquals("ana maria", TextoPesquisa.normalizar(" Ana\u202fMaria "));
    }

    @Test public void tratamentoDeCaixaIndependeDoIdiomaDoDispositivo() {
        Locale anterior = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertEquals("istanbul", TextoPesquisa.normalizar("İSTANBUL"));
            assertEquals("biologia", TextoPesquisa.normalizar("BIOLOGIA"));
        } finally { Locale.setDefault(anterior); }
    }

    @Test public void vazioOuNuloNaoCriaTermosArtificiais() {
        assertEquals("", TextoPesquisa.normalizar(null));
        assertEquals("", TextoPesquisa.normalizar(" \n\t "));
    }

    @Test public void preservaPontuacaoENumerosDoTitulo() {
        assertEquals("lp 3: exercicios", TextoPesquisa.normalizar("LP 3: Exercícios"));
    }

    @Test public void normalizacaoEhIdempotente() {
        String normalizado = TextoPesquisa.normalizar("  Matemática — 1º ANO ");
        assertEquals(normalizado, TextoPesquisa.normalizar(normalizado));
    }

    @Test public void igualdadeDosResultadosIncluiCamposQueAparecemNaTela() {
        ResultadoDeckPesquisa deck = new ResultadoDeckPesquisa("d1", "Biologia", "Ciências", 12);
        assertEquals(deck, new ResultadoDeckPesquisa("d1", "Biologia", "Ciências", 12));
        assertNotEquals(deck, new ResultadoDeckPesquisa("d1", "Biologia", "Ciências", 13));
        assertNotEquals(deck, new ResultadoDeckPesquisa("d2", "Biologia", "Ciências", 12));
        ResultadoUsuarioPesquisa usuario = new ResultadoUsuarioPesquisa("u1", "Ana", "https://example.com/a.jpg");
        assertEquals(usuario, new ResultadoUsuarioPesquisa("u1", "Ana", "https://example.com/a.jpg"));
        assertNotEquals(usuario, new ResultadoUsuarioPesquisa("u1", "Ana", "https://example.com/b.jpg"));
    }
}
