package com.example.classcardsaplicao.data;

import com.example.classcardsaplicao.model.TextoPesquisa;
import com.example.classcardsaplicao.model.ReferenciaAvatar;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.*;
import java.util.*;
import java.util.function.Supplier;


public final class FotoPerfilPublicacao {
    private final FirebaseFirestore db;
    private final Supplier<String> contaAtual;
    public FotoPerfilPublicacao(FirebaseFirestore db, Supplier<String> contaAtual) {
        this.db = db;
        this.contaAtual = contaAtual;
    }

    public static final int MAX_BYTES = 50 * 1024;

    public Task<String> salvar(String uid, byte[] jpeg, String nomeAuth, List<String> cursosAutorizados) {
        if (uid == null || uid.isEmpty() || uid.contains("/") || jpeg == null || jpeg.length == 0 || jpeg.length > MAX_BYTES)
            return Tasks.forException(new IllegalArgumentException("Foto inválida."));

        String versao = UUID.randomUUID().toString();
        String referencia = ReferenciaAvatar.criar(uid, versao);

        DocumentReference avatar = db.collection("avatares").document(uid);
        DocumentReference privado = db.collection("users").document(uid);
        DocumentReference resumo = privado.collection("gamificacao").document("resumo");
        DocumentReference publico = db.collection("rankings").document(uid);

        return db.runTransaction(tx -> {
            if (!uid.equals(contaAtual.get()))
                throw new IllegalStateException("A conta mudou.");

            DocumentSnapshot perfil = tx.get(privado);
            DocumentSnapshot stats = tx.get(resumo);
            DocumentSnapshot anterior = tx.get(publico);
            String nome = texto(perfil, "nome");

            if (nome.isEmpty()) nome = nomeAuth == null ? "" : nomeAuth.trim();

            if (nome.isEmpty()) nome = texto(anterior, "nome");

            if (nome.isEmpty()) nome = "Estudante";

            nome = nome.substring(0, Math.min(120, nome.length()));

            String fuso = texto(stats, "fusoHorario");

            if (!Arrays.asList(TimeZone.getAvailableIDs()).contains(fuso))
                fuso = TimeZone.getDefault().getID();

            Map<String, Object> canonico = new HashMap<>();

            for (String campo : List.of("xp", "ofensiva", "cardsEstudados"))
                canonico.put(campo, numero(stats, campo));

            canonico.put("ultimoDiaEstudo", texto(stats, "ultimoDiaEstudo"));
            canonico.put("fusoHorario", fuso);

            Map<String, Object> publicacao = new HashMap<>(canonico);

            publicacao.put("uid", uid);
            publicacao.put("nome", nome);
            publicacao.put("nomeBusca", TextoPesquisa.normalizar(nome));
            publicacao.put("fotoUrl", referencia);
            publicacao.put("atualizadoEm", FieldValue.serverTimestamp());


            if (uid.startsWith("moodle-")) publicacao.put("cursosChave", cursosAutorizados.subList(0, Math.min(30, cursosAutorizados.size())));

            Map<String, Object> dados = new HashMap<>();
            dados.put("fotoUrl", referencia);
            dados.put("fotoStoragePath", FieldValue.delete());
            dados.put("fotoAtualizadaEm", FieldValue.serverTimestamp());

            Map<String, Object> imagem = new HashMap<>();
            imagem.put("uid", uid);
            imagem.put("versao", versao);
            imagem.put("conteudo", Blob.fromBytes(jpeg));
            imagem.put("mimeType", "image/jpeg");
            imagem.put("atualizadoEm", FieldValue.serverTimestamp());

            tx.set(avatar, imagem);
            tx.set(resumo, canonico, SetOptions.merge());
            tx.set(privado, dados, SetOptions.merge());
            tx.set(publico, publicacao);
            return referencia;
        });
    }

    private static String texto(DocumentSnapshot d, String campo) { Object v = d.get(campo); return v instanceof String ? ((String) v).trim() : ""; }
    private static long numero(DocumentSnapshot d, String campo) { Object v = d.get(campo); return v instanceof Number ? Math.max(0, ((Number) v).longValue()) : 0; }
}
