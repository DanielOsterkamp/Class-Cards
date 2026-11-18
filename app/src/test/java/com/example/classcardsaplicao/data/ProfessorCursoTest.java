package com.example.classcardsaplicao.data;

import org.junit.Test;
import java.util.Map;
import java.util.List;
import static org.junit.Assert.*;

public class ProfessorCursoTest {
    @Test public void professorSomenteNoCursoDaClaim() {
        Map<String, Object> claims = Map.of("moodle", Map.of("prof", List.of("site:2")));
        assertTrue(DeckRepository.professorNoCurso(claims, "site:2"));
        assertFalse(DeckRepository.professorNoCurso(claims, "site:3"));
    }
    @Test public void alunoEContaComumNaoSaoProfessores() {
        assertFalse(DeckRepository.professorNoCurso(Map.of(), "site:2"));
        assertFalse(DeckRepository.professorNoCurso(Map.of("moodle", Map.of("cursos", List.of("site:2"))), "site:2"));
    }
    @Test public void claimsIncompletasNaoAutorizam() {
        assertFalse(DeckRepository.professorNoCurso(Map.of("moodle", "invalido"), "site:2"));
        assertFalse(DeckRepository.professorNoCurso(Map.of("moodle", Map.of("prof", "site:2")), "site:2"));
        assertFalse(DeckRepository.professorNoCurso(Map.of(), null));
    }
}
