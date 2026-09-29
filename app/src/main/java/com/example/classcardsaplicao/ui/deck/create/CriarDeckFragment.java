package com.example.classcardsaplicao.ui.deck.create;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.model.Flashcard;
import com.example.classcardsaplicao.ui.deck.FlashcardAdapter;
import com.example.classcardsaplicao.ui.deck.FlashcardDialogHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Objects;

public class CriarDeckFragment extends Fragment {

    public CriarDeckFragment() {
        super(R.layout.fragment_criar_deck);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        EditText inputNumeroCards = view.findViewById(R.id.inputNumeroCards);
        EditText inputDescricaoIA = view.findViewById(R.id.inputDescricaoIA);

        MaterialButton btnGerarFlashcards =
                view.findViewById(R.id.btnGerarFlashcards);

        EditText inputNomeDeck = view.findViewById(R.id.inputNomeDeck);
        EditText inputMateriaDeck = view.findViewById(R.id.inputMateriaDeck);

        MaterialButton btnSalvarDeck =
                view.findViewById(R.id.btnSalvarDeck);

        CriarDeckViewModel viewModel =
                new ViewModelProvider(this).get(CriarDeckViewModel.class);

        ImageButton btnMenu = view.findViewById(R.id.btnMenu);

        SwitchMaterial switchModoCriacao =
                view.findViewById(R.id.switchModoCriacao);

        LinearLayout containerManual =
                view.findViewById(R.id.containerManual);

        LinearLayout containerAutomatico =
                view.findViewById(R.id.containerAutomatico);

        TextView txtManual =
                view.findViewById(R.id.txtManual);

        TextView txtAutomatico =
                view.findViewById(R.id.txtAutomatico);

        EditText inputPergunta = view.findViewById(R.id.inputPergunta);
        EditText inputResposta = view.findViewById(R.id.inputResposta);

        MaterialButton btnCriarFlashcard =
                view.findViewById(R.id.btnCriarFlashcard);

        RecyclerView recyclerFlashcards =
                view.findViewById(R.id.recyclerFlashcards);

        FlashcardAdapter flashcardAdapter = new FlashcardAdapter();

        recyclerFlashcards.setLayoutManager(
                new LinearLayoutManager(requireContext())
        );

        recyclerFlashcards.setAdapter(flashcardAdapter);

        viewModel.getFlashcards().observe(
                getViewLifecycleOwner(),
                flashcards -> flashcardAdapter.atualizarFlashcards(flashcards)
        );

        viewModel.getErro().observe(
                getViewLifecycleOwner(),
                mensagem -> {

                    if (mensagem == null) {
                        return;
                    }

                    btnSalvarDeck.setEnabled(true);

                    Toast.makeText(
                            requireContext(),
                            mensagem,
                            Toast.LENGTH_SHORT
                    ).show();
                }
        );

        viewModel.getDeckCriado().observe(
                getViewLifecycleOwner(),
                deckId -> {

                    if (deckId == null) {
                        return;
                    }

                    btnSalvarDeck.setEnabled(true);

                    Toast.makeText(
                            requireContext(),
                            "Deck criado com sucesso.",
                            Toast.LENGTH_SHORT
                    ).show();

                    NavHostFragment.findNavController(this)
                            .navigate(
                                    R.id.action_criarDeckFragment_to_homeFragment
                            );
                }
        );

        flashcardAdapter.setOnExcluirFlashcardListener(
                viewModel::removerFlashcard
        );

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


        // Menu lateral
        btnMenu.setOnClickListener(v -> {
            DrawerLayout drawerLayout =
                    requireActivity().findViewById(R.id.drawerLayout);

            drawerLayout.openDrawer(GravityCompat.START);
        });

        btnGerarFlashcards.setOnClickListener(v -> {

            String materia =
                    inputMateriaDeck.getText().toString().trim();

            String descricao =
                    inputDescricaoIA.getText().toString().trim();

            String quantidade =
                    inputNumeroCards.getText().toString().trim();

            viewModel.gerarFlashcards(
                    materia,
                    descricao,
                    quantidade
            );
        });



        btnSalvarDeck.setOnClickListener(v -> {

            FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();

            if (usuario == null) {
                Toast.makeText(
                        requireContext(),
                        "Usuário não autenticado.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            String nome = inputNomeDeck.getText().toString().trim();
            String materia = inputMateriaDeck.getText().toString().trim();

            btnSalvarDeck.setEnabled(false);

            viewModel.salvarDeck(
                    usuario.getUid(),
                    nome,
                    materia
            );
        });

        viewModel.getGerandoFlashcards().observe(
                getViewLifecycleOwner(),
                gerando -> {

                    btnGerarFlashcards.setEnabled(!gerando);

                    btnGerarFlashcards.setText(
                            gerando
                                    ? "Gerando..."
                                    : "Gerar Flash Cards"
                    );
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

            Toast.makeText(
                    requireContext(),
                    "Flashcard criado.",
                    Toast.LENGTH_SHORT
            ).show();
        });



        // Alternar Manual / Automático
        switchModoCriacao.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {

                    if (isChecked) {

                        // Automático
                        containerManual.setVisibility(View.GONE);
                        containerAutomatico.setVisibility(View.VISIBLE);

                        txtManual.setTextColor(
                                getResources().getColor(
                                        R.color.text_secondary,
                                        null
                                )
                        );

                        txtAutomatico.setTextColor(
                                getResources().getColor(
                                        R.color.text_primary,
                                        null
                                )
                        );

                    } else {

                        // Manual
                        containerManual.setVisibility(View.VISIBLE);
                        containerAutomatico.setVisibility(View.GONE);

                        txtManual.setTextColor(
                                getResources().getColor(
                                        R.color.text_primary,
                                        null
                                )
                        );

                        txtAutomatico.setTextColor(
                                getResources().getColor(
                                        R.color.text_secondary,
                                        null
                                )
                        );
                    }
                }
        );
    }

    private void abrirDialogEditarFlashcard(
            int position,
            Flashcard flashcard,
            CriarDeckViewModel viewModel
    ) {

        View dialogView = LayoutInflater
                .from(requireContext())
                .inflate(R.layout.dialog_editar_flashcard, null);

        EditText inputPergunta =
                dialogView.findViewById(R.id.inputEditarPergunta);

        EditText inputResposta =
                dialogView.findViewById(R.id.inputEditarResposta);

        inputPergunta.setText(flashcard.getPergunta());
        inputResposta.setText(flashcard.getResposta());

        MaterialButton btnCancelar =
                dialogView.findViewById(R.id.btnCancelarEdicao);

        MaterialButton btnSalvar =
                dialogView.findViewById(R.id.btnSalvarEdicao);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
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