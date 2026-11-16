package com.example.classcardsaplicao.data.moodle;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;
import android.widget.Toast;

import androidx.browser.customtabs.CustomTabsIntent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Login pelo navegador, do jeito que o Moodle exige (admin/tool/mobile/launch.php):
 * o app nunca vê a senha, só recebe um token em classcards://token=BASE64.
 *
 * BASE64 decodificado = md5(wwwroot + passport) ::: token [::: private token]
 */
public final class MoodleLogin {

    private static final String PREFS = "moodle_login";
    private static final String CHAVE_PASSPORT = "passport";
    private static final String CHAVE_CRIADO_EM = "passport_criado_em";
    /** O Moodle aceita o login por 15 minutos depois de abrir o launch.php. */
    private static final long VALIDADE_PASSPORT_MS = 15 * 60 * 1000L;
    private static final String SEPARADOR = ":::";

    private MoodleLogin() {
    }

    /** Gera um passport novo, guarda e abre a página de login do Moodle. */
    public static void iniciar(Activity activity) {
        String passport = gerarPassport();
        prefs(activity).edit()
                .putString(CHAVE_PASSPORT, passport)
                .putLong(CHAVE_CRIADO_EM, System.currentTimeMillis())
                .apply();

        Uri uri = Uri.parse(MoodleConfig.url(activity) + "/admin/tool/mobile/launch.php")
                .buildUpon()
                .appendQueryParameter("service", "moodle_mobile_app")
                .appendQueryParameter("passport", passport)
                .appendQueryParameter("urlscheme", MoodleConfig.urlScheme(activity))
                .build();

        try {
            new CustomTabsIntent.Builder().build().launchUrl(activity, uri);
        } catch (ActivityNotFoundException semCustomTabs) {
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException semNavegador) {
                Toast.makeText(activity, "Nenhum navegador encontrado para abrir o login do Moodle.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Confere o retorno classcards://token=... e devolve o token do Moodle.
     * O passport só vale uma vez: depois desta chamada ele é apagado, dando certo ou não.
     */
    public static String validarRetorno(Context context, Uri retorno) throws MoodleException {
        SharedPreferences prefs = prefs(context);
        String passport = prefs.getString(CHAVE_PASSPORT, null);
        long criadoEm = prefs.getLong(CHAVE_CRIADO_EM, 0);
        prefs.edit().remove(CHAVE_PASSPORT).remove(CHAVE_CRIADO_EM).apply();

        if (passport == null) {
            throw new MoodleException("Este retorno do Moodle não foi pedido por este app. Tente entrar de novo.",
                    "sem_passport");
        }
        if (System.currentTimeMillis() - criadoEm > VALIDADE_PASSPORT_MS) {
            throw new MoodleException("O login no Moodle demorou demais. Tente entrar de novo.", "passport_expirado");
        }
        if (retorno == null) {
            throw new MoodleException("O Moodle não devolveu os dados do login.", "sem_dados");
        }

        // O endereço chega como classcards://token=BASE64 (às vezes com "=" trocado por %3D).
        String texto = Uri.decode(retorno.toString());
        int inicio = texto.indexOf("token=");
        if (inicio < 0) {
            throw new MoodleException("O Moodle não devolveu um token.", "sem_token");
        }
        String base64 = texto.substring(inicio + "token=".length()).trim();

        String decodificado;
        try {
            decodificado = new String(Base64.decode(base64, Base64.DEFAULT), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new MoodleException("A resposta do Moodle veio em um formato inesperado.", "base64_invalido");
        }

        String[] partes = decodificado.split(SEPARADOR);
        if (partes.length < 2 || partes[1].trim().isEmpty()) {
            throw new MoodleException("A resposta do Moodle veio incompleta.", "partes_faltando");
        }

        // Mesma conferência do script decodificar-token.sh: a parte 1 tem que ser md5(wwwroot + passport).
        String esperado = md5(MoodleConfig.url(context) + passport);
        if (!MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8),
                partes[0].trim().getBytes(StandardCharsets.UTF_8))) {
            throw new MoodleException("A resposta do Moodle não confere com o login iniciado. Tente entrar de novo.",
                    "passport_nao_confere");
        }
        return partes[1].trim();
    }

    private static String gerarPassport() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return hex(bytes);
    }

    static String md5(String texto) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return hex(md.digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
