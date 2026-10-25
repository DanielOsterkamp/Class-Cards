package com.example.classcardsaplicao.data;

import androidx.annotation.NonNull;

import com.example.classcardsaplicao.model.Usuario;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class UserRepository {

    private final FirebaseFirestore firestore;

    public UserRepository() {
        firestore = FirebaseFirestore.getInstance();
    }

    public void criarUsuario(String uid, String nome, String email, @NonNull UserCallback callback) {

        Usuario usuario = new Usuario(uid, nome, email, null, System.currentTimeMillis());

        firestore.collection("users")
                .document(uid)
                .set(usuario)
                .addOnSuccessListener(unused -> {
                    RankingRepository.atualizarEmSegundoPlano();
                    callback.onSuccess();
                })
                .addOnFailureListener(callback::onError);
    }

    public interface UserCallback {
        void onSuccess();

        void onError(Exception exception);
    }

    public void criarUsuarioSeNaoExistir(String uid, String nome, String email, UserCallback callback) {
        firestore.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {

                    if (documentSnapshot.exists()) {
                        callback.onSuccess();
                        return;
                    }

                    criarUsuario(uid, nome, email, callback);
                })
                .addOnFailureListener(callback::onError);
    }

    public void buscarUsuario(String uid, BuscarUsuarioCallback callback) {
        firestore.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String nome = documentSnapshot.getString("nome");
                        String email = documentSnapshot.getString("email");
                        String fotoUrl = documentSnapshot.getString("fotoUrl");

                        callback.onSuccess(nome, email, fotoUrl);
                    } else {
                        callback.onError(
                                new Exception("Usuário não encontrado.")
                        );
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    public interface BuscarUsuarioCallback {
        void onSuccess(String nome, String email, String fotoUrl);
        void onError(Exception exception);
    }
}
