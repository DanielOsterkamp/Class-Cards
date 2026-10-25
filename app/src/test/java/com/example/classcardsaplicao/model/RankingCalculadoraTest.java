package com.example.classcardsaplicao.model;

import org.junit.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.Assert.*;

/** Verifica posições, seleção do top 5 e a validade das ofensivas sem consultar o Firebase. */
public class RankingCalculadoraTest {

    private static final long AGORA = utc("2026-10-24 12:00");

    @Test
    public void cadaCategoriaOrdenaPelaSuaPropriaMetrica() {
        List<UsuarioRanking> usuarios = Arrays.asList(
                usuario("a", 20, 1, 50),
                usuario("b", 10, 5, 30),
                usuario("c", 30, 3, 10));

        List<RankingSecao> secoes = RankingCalculadora.montar(usuarios, "a", AGORA);

        assertEquals(3, secoes.size());
        assertEquals(TipoRanking.OFENSIVAS, secoes.get(0).getTipo());
        assertEquals(TipoRanking.PONTOS, secoes.get(1).getTipo());
        assertEquals(TipoRanking.CARDS_ESTUDADOS, secoes.get(2).getTipo());
        assertEquals(Arrays.asList("b", "c", "a"), ids(secoes.get(0)));
        assertEquals(Arrays.asList("c", "a", "b"), ids(secoes.get(1)));
        assertEquals(Arrays.asList("a", "b", "c"), ids(secoes.get(2)));
    }

    @Test
    public void usuarioEmPrimeiroNaoEhDuplicadoAoFinal() {
        conferirUsuarioNoTop5("u1", 1);
    }

    @Test
    public void usuarioEmQuintoNaoEhDuplicadoAoFinal() {
        conferirUsuarioNoTop5("u5", 5);
    }

    @Test
    public void usuarioEmSextoApareceDepoisDosCincoMelhores() {
        conferirUsuarioForaDoTop5("u6", 6);
    }

    @Test
    public void usuarioEmNonoMantemSuaPosicaoRealNaUltimaLinha() {
        conferirUsuarioForaDoTop5("u9", 9);
    }

    @Test
    public void empateUsaUidComoDesempateEstavelComPosicoesOrdinais() {
        List<UsuarioRanking> usuarios = Arrays.asList(
                usuario("c", 10, 2, 4), usuario("a", 10, 2, 4), usuario("b", 10, 2, 4));

        for (RankingSecao secao : RankingCalculadora.montar(usuarios, "c", AGORA)) {
            assertEquals(Arrays.asList("a", "b", "c"), ids(secao));
            assertEquals(1, secao.getEntradas().get(0).getPosicao());
            assertEquals(2, secao.getEntradas().get(1).getPosicao());
            assertEquals(3, secao.getEntradas().get(2).getPosicao());
            assertTrue(secao.getEntradas().get(2).isUsuarioAtual());
        }
    }

    @Test
    public void empateNaBordaDoTop5NaoEscondeUsuarioAtual() {
        List<UsuarioRanking> usuarios = new ArrayList<>();
        for (String uid : Arrays.asList("u6", "u2", "u5", "u1", "u4", "u3")) {
            usuarios.add(usuario(uid, 10, 2, 4));
        }

        for (RankingSecao secao : RankingCalculadora.montar(usuarios, "u6", AGORA)) {
            assertEquals(Arrays.asList("u1", "u2", "u3", "u4", "u5", "u6"), ids(secao));
            assertEquals(6, secao.getEntradas().get(5).getPosicao());
            assertTrue(secao.getEntradas().get(5).isUsuarioAtual());
        }
    }

    @Test
    public void menosDeCincoUsuariosMostraApenasOsParticipantesExistentes() {
        List<UsuarioRanking> usuarios = Arrays.asList(usuario("b", 3, 3, 3), usuario("a", 5, 5, 5));

        for (RankingSecao secao : RankingCalculadora.montar(usuarios, "b", AGORA)) {
            assertEquals(Arrays.asList("a", "b"), ids(secao));
            assertEquals(2, secao.getEntradas().size());
            assertEquals(2, secao.getEntradas().get(1).getPosicao());
        }
    }

