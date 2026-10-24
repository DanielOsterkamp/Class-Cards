package com.example.classcardsaplicao.data;

import com.example.classcardsaplicao.model.Flashcard;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerationConfig;
import com.google.firebase.ai.GenerativeModel;
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

public class AiRepository {

    private static final ScheduledExecutorService TIMEOUT = Executors.newSingleThreadScheduledExecutor();

    // esse codigo serve para organizar os metodos referentes a IA

    private final GenerativeModelFutures model;
    private final Executor executor;

    public AiRepository() {

        // aqui mostramos pra IA como queremos nosso formato de resposta para a criação do flashcard
        // falamos que queremos um objeto, passamos os atributos pergunta e respostas,
        // também falamos que queremos um array desses cards, já que a gente consegue criar mais de um por vez

        Schema flashcardSchema = Schema.obj(
                Map.of("flashcards",
                        Schema.array(
                                Schema.obj(
                                        Map.of("pergunta", Schema.str(),
                                                "resposta", Schema.str()
                                        )
                                )
                        )
                )
        );

        GenerationConfig.Builder configBuilder = new GenerationConfig.Builder();

        configBuilder.responseMimeType = "application/json";
        configBuilder.responseSchema = flashcardSchema;

        GenerationConfig generationConfig = configBuilder.build();


        // aqui pega qual modelo de IA estamos usando

        GenerativeModel generativeModel = FirebaseAI.getInstance(GenerativeBackend.googleAI())
                        .generativeModel("gemini-3.5-flash", generationConfig);

        model = GenerativeModelFutures.from(generativeModel);

        // o executor define como vamos fazer o callback

        executor = Executors.newSingleThreadExecutor();
    }

    public void gerarFlashcards(
            String materia,
            String descricao,
            int quantidade,
            AiCallback callback
    ) {

        String prompt =
                "Crie exatamente " + quantidade +
                        " flashcards educacionais sobre a matéria \"" +
                        materia + "\".\n\n" +

                        "Conteúdo solicitado pelo usuário:\n" +
                        descricao + "\n\n" +

                        "Cada flashcard deve possuir uma pergunta clara " +
                        "e uma resposta objetiva e correta. " +
                        "Não repita perguntas.";

        Content content = new Content.Builder()
                .addText(prompt)
                .build();

        ListenableFuture<GenerateContentResponse> response =
                Futures.withTimeout(model.generateContent(content), 90, TimeUnit.SECONDS, TIMEOUT);

        // aqui pegamos as informações que foram passadas, transformamos em objetos de Flashcards e tratamos possiveis erros

        Futures.addCallback(
                response,
                new FutureCallback<GenerateContentResponse>() {

                    @Override
                    public void onSuccess(GenerateContentResponse result) {
                        try {

                            String json = result.getText();

                            if (json == null || json.isEmpty()) {
                                callback.onError(new Exception("A IA não retornou flashcards."));
                                return;
                            }

                            JSONObject objeto = new JSONObject(json);

                            JSONArray array = objeto.getJSONArray("flashcards");

                            List<Flashcard> flashcards = new ArrayList<>();

                            for (int i = 0; i < array.length(); i++) {

                                JSONObject item = array.getJSONObject(i);

                                Flashcard flashcard =
                                        new Flashcard(
                                                null,
                                                item.getString("pergunta"),
                                                item.getString("resposta"),
                                                System.currentTimeMillis()
                                        );

                                flashcards.add(flashcard);
                            }

                            callback.onSuccess(flashcards);

                        } catch (Exception exception) {
                            callback.onError(exception);
                        }
                    }

                    @Override
                    public void onFailure(Throwable throwable) {

                        android.util.Log.e(
                                "AI_ERROR",
                                "Erro ao gerar flashcards",
                                throwable
                        );

                        callback.onError(
                                tratarErroAi(throwable)
                        );
                    }
                },
                executor
        );
    }

    private Exception tratarErroAi(Throwable throwable) {

        if (throwable instanceof TimeoutException) {
            return new Exception("A criação dos flashcards demorou demais. Tente novamente.");
        }
        String mensagem = throwable.getMessage();

        if (mensagem == null) {
            return new Exception(
                    "Não foi possível gerar os flashcards. Tente novamente."
            );
        }

        String mensagemLower = mensagem.toLowerCase();

        // Modelo temporariamente sobrecarregado
        if (mensagemLower.contains("overloaded")
                || mensagemLower.contains("prefill_queue_overloaded")
                || mensagemLower.contains("high demand")) {

            return new Exception(
                    "A IA está temporariamente sobrecarregada. " +
                            "Tente novamente em alguns instantes."
            );
        }

        // Cota / limite de requisições atingido
        if (mensagemLower.contains("quota exceeded")
                || mensagemLower.contains("exceeded your current quota")
                || mensagemLower.contains("resource_exhausted")) {

            return new Exception(
                    "O limite de uso da IA foi atingido. " +
                            "Aguarde alguns instantes e tente novamente."
            );
        }

        // Erro genérico
        return new Exception(
                "Não foi possível gerar os flashcards. Tente novamente."
        );
    }

    // então sempre que der certo ele vai retornar uma lista de flashcards, e se der errado, passa a exeption



    public interface AiCallback {

        void onSuccess(List<Flashcard> flashcards);

        void onError(Exception exception);
    }
}