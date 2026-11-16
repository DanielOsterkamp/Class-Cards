package com.example.classcardsaplicao.data.moodle;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Chamadas à API REST do Moodle (/webservice/rest/server.php) com o token do usuário.
 * Usa só o que o Android já traz (HttpURLConnection + org.json). Os métodos BLOQUEIAM:
 * chame fora da thread principal. Tokens nunca vão para os logs.
 */
public class MoodleApi {

    private static final int TIMEOUT_MS = 20_000;

    private final String endpoint;

    public MoodleApi(Context context) {
        this.endpoint = MoodleConfig.url(context) + "/webservice/rest/server.php";
    }

    /** Resposta do plugin: o passe do Firebase e os dados do usuário. */
    public static class PasseFirebase {
        public final String customToken;
        public final String uid;
        public final long moodleUserId;
        public final String nome;
        public final String email;
        public final List<CursoMoodle> cursos;

        PasseFirebase(String customToken, String uid, long moodleUserId, String nome, String email,
                      List<CursoMoodle> cursos) {
            this.customToken = customToken;
            this.uid = uid;
            this.moodleUserId = moodleUserId;
            this.nome = nome;
            this.email = email;
            this.cursos = cursos;
        }
    }

    /** Pede ao plugin local_classcards o custom token do Firebase para o dono do token do Moodle. */
    public PasseFirebase obterPasseFirebase(String tokenMoodle) throws MoodleException {
        JSONObject json = objeto(chamar("local_classcards_get_firebase_token", tokenMoodle, null));
        try {
            List<CursoMoodle> cursos = new ArrayList<>();
            JSONArray lista = json.getJSONArray("cursos");
            for (int i = 0; i < lista.length(); i++) {
                cursos.add(CursoMoodle.deJson(lista.getJSONObject(i)));
            }
            return new PasseFirebase(
                    json.getString("token"),
                    json.getString("uid"),
                    json.getLong("userid"),
                    json.optString("fullname", ""),
                    json.optString("email", ""),
                    cursos);
        } catch (JSONException e) {
            throw new MoodleException("A resposta do Moodle veio em um formato inesperado.", "json_plugin", e);
        }
    }

    /** Faz um POST para uma função do Moodle e devolve o JSON (objeto ou lista). */
    Object chamar(String funcao, String tokenMoodle, Map<String, String> parametros) throws MoodleException {
        StringBuilder corpo = new StringBuilder();
        adicionar(corpo, "wstoken", tokenMoodle);
        adicionar(corpo, "wsfunction", funcao);
        adicionar(corpo, "moodlewsrestformat", "json");
        if (parametros != null) {
            for (Map.Entry<String, String> p : parametros.entrySet()) {
                adicionar(corpo, p.getKey(), p.getValue());
            }
        }

        HttpURLConnection conexao = null;
        try {
            conexao = (HttpURLConnection) new URL(endpoint).openConnection();
            conexao.setRequestMethod("POST");
            conexao.setConnectTimeout(TIMEOUT_MS);
            conexao.setReadTimeout(TIMEOUT_MS);
            conexao.setDoOutput(true);
            conexao.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            try (OutputStream saida = conexao.getOutputStream()) {
                saida.write(corpo.toString().getBytes(StandardCharsets.UTF_8));
            }
            int status = conexao.getResponseCode();
            InputStream entrada = status < 400 ? conexao.getInputStream() : conexao.getErrorStream();
            String texto = ler(entrada);
            if (status >= 400) {
                throw new MoodleException("O Moodle respondeu com erro (HTTP " + status + ").", "http_" + status);
            }
            Object json = new JSONTokener(texto).nextValue();
            if (json instanceof JSONObject && ((JSONObject) json).has("exception")) {
                JSONObject erro = (JSONObject) json;
                String codigo = erro.optString("errorcode", "erro_moodle");
                throw new MoodleException(mensagemAmigavel(codigo, erro.optString("message", "")), codigo);
            }
            return json;
        } catch (IOException e) {
            throw new MoodleException("Não foi possível conectar ao Moodle. Verifique a internet e tente de novo.",
                    "rede", e);
        } catch (JSONException e) {
            throw new MoodleException("A resposta do Moodle veio em um formato inesperado.", "json", e);
        } finally {
            if (conexao != null) {
                conexao.disconnect();
            }
        }
    }

    private static JSONObject objeto(Object json) throws MoodleException {
        if (json instanceof JSONObject) {
            return (JSONObject) json;
        }
        throw new MoodleException("A resposta do Moodle veio em um formato inesperado.", "json_tipo");
    }

    private static String mensagemAmigavel(String codigo, String mensagemMoodle) {
        switch (codigo) {
            case "invalidtoken":
                return "Sua sessão do Moodle expirou. Entre com o Moodle de novo.";
            case "error_notenabled":
                return "Este Moodle não está habilitado para o ClassCards.";
            case "error_keynotconfigured":
            case "error_keyinvalid":
            case "error_projectmismatch":
                return "O ClassCards ainda não foi configurado neste Moodle. Avise o administrador.";
            case "servicenotavailable":
            case "enablewsdescription":
                return "O acesso de aplicativos está desligado neste Moodle.";
            case "accessexception":
                return "Você não tem permissão para fazer isso no Moodle.";
            default:
                // Funções inexistentes caem aqui (ex.: plugin não instalado neste Moodle).
                return "O Moodle recusou o pedido: " + mensagemMoodle;
        }
    }

    private static void adicionar(StringBuilder corpo, String chave, String valor) {
        try {
            if (corpo.length() > 0) {
                corpo.append('&');
            }
            corpo.append(URLEncoder.encode(chave, "UTF-8")).append('=')
                    .append(URLEncoder.encode(valor == null ? "" : valor, "UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String ler(InputStream entrada) throws IOException {
        if (entrada == null) {
            return "";
        }
        try (InputStream in = entrada; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] bloco = new byte[8192];
            int n;
            while ((n = in.read(bloco)) != -1) {
                buffer.write(bloco, 0, n);
            }
            return buffer.toString("UTF-8");
        }
    }
}
