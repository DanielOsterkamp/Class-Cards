package com.example.classcardsaplicao.data;

import com.example.classcardsaplicao.model.FsrsScheduler;
import com.example.classcardsaplicao.model.ProgressoFlashcard;
import com.example.classcardsaplicao.model.Rating;
import com.example.classcardsaplicao.model.ResultadoOfensiva;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class EstatisticasRepository {
    private final FirebaseFirestore firestore;
    private final java.util.function.Supplier<LocalDate> dataAtual;

    public EstatisticasRepository() {
        this(FirebaseFirestore.getInstance(), LocalDate::now);
    }

    EstatisticasRepository(FirebaseFirestore firestore, java.util.function.Supplier<LocalDate> dataAtual) {
        this.firestore = firestore;
        this.dataAtual = dataAtual;
    }

    /** FSRS, contagem e recibo são confirmados juntos. Somente a última revisão
     * conquista a ofensiva. O estudo tradicional nunca escreve XP. */
    public Task<ResultadoOfensiva> registrarRevisaoTradicional(
            String userId, String sessaoId, String deckId, List<String> fila,
            String flashcardId, Rating rating) {
        if (userId == null || sessaoId == null || deckId == null || rating == null
                || fila == null || fila.isEmpty() || !fila.contains(flashcardId)
                || new HashSet<>(fila).size() != fila.size()) {
            throw new IllegalArgumentException("Revisão inválida.");
        }
        DocumentReference usuario = firestore.collection("users").document(userId);
        DocumentReference geral = usuario.collection("estatisticas").document("geral");
        DocumentReference sessao = usuario.collection("sessoesEstudo").document(sessaoId);
        DocumentReference recibo = sessao.collection("revisoes").document(flashcardId);
        DocumentReference progressoRef = usuario.collection("progressoFlashcards").document(flashcardId);

        return firestore.runTransaction(transaction -> {
            DocumentSnapshot revisao = transaction.get(recibo);
            if (revisao.exists()) {
                return new ResultadoOfensiva((int) numero(revisao, "ofensivaAtual"),
                        Boolean.TRUE.equals(revisao.getBoolean("ofensivaAlterada")));
            }
            DocumentSnapshot estado = transaction.get(sessao);
            DocumentSnapshot estatisticas = transaction.get(geral);
            DocumentSnapshot progressoSnapshot = transaction.get(progressoRef);
            if (estado.exists() && (!fila.equals(estado.get("flashcardIds"))
                    || !deckId.equals(estado.getString("deckId")))) {
                throw new IllegalStateException("A fila da sessão não pode mudar.");
            }
            int estudados = (int) numero(estado, "flashcardsEstudados");
            if (estudados >= fila.size() || !flashcardId.equals(fila.get(estudados))) {
                throw new IllegalStateException("Revisão fora da ordem da sessão.");
            }
            LocalDate hoje = dataAtual.get();
            boolean concluiu = estudados + 1 == fila.size();
            int ofensiva = (int) numero(estatisticas, "ofensivaAtual");
            boolean conquistou = false;
            Map<String, Object> dados = new HashMap<>();
            dados.put("totalFlashcardsEstudados", numero(estatisticas, "totalFlashcardsEstudados") + 1);
            if (concluiu) {
                String ultimaData = estatisticas.getString("ultimaDataEstudo");
                LocalDate anterior = ultimaData == null ? null : LocalDate.parse(ultimaData);
                conquistou = anterior == null || hoje.isAfter(anterior);
                if (conquistou) {
                    ofensiva = CalculaOfensivaRepository.calculaOfensiva(ofensiva, anterior, hoje);
                    dados.put("ofensivaAtual", ofensiva);
                    dados.put("maiorOfensiva", Math.max(numero(estatisticas, "maiorOfensiva"), ofensiva));
                    dados.put("ultimaDataEstudo", hoje.toString());
                }
            }
            ProgressoFlashcard progresso = progressoSnapshot.toObject(ProgressoFlashcard.class);
            if (progresso == null) progresso = new ProgressoFlashcard(userId, deckId, flashcardId);
            new FsrsScheduler().revisar(progresso, rating);

            Map<String, Object> registro = new HashMap<>();
            registro.put("deckId", deckId);
            registro.put("flashcardIds", fila);
            registro.put("flashcardsEstudados", estudados + 1);
            registro.put("concluida", concluiu);
            registro.put("modo", "tradicional");
            Map<String, Object> confirmacao = new HashMap<>();
            confirmacao.put("ofensivaAtual", ofensiva);
            confirmacao.put("ofensivaAlterada", conquistou);
            confirmacao.put("dataEstudo", hoje.toString());
            transaction.set(progressoRef, progresso);
            transaction.set(geral, dados, SetOptions.merge());
            transaction.set(sessao, registro);
            transaction.set(recibo, confirmacao);
            return new ResultadoOfensiva(ofensiva, conquistou);
        });
    }

    private static long numero(DocumentSnapshot snapshot, String campo) {
        Long valor = snapshot.getLong(campo);
        return valor == null ? 0 : valor;
    }
}
