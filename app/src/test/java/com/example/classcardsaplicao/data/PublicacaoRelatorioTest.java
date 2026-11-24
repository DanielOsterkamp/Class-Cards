package com.example.classcardsaplicao.data;

import org.junit.Test;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class PublicacaoRelatorioTest {
    private Map<String, Object> claims(List<String> cursos, List<String> prof) {
        return Map.of("moodle", Map.of("cursos", cursos, "prof", prof));
    }

    @Test public void somenteAlunoMatriculadoPublicaNaTurma() {
        assertTrue(PublicacaoRelatorio.alunoDoCurso("moodle-site-3", claims(List.of("site:2"), List.of()), "site:2"));
        assertFalse(PublicacaoRelatorio.alunoDoCurso("comum", claims(List.of("site:2"), List.of()), "site:2"));
        assertFalse(PublicacaoRelatorio.alunoDoCurso("moodle-site-3", Map.of(), "site:2"));
        assertFalse(PublicacaoRelatorio.alunoDoCurso("moodle-site-3", claims(List.of("site:3"), List.of()), "site:2"));
        assertFalse(PublicacaoRelatorio.alunoDoCurso("moodle-site-3", claims(List.of("site:2"), List.of("site:2")), "site:2"));
    }

    @Test public void professorDeOutraTurmaPodeEstudarComoAluno() {
        assertTrue(PublicacaoRelatorio.alunoDoCurso("moodle-site-3",
                claims(List.of("site:2", "site:3"), List.of("site:3")), "site:2"));
    }

    @Test public void tentativaRepetidaTemIdEstavelEModosNaoColidem() {
        String id = PublicacaoRelatorio.id("moodle-site-3", "revisoesTradicionais", "tentativa");
        assertEquals(id, PublicacaoRelatorio.id("moodle-site-3", "revisoesTradicionais", "tentativa"));
        assertTrue(id.matches("[a-f0-9]{64}"));
        assertNotEquals(id, PublicacaoRelatorio.id("moodle-site-3", "revisoesDissertativas", "tentativa"));
        assertNotEquals(id, PublicacaoRelatorio.id("moodle-site-4", "revisoesTradicionais", "tentativa"));
        assertNotEquals(id, PublicacaoRelatorio.id("moodle-site-3", "revisoesTradicionais", "outra"));
    }

    @Test public void projecaoContemSomenteDadosNecessariosSemRespostaOuPontos() {
        Map<String, Object> evento = Map.of("deckId", "moodle-deck", "flashcardId", "c",
                "rating", "HARD", "createdAt", 123L, "resposta", "privada", "xp", 999L,
                "relatorio", Map.of("assunto", "Células", "titulo", "Biologia", "verificado", true));
        Map<String, Object> dados = PublicacaoRelatorio.dados("moodle-site-3", "revisoesDissertativas", "t",
                evento, Map.of("nome", "Ana", "fotoUrl", "https://foto.invalid/a", "email", "privado"));
        assertEquals(12, dados.size());
        assertEquals("dissertativo", dados.get("modo"));
        assertEquals("Ana", dados.get("nome"));
        assertEquals("Células", dados.get("assunto"));
        assertEquals(123L, dados.get("createdAt"));
        assertFalse(dados.containsKey("xp"));
        assertFalse(dados.containsKey("resposta"));
        assertFalse(dados.containsKey("email"));
    }

    @Test public void perfilAindaNaoPublicadoUsaApresentacaoPadrao() {
        Map<String, Object> evento = Map.of("relatorio", Map.of("assunto", "", "titulo", "Deck", "verificado", false));
        Map<String, Object> dados = PublicacaoRelatorio.dados("moodle-site-3", "revisoesTradicionais", "t", evento, Map.of());
        assertEquals("Estudante", dados.get("nome"));
        assertEquals("", dados.get("fotoUrl"));
        assertEquals("tradicional", dados.get("modo"));
    }
}
