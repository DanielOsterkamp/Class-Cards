package com.example.classcardsaplicao.model;

import java.util.List;

// essa classe é usada para armazenar quem vai aparecer no ranking
public final class RankingSecao {
    private final TipoRanking tipo;
    private final List<EntradaRanking> entradas;

    public RankingSecao(TipoRanking tipo, List<EntradaRanking> entradas) {
        this.tipo = tipo;
        this.entradas = List.copyOf(entradas);
    }
    public TipoRanking getTipo() { return tipo; }
    public List<EntradaRanking> getEntradas() { return entradas; }
}
