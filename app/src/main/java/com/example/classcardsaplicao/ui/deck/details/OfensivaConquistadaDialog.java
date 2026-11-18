package com.example.classcardsaplicao.ui.deck.details;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.classcardsaplicao.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class OfensivaConquistadaDialog extends DialogConclusaoEstudo {
    public static final String TAG = "ofensivaConquistada";
    public static final String FECHADO = "ofensivaConquistadaFechada";

    @Override protected String resultadoFechamento() { return FECHADO; }

    @NonNull @Override public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = getLayoutInflater().inflate(R.layout.dialog_ofensiva_conquistada, null);
        ((TextView) view.findViewById(R.id.txtOfensivaAnterior)).setText(getString(
                R.string.ofensiva_valor_anterior, requireArguments().getLong("anterior")));
        ((TextView) view.findViewById(R.id.txtOfensivaAtual)).setText(getString(
                R.string.ofensiva_valor_atual, requireArguments().getLong("atual")));
        view.findViewById(R.id.btnFecharOfensiva).setOnClickListener(v -> dismiss());
        return new MaterialAlertDialogBuilder(requireContext()).setView(view).create();
    }
}
