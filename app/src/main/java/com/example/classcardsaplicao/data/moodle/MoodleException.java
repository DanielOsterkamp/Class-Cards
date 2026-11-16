package com.example.classcardsaplicao.data.moodle;

/** Erro do login ou da API do Moodle, já com uma mensagem que pode ser mostrada ao usuário. */
public class MoodleException extends Exception {

    private final String codigo;

    public MoodleException(String mensagemParaUsuario, String codigo) {
        super(mensagemParaUsuario);
        this.codigo = codigo;
    }

    public MoodleException(String mensagemParaUsuario, String codigo, Throwable causa) {
        super(mensagemParaUsuario, causa);
        this.codigo = codigo;
    }

    /** Código técnico (ex.: errorcode do Moodle), útil nos logs. Nunca contém tokens. */
    public String getCodigo() {
        return codigo;
    }
}
