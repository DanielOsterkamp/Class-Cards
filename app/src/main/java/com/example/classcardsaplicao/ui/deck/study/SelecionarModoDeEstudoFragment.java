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
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mViewModel = new ViewModelProvider(this).get(SelecionarModoDeEstudoViewModel.class);
        // TODO: Use the ViewModel
    }

}