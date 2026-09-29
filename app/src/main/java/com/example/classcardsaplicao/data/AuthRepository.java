package com.example.classcardsaplicao.data;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import java.lang.reflect.Executable;
import java.util.Objects;

public class AuthRepository {

    // esse codigo serve para a autenticação do usuario no firebase

    private final FirebaseAuth firebaseAuth;

    public AuthRepository() {
        firebaseAuth = FirebaseAuth.getInstance();
    }

    // no metodo cadastrar ele usa o metodo de criar usuario com email e senha, se der certo ele devolve o ID do usuario que foi criado
    public void cadastrar(String email, String senha, AuthCallback callback){
        firebaseAuth.createUserWithEmailAndPassword(email,senha).addOnSuccessListener(authResult -> {
            String uid = Objects.requireNonNull(authResult.getUser()).getUid();
            callback.onSuccess(uid);
        }).addOnFailureListener(e -> {
            callback.onError(e);
        });
    }

    // no login ele faz algo parecido, usa o de logar e retorna o id de quem logou
    public void login(String email, String senha, AuthCallback callback) {
        firebaseAuth.signInWithEmailAndPassword(email, senha)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    callback.onSuccess(uid);
                })
                .addOnFailureListener(callback::onError);
        // esses :: servem pra quando der erro, ele usa o metodo on error do callback
    }

    public boolean estaLogado() {
        return firebaseAuth.getCurrentUser() != null;
    }

    public void logout() {
        firebaseAuth.signOut();
    }

    public interface AuthCallback{
        void onSuccess(String uid);
        void onError(Exception exception);
    }

    public FirebaseUser getUsuarioAtual() {
        return firebaseAuth.getCurrentUser();
    }

    public void loginComGoogle(String idToken, AuthCallback callback) {

        // aqui pega as credenciais do usuario por meio do id dele

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);

        firebaseAuth
                .signInWithCredential(credential)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    callback.onSuccess(uid);
                })
                .addOnFailureListener(callback::onError);
    }




}
