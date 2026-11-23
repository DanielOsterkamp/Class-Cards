package com.example.classcardsaplicao.model;

import java.util.*;

/** Cálculo puro: soma revisões, mas conta cada par aluno/card só uma vez no período. */
public final class ResumoRelatorio {
    public static final int MINIMO_REVISOES = 10;
    public static final class Registro {
        public String curso = "", uid = "", nome = "", foto = "", assunto = "", deck = "", titulo = "", card = "", dia = "", modo = "";
        public boolean verificado;
        public long revisoes, errei, dificil, bom, facil, ultima;
    }
    public static final class Linha {
        public final String chave;
        public String nome, foto = "";
        public long revisoes, errei, dificil, ultima;
        public final Set<String> cards = new HashSet<>(), alunos = new HashSet<>(), dias = new HashSet<>();
        Linha(String chave, String nome) { this.chave = chave; this.nome = nome; }
        void somar(Registro r) {
            revisoes += r.revisoes; errei += r.errei; dificil += r.dificil;
            ultima = Math.max(ultima, r.ultima);
            cards.add(r.uid + ":" + r.deck + ":" + r.card); alunos.add(r.uid); dias.add(r.dia);
        }
        public double taxaErro() { return revisoes == 0 ? 0 : (double) errei / revisoes; }
        public boolean temAmostra() { return revisoes >= MINIMO_REVISOES; }
    }
    public final List<Registro> registros;
    public final Linha total = new Linha("total", "Total");
    public final List<Linha> alunos, turmas, assuntos;
    public ResumoRelatorio(List<Registro> registros, Map<String, String> nomesCursos) {
        this.registros = List.copyOf(registros);
        Map<String, Linha> a = new HashMap<>(), t = new HashMap<>(), s = new HashMap<>();
        for (Registro r : registros) {
            total.somar(r);
            Linha aluno = a.computeIfAbsent(r.uid, k -> new Linha(k, r.nome.isEmpty() ? "Estudante" : r.nome));
            aluno.foto = r.foto; aluno.somar(r);
            t.computeIfAbsent(r.curso, k -> new Linha(k, nomesCursos.getOrDefault(k, k))).somar(r);
            String assunto = r.assunto.trim().isEmpty() ? "Sem assunto informado" : r.assunto;
            // Assuntos iguais de turmas diferentes continuam separados.
            s.computeIfAbsent(r.curso + "|" + TextoPesquisa.normalizar(assunto),
                    k -> new Linha(k, assunto + " · " + nomesCursos.getOrDefault(r.curso, r.curso))).somar(r);
        }
        alunos = new ArrayList<>(a.values()); turmas = new ArrayList<>(t.values()); assuntos = new ArrayList<>(s.values());
    }
    public static Comparator<Linha> porDificuldade() {
        return Comparator.<Linha, Boolean>comparing(Linha::temAmostra).reversed()
                .thenComparing(Comparator.comparingDouble(Linha::taxaErro).reversed())
                .thenComparing(Comparator.comparingLong((Linha l) -> l.dificil).reversed())
                .thenComparing(l -> l.nome).thenComparing(l -> l.chave);
    }
    public List<Linha> dificuldades() {
        List<Linha> lista = new ArrayList<>();
        for (Linha l : alunos) if (l.temAmostra() && (l.errei > 0 || l.dificil > 0)) lista.add(l);
        lista.sort(porDificuldade()); return lista;
    }
    public List<Linha> atividade() {
        List<Linha> lista = new ArrayList<>(alunos);
        lista.sort(Comparator.<Linha>comparingInt(l -> l.cards.size()).reversed()
                .thenComparing(Comparator.comparingLong((Linha l) -> l.revisoes).reversed()).thenComparing(l -> l.chave));
        return lista;
    }
    public ResumoRelatorio detalhe(String tipo, String chave, Map<String, String> nomes) {
        List<Registro> filtrados = new ArrayList<>();
        for (Registro r : registros) {
            String assunto = r.assunto.trim().isEmpty() ? "Sem assunto informado" : r.assunto;
            if (("aluno".equals(tipo) && r.uid.equals(chave)) || ("turma".equals(tipo) && r.curso.equals(chave))
                    || ("assunto".equals(tipo) && (r.curso + "|" + TextoPesquisa.normalizar(assunto)).equals(chave))) filtrados.add(r);
        }
        return new ResumoRelatorio(filtrados, nomes);
    }
}
