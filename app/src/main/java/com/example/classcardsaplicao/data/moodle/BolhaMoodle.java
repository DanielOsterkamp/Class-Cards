package com.example.classcardsaplicao.data.moodle;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * As "duas bolhas" do app (Fase 9):
 *
 * - Bolha Moodle: contas que entraram pelo Moodle (uid "moodle-<site>-<userid>", criado pelo plugin).
 *   Os baralhos delas ficam na coleção separada "decksMoodle", presos a um curso ("<site>:<courseid>"),
 *   e só membros do curso (e o dono) conseguem ler — conferido pelas regras do Firestore com as claims.
 * - Bolha de fora: contas de e-mail/Google. Continuam usando a coleção "decks" exatamente como antes.
 *
 * Os baralhos da bolha Moodle têm id começando com "moodle-", então dá para saber a coleção só pelo id.
 */
public final class BolhaMoodle {

    public static final String COLECAO_DECKS = "decks";
    public static final String COLECAO_DECKS_MOODLE = "decksMoodle";
    public static final String PREFIXO_UID = "moodle-";
    public static final String PREFIXO_DECK = "moodle-";

    /** O Firestore aceita no máximo 30 valores num filtro "in" / "array-contains-any". */
    public static final int LIMITE_IN = 30;

    /** Curso escolhido no filtro da pesquisa (null = todos os cursos da pessoa). */
    private static String filtroPesquisa;

    private BolhaMoodle() {
    }

    public static void definirFiltroPesquisa(String chaveCurso) {
        filtroPesquisa = chaveCurso;
    }

    /** Cursos que a pesquisa deve considerar: o do filtro, ou todos os da pessoa (até 30). */
    public static List<String> chavesDaPesquisa() {
        List<String> minhas = minhasChaves();
        if (filtroPesquisa != null && minhas.contains(filtroPesquisa)) {
            return Collections.singletonList(filtroPesquisa);
        }
        return minhas.size() > LIMITE_IN ? minhas.subList(0, LIMITE_IN) : minhas;
    }

    /** As duas listas de cursos têm algum curso em comum? */
    public static boolean temCursoEmComum(List<?> cursosDoOutro, List<String> meus) {
        if (cursosDoOutro == null) {
            return false;
        }
        for (Object curso : cursosDoOutro) {
            if (meus.contains(String.valueOf(curso))) {
                return true;
            }
        }
        return false;
    }

    /** Coleção em que um baralho está guardado, pelo id dele. */
    public static String colecaoDoDeck(String deckId) {
        return deckId != null && deckId.startsWith(PREFIXO_DECK) ? COLECAO_DECKS_MOODLE : COLECAO_DECKS;
    }

    /** Uma conta é da bolha Moodle? (vale para qualquer uid, inclusive de outra pessoa) */
    public static boolean contaMoodle(String uid) {
        return uid != null && uid.startsWith(PREFIXO_UID);
    }

    /** A conta conectada agora é da bolha Moodle (e a sessão do Moodle está neste aparelho)? */
    public static boolean ehMoodle() {
        String uid = FirebaseAuth.getInstance().getUid();
        return contaMoodle(uid) && MoodleSessao.ativa(contexto(), uid);
    }

    /** Chaves "<site>:<courseid>" dos cursos da conta conectada (vazia fora da bolha Moodle). */
    public static List<String> minhasChaves() {
        String uid = FirebaseAuth.getInstance().getUid();
        String site = site(uid);
        if (site == null) {
            return Collections.emptyList();
        }
        List<String> chaves = new ArrayList<>();
        for (CursoMoodle curso : MoodleSessao.cursos(contexto(), uid)) {
            chaves.add(site + ":" + curso.getId());
        }
        return chaves;
    }

    /** Chave do curso cujo nome é a matéria informada, ou null se não for um curso da conta conectada. */
    public static String chaveDaMateria(String materia) {
        String uid = FirebaseAuth.getInstance().getUid();
        String site = site(uid);
        if (site == null || materia == null) {
            return null;
        }
        for (CursoMoodle curso : MoodleSessao.cursos(contexto(), uid)) {
            if (curso.getNome().equals(materia.trim())) {
                return site + ":" + curso.getId();
            }
        }
        return null;
    }

    /** Nome do curso de uma chave (para mostrar no filtro), ou a própria chave se não for um curso conhecido. */
    public static String nomeDoCurso(String chave) {
        String uid = FirebaseAuth.getInstance().getUid();
        String site = site(uid);
        if (site != null && chave != null) {
            for (CursoMoodle curso : MoodleSessao.cursos(contexto(), uid)) {
                if (chave.equals(site + ":" + curso.getId())) {
                    return curso.getNome();
                }
            }
        }
        return chave;
    }

    /** O "<site>" de um uid "moodle-<site>-<userid>". */
    static String site(String uid) {
        if (!contaMoodle(uid)) {
            return null;
        }
        String resto = uid.substring(PREFIXO_UID.length());
        int traco = resto.lastIndexOf('-');
        return traco > 0 ? resto.substring(0, traco) : null;
    }

    private static Context contexto() {
        return FirebaseApp.getInstance().getApplicationContext();
    }
}
