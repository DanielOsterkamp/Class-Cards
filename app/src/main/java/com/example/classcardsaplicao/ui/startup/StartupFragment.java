package com.example.classcardsaplicao.ui.startup;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.data.AuthRepository;

public class StartupFragment extends Fragment {

    public StartupFragment() {
        super(R.layout.fragment_startup);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        AuthRepository authRepository = new AuthRepository();

        if (authRepository.estaLogado()) {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_startupFragment_to_homeFragment);
        } else {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_startupFragment_to_loginFragment);
        }
    }
}