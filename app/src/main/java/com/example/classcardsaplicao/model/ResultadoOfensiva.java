package com.example.classcardsaplicao.model;

public class ResultadoOfensiva {

    private final int ofensivaAtual;
    private final boolean ofensivaAlterada;

    public ResultadoOfensiva(
            int ofensivaAtual,
            boolean ofensivaAlterada
    ) {
        this.ofensivaAtual = ofensivaAtual;
        this.ofensivaAlterada = ofensivaAlterada;
    }

    public int getOfensivaAtual() {
        return ofensivaAtual;
    }

    public boolean isOfensivaAlterada() {
        return ofensivaAlterada;
    }
}