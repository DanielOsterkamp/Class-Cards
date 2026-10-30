package com.example.classcardsaplicao.ui.deck.study;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.model.Flashcard;
import com.example.classcardsaplicao.model.Rating;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class EstudarDeckFragment extends Fragment {

    public EstudarDeckFragment() {
        super(R.layout.fragment_estudar_deck);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        LinearLayout layoutAvaliacoes = view.findViewById(R.id.layoutAvaliacoes);

        MaterialButton btnErrei = view.findViewById(R.id.btnErrei);

        MaterialButton btnDificil = view.findViewById(R.id.btnDificil);

        MaterialButton btnBom = view.findViewById(R.id.btnBom);

        MaterialButton btnFacil = view.findViewById(R.id.btnFacil);

        MaterialCardView cardFlashcard = view.findViewById(R.id.cardFlashcard);

        TextView txtInstrucao = view.findViewById(R.id.txtInstrucao);

        EstudarDeckViewModel viewModel = new ViewModelProvider(this).get(EstudarDeckViewModel.class);

        TextView txtNomeDeck = view.findViewById(R.id.txtNomeDeck);

        TextView txtConteudoFlashcard = view.findViewById(R.id.txtConteudoFlashcard);

        TextView txtProgresso = view.findViewById(R.id.txtProgresso);


        ImageButton btnVoltar = view.findViewById(R.id.btnVoltar);

        String deckId = requireArguments().getString("deckId");

        btnErrei.setOnClickListener(v ->
                viewModel.avaliarFlashcard(Rating.AGAIN)
        );

        btnDificil.setOnClickListener(v ->
                viewModel.avaliarFlashcard(Rating.HARD)
        );

        btnBom.setOnClickListener(v ->
                viewModel.avaliarFlashcard(Rating.GOOD)
        );

        btnFacil.setOnClickListener(v ->
                viewModel.avaliarFlashcard(Rating.EASY)
        );

        cardFlashcard.setOnClickListener(v -> {

            Flashcard flashcard = viewModel.getFlashcardAtual();

            if (flashcard == null || Boolean.TRUE.equals(viewModel.getSalvandoProgresso().getValue())) {
                return;
            }

            viewModel.alternarLado();

            if (viewModel.isMostrandoResposta()) {

                txtConteudoFlashcard.setText(
                        flashcard.getResposta()
                );

                txtInstrucao.setText("Como foi lembrar deste conteúdo?");

                layoutAvaliacoes.setVisibility(View.VISIBLE);

            } else {

                txtConteudoFlashcard.setText(flashcard.getPergunta());

                txtInstrucao.setText("Toque no card para ver a resposta");

                layoutAvaliacoes.setVisibility(View.GONE);
            }
        });

        if (deckId == null || deckId.isEmpty()) {
            Toast.makeText(
                    requireContext(),
                    "Deck inválido.",
                    Toast.LENGTH_SHORT
            ).show();

            NavHostFragment.findNavController(this).popBackStack();

            return;
        }

        btnVoltar.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .popBackStack()
        );

        viewModel.getDeck().observe(
                getViewLifecycleOwner(),
                deck -> {

                    if (deck != null) {
                        txtNomeDeck.setText(deck.getTitulo());
                    }
                }
        );

        viewModel.getSessaoConcluida().observe(
                getViewLifecycleOwner(),
                concluida -> {

                    if (!Boolean.TRUE.equals(concluida)) {
                        return;
                    }

                    ConclusaoEstudo.voltarAoDeck(this, viewModel.getResultadoOfensiva());
                }
        );

        viewModel.getFlashcards().observe(
                getViewLifecycleOwner(),
                flashcards -> {

                    if (flashcards == null || flashcards.isEmpty()) {

                        txtConteudoFlashcard.setText(
                                "Você não possui flashcards para revisar agora."
                        );

                        txtProgresso.setText("Revisões concluídas");

                        layoutAvaliacoes.setVisibility(View.GONE);
                        cardFlashcard.setEnabled(false);

                        return;
                    }

                    cardFlashcard.setEnabled(true);

                    Flashcard atual = viewModel.getFlashcardAtual();
                    if (atual == null) return;
                    boolean mostrandoResposta = viewModel.isMostrandoResposta();
                    txtConteudoFlashcard.setText(mostrandoResposta ? atual.getResposta() : atual.getPergunta());
                    txtInstrucao.setText(mostrandoResposta
                            ? "Como foi lembrar deste conteúdo?" : "Toque no card para ver a resposta");
                    layoutAvaliacoes.setVisibility(mostrandoResposta ? View.VISIBLE : View.GONE);
                    txtProgresso.setText((viewModel.getIndiceAtual() + 1) + " / " + flashcards.size());
                }
        );
        viewModel.getAvaliacaoConcluida().observe(
                getViewLifecycleOwner(),
                concluida -> {

                    if (!Boolean.TRUE.equals(concluida)) {
                        return;
                    }

                    viewModel.consumirAvaliacaoConcluida();

                    boolean avancou = viewModel.proximoFlashcard();

                    if (!avancou) {

                        return;
                    }

                    Flashcard flashcard = viewModel.getFlashcardAtual();

                    if (flashcard == null) {
                        return;
                    }

                    txtConteudoFlashcard.setText(flashcard.getPergunta());

                    txtInstrucao.setText("Toque no card para ver a resposta");

                    layoutAvaliacoes.setVisibility(View.GONE);

                    List<Flashcard> lista = viewModel.getFlashcards().getValue();

                    if (lista != null) {

                        txtProgresso.setText((viewModel.getIndiceAtual() + 1) + " / " + lista.size());
                    }
                }
        );

        viewModel.getSalvandoProgresso().observe(
                getViewLifecycleOwner(),
                salvando -> {

                    boolean habilitado =
                            !Boolean.TRUE.equals(salvando) && viewModel.getProgressoAtual().getValue() != null;

                    btnErrei.setEnabled(habilitado);
                    btnDificil.setEnabled(habilitado);
                    btnBom.setEnabled(habilitado);
                    btnFacil.setEnabled(habilitado);
                }
        );


        viewModel.getProgressoAtual().observe(getViewLifecycleOwner(), progresso -> {
            boolean habilitado = progresso != null
                    && !Boolean.TRUE.equals(viewModel.getSalvandoProgresso().getValue());
            btnErrei.setEnabled(habilitado);
            btnDificil.setEnabled(habilitado);
            btnBom.setEnabled(habilitado);
            btnFacil.setEnabled(habilitado);
        });
        viewModel.getErro().observe(getViewLifecycleOwner(), mensagem -> {
            if (mensagem != null) Toast.makeText(requireContext(), mensagem, Toast.LENGTH_LONG).show();
        });
        viewModel.carregarDeckSeNecessario(deckId);


    }
}