package com.example.classcardsaplicao.data;

import com.example.classcardsaplicao.model.ResultadoDeckPesquisa;
import com.example.classcardsaplicao.model.ResultadoUsuarioPesquisa;
import java.util.List;

/** O cursor é opaco para a tela: somente a implementação conhece o documento do Firestore. */
public interface PesquisaFonte {
    void buscarDecks(String termo, Object cursor, Callback<ResultadoDeckPesquisa> callback);
    void buscarUsuarios(String termo, Object cursor, Callback<ResultadoUsuarioPesquisa> callback);

    interface Callback<T> {
        void onSuccess(List<T> itens, Object proximoCursor, boolean temMais);
        void onError(Exception erro);
    }
}
