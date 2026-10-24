package com.example.classcardsaplicao.model;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;


public final class RegrasGamificacao {
    // O XP depende da classificação final escolhida pelo estudante, não da sugestão da IA.
    public static long xpPorRevisao(Rating rating) {
        if (rating == null) {
            throw new IllegalArgumentException("Informe a classificação da revisão.");
        }
        switch (rating) {
            case AGAIN: return 1;
            case HARD: return 2;
            case GOOD: return 3;
            case EASY: return 5;
            default: throw new IllegalArgumentException("Classificação inválida.");
        }
    }

    public static String dia(long instante, TimeZone zona, int deslocamento) {
        Calendar calendario = Calendar.getInstance(zona);
        calendario.setTimeInMillis(instante);
        calendario.add(Calendar.DAY_OF_MONTH, deslocamento);

        // O formato ano-mês-dia permite comparar as datas como texto na ordem cronológica.
        SimpleDateFormat formato = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        formato.setTimeZone(zona);
        return formato.format(calendario.getTime());
    }

    public static long proximaOfensiva(long atual, String ultimoDia, String hoje, String ontem) {

        if (ultimoDia != null && ultimoDia.compareTo(hoje) >= 0)
            return Math.max(1, atual);

        return ontem.equals(ultimoDia) ? Math.max(0, atual) + 1 : 1;
    }

    public static long ofensivaVisivel(long atual, String ultimoDia, String hoje, String ontem) {
        return hoje.equals(ultimoDia) || ontem.equals(ultimoDia) ? Math.max(0, atual) : 0;
    }
}
