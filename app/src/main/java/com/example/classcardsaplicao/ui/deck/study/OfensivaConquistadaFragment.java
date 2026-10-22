package com.example.classcardsaplicao.ui.deck.study;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.view.ViewCompat;
import androidx.navigation.fragment.NavHostFragment;
import com.example.classcardsaplicao.R;

public class OfensivaConquistadaFragment extends Fragment {
    public OfensivaConquistadaFragment() { super(R.layout.fragment_ofensiva_conquistada); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewCompat.setAccessibilityHeading(view.findViewById(R.id.txtTituloOfensiva), true);
        Bundle args = requireArguments();
        int ofensiva = args.getInt("ofensivaAtual");
        int estudados = args.getInt("flashcardsEstudados");
        ((TextView) view.findViewById(R.id.txtTituloOfensiva)).setText(
                args.getBoolean("conquistou") ? R.string.ofensiva_conquistada : R.string.sessao_concluida);
        ((TextView) view.findViewById(R.id.txtValorOfensiva)).setText(
                getResources().getQuantityString(R.plurals.dias_ofensiva, ofensiva, ofensiva));
        ((TextView) view.findViewById(R.id.txtResumoSessao)).setText(
                getResources().getQuantityString(R.plurals.cards_estudados, estudados, estudados));
        ((TextView) view.findViewById(R.id.txtMensagemOfensiva)).setText(
                args.getBoolean("conquistou") ? R.string.ofensiva_confirmada : R.string.ofensiva_ja_conquistada);
        view.findViewById(R.id.btnContinuarOfensiva).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
    }
}
