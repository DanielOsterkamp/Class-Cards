package com.example.classcardsaplicao.ui.moodle;

import android.content.Context;
import android.view.View;
import android.widget.EditText;

import com.example.classcardsaplicao.data.moodle.BolhaMoodle;
import com.example.classcardsaplicao.data.moodle.CursoMoodle;
import com.example.classcardsaplicao.data.moodle.MoodleSessao;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;

import java.util.List;
import java.util.function.Consumer;



public final class FiltroCursoMoodle {

    private static final String PREFIXO = "Curso: ";

    private FiltroCursoMoodle() {
    }



    public static void aplicar(EditText campo, Consumer<String> aoMudar) {
        if (campo == null) {
            return;
        }
        Context context = campo.getContext();
        List<CursoMoodle> cursos = MoodleSessao.cursos(context, FirebaseAuth.getInstance().getUid());
        if (!BolhaMoodle.ehMoodle() || cursos.isEmpty()) {
            campo.setVisibility(View.GONE);
            return;
        }
        campo.setKeyListener(null);
        campo.setFocusable(false);
        campo.setCursorVisible(false);
        campo.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, android.R.drawable.arrow_down_float, 0);
        campo.setText(PREFIXO + "Todos");
        campo.setVisibility(View.VISIBLE);

        String[] opcoes = new String[cursos.size() + 1];
        String[] chaves = new String[cursos.size() + 1];
        opcoes[0] = "Todos";
        List<String> minhas = BolhaMoodle.minhasChaves();
        for (int i = 0; i < cursos.size(); i++) {
            opcoes[i + 1] = cursos.get(i).getNome();
            chaves[i + 1] = i < minhas.size() ? minhas.get(i) : null;
        }
        final int[] marcado = {0};
        campo.setOnClickListener(v -> new MaterialAlertDialogBuilder(context)
                .setTitle("Filtrar por curso")
                .setSingleChoiceItems(opcoes, marcado[0], (dialogo, escolhido) -> {
                    dialogo.dismiss();
                    if (escolhido == marcado[0]) {
                        return;
                    }
                    marcado[0] = escolhido;
                    campo.setText(PREFIXO + opcoes[escolhido]);
                    aoMudar.accept(chaves[escolhido]);
                })
                .setNegativeButton("Cancelar", null)
                .show());
    }
}
