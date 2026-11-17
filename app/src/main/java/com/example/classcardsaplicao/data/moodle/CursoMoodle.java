package com.example.classcardsaplicao.data.moodle;

import org.json.JSONException;
import org.json.JSONObject;

/** Um curso (matéria/turma) em que o usuário está inscrito no Moodle, com o papel dele nesse curso. */
public class CursoMoodle {

    public static final String PAPEL_PROFESSOR = "professor";
    public static final String PAPEL_ALUNO = "aluno";

    private final long id;
    private final String nome;
    private final String nomeBreve;
    private final String papel;

    public CursoMoodle(long id, String nome, String nomeBreve, String papel) {
        this.id = id;
        this.nome = nome;
        this.nomeBreve = nomeBreve;
        this.papel = papel;
    }

    public long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getNomeBreve() {
        return nomeBreve;
    }

    public String getPapel() {
        return papel;
    }

    public boolean isProfessor() {
        return PAPEL_PROFESSOR.equals(papel);
    }

    /** Lê o formato devolvido pelo plugin local_classcards. */
    static CursoMoodle deJson(JSONObject json) throws JSONException {
        return new CursoMoodle(
                json.getLong("id"),
                json.getString("fullname"),
                json.optString("shortname", ""),
                json.optString("papel", PAPEL_ALUNO));
    }

    JSONObject paraJson() throws JSONException {
        return new JSONObject()
                .put("id", id)
                .put("fullname", nome)
                .put("shortname", nomeBreve)
                .put("papel", papel);
    }

    @Override
    public String toString() {
        // Usado por listas de seleção: mostra o nome do curso.
        return nome;
    }
}
