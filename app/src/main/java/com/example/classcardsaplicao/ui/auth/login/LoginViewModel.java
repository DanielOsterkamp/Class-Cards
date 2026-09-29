package com.example.classcardsaplicao.ui.auth.login;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.classcardsaplicao.data.AuthRepository;
import com.example.classcardsaplicao.data.UserRepository;
import com.google.firebase.auth.FirebaseUser;

public class LoginViewModel extends ViewModel {


    // aqui a gente pega os repositorios de autenticação e de usuarios para usar os seus metodos
    private final AuthRepository authRepository;

    private final UserRepository userRepository;

    private final MutableLiveData<Boolean> carregando = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> loginSucesso = new MutableLiveData<>(false);
    private final MutableLiveData<String> erro = new MutableLiveData<>();

    public LoginViewModel() {
        authRepository = new AuthRepository();
        userRepository = new UserRepository();
    }


    public LiveData<Boolean> getCarregando() {
        return carregando;
    }

    public LiveData<Boolean> getLoginSucesso() {
        return loginSucesso;
    }

    public LiveData<String> getErro() {
        return erro;
    }

    public void loginComGoogle(String idToken) {
        carregando.setValue(true);

        authRepository.loginComGoogle(idToken, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(String uid) {

                FirebaseUser usuario = authRepository.getUsuarioAtual();

                if (usuario == null) {
                    carregando.setValue(false);
                    erro.setValue("Não foi possível obter os dados do usuário.");
                    return;
                }

                String nome = usuario.getDisplayName();
                String email = usuario.getEmail();

                userRepository.criarUsuarioSeNaoExistir(
                        uid,
                        nome,
                        email,
                        new UserRepository.UserCallback() {
                            @Override
                            public void onSuccess() {
                                carregando.setValue(false);
                                loginSucesso.setValue(true);
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



    public void login(String email, String senha) {
        carregando.setValue(true);

        authRepository.login(email, senha, new AuthRepository.AuthCallback() {

            @Override
            public void onSuccess(String uid) {
                carregando.setValue(false);
                loginSucesso.setValue(true);
            }

            @Override
            public void onError(Exception exception) {
                carregando.setValue(false);
                erro.setValue(exception.getMessage());
            }
        });
    }
}