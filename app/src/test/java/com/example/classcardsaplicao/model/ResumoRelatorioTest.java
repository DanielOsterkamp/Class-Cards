package com.example.classcardsaplicao.model;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class ResumoRelatorioTest {
    private ResumoRelatorio.Registro registro(String uid,String card,String dia,long quantidade,long erros) {
        ResumoRelatorio.Registro r=new ResumoRelatorio.Registro();r.uid=uid;r.nome=uid;r.card=card;r.deck="deck";r.curso="site:2";r.assunto="Células";r.dia=dia;r.revisoes=quantidade;r.errei=erros;return r;
    }
    @Test public void repeticoesNaoDuplicamCardsNoPeriodo() {
        ResumoRelatorio r=new ResumoRelatorio(List.of(registro("a","c","2026-01-01",3,1),registro("a","c","2026-01-02",4,2)),Map.of());
        assertEquals(7,r.total.revisoes);assertEquals(1,r.total.cards.size());assertEquals(2,r.alunos.get(0).dias.size());assertEquals(3.0/7,r.total.taxaErro(),0.00001);
    }
    @Test public void alunosDiferentesContamAtividadesSeparadas() {
        ResumoRelatorio r=new ResumoRelatorio(List.of(registro("a","c","d",1,0),registro("b","c","d",1,0)),Map.of());
        assertEquals(2,r.total.cards.size());assertEquals(2,r.total.alunos.size());
    }
    @Test public void poucaAmostraNaoIndicaDificuldade() {
        ResumoRelatorio r=new ResumoRelatorio(List.of(registro("a","c","d",9,9)),Map.of());assertTrue(r.dificuldades().isEmpty());
    }
    @Test public void dificuldadeUsaTaxaEmVezDeTotalBruto() {
        ResumoRelatorio r=new ResumoRelatorio(List.of(registro("a","c","d",100,20),registro("b","c","d",10,8)),Map.of());assertEquals("b",r.dificuldades().get(0).chave);
    }
    @Test public void assuntosSaoSeparadosPorCurso() {
        ResumoRelatorio.Registro a=registro("a","c","d",1,0),b=registro("b","c","d",1,0);b.curso="site:3";
        ResumoRelatorio r=new ResumoRelatorio(List.of(a,b),Map.of());assertEquals(2,r.assuntos.size());
    }
    @Test public void detalheNaoMisturaAlunos() {
        ResumoRelatorio r=new ResumoRelatorio(List.of(registro("a","c","d",1,0),registro("b","d","d",1,0)),Map.of());assertEquals(1,r.detalhe("aluno","a",Map.of()).total.revisoes);
    }
    @Test public void semAssuntoTemGrupoExplicito() {
        ResumoRelatorio.Registro a=registro("a","c","d",1,0);a.assunto="";
        assertTrue(new ResumoRelatorio(List.of(a),Map.of()).assuntos.get(0).nome.startsWith("Sem assunto informado"));
    }
    @Test public void atividadeOrdenaPorCardsDistintos() {
        ResumoRelatorio r=new ResumoRelatorio(List.of(registro("a","c","d",20,0),registro("b","c","d",1,0),registro("b","d","d",1,0)),Map.of());assertEquals("b",r.atividade().get(0).chave);
    }
}
