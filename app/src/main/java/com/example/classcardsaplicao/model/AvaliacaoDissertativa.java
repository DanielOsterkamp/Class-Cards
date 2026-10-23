package com.example.classcardsaplicao.model;


public final class AvaliacaoDissertativa {

    // a sugestao singnifica o nivel que a IA sugeriu
    private final String explicacao;
    private final Rating sugestao;

    // recusa avaliações erradas caso a IA falhe
    public AvaliacaoDissertativa(String explicacao, Rating sugestao) {
        if (explicacao == null || explicacao.trim().isEmpty() || sugestao == null) {
            throw new IllegalArgumentException("Avaliação incompleta.");
        }

        this.explicacao = explicacao.trim();
        this.sugestao = sugestao;
    }

    public String getExplicacao() {
        return explicacao;
    }

    public Rating getSugestao() {
        return sugestao;
    }
}
