package com.example.classcardsaplicao.model;

import java.util.Objects;

/** A pesquisa usa apenas o perfil público; nunca lê e-mail ou histórico de outro usuário. */
public final class ResultadoUsuarioPesquisa {
    private final String uid;
    private final String nome;
    private final String fotoUrl;

    public ResultadoUsuarioPesquisa(String uid, String nome, String fotoUrl) {
        this.uid = uid == null ? "" : uid;
        this.nome = nome == null || nome.trim().isEmpty() ? "Estudante" : nome.trim();
        this.fotoUrl = fotoUrl != null && fotoUrl.startsWith("https://") && fotoUrl.length() <= 4096
                ? fotoUrl : "";
    }

    public String getUid() { return uid; }
    public String getNome() { return nome; }
    public String getFotoUrl() { return fotoUrl; }

    @Override public boolean equals(Object outro) {
        if (this == outro) return true;
        if (!(outro instanceof ResultadoUsuarioPesquisa)) return false;
        ResultadoUsuarioPesquisa usuario = (ResultadoUsuarioPesquisa) outro;
        return uid.equals(usuario.uid) && nome.equals(usuario.nome) && fotoUrl.equals(usuario.fotoUrl);
    }

    @Override public int hashCode() { return Objects.hash(uid, nome, fotoUrl); }
}
