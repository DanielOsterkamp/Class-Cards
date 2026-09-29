package com.example.classcardsaplicao.ui.deck;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.model.Flashcard;
import com.google.android.material.button.MaterialButton;

public class FlashcardDialogHelper {

    public static void abrirDialogEditar(
            Context context,
            Flashcard flashcard,
            OnSalvarListener listener
    ) {

        View dialogView = LayoutInflater
                .from(context)
                .inflate(R.layout.dialog_editar_flashcard, null);

        EditText inputPergunta =
                dialogView.findViewById(R.id.inputEditarPergunta);

        EditText inputResposta =
                dialogView.findViewById(R.id.inputEditarResposta);

        MaterialButton btnCancelar =
                dialogView.findViewById(R.id.btnCancelarEdicao);

        MaterialButton btnSalvar =
                dialogView.findViewById(R.id.btnSalvarEdicao);

        inputPergunta.setText(flashcard.getPergunta());
        inputResposta.setText(flashcard.getResposta());

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(dialogView)
                .create();

        btnCancelar.setOnClickListener(v -> dialog.dismiss());

        btnSalvar.setOnClickListener(v -> {

            String pergunta =
                    inputPergunta.getText().toString().trim();

            String resposta =
                    inputResposta.getText().toString().trim();

            if (pergunta.isEmpty()) {
                inputPergunta.setError("Informe a pergunta.");
                return;
            }

            if (resposta.isEmpty()) {
                inputResposta.setError("Informe a resposta.");
                return;
            }

            listener.onSalvar(pergunta, resposta);

            dialog.dismiss();
        });

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(
                    android.R.color.transparent
            );
        }
    }

    public interface OnSalvarListener {
        void onSalvar(String pergunta, String resposta);
    }
}