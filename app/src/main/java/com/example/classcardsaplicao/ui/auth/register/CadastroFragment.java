package com.example.classcardsaplicao.ui.auth.register;

import androidx.lifecycle.ViewModelProvider;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.example.classcardsaplicao.R;

public class CadastroFragment extends Fragment {

    private CadastroViewModel mViewModel;

    EditText inputNome;
    EditText inputEmail;
    EditText inputSenha;
    EditText inputConfirmarSenha;
    Button btnConcluir;

    public static CadastroFragment newInstance() {
        return new CadastroFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cadastro, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        CadastroViewModel cadastroViewModel = new ViewModelProvider(this)
                .get(CadastroViewModel.class);

         inputNome = view.findViewById(R.id.inputNome);
         inputEmail = view.findViewById(R.id.inputEmail);
         inputSenha = view.findViewById(R.id.inputSenha);
         inputConfirmarSenha = view.findViewById(R.id.inputConfirmarSenha);
         btnConcluir = view.findViewById(R.id.btnConcluir);


        btnConcluir.setOnClickListener(v ->{

            String nome = inputNome.getText().toString().trim();
            String email = inputEmail.getText().toString().trim();
            String senha = inputSenha.getText().toString();
            String confirmarSenha = inputConfirmarSenha.getText().toString();

            if (nome.isEmpty() || email.isEmpty() || senha.isEmpty() || confirmarSenha.isEmpty()) {
                Toast.makeText(requireContext(), "Preencha todos os campos", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!senha.equals(confirmarSenha)) {
                Toast.makeText(requireContext(), "As senhas não coincidem", Toast.LENGTH_SHORT).show();
                return;
            }

            cadastroViewModel.cadastrar(nome, email, senha);
        });

        Button btnVoltar = view.findViewById(R.id.btnVoltar);

        btnVoltar.setOnClickListener(v -> {
            NavHostFragment.findNavController(this).popBackStack();
        });

        cadastroViewModel.getCadastroSucesso().observe(
                getViewLifecycleOwner(),
                sucesso -> {
                    if (Boolean.TRUE.equals(sucesso)) {
                        Toast.makeText(
                                requireContext(),
                                "Cadastro realizado com sucesso",
                                Toast.LENGTH_SHORT
                        ).show();

                        NavHostFragment.findNavController(this)
                                .navigate(R.id.action_cadastroFragment_to_homeFragment);
                    }
                }
        );

        cadastroViewModel.getErro().observe(
                getViewLifecycleOwner(),
                mensagem -> {
                    if (mensagem != null) {
                        Toast.makeText(
                                requireContext(),
                                mensagem,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }



}