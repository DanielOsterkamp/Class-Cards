package com.example.classcardsaplicao.data.moodle;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Guarda a sessão do Moodle no celular: o token (criptografado com uma chave do Android Keystore,
 * que não sai do aparelho) e a lista de cursos com o papel em cada um.
 *
 * A sessão fica ligada à conta Firebase que fez o login pelo Moodle: se outra conta entrar no app,
 * ela não "herda" o Moodle da anterior.
 */
public final class MoodleSessao {

    private static final String TAG = "MOODLE";
    private static final String PREFS = "moodle_sessao";
    private static final String CHAVE_UID = "firebase_uid";
    private static final String CHAVE_TOKEN = "token_cifrado";
    private static final String CHAVE_CURSOS = "cursos";
    private static final String ALIAS = "classcards_moodle_token";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMACAO = "AES/GCM/NoPadding";

    private MoodleSessao() {
    }

    /** Salva a sessão depois de um login pelo Moodle que deu certo. */
    public static void salvar(Context context, String firebaseUid, String tokenMoodle, List<CursoMoodle> cursos) {
        try {
            JSONArray lista = new JSONArray();
            for (CursoMoodle curso : cursos) {
                lista.put(curso.paraJson());
            }
            prefs(context).edit()
                    .putString(CHAVE_UID, firebaseUid)
                    .putString(CHAVE_TOKEN, cifrar(tokenMoodle))
                    .putString(CHAVE_CURSOS, lista.toString())
                    .apply();
        } catch (Exception e) {
            // Sem a sessão salva, o login no Firebase continua valendo; só os recursos do Moodle ficam indisponíveis.
            Log.w(TAG, "Não foi possível salvar a sessão do Moodle: " + e.getClass().getSimpleName());
        }
    }

    /** O usuário Firebase atual entrou pelo Moodle? */
    public static boolean ativa(Context context, String firebaseUidAtual) {
        return firebaseUidAtual != null && firebaseUidAtual.equals(prefs(context).getString(CHAVE_UID, null))
                && prefs(context).contains(CHAVE_TOKEN);
    }

    /** Token do Moodle do usuário atual, ou null se ele não entrou pelo Moodle. */
    public static String token(Context context, String firebaseUidAtual) {
        if (!ativa(context, firebaseUidAtual)) {
            return null;
        }
        try {
            return decifrar(prefs(context).getString(CHAVE_TOKEN, null));
        } catch (Exception e) {
            Log.w(TAG, "Não foi possível ler o token do Moodle: " + e.getClass().getSimpleName());
            return null;
        }
    }

    /** Cursos do usuário atual (vazio se ele não entrou pelo Moodle). */
    public static List<CursoMoodle> cursos(Context context, String firebaseUidAtual) {
        if (!ativa(context, firebaseUidAtual)) {
            return Collections.emptyList();
        }
        List<CursoMoodle> cursos = new ArrayList<>();
        try {
            JSONArray lista = new JSONArray(prefs(context).getString(CHAVE_CURSOS, "[]"));
            for (int i = 0; i < lista.length(); i++) {
                cursos.add(CursoMoodle.deJson(lista.getJSONObject(i)));
            }
        } catch (JSONException e) {
            Log.w(TAG, "Lista de cursos do Moodle inválida");
        }
        return cursos;
    }

    /** É professor em pelo menos um curso? (define o modo professor da interface) */
    public static boolean professor(Context context, String firebaseUidAtual) {
        for (CursoMoodle curso : cursos(context, firebaseUidAtual)) {
            if (curso.isProfessor()) {
                return true;
            }
        }
        return false;
    }

    /** Apaga a sessão do Moodle deste aparelho. */
    public static void limpar(Context context) {
        prefs(context).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static SecretKey chave() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        if (ks.containsAlias(ALIAS)) {
            return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
        }
        KeyGenerator gerador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        gerador.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return gerador.generateKey();
    }

    private static String cifrar(String texto) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMACAO);
        cipher.init(Cipher.ENCRYPT_MODE, chave());
        byte[] cifrado = cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":"
                + Base64.encodeToString(cifrado, Base64.NO_WRAP);
    }

    private static String decifrar(String guardado) throws Exception {
        if (guardado == null) {
            return null;
        }
        String[] partes = guardado.split(":", 2);
        Cipher cipher = Cipher.getInstance(TRANSFORMACAO);
        cipher.init(Cipher.DECRYPT_MODE, chave(), new GCMParameterSpec(128, Base64.decode(partes[0], Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(partes[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }
}
