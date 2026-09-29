package com.example.classcardsaplicao.model;

public class Usuario {

    private String id;
    private String nome;
    private String email;
    private String fotoUrl;
    private long createdAt;

    public Usuario() {
    }

    public Usuario(String id, String nome, String email, String fotoUrl, long createdAt) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.fotoUrl = fotoUrl;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getFotoUrl() {
        return fotoUrl;
    }

    public void setFotoUrl(String fotoUrl) {
        this.fotoUrl = fotoUrl;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }
}
