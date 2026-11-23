package com.example.classcardsaplicao.data;

import android.net.Uri;
import java.io.File;

/** Contrato substituível nos testes: não exige galeria, conta real ou upload real. */
public interface FotoPerfilFonte {
    interface Callback<T> { void sucesso(T valor); void erro(Exception erro); }
    String uidAtual();
    void carregar(String uid, Callback<String> callback);
    void preparar(Uri origem, Callback<File> callback);
    void salvar(String uid, File imagem, Callback<String> callback);
    void cancelar();
    void encerrar();
}
