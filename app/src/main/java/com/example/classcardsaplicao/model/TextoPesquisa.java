package com.example.classcardsaplicao.model;

import java.text.Normalizer;
import java.util.Locale;

/** A mesma chave é usada ao gravar o documento e ao consultar seu índice de pesquisa. */
public final class TextoPesquisa {

    private TextoPesquisa() { }

    public static String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Z}\\s]+", " ")
                .trim();
    }
}
