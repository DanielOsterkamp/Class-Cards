package com.example.classcardsaplicao.model;

import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Deck {

    private String id;
    private String ownerId;
    private String titulo;
    private String descricao;
    private long createdAt;
    private long updatedAt;
    private String materia;

    private int quantidadeCards;
    private boolean excluindo;

    public boolean isExcluindo() { return excluindo; }
    public void setExcluindo(boolean excluindo) { this.excluindo = excluindo; }

    public Deck() {
    }

    public Deck(
            String id,
            String ownerId,
            String titulo,
            String materia,
            String descricao,
            int quantidadeCards,
            long createdAt,
            long updatedAt
    ) {
        this.id = id;
        this.ownerId = ownerId;
        this.titulo = titulo;
        this.materia = materia;
        this.descricao = descricao;
        this.quantidadeCards = quantidadeCards;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getMateria() {
        return materia;
    }

    public void setMateria(String materia) {
        this.materia = materia;
    }

    public String getId() {
        return id;
    }

    public int getQuantidadeCards() {
        return quantidadeCards;
    }

    public void setQuantidadeCards(int quantidadeCards) {
        this.quantidadeCards = quantidadeCards;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
