package com.example.classcardsaplicao.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


// prepara os cards que serão estudados na sessão
public final class SessaoDissertativa {

    public static List<Flashcard> selecionar(List<Flashcard> cards, List<ProgressoFlashcard> progressos, long agora) {

        Map<String, ProgressoFlashcard> map = new HashMap<>();
        for (ProgressoFlashcard p : progressos)
            map.put(p.getFlashcardId(), p);

        List<Flashcard> vencidos = new ArrayList<>();
        List<Flashcard> novos = new ArrayList<>();
        for (Flashcard card : cards) {
            ProgressoFlashcard p = map.get(card.getId());
            if (p == null)
                novos.add(card);
            else if (p.getProximaRevisao() <= agora)
                vencidos.add(card);
        }

        // os cards mais atrasados são estudados antes, limita em 20 os cards novos
        vencidos.sort(Comparator.comparingLong(c -> map.get(c.getId()).getProximaRevisao()));
        vencidos.addAll(novos.subList(0, Math.min(20, novos.size())));
        return vencidos;
    }


    // restaura os cards que estavam sendo estudados se o app fecha no meio
    public static List<Flashcard> restaurar(List<Flashcard> cards, List<String> ids) {

        Map<String, Flashcard> map = new HashMap<>();

        for (Flashcard card : cards)
            map.put(card.getId(), card);

        List<Flashcard> resultado = new ArrayList<>();

        for (String id : ids)
            resultado.add(map.get(id));

        return resultado;
    }
}
