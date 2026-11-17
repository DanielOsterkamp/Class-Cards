package com.example.classcardsaplicao.data.moodle;

/**
 * Um PDF de um curso do Moodle que pode ser usado pela IA para gerar flashcards.
 * O conteúdo só é preenchido depois do download (quando a pessoa escolhe o material).
 */
public class MaterialMoodle {

    /** Limite do app: a IA aceita até 20 MB por pedido, e o PDF cresce ~33% no envio. */
    public static final long TAMANHO_MAXIMO = 10L * 1024 * 1024;

    private final long cmid;
    private final String nome;
    private final String arquivo;
    private final long tamanho;
    private final String fileUrl;
    private byte[] conteudo;

    public MaterialMoodle(long cmid, String nome, String arquivo, long tamanho, String fileUrl) {
        this.cmid = cmid;
        this.nome = nome;
        this.arquivo = arquivo;
        this.tamanho = tamanho;
        this.fileUrl = fileUrl;
    }

    public long getCmid() {
        return cmid;
    }

    public String getNome() {
        return nome;
    }

    public String getArquivo() {
        return arquivo;
    }

    public long getTamanho() {
        return tamanho;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public boolean grandeDemais() {
        return tamanho > TAMANHO_MAXIMO;
    }

    /** Bytes do PDF já baixado, ou null se ainda não foi baixado. */
    public byte[] getConteudo() {
        return conteudo;
    }

    void setConteudo(byte[] conteudo) {
        this.conteudo = conteudo;
    }

    @Override
    public String toString() {
        return nome;
    }
}
