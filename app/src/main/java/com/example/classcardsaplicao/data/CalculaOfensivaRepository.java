package com.example.classcardsaplicao.data;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class CalculaOfensivaRepository {

    public static int calculaOfensiva(int ofensivaAtual, LocalDate ultimoLogin, LocalDate dataAtual){
        if (ultimoLogin == null){
            return 1;
        }

        Long dias = ChronoUnit.DAYS.between(ultimoLogin,dataAtual);

        if (dias == 1){
            return ofensivaAtual +1;
        }

        if (dias<1){
            return ofensivaAtual;
        }

            return 1;
    }

}
