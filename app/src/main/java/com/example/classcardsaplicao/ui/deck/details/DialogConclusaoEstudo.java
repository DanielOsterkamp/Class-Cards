package com.example.classcardsaplicao.ui.deck.details;

import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

/** Notifica fechamentos reais; a destruição da janela durante uma rotação não avança a fila. */
public abstract class DialogConclusaoEstudo extends DialogFragment {
    private boolean fechamentoSolicitado;
    private boolean notificado;

    protected abstract String resultadoFechamento();

    @Override public void dismiss() {
        fechamentoSolicitado = true;
        super.dismiss();
    }

    @Override public void onCancel(@NonNull DialogInterface dialog) {
        fechamentoSolicitado = true;
        super.onCancel(dialog);
    }

    @Override public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (!fechamentoSolicitado || notificado) return;
        notificado = true;
        Bundle resultado = new Bundle();
        resultado.putString("sessaoId", getArguments() == null ? "" : getArguments().getString("sessaoId", ""));
        getParentFragmentManager().setFragmentResult(resultadoFechamento(), resultado);
    }
}
