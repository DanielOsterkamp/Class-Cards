package com.example.classcardsaplicao.model;

public final class FsrsParametros { // a classe sendo final faz com que ela não possa ser herdada

    private FsrsParametros() { // o construtor não tem nada pq não precisamos do objeto dessa classe, todos os metodos são estáticos
    }

    public static final double[] W = {
            0.212,
            1.2931,
            2.3065,
            8.2956,
            6.4133,
            0.8334,
            3.0194,
            0.001,
            1.8722,
            0.1666,
            0.796,
            1.4835,
            0.0614,
            0.2629,
            1.6483,
            0.6014,
            1.8729,
            0.5425,
            0.0912,
            0.0658,
            0.1542
    };

    // as constantes aqui são utilizadas pro calculo da repetição espaçada

    public static final long PASSO_AGAIN_MINUTOS = 1;
    public static final long PASSO_HARD_MINUTOS = 5;
    public static final long PASSO_GOOD_MINUTOS = 10;

    public static final double RETENCAO_DESEJADA = 0.90;

    public static final int INTERVALO_MAXIMO_DIAS = 36500;
}