package com.example.classcardsaplicao.model;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.text.SimpleDateFormat;
import java.util.Locale;
import static org.junit.Assert.*;

public class EstudoDissertativoTest {
    private Flashcard card(String id) { return new Flashcard(id, "Pergunta", "Resposta", 0); }
    private ProgressoFlashcard progresso(String id, long vencimento) {
        ProgressoFlashcard p = new ProgressoFlashcard("u", "d", id);
        p.setProximaRevisao(vencimento);
        return p;
    }
    @Test public void sessaoOrdenaVencidosAntesDeNovosEExcluiFuturos() {
        List<Flashcard> cards = Arrays.asList(card("novo"), card("futuro"), card("recente"), card("atrasado"));
        List<ProgressoFlashcard> progressos = Arrays.asList(progresso("futuro", 200),
                progresso("recente", 90), progresso("atrasado", 20));
        List<Flashcard> sessao = SessaoDissertativa.selecionar(cards, progressos, 100);
        assertEquals(3, sessao.size());
        assertEquals("atrasado", sessao.get(0).getId());
        assertEquals("recente", sessao.get(1).getId());
        assertEquals("novo", sessao.get(2).getId());
    }
    @Test public void limiteAfetaSomenteOsNovos() {
        List<Flashcard> cards = new ArrayList<>();
        List<ProgressoFlashcard> progressos = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            cards.add(card("v" + i)); progressos.add(progresso("v" + i, 0));
            cards.add(card("n" + i));
        }
        assertEquals(50, SessaoDissertativa.selecionar(cards, progressos, 100).size());
    }
    @Test public void sessaoSemCardsNaoFalha() {
        assertTrue(SessaoDissertativa.selecionar(Collections.emptyList(), Collections.emptyList(), 0).isEmpty());
    }
    @Test public void vencimentoExatoJaEntraNaSessao() {
        assertEquals(1, SessaoDissertativa.selecionar(Collections.singletonList(card("c")),
                Collections.singletonList(progresso("c", 100)), 100).size());
    }
    @Test public void restauracaoMantemOrdemEPosicaoDosCardsRemovidos() {
        List<Flashcard> restaurada = SessaoDissertativa.restaurar(Arrays.asList(card("b"), card("a")),
                Arrays.asList("a", "removido", "b"));
        assertEquals("a", restaurada.get(0).getId());
        assertNull(restaurada.get(1));
        assertEquals("b", restaurada.get(2).getId());
    }
    @Test public void primeiroEstudoIniciaOfensiva() {
        assertEquals(1, RegrasGamificacao.proximaOfensiva(0, null, "2026-10-22", "2026-10-21"));
    }
    @Test public void variasRevisoesNoMesmoDiaNaoAumentamOfensiva() {
        assertEquals(5, RegrasGamificacao.proximaOfensiva(5, "2026-10-22", "2026-10-22", "2026-10-21"));
    }
    @Test public void diaConsecutivoIncrementaOfensiva() {
        assertEquals(6, RegrasGamificacao.proximaOfensiva(5, "2026-10-21", "2026-10-22", "2026-10-21"));
    }
    @Test public void interrupcaoReiniciaOfensiva() {
        assertEquals(1, RegrasGamificacao.proximaOfensiva(5, "2026-10-20", "2026-10-22", "2026-10-21"));
    }
    @Test public void ofensivaExpiradaApareceComoZeroSemNovoEstudo() {
        assertEquals(0, RegrasGamificacao.ofensivaVisivel(5, "2026-10-20", "2026-10-22", "2026-10-21"));
        assertEquals(5, RegrasGamificacao.ofensivaVisivel(5, "2026-10-21", "2026-10-22", "2026-10-21"));
    }
    @Test public void diaRespeitaFusoEMudancaDeMes() throws Exception {
        SimpleDateFormat utc = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
        utc.setTimeZone(TimeZone.getTimeZone("UTC"));
        long instante = utc.parse("2026-11-01 01:00").getTime();
        TimeZone zona = TimeZone.getTimeZone("America/Sao_Paulo");
        assertEquals("2026-10-31", RegrasGamificacao.dia(instante, zona, 0));
        assertEquals("2026-10-30", RegrasGamificacao.dia(instante, zona, -1));
    }
    @Test public void ontemUsaDiaDoCalendarioMesmoComHorarioDeVerao() throws Exception {
        TimeZone zona = TimeZone.getTimeZone("America/New_York");
        SimpleDateFormat local = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
        local.setTimeZone(zona);
        assertEquals("2026-03-08", RegrasGamificacao.dia(local.parse("2026-03-09 00:30").getTime(), zona, -1));
    }
    @Test public void xpPorRevisaoSegueRegraConfirmada() {
        assertEquals(1, RegrasGamificacao.xpPorRevisao(Rating.AGAIN));
        assertEquals(2, RegrasGamificacao.xpPorRevisao(Rating.HARD));
        assertEquals(3, RegrasGamificacao.xpPorRevisao(Rating.GOOD));
        assertEquals(5, RegrasGamificacao.xpPorRevisao(Rating.EASY));
    }
    @Test(expected = IllegalArgumentException.class) public void avaliacaoVaziaNaoPodeSerExibidaComoSucesso() {
        new AvaliacaoDissertativa(" ", Rating.GOOD);
    }
    @Test(expected = IllegalArgumentException.class) public void avaliacaoSemClassificacaoNaoPodeSerConfirmada() {
        new AvaliacaoDissertativa("Explicação", null);
    }
    @Test public void classificacaoConfirmadaAtualizaFsrs() {
        ProgressoFlashcard p = new ProgressoFlashcard("u", "d", "c");
        new FsrsScheduler().revisar(p, Rating.GOOD);
        assertEquals(1, p.getRepeticoes());
        assertTrue(p.getUltimaRevisao() > 0);
        assertTrue(p.getProximaRevisao() > p.getUltimaRevisao());
    }
}
