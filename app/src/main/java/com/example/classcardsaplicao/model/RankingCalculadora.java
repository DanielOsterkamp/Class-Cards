package com.example.classcardsaplicao.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RankingCalculadora {
    public static List<RankingSecao> montar(List<UsuarioRanking> usuarios, String uidAtual, long agora) {

        List<RankingSecao> secoes = new ArrayList<>();

        for (TipoRanking tipo : TipoRanking.values()) {

            List<UsuarioRanking> ordenados = new ArrayList<>(usuarios);
            ordenados.sort(Comparator.comparingLong((UsuarioRanking u) -> u.valorEm(tipo, agora))
                    .reversed().thenComparing(UsuarioRanking::getUid));

            List<EntradaRanking> entradas = new ArrayList<>();


            // pega os 5 melhores da lista de usuarios
            for (int i = 0; i < Math.min(5, ordenados.size()); i++) {
                UsuarioRanking usuario = ordenados.get(i);
                entradas.add(new EntradaRanking(i + 1, usuario, usuario.getUid().equals(uidAtual)));
            }

            // Não inventa a posição 6: percorre toda a lista para encontrar a posição verdadeira.
            for (int i = 5; i < ordenados.size(); i++) {

                UsuarioRanking usuario = ordenados.get(i);

                // caso o usuario não esteja entre os 5 primeiros, coloca ele na posição que ele está
                if (usuario.getUid().equals(uidAtual)) {
                    entradas.add(new EntradaRanking(i + 1, usuario, true));
                    break;
                }

            }
            secoes.add(new RankingSecao(tipo, entradas));
        }
        return secoes;
    }
}
