package com.example.classcardsaplicao.model;

public enum Rating {

    AGAIN(1),
    HARD(2),
    GOOD(3),
    EASY(4);

    private final int valor;

    Rating(int valor) {
        this.valor = valor;
    }

    public int getValor() {
        return valor;
    }
}