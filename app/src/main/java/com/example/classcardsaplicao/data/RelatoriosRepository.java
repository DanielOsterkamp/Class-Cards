package com.example.classcardsaplicao.data;

import com.example.classcardsaplicao.data.moodle.*;
import com.example.classcardsaplicao.model.ResumoRelatorio.Registro;
import com.google.android.gms.tasks.*;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import java.util.*;


public class RelatoriosRepository {
    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    public RelatoriosRepository() {
        this(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance());
    }

    RelatoriosRepository(FirebaseAuth auth, FirebaseFirestore db) {
        this.auth = auth;
        this.db = db;
    }
    public Task<Map<String, String>> cursosProfessor(android.content.Context context) {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null)
            return Tasks.forException(new IllegalStateException("Entre com sua conta de professor."));

        return user.getIdToken(false).continueWith(task -> {
            if (!task.isSuccessful())
                throw task.getException();

            if (!user.getUid().equals(auth.getUid()))
                throw new IllegalStateException("A conta mudou.");

            Object moodle = task.getResult().getClaims().get("moodle");
            Object prof = moodle instanceof Map ? ((Map<?, ?>) moodle).get("prof") : null;
            Map<String, String> cursos = new LinkedHashMap<>();

            if (prof instanceof List)
                for (Object chave : (List<?>) prof)
                    if (chave instanceof String) cursos.put((String) chave, (String) chave);

            for (CursoMoodle c : MoodleSessao.cursos(context, user.getUid())) {
                String uid = user.getUid(); int ultimo = uid.lastIndexOf('-');
                if (uid.startsWith("moodle-") && ultimo > 7) {
                    String chave = uid.substring(7, ultimo) + ":" + c.getId();
                    if (cursos.containsKey(chave))
                        cursos.put(chave, c.getNome());
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
        if (!atual.getAsBoolean() || !uid.equals(auth.getUid())) {
            fonte.trySetException(new IllegalStateException("A conta mudou."));
            return;
        }

        if (indice >= cursos.size()) {
            fonte.trySetResult(resultado);
            return;
        }

        Date desde, ate;
        try {
            java.text.SimpleDateFormat formato = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
            formato.setTimeZone(TimeZone.getTimeZone("UTC"));
            formato.setLenient(false);
            desde = formato.parse(inicio);
            ate = new Date(formato.parse(fim).getTime() + 86400000L);

            if (ate.getTime() <= desde.getTime() || ate.getTime() - desde.getTime() > 93L * 86400000L)
                throw new IllegalArgumentException("Escolha um período de até 93 dias.");

        } catch (Exception erro) {
            fonte.trySetException(erro);
            return;
        }

        Query query = db.collection("relatoriosCursos").document(cursos.get(indice)).collection("revisoes")
                .whereGreaterThanOrEqualTo("createdAt", desde).whereLessThan("createdAt", ate)
                .orderBy("createdAt").limit(250);
        if (cursor != null) query = query.startAfter(cursor);
        query.get(Source.SERVER).addOnSuccessListener(pagina -> {
            if (!atual.getAsBoolean() || !uid.equals(auth.getUid())) { fonte.trySetException(new IllegalStateException("A conta mudou.")); return; }
            for (DocumentSnapshot d : pagina) {
                Registro r = new Registro(); r.curso = cursos.get(indice); r.uid = texto(d,"uid");
                r.nome = texto(d,"nome"); r.foto = texto(d,"fotoUrl"); r.assunto = texto(d,"assunto");
                r.deck = texto(d,"deckId"); r.titulo = texto(d,"titulo"); r.card = texto(d,"flashcardId");
                r.modo = texto(d,"modo"); r.verificado = Boolean.TRUE.equals(d.getBoolean("verificado"));
                Date quando = d.getDate("createdAt");
                if (quando == null) continue;
                r.ultima = quando.getTime();
                java.text.SimpleDateFormat dia = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
                dia.setTimeZone(TimeZone.getTimeZone("UTC"));
                r.dia = dia.format(quando);
                String rating = texto(d,"rating");
                r.revisoes = 1;
                r.errei = "AGAIN".equals(rating) ? 1 : 0;
                r.dificil = "HARD".equals(rating) ? 1 : 0;
                r.bom = "GOOD".equals(rating) ? 1 : 0;
                r.facil = "EASY".equals(rating) ? 1 : 0;
                resultado.add(r);
            }

            if (pagina.size() == 250)
                buscarCurso(cursos, indice, inicio, fim, uid, resultado, fonte, pagina.getDocuments().get(249), atual);
            else
                buscarCurso(cursos, indice + 1, inicio, fim, uid, resultado, fonte, null, atual);

        }).addOnFailureListener(fonte::trySetException);
    }
    private static String texto(DocumentSnapshot d, String c) { Object v=d.get(c); return v instanceof String ? (String)v : ""; }
}
