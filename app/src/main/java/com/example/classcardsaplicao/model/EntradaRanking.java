package com.example.classcardsaplicao.model;


public final class EntradaRanking {
    private final int posicao;
    private final UsuarioRanking usuario;
    private final boolean usuarioAtual;

    public EntradaRanking(int posicao, UsuarioRanking usuario, boolean usuarioAtual) {
        this.posicao = posicao;
        this.usuario = usuario;
        this.usuarioAtual = usuarioAtual;
    }

    public int getPosicao() {
        return posicao;
    }

    public boolean isUsuarioAtual() {
        return usuarioAtual;
    }

    public UsuarioRanking getUsuario() {
        return usuario;
    }
}
