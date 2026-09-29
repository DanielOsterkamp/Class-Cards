package com.example.classcardsaplicao.model;

public class ProgressoFlashcard {

    // essa classe armazena as informações da repetição espaçada de um card, esses valores são usados nos calculos
    // grande parte deles tá explicada no FsrsScheduler

    private String userId;
    private String deckId;
    private String flashcardId;

    private int diasDecorridos;
    private int diasAgendados;

    private double stability;
    private double difficulty;

    private long ultimaRevisao;
    private long proximaRevisao;

    private int repeticoes;
    private int lapsos;

    private String estado;

    // construtor vazio pro firebase
    public ProgressoFlashcard() {
    }

    public ProgressoFlashcard(
            String userId,
            String deckId,
            String flashcardId
    ) {
        this.userId = userId;
        this.deckId = deckId;
        this.flashcardId = flashcardId;

        this.stability = 0;
        this.difficulty = 0;

        this.ultimaRevisao = 0;
        this.proximaRevisao = 0;

        this.diasDecorridos = 0;
        this.diasAgendados = 0;

        this.repeticoes = 0;
        this.lapsos = 0;

        this.estado = EstadoFlashcard.NEW.name();
    }

    public String getUserId() {
        return userId;
    }

    public String getDeckId() {
        return deckId;
    }

    public int getDiasDecorridos() {
        return diasDecorridos;
    }

    public void setDiasDecorridos(int diasDecorridos) {
        this.diasDecorridos = diasDecorridos;
    }

    public int getDiasAgendados() {
        return diasAgendados;
    }

    public void setDiasAgendados(int diasAgendados) {
        this.diasAgendados = diasAgendados;
    }

    public String getFlashcardId() {
        return flashcardId;
    }

    public double getStability() {
        return stability;
    }

    public void setStability(double stability) {
        this.stability = stability;
    }

    public double getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(double difficulty) {
        this.difficulty = difficulty;
    }

    public long getUltimaRevisao() {
        return ultimaRevisao;
    }

    public void setUltimaRevisao(long ultimaRevisao) {
        this.ultimaRevisao = ultimaRevisao;
    }

    public long getProximaRevisao() {
        return proximaRevisao;
    }

    public void setProximaRevisao(long proximaRevisao) {
        this.proximaRevisao = proximaRevisao;
    }

    public int getRepeticoes() {
        return repeticoes;
    }

    public void setRepeticoes(int repeticoes) {
        this.repeticoes = repeticoes;
    }

    public int getLapsos() {
        return lapsos;
    }

    public void setLapsos(int lapsos) {
        this.lapsos = lapsos;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}