    @Test
    public void conjuntoVazioMantemAsTresSecoesSemInventarUsuarios() {
        List<RankingSecao> secoes = RankingCalculadora.montar(Collections.emptyList(), "ausente", AGORA);

        assertEquals(3, secoes.size());
        for (RankingSecao secao : secoes) {
            assertTrue(secao.getEntradas().isEmpty());
        }
    }

    @Test
    public void usuarioNaoPublicadoNaoRecebePosicaoFicticia() {
        for (RankingSecao secao : RankingCalculadora.montar(noveUsuarios(), "nao-publicado", AGORA)) {
            assertEquals(5, secao.getEntradas().size());
            assertEquals(Arrays.asList("u1", "u2", "u3", "u4", "u5"), ids(secao));
            for (EntradaRanking entrada : secao.getEntradas()) {
                assertFalse(entrada.isUsuarioAtual());
            }
        }
    }

    @Test
    public void ofensivaDeHojeEOntemPermaneceAtivaMasAntigaEFuturaValemZero() {
        assertEquals(8, usuarioComData("hoje", 8, "2026-10-24", "UTC").valorEm(TipoRanking.OFENSIVAS, AGORA));
        assertEquals(7, usuarioComData("ontem", 7, "2026-10-23", "UTC").valorEm(TipoRanking.OFENSIVAS, AGORA));
        assertEquals(0, usuarioComData("antiga", 99, "2026-10-22", "UTC").valorEm(TipoRanking.OFENSIVAS, AGORA));
        assertEquals(0, usuarioComData("futura", 99, "2026-10-25", "UTC").valorEm(TipoRanking.OFENSIVAS, AGORA));
    }

    @Test
    public void ofensivaExpiradaNaoOcupaPrimeiroLugarMesmoComValorArmazenadoMaior() {
        List<UsuarioRanking> usuarios = Arrays.asList(
                usuarioComData("expirada", 100, "2026-10-20", "UTC"),
                usuarioComData("ativa", 2, "2026-10-24", "UTC"));

        RankingSecao ofensivas = RankingCalculadora.montar(usuarios, "expirada", AGORA).get(0);

        assertEquals(Arrays.asList("ativa", "expirada"), ids(ofensivas));
        assertEquals(2, ofensivas.getEntradas().get(1).getPosicao());
    }

    @Test
    public void validadeDaOfensivaRespeitaOCalendarioDoFusoDoUsuario() {
        long pertoDaViradaUtc = utc("2026-10-25 01:30");
        UsuarioRanking saoPaulo = usuarioComData("sp", 7, "2026-10-23", "America/Sao_Paulo");
        UsuarioRanking utc = usuarioComData("utc", 7, "2026-10-23", "UTC");

        // Em São Paulo ainda é dia 24: o estudo do dia 23 foi ontem.
        assertEquals(7, saoPaulo.valorEm(TipoRanking.OFENSIVAS, pertoDaViradaUtc));
        assertEquals(0, utc.valorEm(TipoRanking.OFENSIVAS, pertoDaViradaUtc));
        assertEquals(0, saoPaulo.valorEm(TipoRanking.OFENSIVAS, utc("2026-10-25 03:01")));
    }

    @Test
    public void ontemEhUmDiaDeCalendarioMesmoNaMudancaDeHorarioDeVerao() {
        UsuarioRanking usuario = usuarioComData("ny", 4, "2026-03-08", "America/New_York");
        // Dia 9, 00:30 local: subtrair simplesmente 24 horas cairia no dia 7.
        assertEquals(4, usuario.valorEm(TipoRanking.OFENSIVAS, utc("2026-03-09 04:30")));
    }

    @Test
    public void metricasNegativasSaoNormalizadasENomeVazioTemFallback() {
        UsuarioRanking usuario = new UsuarioRanking("u", "  ", null,
                -10, -2, -4, "2026-10-24", "UTC");

        assertEquals("Estudante", usuario.getNome());
        assertEquals(0, usuario.valorEm(TipoRanking.PONTOS, AGORA));
        assertEquals(0, usuario.valorEm(TipoRanking.OFENSIVAS, AGORA));
        assertEquals(0, usuario.valorEm(TipoRanking.CARDS_ESTUDADOS, AGORA));
    }

