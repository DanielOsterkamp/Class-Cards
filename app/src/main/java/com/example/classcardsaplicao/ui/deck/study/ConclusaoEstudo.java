package com.example.classcardsaplicao.ui.deck.study;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.model.ResultadoOfensiva;
import com.example.classcardsaplicao.model.ResumoEstudo;
import com.google.firebase.auth.FirebaseAuth;
import java.util.UUID;

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

    public static Bundle dadosResumo(ResumoEstudo resumo) {
        if (resumo == null) return null;
        Bundle dados = new Bundle();
        dados.putLong("cardsAntes", resumo.cardsAntes);
        dados.putLong("cardsAgora", resumo.cardsAgora);
        dados.putLong("pontosAntes", resumo.pontosAntes);
        dados.putLong("pontosAgora", resumo.pontosAgora);
        return dados;
    }

    public static ResumoEstudo lerResumo(Bundle dados) {
        return dados == null ? null : new ResumoEstudo(dados.getLong("cardsAntes"), dados.getLong("cardsAgora"),
                dados.getLong("pontosAntes"), dados.getLong("pontosAgora"));
    }

    public static void voltarAoDeck(Fragment fragment, ResultadoOfensiva resultado) {
        voltarAoDeck(fragment, resultado, null);
    }

    public static void voltarAoDeck(Fragment fragment, ResultadoOfensiva resultado, ResumoEstudo resumo) {
        NavController nav = NavHostFragment.findNavController(fragment);
        if (nav.getCurrentDestination() == null
                || (nav.getCurrentDestination().getId() != R.id.estudarDeckFragment
                && nav.getCurrentDestination().getId() != R.id.estudarDeckDissertativoFragment)) return;
        Bundle conclusao = resultado == null ? new Bundle() : dados(resultado);
        conclusao.putBundle("resumo", dadosResumo(resumo));
        conclusao.putString("sessaoId", UUID.randomUUID().toString());
        conclusao.putString("uid", FirebaseAuth.getInstance().getUid());
        // O resumo é apresentado também quando a ofensiva de hoje já foi conquistada.
        nav.getBackStackEntry(R.id.deckSelecionadoFragment).getSavedStateHandle().set(CHAVE, conclusao);
        nav.popBackStack(R.id.deckSelecionadoFragment, false);
    }
}
