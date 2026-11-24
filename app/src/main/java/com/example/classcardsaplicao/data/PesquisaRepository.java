package com.example.classcardsaplicao.data;

import com.example.classcardsaplicao.data.moodle.BolhaMoodle;
import com.example.classcardsaplicao.model.ResultadoDeckPesquisa;
import com.example.classcardsaplicao.model.ResultadoUsuarioPesquisa;
import com.example.classcardsaplicao.model.TextoPesquisa;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.List;

public class PesquisaRepository implements PesquisaFonte {
    public static final int TAMANHO_PAGINA = 24;
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    @Override public void buscarDecks(String termo, Object cursor, Callback<ResultadoDeckPesquisa> callback) {
        Query base = firestore.collection("decks");

        if (BolhaMoodle.ehMoodle()) {
            List<String> cursos = BolhaMoodle.chavesDaPesquisa();
            if (cursos.isEmpty()) {
                callback.onSuccess(new ArrayList<>(), null, false);
                return;
            }
            base = firestore.collection(BolhaMoodle.COLECAO_DECKS_MOODLE).whereIn("cursoChave", cursos);
        }
        buscar(base, "tituloBusca", termo, cursor, doc -> true, doc -> new ResultadoDeckPesquisa(doc.getId(),
                texto(doc, "titulo"), texto(doc, "materia"), numero(doc, "quantidadeCards")), callback);
    }

    @Override public void buscarUsuarios(String termo, Object cursor, Callback<ResultadoUsuarioPesquisa> callback) {

        boolean moodle = BolhaMoodle.ehMoodle();
        List<String> cursos = BolhaMoodle.chavesDaPesquisa();
        Filtro filtro = doc -> moodle
                ? BolhaMoodle.contaMoodle(doc.getId()) && BolhaMoodle.temCursoEmComum(
                        doc.get("cursosChave") instanceof List ? (List<?>) doc.get("cursosChave") : null, cursos)
                : !BolhaMoodle.contaMoodle(doc.getId());
        buscar(firestore.collection("rankings"), "nomeBusca", termo, cursor, filtro, doc -> new ResultadoUsuarioPesquisa(doc.getId(),
                texto(doc, "nome"), texto(doc, "fotoUrl")), callback);
    }

    private <T> void buscar(Query base, String campo, String termo, Object cursor, Filtro filtro,
            Conversor<T> conversor, Callback<T> callback) {
        String uid = FirebaseAuth.getInstance().getUid();
        String chave = TextoPesquisa.normalizar(termo);

        if (uid == null) {
            callback.onError(new IllegalStateException("Entre na sua conta para pesquisar."));
            return;
        }

        if (chave.length() < 2 || chave.length() > 80 || (cursor != null && !(cursor instanceof DocumentSnapshot))) {
            callback.onError(new IllegalArgumentException("Informe entre 2 e 80 caracteres para pesquisar."));
            return;
        }

        Query query = base.orderBy(campo)
                .startAt(chave).endAt(chave + "\uf8ff").limit(TAMANHO_PAGINA + 1);
        if (cursor != null)
            query = query.startAfter((DocumentSnapshot) cursor);

        query.get(Source.SERVER).addOnSuccessListener(snapshot -> {

            if (!mesmaConta(uid, callback))
                return;

            List<T> itens = new ArrayList<>();

            List<DocumentSnapshot> documentos = snapshot.getDocuments();

            int quantidade = Math.min(TAMANHO_PAGINA, documentos.size());

            for (int i = 0; i < quantidade; i++)
                if (filtro.aceita(documentos.get(i))) itens.add(conversor.converter(documentos.get(i)));

            Object proximo = quantidade == 0 ? null : documentos.get(quantidade - 1);
            callback.onSuccess(itens, proximo, documentos.size() > TAMANHO_PAGINA);

        }).addOnFailureListener(erro -> {
            if (mesmaConta(uid, callback)) callback.onError(erro);
        });
    }

    private static boolean mesmaConta(String uid, Callback<?> callback) {
        if (uid.equals(FirebaseAuth.getInstance().getUid())) return true;
        callback.onError(new IllegalStateException("A conta mudou. Faça uma nova pesquisa."));
        return false;
    }

    private static String texto(DocumentSnapshot doc, String campo) {
        Object valor = doc.get(campo);
        return valor instanceof String ? ((String) valor).trim() : "";
    }

    private static long numero(DocumentSnapshot doc, String campo) {
        Object valor = doc.get(campo);
        return valor instanceof Number ? Math.max(0, ((Number) valor).longValue()) : 0;
    }

    private interface Conversor<T> { T converter(DocumentSnapshot documento); }
    private interface Filtro { boolean aceita(DocumentSnapshot documento); }
}
