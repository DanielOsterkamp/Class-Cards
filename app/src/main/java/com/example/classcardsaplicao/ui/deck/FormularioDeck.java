package com.example.classcardsaplicao.ui.deck;

import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.classcardsaplicao.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

/** Mantém a troca Manual/Automático e o bloqueio dos campos iguais nas duas telas. */
public final class FormularioDeck {
    private final View raiz;
    private final SwitchMaterial modo;

    public FormularioDeck(View raiz) {
        this.raiz = raiz;
        modo = raiz.findViewById(R.id.switchModoCriacao);
        modo.setOnCheckedChangeListener((botao, automatico) -> apresentarModo());
        apresentarModo();
    }

    public void apresentarModo() {
        boolean automatico = modo.isChecked();
        raiz.findViewById(R.id.containerManual).setVisibility(automatico ? View.GONE : View.VISIBLE);
        raiz.findViewById(R.id.containerAutomatico).setVisibility(automatico ? View.VISIBLE : View.GONE);
        ((TextView) raiz.findViewById(R.id.txtManual)).setTextColor(ContextCompat.getColor(raiz.getContext(),
                automatico ? R.color.text_secondary : R.color.text_primary));
        ((TextView) raiz.findViewById(R.id.txtAutomatico)).setTextColor(ContextCompat.getColor(raiz.getContext(),
                automatico ? R.color.text_primary : R.color.text_secondary));
    }

    public void atualizar(boolean gerando, boolean salvando, boolean pronto) {
        boolean habilitado = pronto && !gerando && !salvando;
        for (int id : new int[]{R.id.inputNomeDeck, R.id.inputMateriaDeck, R.id.inputPergunta,
                R.id.inputResposta, R.id.inputNumeroCards, R.id.inputDescricaoIA,
                R.id.btnCriarFlashcard, R.id.btnGerarFlashcards, R.id.btnSalvarDeck, R.id.switchModoCriacao}) {
            raiz.findViewById(id).setEnabled(habilitado);
        }
        raiz.findViewById(R.id.containerCarregamentoCriacao).setVisibility(gerando ? View.VISIBLE : View.GONE);
        raiz.findViewById(R.id.progressSalvarDeck).setVisibility(salvando ? View.VISIBLE : View.GONE);
        ((MaterialButton) raiz.findViewById(R.id.btnGerarFlashcards)).setText(
                gerando ? R.string.deck_gerando : R.string.deck_gerar_ia);
        if (gerando) modo.setChecked(true);
        apresentarModo();
    }
}
