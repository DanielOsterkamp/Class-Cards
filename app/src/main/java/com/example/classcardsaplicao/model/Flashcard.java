package com.example.classcardsaplicao.model;

public class Flashcard {

    private String id;
    private String pergunta;
    private String resposta;
    private long createdAt;

    public Flashcard() { // construtor vazio para o firebase

    }

    public Flashcard(String id, String pergunta, String resposta, long createdAt) {
        this.id = id;
        this.pergunta = pergunta;
        this.resposta = resposta;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPergunta() {
        return pergunta;
    }

    public void setPergunta(String pergunta) {
        this.pergunta = pergunta;
    }

    public String getResposta() {
        return resposta;
    }

    public void setResposta(String resposta) {
        this.resposta = resposta;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}