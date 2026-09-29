package com.example.classcardsaplicao.ui.auth.register;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.classcardsaplicao.data.AuthRepository;
import com.example.classcardsaplicao.data.UserRepository;

public class CadastroViewModel extends ViewModel {

    private final AuthRepository authRepository;
    private final UserRepository userRepository;

    private final MutableLiveData<Boolean> carregando = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> cadastroSucesso = new MutableLiveData<>(false);
    private final MutableLiveData<String> erro = new MutableLiveData<>();

    public CadastroViewModel() {
        authRepository = new AuthRepository();
        userRepository = new UserRepository();
    }

    public LiveData<Boolean> getCarregando() {
        return carregando;
    }

    public LiveData<Boolean> getCadastroSucesso() {
        return cadastroSucesso;
    }

    public LiveData<String> getErro() {
        return erro;
    }

    public void cadastrar(String nome, String email, String senha) {
        carregando.setValue(true);

        authRepository.cadastrar(email, senha, new AuthRepository.AuthCallback() {

            @Override
            public void onSuccess(String uid) {

                userRepository.criarUsuario(
                        uid,
                        nome,
                        email,
                        new UserRepository.UserCallback() {

                            @Override
                            public void onSuccess() {
                                carregando.setValue(false);
                                cadastroSucesso.setValue(true);
                            }

                            @Override
                            public void onError(Exception exception) {
                                carregando.setValue(false);
                                erro.setValue(exception.getMessage());
                            }
                        }
                );
            }

            @Override
            public void onError(Exception exception) {
                carregando.setValue(false);
                erro.setValue(exception.getMessage());
            }
        });
    }
}