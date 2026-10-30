package com.example.classcardsaplicao.ui.deck.study;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.model.ResultadoOfensiva;

/** Transporta o resultado já salvo, sem efetuar novas gravações. */
public final class ConclusaoEstudo {
    public static final String CHAVE = "conclusaoOfensiva";
    private ConclusaoEstudo() { }

    public static Bundle dados(ResultadoOfensiva resultado) {
        Bundle dados = new Bundle();
        dados.putLong("anterior", resultado.anterior);
        dados.putLong("atual", resultado.atual);
        dados.putBoolean("conquistada", resultado.conquistada);
        return dados;
    }

    public static ResultadoOfensiva ler(Bundle dados) {
        return new ResultadoOfensiva(dados.getLong("anterior"), dados.getLong("atual"),
                dados.getBoolean("conquistada"));
    }

    public static void voltarAoDeck(Fragment fragment, ResultadoOfensiva resultado) {
        NavController nav = NavHostFragment.findNavController(fragment);
        if (nav.getCurrentDestination() == null
                || (nav.getCurrentDestination().getId() != R.id.estudarDeckFragment
                && nav.getCurrentDestination().getId() != R.id.estudarDeckDissertativoFragment)) return;
        if (resultado != null && resultado.conquistada) {
            nav.getBackStackEntry(R.id.deckSelecionadoFragment).getSavedStateHandle()
                    .set(CHAVE, dados(resultado));
        }
        nav.popBackStack(R.id.deckSelecionadoFragment, false);
    }
}
