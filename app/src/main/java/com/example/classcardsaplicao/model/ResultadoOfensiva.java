package com.example.classcardsaplicao.model;

/** Valores confirmados pela transação que encerrou a sessão. */
public final class ResultadoOfensiva {
    public final long anterior;
    public final long atual;
    public final boolean conquistada;

    public ResultadoOfensiva(long anterior, long atual, boolean conquistada) {
        this.anterior = anterior;
        this.atual = atual;
        this.conquistada = conquistada;
    }

    public static ResultadoOfensiva calcular(long valor, String ultimoDia,
            String hoje, String ontem, boolean ultimaRevisao) {
        long anterior = RegrasGamificacao.ofensivaVisivel(valor, ultimoDia, hoje, ontem);
        boolean conquistada = ultimaRevisao && (ultimoDia == null || ultimoDia.compareTo(hoje) < 0);
        return new ResultadoOfensiva(anterior, conquistada
                ? RegrasGamificacao.proximaOfensiva(valor, ultimoDia, hoje, ontem) : anterior, conquistada);
    }
}
