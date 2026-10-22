package com.example.classcardsaplicao.ui.deck.study;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.model.Flashcard;

public class EstudarDeckDissertativoFragment extends Fragment {

    private boolean respostaRevelada;

    public EstudarDeckDissertativoFragment() {
        super(R.layout.fragment_estudar_deck_dissertativo);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String deckId = getArguments() == null ? null : getArguments().getString("deckId");
        if (deckId == null || deckId.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Deck inválido.", Toast.LENGTH_SHORT).show();
            NavHostFragment.findNavController(this).popBackStack();
            return;
        }

        respostaRevelada = savedInstanceState != null
                && savedInstanceState.getBoolean("respostaRevelada");
        EstudarDeckViewModel viewModel = new ViewModelProvider(this).get(EstudarDeckViewModel.class);
        TextView nome = view.findViewById(R.id.txtNomeDeck);
        TextView pergunta = view.findViewById(R.id.txtPergunta);
        TextView contador = view.findViewById(R.id.txtContadorFlashcards);
        EditText resposta = view.findViewById(R.id.editResposta);
        View avaliar = view.findViewById(R.id.btnAvaliarResposta);
        View resultado = view.findViewById(R.id.containerResultado);
        TextView esperada = view.findViewById(R.id.txtRespostaEsperada);
        TextView explicacao = view.findViewById(R.id.txtExplicacaoIA);

        view.findViewById(R.id.containerCarregamento).setVisibility(View.GONE);
        view.findViewById(R.id.containerAvaliacaoFinal).setVisibility(View.GONE);
        view.findViewById(R.id.txtSugestaoIA).setVisibility(View.GONE);
        avaliar.setEnabled(false);

        avaliar.setOnClickListener(v -> {
            Flashcard atual = viewModel.getFlashcardAtual();
            if (atual == null) return;
            if (resposta.getText().toString().trim().isEmpty()) {
                resposta.setError("Digite sua resposta antes de continuar.");
                resposta.requestFocus();
                return;
            }
            resposta.setError(null);
            respostaRevelada = true;
            esperada.setText(atual.getResposta());
            explicacao.setText("A avaliação por IA estará disponível em uma próxima etapa.");
            resultado.setVisibility(View.VISIBLE);
            resposta.setEnabled(false);
            avaliar.setEnabled(false);
        });

        viewModel.getDeck().observe(getViewLifecycleOwner(), deck -> {
            if (deck != null) nome.setText(deck.getTitulo());
        });
        viewModel.getFlashcards().observe(getViewLifecycleOwner(), flashcards -> {
            Flashcard atual = viewModel.getFlashcardAtual();
            boolean disponivel = atual != null;
            view.findViewById(R.id.containerResposta).setVisibility(disponivel ? View.VISIBLE : View.GONE);
            resultado.setVisibility(disponivel && respostaRevelada ? View.VISIBLE : View.GONE);
            avaliar.setEnabled(disponivel && !respostaRevelada);
            resposta.setEnabled(disponivel && !respostaRevelada);
            if (!disponivel) {
                pergunta.setText("Você não possui flashcards para revisar agora.");
                contador.setText("Nenhum flashcard disponível");
                return;
            }
            pergunta.setText(atual.getPergunta());
            contador.setText((viewModel.getIndiceAtual() + 1) + " / " + flashcards.size());
            if (respostaRevelada) {
                esperada.setText(atual.getResposta());
                explicacao.setText("A avaliação por IA estará disponível em uma próxima etapa.");
            }
        });
        viewModel.getErro().observe(getViewLifecycleOwner(), erro -> {
            if (erro != null) {
                Toast.makeText(requireContext(), erro, Toast.LENGTH_LONG).show();
                contador.setText(erro);
            }
        });
        viewModel.carregarDeckSeNecessario(deckId);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("respostaRevelada", respostaRevelada);
    }
}
