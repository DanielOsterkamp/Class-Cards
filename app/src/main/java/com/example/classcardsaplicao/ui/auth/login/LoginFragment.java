package com.example.classcardsaplicao.ui.auth.login;

import androidx.core.content.ContextCompat;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.lifecycle.ViewModelProvider;

import androidx.credentials.CredentialManager;

import androidx.credentials.Credential;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
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
import android.widget.TextView;
import android.widget.Toast;



import com.example.classcardsaplicao.R;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

public class LoginFragment extends Fragment {

    // ao invés de usar activities igual a gente está acostumado em aula, fiz o projeto com fragments
    // o codigo do fragment serve para gerenciar a parte da interface e o ViewModel a parte logica

    private LoginViewModel mViewModel;

    // então aqui estamos pegando a parte logica do login
    CredentialManager credentialManager;


    EditText inputEmail;
    EditText inputSenha;
    Button btnEntrar;

    public static LoginFragment newInstance() {
        return new LoginFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mViewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        credentialManager = CredentialManager.create(requireContext());

        Button btnGoogle = view.findViewById(R.id.btnGoogle);

        inputEmail = view.findViewById(R.id.inputEmailLogin);
        inputSenha = view.findViewById(R.id.inputSenhaLogin);
        btnEntrar = view.findViewById(R.id.btnEntrar);

        // evento de click do botao de entrar
        btnEntrar.setOnClickListener(v -> {
            String email = inputEmail.getText().toString().trim();
            String senha = inputSenha.getText().toString();

            if (email.isEmpty() || senha.isEmpty()) {
                Toast.makeText(
                        requireContext(),
                        "Preencha todos os campos",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            mViewModel.login(email, senha);
        });


        // evento de click do botao de entrar com o google
        btnGoogle.setOnClickListener(v -> loginGoogle());

        mViewModel.getLoginSucesso().observe(
                getViewLifecycleOwner(),
                sucesso -> {
                    if (Boolean.TRUE.equals(sucesso)) {
                        Toast.makeText(
                                requireContext(),
                                "Login realizado com sucesso",
                                Toast.LENGTH_SHORT
                        ).show();

                        NavHostFragment.findNavController(this)
                                .navigate(R.id.action_loginFragment_to_homeFragment);
                    }
                }
        );

        mViewModel.getErro().observe(
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



        TextView cadastrar = view.findViewById(R.id.cadastrar);

        cadastrar.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_loginFragment_to_cadastroFragment);
        });
    }

    // em baixo é o codigo necessário pra api de logar com o google
    private void loginGoogle() {

        GetGoogleIdOption googleIdOption =
                new GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(getString(R.string.default_web_client_id))
                        .build();

        GetCredentialRequest request =
                new GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build();

        credentialManager.getCredentialAsync(
                requireContext(),
                request,
                null,
                ContextCompat.getMainExecutor(requireContext()),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {

                    @Override
                    public void onResult(GetCredentialResponse result) {
                        tratarCredencialGoogle(result.getCredential());
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        Toast.makeText(
                                requireContext(),
                                e.getClass().getSimpleName() + ": " + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();

                        android.util.Log.e("GOOGLE_LOGIN", "Erro no Credential Manager", e);
                    }
                }
        );
    }

    private void tratarCredencialGoogle(Credential credential) {

        if (credential instanceof CustomCredential) {

            CustomCredential customCredential = (CustomCredential) credential;

            if (GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                    .equals(customCredential.getType())) {

                GoogleIdTokenCredential googleCredential =
                        GoogleIdTokenCredential.createFrom(
                                customCredential.getData()
                        );

                String idToken = googleCredential.getIdToken();

                mViewModel.loginComGoogle(idToken);
            }
        }
    }

}

