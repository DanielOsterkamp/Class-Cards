package com.example.classcardsaplicao.data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class PublicacaoRelatorio {
    private PublicacaoRelatorio() { }

    static boolean alunoDoCurso(String uid, Map<String, Object> claims, String curso) {
        Object moodle = claims.get("moodle");
        if (!uid.startsWith("moodle-") || curso == null || !(moodle instanceof Map)) return false;
        Map<?, ?> papeis = (Map<?, ?>) moodle;
        Object cursos = papeis.get("cursos"), professores = papeis.get("prof");
        return cursos instanceof List && ((List<?>) cursos).contains(curso)
                && (!(professores instanceof List) || !((List<?>) professores).contains(curso));
    }

    static String id(String uid, String colecao, String tentativa) {
        String origem = uid.length() + ":" + uid + colecao.length() + ":" + colecao + tentativa;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(origem.getBytes(StandardCharsets.UTF_8));
            StringBuilder resultado = new StringBuilder(64);
            for (byte valor : digest) {
                resultado.append(Character.forDigit((valor & 255) >>> 4, 16));
                resultado.append(Character.forDigit(valor & 15, 16));
            }
            return resultado.toString();
        } catch (NoSuchAlgorithmException erro) {
            throw new IllegalStateException("SHA-256 indisponível.", erro);
        }
    }

    static Map<String, Object> dados(String uid, String colecao, String tentativa, Map<String, Object> evento, Map<String, Object> perfil) {
        Map<?, ?> contexto = (Map<?, ?>) evento.get("relatorio");
        Map<String, Object> dados = new HashMap<>();
        dados.put("uid", uid);
        dados.put("eventoId", tentativa);
        dados.put("modo", "revisoesTradicionais".equals(colecao) ? "tradicional" : "dissertativo");
        dados.put("nome", perfil.getOrDefault("nome", "Estudante"));
        dados.put("fotoUrl", perfil.getOrDefault("fotoUrl", ""));
        dados.put("deckId", evento.get("deckId"));
        dados.put("flashcardId", evento.get("flashcardId"));
        dados.put("rating", evento.get("rating"));
        dados.put("createdAt", evento.get("createdAt"));
        for (String campo : new String[]{"assunto", "titulo", "verificado"}) dados.put(campo, contexto.get(campo));
        return dados;
    }
}