    @Test
    public void ordenacaoDePontosNaoSofreOverflowComValoresLong() {
        List<UsuarioRanking> usuarios = Arrays.asList(
                usuario("baixo", 1, 0, 0), usuario("medio", 2, 0, 0), usuario("alto", Long.MAX_VALUE, 0, 0));

        RankingSecao pontos = RankingCalculadora.montar(usuarios, "baixo", AGORA).get(1);

        assertEquals(Arrays.asList("alto", "medio", "baixo"), ids(pontos));
        assertEquals(Long.MAX_VALUE, pontos.getEntradas().get(0).getUsuario().valorEm(TipoRanking.PONTOS, AGORA));
    }

    @Test
    public void calcularRankingNaoReordenaListaOriginal() {
        List<UsuarioRanking> usuarios = new ArrayList<>(Arrays.asList(
                usuario("c", 1, 1, 1), usuario("b", 2, 2, 2), usuario("a", 3, 3, 3)));

        RankingCalculadora.montar(usuarios, "a", AGORA);

        assertEquals("c", usuarios.get(0).getUid());
        assertEquals("b", usuarios.get(1).getUid());
        assertEquals("a", usuarios.get(2).getUid());
    }

    private void conferirUsuarioNoTop5(String uid, int posicao) {
        for (RankingSecao secao : RankingCalculadora.montar(noveUsuarios(), uid, AGORA)) {
            assertEquals(5, secao.getEntradas().size());
            int encontrados = 0;
            for (EntradaRanking entrada : secao.getEntradas()) {
                if (entrada.isUsuarioAtual()) {
                    encontrados++;
                    assertEquals(uid, entrada.getUsuario().getUid());
                    assertEquals(posicao, entrada.getPosicao());
                }
            }
            assertEquals(1, encontrados);
        }
    }

    private void conferirUsuarioForaDoTop5(String uid, int posicao) {
        for (RankingSecao secao : RankingCalculadora.montar(noveUsuarios(), uid, AGORA)) {
            assertEquals(6, secao.getEntradas().size());
            assertEquals(Arrays.asList("u1", "u2", "u3", "u4", "u5"), ids(secao).subList(0, 5));
            EntradaRanking ultima = secao.getEntradas().get(5);
            assertEquals(uid, ultima.getUsuario().getUid());
            assertEquals(posicao, ultima.getPosicao());
            assertTrue(ultima.isUsuarioAtual());
            int encontrados = 0;
            for (EntradaRanking entrada : secao.getEntradas()) {
                if (entrada.isUsuarioAtual()) encontrados++;
            }
            assertEquals(1, encontrados);
        }
    }

    private List<UsuarioRanking> noveUsuarios() {
        List<UsuarioRanking> usuarios = new ArrayList<>();
        for (int i = 9; i >= 1; i--) {
            usuarios.add(usuario("u" + i, 10 - i, 10 - i, 10 - i));
        }
        return usuarios;
    }

    private UsuarioRanking usuario(String uid, long xp, long ofensiva, long cards) {
        return new UsuarioRanking(uid, "Nome " + uid, "https://example.com/" + uid + ".png",
                xp, ofensiva, cards, "2026-10-24", "UTC");
    }

    private UsuarioRanking usuarioComData(String uid, long ofensiva, String ultimoDia, String fuso) {
        return new UsuarioRanking(uid, "Nome " + uid, null, 0, ofensiva, 0, ultimoDia, fuso);
    }

    private List<String> ids(RankingSecao secao) {
        List<String> resultado = new ArrayList<>();
        for (EntradaRanking entrada : secao.getEntradas()) {
            resultado.add(entrada.getUsuario().getUid());
        }
        return resultado;
    }

    private static long utc(String data) {
        SimpleDateFormat formato = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
        formato.setTimeZone(TimeZone.getTimeZone("UTC"));
        formato.setLenient(false);
        try {
            return formato.parse(data).getTime();
        } catch (ParseException exception) {
            throw new AssertionError("Data fixa inválida no teste", exception);
        }
    }
}
