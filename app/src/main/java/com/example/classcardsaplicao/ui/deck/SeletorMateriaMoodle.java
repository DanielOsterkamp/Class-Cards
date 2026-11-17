package com.example.classcardsaplicao.ui.deck;

import android.content.Context;
import android.widget.EditText;

import com.example.classcardsaplicao.data.moodle.CursoMoodle;
import com.example.classcardsaplicao.data.moodle.MoodleConfig;
import com.example.classcardsaplicao.data.moodle.MoodleSessao;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;

import java.util.List;

/**
 * Para quem entrou pelo Moodle: o campo "Matéria" do baralho deixa de ser digitado e passa a ser
 * escolhido numa lista com os cursos em que a pessoa está inscrita. O nome do curso escolhido
 * vai para o próprio campo, então salvar, validar, a IA e a pesquisa continuam funcionando igual.
 *
 * Para contas de e-mail/Google, ou para quem não tem cursos no Moodle, o campo fica como antes.
 */
public final class SeletorMateriaMoodle {

    private SeletorMateriaMoodle() {
    }

    public static void aplicar(EditText campo) {
        if (campo == null) {
            return;
        }
        Context context = campo.getContext();
        if (!MoodleConfig.habilitado(context)) {
            return;
        }
        List<CursoMoodle> cursos = MoodleSessao.cursos(context, FirebaseAuth.getInstance().getUid());
        if (cursos.isEmpty()) {
            return;
        }

        // Continua sendo o mesmo campo de texto, mas sem teclado: o texto só muda pela lista.
        campo.setKeyListener(null);
        campo.setFocusable(false);
        campo.setCursorVisible(false);
        campo.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, android.R.drawable.arrow_down_float, 0);
        campo.setOnClickListener(v -> {
            if (campo.isEnabled()) {
                mostrarLista(campo, cursos);
            }
        });
    }

    private static void mostrarLista(EditText campo, List<CursoMoodle> cursos) {
        String[] nomes = new String[cursos.size()];
        int marcado = -1;
        String atual = campo.getText() == null ? "" : campo.getText().toString().trim();
        for (int i = 0; i < cursos.size(); i++) {
            nomes[i] = cursos.get(i).getNome();
            if (nomes[i].equals(atual)) {
                marcado = i;
            }
        }
        new MaterialAlertDialogBuilder(campo.getContext())
                .setTitle("Escolha a matéria")
                .setSingleChoiceItems(nomes, marcado, (dialogo, escolhido) -> {
                    campo.setText(nomes[escolhido]);
                    campo.setError(null);
                    dialogo.dismiss();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
