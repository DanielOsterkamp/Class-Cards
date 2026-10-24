package com.example.classcardsaplicao.ui.deck.details;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.data.AuthRepository;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseUser;

public class DeckSelecionadoFragment extends Fragment {

    public DeckSelecionadoFragment() {
        super(R.layout.fragment_deck_selecionado);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        ImageButton btnVoltar = view.findViewById(R.id.btnVoltar);

        Button btnEstudar = view.findViewById(R.id.btnEstudar);


        btnVoltar.setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());

        TextView txtNomeDeck = view.findViewById(R.id.txtNomeDeck);
        TextView txtMateriaDeck = view.findViewById(R.id.txtMateriaDeck);
        TextView txtQuantidadeCards = view.findViewById(R.id.txtQuantidadeCards);

        MaterialButton btnEditarDeck = view.findViewById(R.id.btnDissertativo);

        DeckSelecionadoViewModel viewModel = new ViewModelProvider(this).get(DeckSelecionadoViewModel.class);

        AuthRepository authRepository = new AuthRepository();

        String deckId = requireArguments().getString("deckId");

        viewModel.getDeck().observe(
                getViewLifecycleOwner(),
                deck -> {

                    txtNomeDeck.setText(deck.getTitulo());
                    txtMateriaDeck.setText(deck.getMateria());

                    int quantidade = deck.getQuantidadeCards();

                    txtQuantidadeCards.setText(
                            quantidade == 1
                                    ? "1 card"
                                    : quantidade + " cards"
                    );

                    FirebaseUser usuario =
                            authRepository.getUsuarioAtual();

                    boolean usuarioEhDono =
                            usuario != null
                                    && usuario.getUid().equals(deck.getOwnerId());

                    btnEditarDeck.setVisibility(
                            usuarioEhDono
                                    ? View.VISIBLE
                                    : View.GONE
                    );
                }
        );

        btnEstudar.setOnClickListener(v -> {

            Bundle bundle = new Bundle();
            bundle.putString("deckId", deckId);

            NavHostFragment.findNavController(this)
                    .navigate(
                            R.id.action_deckSelecionadoFragment_to_SelecionarModoDeEstudo,
                            bundle
                    );
        });

        btnEditarDeck.setOnClickListener(v -> {

            Bundle bundle = new Bundle();
            bundle.putString("deckId", deckId);

            NavHostFragment.findNavController(this)
                    .navigate(
                            R.id.action_deckSelecionadoFragment_to_editarDeckFragment,
                            bundle
                    );
        });

        viewModel.getErro().observe(
                getViewLifecycleOwner(),
                erro -> Toast.makeText(
                        requireContext(),
                        erro,
                        Toast.LENGTH_SHORT
                ).show()
        );



        viewModel.carregarDeck(deckId);
    }
}