package com.example.classcardsaplicao.data;

import android.util.Log;
import com.example.classcardsaplicao.model.UsuarioRanking;
import com.example.classcardsaplicao.model.TextoPesquisa;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.FieldValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;

public class RankingRepository {
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    /** Lê apenas a projeção pública do usuário escolhido, sem acessar seu documento privado. */
    public Task<UsuarioRanking> buscarUsuario(String uid) {
        if (uid == null || uid.trim().isEmpty() || uid.contains("/")) {
            return Tasks.forException(new IllegalArgumentException("Usuário inválido."));
        }
        return firestore.collection("rankings").document(uid).get().continueWith(task -> {
            if (!task.isSuccessful()) throw Objects.requireNonNull(task.getException());
            DocumentSnapshot doc = task.getResult();
            if (!doc.exists()) throw new IllegalStateException("Perfil público não encontrado.");
            return new UsuarioRanking(doc.getId(), texto(doc, "nome"), fotoValida(texto(doc, "fotoUrl")),
                    numero(doc, "xp"), numero(doc, "ofensiva"), numero(doc, "cardsEstudados"),
                    texto(doc, "ultimoDiaEstudo"), texto(doc, "fusoHorario"));
        });
    }

    public Task<List<UsuarioRanking>> buscarParticipantes() {

        return firestore.collection("rankings").get(Source.SERVER).continueWith(task -> {
            if (!task.isSuccessful()) throw Objects.requireNonNull(task.getException());

            List<UsuarioRanking> usuarios = new ArrayList<>();

            for (QueryDocumentSnapshot doc : task.getResult()) {
                usuarios.add(new UsuarioRanking(doc.getId(), texto(doc, "nome"), texto(doc, "fotoUrl"),
                        numero(doc, "xp"), numero(doc, "ofensiva"), numero(doc, "cardsEstudados"),
                        texto(doc, "ultimoDiaEstudo"), texto(doc, "fusoHorario")));
            }
            return usuarios;
        });
    }

    public Task<Void> sincronizarAtual() {

        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();

        if (usuario == null)
            return Tasks.forException(new IllegalStateException("Usuário não autenticado."));

        return sincronizar(usuario, 0);
    }

    private Task<Void> sincronizar(FirebaseUser usuario, int tentativa) {

        String uid = usuario.getUid();

        DocumentReference privado = firestore.collection("users").document(uid);
        DocumentReference resumo = privado.collection("gamificacao").document("resumo");
        DocumentReference publico = firestore.collection("rankings").document(uid);

        return resumo.get(Source.SERVER).continueWithTask(inicial -> {

            if (!inicial.isSuccessful()) return Tasks.<Void>forException(Objects.requireNonNull(inicial.getException()));

            long versaoConsultada = numero(inicial.getResult(), "versaoEstudos");

            return privado.collection("progressoFlashcards").get(Source.SERVER).continueWithTask(progresso -> {

                if (!progresso.isSuccessful())
                    return Tasks.<Void>forException(progresso.getException());

                long quantidade = 0;

                for (DocumentSnapshot card : progresso.getResult()) {
                    if (numero(card, "repeticoes") > 0 || numero(card, "ultimaRevisao") > 0) quantidade++;
                }

                final long cardsEstudados = quantidade;

                return firestore.<Void>runTransaction(transaction -> {

                    DocumentSnapshot stats = transaction.get(resumo);
                    DocumentSnapshot perfil = transaction.get(privado);
                    DocumentSnapshot anterior = transaction.get(publico);

                    if (numero(stats, "versaoEstudos") != versaoConsultada) throw new VersaoAlterada();

                    String nome = primeiro(texto(perfil, "nome"), usuario.getDisplayName(), texto(anterior, "nome"), "Estudante");
                    nome = nome.substring(0, Math.min(nome.length(), 120)); // limita o tamanho do nome
                    String foto = fotoValida(texto(perfil, "fotoUrl"));

                    if (foto.isEmpty() && usuario.getPhotoUrl() != null)
                        foto = fotoValida(usuario.getPhotoUrl().toString());

                    if (foto.isEmpty())
                        foto = fotoValida(texto(anterior, "fotoUrl"));

                    String fuso = texto(stats, "fusoHorario");

                    if (!Arrays.asList(TimeZone.getAvailableIDs()).contains(fuso))
                        fuso = TimeZone.getDefault().getID();

                    long xp = numero(stats, "xp");
                    long ofensiva = numero(stats, "ofensiva");
                    String ultimoDia = texto(stats, "ultimoDiaEstudo");

                    Map<String, Object> canonico = new HashMap<>();

                    canonico.put("xp", xp);
                    canonico.put("ofensiva", ofensiva);
                    canonico.put("ultimoDiaEstudo", ultimoDia);
                    canonico.put("fusoHorario", fuso);
                    canonico.put("cardsEstudados", cardsEstudados);
                    canonico.put("versaoEstudos", versaoConsultada);

                    Map<String, Object> publicacao = new HashMap<>();

                    publicacao.put("uid", uid);
                    publicacao.put("nome", nome);
                    publicacao.put("nomeBusca", TextoPesquisa.normalizar(nome));
                    publicacao.put("fotoUrl", foto);
                    publicacao.put("xp", xp);
                    publicacao.put("ofensiva", ofensiva);
                    publicacao.put("ultimoDiaEstudo", ultimoDia);
                    publicacao.put("fusoHorario", fuso);
                    publicacao.put("cardsEstudados", cardsEstudados);
                    publicacao.put("atualizadoEm", FieldValue.serverTimestamp());

                    transaction.set(resumo, canonico, SetOptions.merge());

                    transaction.set(publico, publicacao);
                    return null;
                });
            });
        }).continueWithTask(resultado -> {
            if (!resultado.isSuccessful() && mudouVersao(resultado.getException()) && tentativa < 3) {
                return sincronizar(usuario, tentativa + 1);
            }
            return resultado;
        });
    }

    public static void atualizarEmSegundoPlano() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        new RankingRepository().sincronizarAtual().addOnFailureListener(erro ->
                Log.w("RANKINGS", "Atualização pendente: " + erro.getClass().getSimpleName()));
    }

    private static boolean mudouVersao(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof VersaoAlterada) return true;
        }
        return false;
    }
    private static final class VersaoAlterada extends RuntimeException { }
    private static long numero(DocumentSnapshot doc, String campo) {
        Object valor = doc.get(campo);
        return valor instanceof Number ? Math.max(0, ((Number) valor).longValue()) : 0;
    }
    private static String texto(DocumentSnapshot doc, String campo) {
        Object valor = doc.get(campo);
        return valor instanceof String ? ((String) valor).trim() : "";
    }
    private static String primeiro(String... valores) {
        for (String valor : valores) if (valor != null && !valor.trim().isEmpty()) return valor.trim();
        return "Estudante";
    }
    private static String fotoValida(String valor) {
        return valor != null && valor.startsWith("https://") && valor.length() <= 4096 ? valor : "";
    }
}
