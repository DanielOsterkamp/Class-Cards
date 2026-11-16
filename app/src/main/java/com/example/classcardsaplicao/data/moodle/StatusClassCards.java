package com.example.classcardsaplicao.data.moodle;

import java.util.List;

/** Situação do ClassCards no Moodle para o usuário atual (função local_classcards_get_status). */
public class StatusClassCards {

    /** Situação de um curso: papel do usuário e se os materiais estão liberados. */
    public static class Curso {
        public final long id;
        public final String nome;
        public final String papel;
        public final boolean autorizado;
        public final boolean podeAutorizar;

        public Curso(long id, String nome, String papel, boolean autorizado, boolean podeAutorizar) {
            this.id = id;
            this.nome = nome;
            this.papel = papel;
            this.autorizado = autorizado;
            this.podeAutorizar = podeAutorizar;
        }
    }

    public final boolean habilitado;
    public final int termosVersao;
    public final String termosTexto;
    public final String avisoAluno;
    public final List<Curso> cursos;

    public StatusClassCards(boolean habilitado, int termosVersao, String termosTexto, String avisoAluno,
                            List<Curso> cursos) {
        this.habilitado = habilitado;
        this.termosVersao = termosVersao;
        this.termosTexto = termosTexto;
        this.avisoAluno = avisoAluno;
        this.cursos = cursos;
    }

    /** Situação de um curso pelo id, ou null se o usuário não está inscrito nele. */
    public Curso curso(long id) {
        for (Curso curso : cursos) {
            if (curso.id == id) {
                return curso;
            }
        }
        return null;
    }
}
