package com.example.classcardsaplicao.model;

/** Resultado da mesma transação que salvou o progresso, os pontos e a ofensiva. */
public final class ResultadoRevisao {
    public final ResultadoOfensiva ofensiva;
    public final ResumoEstudo resumo;

    public ResultadoRevisao(ResultadoOfensiva ofensiva, ResumoEstudo resumo) {
        this.ofensiva = ofensiva;
        this.resumo = resumo;
    }
}
