package com.example.classcardsaplicao.ui.deck.details;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.classcardsaplicao.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;

public class DeckSelecionadoFragment extends Fragment {
    private FirebaseAuth.AuthStateListener listenerAuth;

    public DeckSelecionadoFragment() { super(R.layout.fragment_deck_selecionado); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        DeckSelecionadoViewModel viewModel = new ViewModelProvider(this).get(DeckSelecionadoViewModel.class);
        String deckId = requireArguments().getString("deckId");
        view.findViewById(R.id.btnVoltar).setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

        OnBackPressedCallback aguardarExclusao = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() {
                Toast.makeText(requireContext(), R.string.deck_aguardar_exclusao, Toast.LENGTH_SHORT).show();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), aguardarExclusao);

        Runnable atualizarAcoes = () -> {
            boolean ocupado = Boolean.TRUE.equals(viewModel.getExcluindo().getValue());
            aguardarExclusao.setEnabled(ocupado);
            boolean dono = viewModel.usuarioEhDono();
            boolean pendente = viewModel.getDeck().getValue() != null && viewModel.getDeck().getValue().isExcluindo();
            view.findViewById(R.id.btnEditarDeck).setVisibility(dono && !pendente ? View.VISIBLE : View.GONE);
            view.findViewById(R.id.btnExcluirDeck).setVisibility(dono ? View.VISIBLE : View.GONE);
            view.findViewById(R.id.btnEstudar).setEnabled(viewModel.getDeck().getValue() != null && !ocupado && !pendente);
            view.findViewById(R.id.btnEditarDeck).setEnabled(!ocupado);
            view.findViewById(R.id.btnExcluirDeck).setEnabled(!ocupado);
            view.findViewById(R.id.btnVoltar).setEnabled(!ocupado);
            view.findViewById(R.id.containerExclusaoDeck).setVisibility(ocupado ? View.VISIBLE : View.GONE);
            view.findViewById(R.id.txtAvisoExclusaoDeck).setVisibility(pendente && !ocupado ? View.VISIBLE : View.GONE);
        };
        viewModel.getDeck().observe(getViewLifecycleOwner(), deck -> {
            if (deck != null) {
                ((TextView) view.findViewById(R.id.txtNomeDeck)).setText(deck.getTitulo());
                ((TextView) view.findViewById(R.id.txtMateriaDeck)).setText(deck.getMateria());
                ((TextView) view.findViewById(R.id.txtQuantidadeCards)).setText(getResources().getQuantityString(
                        R.plurals.pesquisa_quantidade_cards, deck.getQuantidadeCards(), deck.getQuantidadeCards()));
            }
            atualizarAcoes.run();
        });
        viewModel.getNomeCriador().observe(getViewLifecycleOwner(), nome ->
                ((TextView) view.findViewById(R.id.txtCriadorDeck)).setText(getString(R.string.deck_criador, nome)));
        viewModel.getExcluindo().observe(getViewLifecycleOwner(), valor -> atualizarAcoes.run());
        listenerAuth = auth -> atualizarAcoes.run();
        FirebaseAuth.getInstance().addAuthStateListener(listenerAuth);

        view.findViewById(R.id.btnEstudar).setOnClickListener(v -> {
            Bundle argumentos = new Bundle();
            argumentos.putString("deckId", deckId);
            NavHostFragment.findNavController(this).navigate(
                    R.id.action_deckSelecionadoFragment_to_SelecionarModoDeEstudo, argumentos);
        });
        view.findViewById(R.id.btnEditarDeck).setOnClickListener(v -> {
            if (!viewModel.usuarioEhDono()) return;
            Bundle argumentos = new Bundle();
            argumentos.putString("deckId", deckId);
            NavHostFragment.findNavController(this).navigate(
                    R.id.action_deckSelecionadoFragment_to_editarDeckFragment, argumentos);
        });
        view.findViewById(R.id.btnExcluirDeck).setOnClickListener(v -> {
            if (!viewModel.usuarioEhDono() || Boolean.TRUE.equals(viewModel.getExcluindo().getValue())) return;
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.deck_excluir_titulo)
                    .setMessage(getString(R.string.deck_excluir_confirmacao, viewModel.getDeck().getValue().getTitulo()))
                    .setNegativeButton(R.string.deck_cancelar, null)
                    .setPositiveButton(R.string.deck_excluir, (dialog, botao) -> viewModel.excluirDeck())
                    .show();
        });
        viewModel.getErro().observe(getViewLifecycleOwner(), erro -> {
            if (erro != null) Toast.makeText(requireContext(), erro, Toast.LENGTH_LONG).show();
        });
        viewModel.getExcluido().observe(getViewLifecycleOwner(), id -> {
            if (id == null) return;
            NavController nav = NavHostFragment.findNavController(this);
            if (nav.getCurrentDestination() == null || nav.getCurrentDestination().getId() != R.id.deckSelecionadoFragment) return;
            // A pesquisa remove o item também do cache ao receber esse resultado.
            if (nav.getPreviousBackStackEntry() != null) {
                nav.getPreviousBackStackEntry().getSavedStateHandle().set("deckExcluidoId", id);
            }
            Toast.makeText(requireContext(), R.string.deck_excluido, Toast.LENGTH_SHORT).show();
            if (!nav.popBackStack()) nav.navigate(R.id.homeFragment);
        });
        atualizarAcoes.run();
    }

    @Override public void onResume() {
        super.onResume();
        // Recarrega título e quantidade ao retornar da edição.
        new ViewModelProvider(this).get(DeckSelecionadoViewModel.class)
                .carregarDeck(requireArguments().getString("deckId"));
    }

    @Override public void onDestroyView() {
        if (listenerAuth != null) FirebaseAuth.getInstance().removeAuthStateListener(listenerAuth);
        listenerAuth = null;
        super.onDestroyView();
    }
}
