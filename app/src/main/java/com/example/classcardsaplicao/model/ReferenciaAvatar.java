package com.example.classcardsaplicao.model;

/** Referência interna versionada. Os bytes ficam só em avatares/{uid}, fora das listas públicas. */
public final class ReferenciaAvatar {
    private static final String PREFIXO = "avatar-firestore:";
    private static final String UUID = "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}";
    public final String uid;
    public final String versao;
    private ReferenciaAvatar(String uid, String versao) { this.uid = uid; this.versao = versao; }
    public static String criar(String uid, String versao) {
        if (uid == null || uid.isEmpty() || uid.contains("/")) throw new IllegalArgumentException("Avatar inválido.");
        String referencia = PREFIXO + uid + "/" + versao;
        if (ler(referencia) == null) throw new IllegalArgumentException("Avatar inválido.");
        return referencia;
    }
    public static ReferenciaAvatar ler(String valor) {
        if (valor == null || !valor.startsWith(PREFIXO)) return null;
        int separador = valor.indexOf('/', PREFIXO.length());
        if (separador <= PREFIXO.length()) return null;
        String uid = valor.substring(PREFIXO.length(), separador);
        String versao = valor.substring(separador + 1);
        if (uid.length() > 128 || uid.equals(".") || uid.equals("..") || !versao.matches(UUID)) return null;
        return new ReferenciaAvatar(uid, versao);
    }
    public static boolean valida(String valor) { return ler(valor) != null; }
    public static boolean https(String valor) { return valor != null && valor.startsWith("https://") && valor.length() <= 4096; }
    public static String validarPara(String uid, String valor) {
        if (https(valor)) return valor;
        ReferenciaAvatar avatar = ler(valor);
        return avatar != null && avatar.uid.equals(uid) ? valor : "";
    }
}
