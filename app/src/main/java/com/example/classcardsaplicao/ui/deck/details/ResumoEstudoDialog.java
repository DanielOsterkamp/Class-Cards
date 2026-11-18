package com.example.classcardsaplicao.ui.deck.details;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.model.ResumoEstudo;
import com.example.classcardsaplicao.ui.deck.study.ConclusaoEstudo;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.NumberFormat;
import java.util.Locale;

public class ResumoEstudoDialog extends DialogConclusaoEstudo {
    public static final String TAG = "resumoEstudo";
    public static final String FECHADO = "resumoEstudoFechado";

    @Override protected String resultadoFechamento() { return FECHADO; }

    @NonNull @Override public Dialog onCreateDialog(@Nullable Bundle estado) {
        View view = getLayoutInflater().inflate(R.layout.dialog_resumo_estudo, null);
        ResumoEstudo resumo = ConclusaoEstudo.lerResumo(requireArguments().getBundle("resumo"));
        NumberFormat numeros = NumberFormat.getIntegerInstance(Locale.forLanguageTag("pt-BR"));
        // Recibos de versões antigas podem não ter os totais; nesse caso não inventamos valores.
        preencher(view, R.id.txtCardsAntesResumo, R.string.resumo_valor_antes,
                resumo == null ? "—" : numeros.format(resumo.cardsAntes));
        preencher(view, R.id.txtCardsAgoraResumo, R.string.resumo_valor_agora,
                resumo == null ? "—" : numeros.format(resumo.cardsAgora));
        preencher(view, R.id.txtPontosAntesResumo, R.string.resumo_valor_antes,
                resumo == null ? "—" : numeros.format(resumo.pontosAntes));
        preencher(view, R.id.txtPontosAgoraResumo, R.string.resumo_valor_agora,
                resumo == null ? "—" : numeros.format(resumo.pontosAgora));
        view.findViewById(R.id.btnFecharResumoEstudo).setOnClickListener(v -> dismiss());
        return new MaterialAlertDialogBuilder(requireContext()).setView(view).create();
    }

    private void preencher(View view, int id, int texto, String valor) {
        ((TextView) view.findViewById(id)).setText(getString(texto, valor));
    }
}
