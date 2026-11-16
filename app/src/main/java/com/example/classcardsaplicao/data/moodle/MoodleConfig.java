package com.example.classcardsaplicao.data.moodle;

import android.content.Context;

import com.example.classcardsaplicao.R;

/**
 * Configuração do módulo Moodle, lida de res/values/moodle_config.xml.
 * Na versão final o módulo fica desligado; no debug ele é ligado por src/debug/res/values/moodle_config.xml.
 */
public final class MoodleConfig {

    private MoodleConfig() {
    }

    /** O módulo só funciona se estiver ligado E tiver um endereço de Moodle configurado. */
    public static boolean habilitado(Context context) {
        return context.getResources().getBoolean(R.bool.moodle_habilitado) && !url(context).isEmpty();
    }

    /** Endereço do Moodle, igual ao wwwroot do site, sem barra no final (ex.: http://localhost:8000). */
    public static String url(Context context) {
        String url = context.getString(R.string.moodle_url).trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    /** Esquema de retorno configurado no Moodle (tool_mobile/forcedurlscheme). */
    public static String urlScheme(Context context) {
        return context.getString(R.string.moodle_urlscheme);
    }
}
