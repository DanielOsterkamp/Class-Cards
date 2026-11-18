package com.example.classcardsaplicao.model;

/** Totais do usuário antes e depois das revisões confirmadas de uma sessão. */
public final class ResumoEstudo {
    public final long cardsAntes;
    public final long cardsAgora;
    public final long pontosAntes;
    public final long pontosAgora;

    public ResumoEstudo(long cardsAntes, long cardsAgora, long pontosAntes, long pontosAgora) {
        this.cardsAntes = Math.max(0, cardsAntes);
        this.cardsAgora = Math.max(0, cardsAgora);
        this.pontosAntes = Math.max(0, pontosAntes);
        this.pontosAgora = Math.max(0, pontosAgora);
    }

    /** Conserva o início da sessão e atualiza o final com a última gravação confirmada. */
    public ResumoEstudo ate(ResumoEstudo ultima) {
        return ultima == null ? this : new ResumoEstudo(cardsAntes, ultima.cardsAgora, pontosAntes, ultima.pontosAgora);
    }
}
