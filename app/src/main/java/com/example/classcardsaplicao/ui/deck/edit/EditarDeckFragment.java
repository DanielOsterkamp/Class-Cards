package com.example.classcardsaplicao.ui.deck.edit;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.model.Flashcard;
import com.example.classcardsaplicao.ui.deck.create.FlashcardAdapter;
import com.example.classcardsaplicao.ui.deck.FlashcardDialogHelper;
import com.google.android.material.button.MaterialButton;

import java.util.Objects;

public class EditarDeckFragment extends Fragment {


    public EditarDeckFragment() {
        super(R.layout.fragment_editar_deck);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialButton btnSalvarAlteracoes = view.findViewById(R.id.btnSalvarDeck);

        EditText inputPergunta = view.findViewById(R.id.inputPergunta);
        EditText inputResposta = view.findViewById(R.id.inputResposta);

        MaterialButton btnCriarFlashcard = view.findViewById(R.id.btnCriarFlashcard);

        EditarDeckViewModel viewModel = new ViewModelProvider(this).get(EditarDeckViewModel.class);

        EditText inputNomeDeck = view.findViewById(R.id.inputNomeDeck);

        EditText inputMateriaDeck = view.findViewById(R.id.inputMateriaDeck);

        RecyclerView recyclerFlashcards = view.findViewById(R.id.recyclerFlashcards);

        FlashcardAdapter flashcardAdapter = new FlashcardAdapter();

        recyclerFlashcards.setLayoutManager(new LinearLayoutManager(requireContext()));

        recyclerFlashcards.setAdapter(flashcardAdapter);

        String deckId = requireArguments().getString("deckId");

        if (deckId == null) {
            return;
        }

        viewModel.getDeck().observe(
                getViewLifecycleOwner(),
                deck -> {
                    inputNomeDeck.setText(deck.getTitulo());
                    inputMateriaDeck.setText(deck.getMateria());
                }
        );

        btnCriarFlashcard.setOnClickListener(v -> {

            String pergunta = inputPergunta.getText().toString().trim();
            String resposta = inputResposta.getText().toString().trim();

            if (pergunta.isEmpty()) {
                inputPergunta.setError("Digite uma pergunta.");
                return;
            }

            if (resposta.isEmpty()) {
                inputResposta.setError("Digite uma resposta.");
                return;
            }

            viewModel.adicionarFlashcard(pergunta, resposta);

            inputPergunta.setText("");
            inputResposta.setText("");
        });

        btnSalvarAlteracoes.setOnClickListener(v -> {

            String titulo = inputNomeDeck.getText().toString().trim();

            String materia = inputMateriaDeck.getText().toString().trim();

            btnSalvarAlteracoes.setEnabled(false);

            viewModel.salvarAlteracoes(titulo, materia);
        });

        flashcardAdapter.setOnEditarFlashcardListener(
                (position, flashcard) ->
                        FlashcardDialogHelper.abrirDialogEditar(
                                requireContext(),
                                flashcard,
                                (pergunta, resposta) ->
                                        viewModel.editarFlashcard(
                                                position,
                                                pergunta,
                                                resposta
                                        )
                        )
        );

        viewModel.getSalvo().observe(
                getViewLifecycleOwner(),
                salvo -> {

                    if (!Boolean.TRUE.equals(salvo)) {
                        return;
                    }

                    btnSalvarAlteracoes.setEnabled(true);

                    Toast.makeText(
                            requireContext(),
                            "Deck atualizado com sucesso.",
                            Toast.LENGTH_SHORT
                    ).show();

                    NavHostFragment.findNavController(this)
                            .popBackStack();
                }
        );

        viewModel.getErro().observe(
                getViewLifecycleOwner(),
                mensagem -> {

                    if (mensagem == null) {
                        return;
                    }

                    btnSalvarAlteracoes.setEnabled(true);

                    Toast.makeText(
                            requireContext(),
                            mensagem,
                            Toast.LENGTH_SHORT
                    ).show();
                }
        );

        flashcardAdapter.setOnExcluirFlashcardListener(
                viewModel::removerFlashcard
        );

        viewModel.getFlashcards().observe(
                getViewLifecycleOwner(),
                flashcardAdapter::atualizarFlashcards
        );


        viewModel.carregarDeck(deckId);


    }

    private void abrirDialogEditarFlashcard(
            int position,
            Flashcard flashcard,
            EditarDeckViewModel viewModel
    ) {

        View dialogView = LayoutInflater
                .from(requireContext())
                .inflate(R.layout.dialog_editar_flashcard, null);

        EditText inputPergunta = dialogView.findViewById(R.id.inputEditarPergunta);

        EditText inputResposta = dialogView.findViewById(R.id.inputEditarResposta);

        inputPergunta.setText(flashcard.getPergunta());
        inputResposta.setText(flashcard.getResposta());

        MaterialButton btnCancelar = dialogView.findViewById(R.id.btnCancelarEdicao);

        MaterialButton btnSalvar = dialogView.findViewById(R.id.btnSalvarEdicao);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        btnCancelar.setOnClickListener(v -> dialog.dismiss());

        btnSalvar.setOnClickListener(v -> {

            String pergunta = inputPergunta.getText().toString().trim();

            String resposta = inputResposta.getText().toString().trim();

            if (pergunta.isEmpty()) {
                inputPergunta.setError("Informe a pergunta.");
                return;
            }

            if (resposta.isEmpty()) {
                inputResposta.setError("Informe a resposta.");
                return;
            }

            viewModel.editarFlashcard(
                    position,
                    pergunta,
                    resposta
            );

            dialog.dismiss();
        });

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(
                    android.R.color.transparent
            );
        }

        Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawableResource(
                android.R.color.transparent
        );

        dialog.setOnShowListener(dialogInterface -> {

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {

                        String pergunta = inputPergunta.getText().toString().trim();

                        String resposta = inputResposta.getText().toString().trim();

                        if (pergunta.isEmpty()) {
                            inputPergunta.setError("Informe a pergunta.");
                            return;
                        }

                        if (resposta.isEmpty()) {
                            inputResposta.setError("Informe a resposta.");
                            return;
                        }

                        viewModel.editarFlashcard(
                                position,
                                pergunta,
                                resposta
                        );

                        dialog.dismiss();
                    });
        });

        dialog.show();
    }
}