package com.example.classcardsaplicao.model;

import java.util.Objects;

/** Somente os campos necessários à grade da pesquisa; não baixa os cards do deck. */
public final class ResultadoDeckPesquisa {
    private final String id;
    private final String titulo;
    private final String materia;
    private final long quantidadeCards;

    public ResultadoDeckPesquisa(String id, String titulo, String materia, long quantidadeCards) {
        this.id = id == null ? "" : id;
        this.titulo = titulo == null || titulo.trim().isEmpty() ? "Deck sem título" : titulo.trim();
        this.materia = materia == null ? "" : materia.trim();
        this.quantidadeCards = Math.max(0, quantidadeCards);
    }

    public String getId() { return id; }
    public String getTitulo() { return titulo; }
    public String getMateria() { return materia; }
    public long getQuantidadeCards() { return quantidadeCards; }

    @Override public boolean equals(Object outro) {
        if (this == outro) return true;
        if (!(outro instanceof ResultadoDeckPesquisa)) return false;
        ResultadoDeckPesquisa deck = (ResultadoDeckPesquisa) outro;
        return quantidadeCards == deck.quantidadeCards && id.equals(deck.id)
                && titulo.equals(deck.titulo) && materia.equals(deck.materia);
    }

    @Override public int hashCode() { return Objects.hash(id, titulo, materia, quantidadeCards); }
}
