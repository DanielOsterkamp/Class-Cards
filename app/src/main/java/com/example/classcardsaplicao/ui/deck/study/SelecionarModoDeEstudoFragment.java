package com.example.classcardsaplicao.ui.deck.study;

import androidx.lifecycle.ViewModelProvider;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.classcardsaplicao.R;

public class SelecionarModoDeEstudoFragment extends Fragment {

    private SelecionarModoDeEstudoViewModel mViewModel;

    public static SelecionarModoDeEstudoFragment newInstance() {
        return new SelecionarModoDeEstudoFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_selecionar_modo_de_estudo, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String deckId = getArguments() == null ? null : getArguments().getString("deckId");
        if (deckId == null || deckId.trim().isEmpty()) {
            android.widget.Toast.makeText(requireContext(), "Deck inválido.",
                    android.widget.Toast.LENGTH_SHORT).show();
            androidx.navigation.fragment.NavHostFragment.findNavController(this).popBackStack();
            return;
        }
        Bundle args = new Bundle();
        args.putString("deckId", deckId);
        view.findViewById(R.id.btnTradicional).setOnClickListener(v ->
                androidx.navigation.fragment.NavHostFragment.findNavController(this).navigate(
                        R.id.action_SelecionarModoDeEstudo_to_estudarDeckFragment, args));
        view.findViewById(R.id.btnDissertativo).setOnClickListener(v ->
                androidx.navigation.fragment.NavHostFragment.findNavController(this).navigate(
                        R.id.action_SelecionarModoDeEstudo_to_estudarDeckDissertativoFragment, args));
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mViewModel = new ViewModelProvider(this).get(SelecionarModoDeEstudoViewModel.class);
        // TODO: Use the ViewModel
    }

}