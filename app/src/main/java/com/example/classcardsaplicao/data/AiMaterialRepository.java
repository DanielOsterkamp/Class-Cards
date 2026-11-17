package com.example.classcardsaplicao.data;

import android.util.Log;

import com.example.classcardsaplicao.model.Flashcard;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerationConfig;
import com.google.firebase.ai.type.GenerativeBackend;
import com.google.firebase.ai.type.Schema;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Geração de flashcards a partir de um PDF do Moodle (Fase 8).
 *
 * Fica separada do AiRepository para não alterar a geração que já existe: usa o mesmo modelo e o
 * mesmo formato de resposta, mas envia o PDF junto com o pedido (dados inline, até 20 MB por pedido).
 */
public class AiMaterialRepository {

    private static final String TAG = "AI_MATERIAL";
    private static final ScheduledExecutorService TIMEOUT = Executors.newSingleThreadScheduledExecutor();
    /** O mesmo modelo usado pelo AiRepository. */
    private static final String MODELO = "gemini-3.5-flash";

    private final GenerativeModelFutures model;
    private final Executor executor = Executors.newSingleThreadExecutor();

    public AiMaterialRepository() {
        Schema flashcardSchema = Schema.obj(
                Map.of("flashcards",
                        Schema.array(
                                Schema.obj(Map.of("pergunta", Schema.str(), "resposta", Schema.str())))));
        GenerationConfig.Builder config = new GenerationConfig.Builder();
        config.responseMimeType = "application/json";
        config.responseSchema = flashcardSchema;
        GenerativeModel generativeModel = FirebaseAI.getInstance(GenerativeBackend.googleAI())
                .generativeModel(MODELO, config.build());
        model = GenerativeModelFutures.from(generativeModel);
    }

    public void gerarFlashcards(String materia, String foco, int quantidade, byte[] pdf,
                                AiRepository.AiCallback callback) {
        String prompt = "Crie exatamente " + quantidade + " flashcards educacionais sobre a matéria \""
                + materia + "\", usando como base APENAS o conteúdo do documento PDF anexado.\n\n"
                + (foco == null || foco.trim().isEmpty() ? ""
                : "Concentre-se neste assunto pedido pelo usuário:\n" + foco.trim() + "\n\n")
                + "Cada flashcard deve possuir uma pergunta clara e uma resposta objetiva e correta, "
                + "de acordo com o documento. Não invente informações que não estejam no documento. "
                + "Não repita perguntas.";

        Content content = new Content.Builder()
                .addInlineData(pdf, "application/pdf")
                .addText(prompt)
                .build();

        ListenableFuture<GenerateContentResponse> resposta =
                Futures.withTimeout(model.generateContent(content), 120, TimeUnit.SECONDS, TIMEOUT);

        Futures.addCallback(resposta, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                try {
                    String json = result.getText();
                    if (json == null || json.isEmpty()) {
                        callback.onError(new Exception("A IA não retornou flashcards."));
                        return;
                    }
                    JSONArray array = new JSONObject(json).getJSONArray("flashcards");
                    List<Flashcard> flashcards = new ArrayList<>();
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject item = array.getJSONObject(i);
                        flashcards.add(new Flashcard(null, item.getString("pergunta"), item.getString("resposta"),
                                System.currentTimeMillis()));
                    }
                    callback.onSuccess(flashcards);
                } catch (Exception e) {
                    callback.onError(e);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e(TAG, "Erro ao gerar flashcards a partir do PDF", t);
                callback.onError(tratarErro(t));
            }
        }, executor);
    }

    private static Exception tratarErro(Throwable t) {
        if (t instanceof TimeoutException) {
            return new Exception("A leitura do material demorou demais. Tente um PDF menor ou tente novamente.");
        }
        String m = t.getMessage() == null ? "" : t.getMessage().toLowerCase();
        if (m.contains("overloaded") || m.contains("high demand")) {
            return new Exception("A IA está temporariamente sobrecarregada. Tente novamente em alguns instantes.");
        }
        if (m.contains("quota") || m.contains("resource_exhausted")) {
            return new Exception("O limite de uso da IA foi atingido. Aguarde alguns instantes e tente novamente.");
        }
        if (m.contains("too large") || m.contains("payload") || m.contains("request size")) {
            return new Exception("O material é grande demais para a IA. Escolha um PDF menor.");
        }
        if (m.contains("app check")) {
            return new Exception("O app não foi reconhecido pelo Firebase (App Check).");
        }
        return new Exception("Não foi possível gerar os flashcards a partir do material. Tente novamente.");
    }
}
