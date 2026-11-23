package com.example.classcardsaplicao.data;

import com.example.classcardsaplicao.data.moodle.*;
import com.example.classcardsaplicao.model.ResumoRelatorio.Registro;
import com.google.android.gms.tasks.*;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import java.util.*;

/** Professores leem somente os resumos publicados pelo servidor; nunca o histórico privado dos alunos. */
public class RelatoriosRepository {
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    public Task<Map<String, String>> cursosProfessor(android.content.Context context) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return Tasks.forException(new IllegalStateException("Entre com sua conta de professor."));
        return user.getIdToken(false).continueWith(task -> {
            if (!task.isSuccessful()) throw task.getException();
            if (!user.getUid().equals(auth.getUid())) throw new IllegalStateException("A conta mudou.");
            Object moodle = task.getResult().getClaims().get("moodle");
            Object prof = moodle instanceof Map ? ((Map<?, ?>) moodle).get("prof") : null;
            Map<String, String> cursos = new LinkedHashMap<>();
            if (prof instanceof List) for (Object chave : (List<?>) prof) if (chave instanceof String) cursos.put((String) chave, (String) chave);
            for (CursoMoodle c : MoodleSessao.cursos(context, user.getUid())) {
                String uid = user.getUid(); int ultimo = uid.lastIndexOf('-');
                if (uid.startsWith("moodle-") && ultimo > 7) {
                    String chave = uid.substring(7, ultimo) + ":" + c.getId();
                    if (cursos.containsKey(chave)) cursos.put(chave, c.getNome());
                }
            }
            return cursos;
        });
    }
    public Task<List<Registro>> buscar(List<String> cursos, String inicio, String fim, String uid, java.util.function.BooleanSupplier atual) {
        List<Registro> registros = new ArrayList<>();
        TaskCompletionSource<List<Registro>> fonte = new TaskCompletionSource<>();
        buscarCurso(cursos, 0, inicio, fim, uid, registros, fonte, null, atual);
        return fonte.getTask();
    }
    private void buscarCurso(List<String> cursos, int indice, String inicio, String fim, String uid,
                             List<Registro> resultado, TaskCompletionSource<List<Registro>> fonte, DocumentSnapshot cursor, java.util.function.BooleanSupplier atual) {
        if (!atual.getAsBoolean() || !uid.equals(auth.getUid())) { fonte.trySetException(new IllegalStateException("A conta mudou.")); return; }
        if (indice >= cursos.size()) { fonte.trySetResult(resultado); return; }
        Query query = db.collection("relatoriosCursos").document(cursos.get(indice)).collection("resumos")
                .whereGreaterThanOrEqualTo("dia", inicio).whereLessThanOrEqualTo("dia", fim).orderBy("dia").limit(250);
        if (cursor != null) query = query.startAfter(cursor);
        query.get(Source.SERVER).addOnSuccessListener(pagina -> {
            if (!atual.getAsBoolean() || !uid.equals(auth.getUid())) { fonte.trySetException(new IllegalStateException("A conta mudou.")); return; }
            for (DocumentSnapshot d : pagina) {
                Registro r = new Registro(); r.curso = cursos.get(indice); r.uid = texto(d,"uid");
                r.nome = texto(d,"nome"); r.foto = texto(d,"fotoUrl"); r.assunto = texto(d,"assunto");
                r.deck = texto(d,"deckId"); r.titulo = texto(d,"titulo"); r.card = texto(d,"flashcardId");
                r.dia = texto(d,"dia"); r.modo = texto(d,"modo"); r.verificado = Boolean.TRUE.equals(d.getBoolean("verificado"));
                r.revisoes = numero(d,"revisoes"); r.errei = numero(d,"errei"); r.dificil = numero(d,"dificil");
                r.bom = numero(d,"bom"); r.facil = numero(d,"facil"); r.ultima = numero(d,"ultima"); resultado.add(r);
            }
            if (pagina.size() == 250) buscarCurso(cursos, indice, inicio, fim, uid, resultado, fonte, pagina.getDocuments().get(249), atual);
            else buscarCurso(cursos, indice + 1, inicio, fim, uid, resultado, fonte, null, atual);
        }).addOnFailureListener(fonte::trySetException);
    }
    private static String texto(DocumentSnapshot d, String c) { Object v=d.get(c); return v instanceof String ? (String)v : ""; }
    private static long numero(DocumentSnapshot d, String c) { Object v=d.get(c); return v instanceof Number ? Math.max(0,((Number)v).longValue()) : 0; }
}
