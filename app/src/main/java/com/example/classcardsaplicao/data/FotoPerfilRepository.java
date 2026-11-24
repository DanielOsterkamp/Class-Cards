package com.example.classcardsaplicao.data;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import com.example.classcardsaplicao.model.ReferenciaAvatar;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Seleção local e persistência do avatar pequeno; não utiliza bucket nem Firebase Storage. */
public class FotoPerfilRepository implements FotoPerfilFonte {
    private final Context context;
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler principal = new Handler(Looper.getMainLooper());
    private volatile boolean encerrado;
    private int versaoOperacao;
    private static long versaoFoto;
    public static long getVersaoFoto() { return versaoFoto; }
    public FotoPerfilRepository(Context context) { this.context = context.getApplicationContext(); }
    @Override public String uidAtual() { return auth.getUid(); }

    @Override public void carregar(String uid, Callback<String> callback) {
        FirebaseUser usuario = auth.getCurrentUser();
        String fallback = usuario != null && uid.equals(usuario.getUid()) && usuario.getPhotoUrl() != null ? usuario.getPhotoUrl().toString() : "";
        db.collection("users").document(uid).get().addOnSuccessListener(d -> {
            String foto = ReferenciaAvatar.validarPara(uid, d.getString("fotoUrl"));
            callback.sucesso(foto.isEmpty() ? fallback : foto);
        }).addOnFailureListener(callback::erro);
    }

    @Override public void preparar(Uri origem, Callback<File> callback) {
        executor.execute(() -> {
            try {
                File preparada = PreparadorFotoPerfil.preparar(context, origem);
                principal.post(() -> { if (encerrado) preparada.delete(); else callback.sucesso(preparada); });
            } catch (Exception erro) { principal.post(() -> { if (!encerrado) callback.erro(erro); }); }
        });
    }
    @Override public void salvar(String uid, File imagem, Callback<String> callback) {
        FirebaseUser usuario = auth.getCurrentUser();

        if (usuario == null || !uid.equals(usuario.getUid())) {
            callback.erro(new IllegalStateException("A conta mudou."));
            return;
        }

        int operacao = ++versaoOperacao;

        executor.execute(() -> {
            try {
                byte[] jpeg = lerImagem(imagem);
                principal.post(() -> {

                    if (!valida(operacao, uid))
                        return;

                    usuario.getIdToken(false).addOnSuccessListener(token -> {
                        if (!valida(operacao, uid))
                            return;

                        List<String> cursos = new ArrayList<>();
                        Object moodle = token.getClaims().get("moodle");
                        Object lista = moodle instanceof Map ? ((Map<?, ?>) moodle).get("cursos") : null;

                        if (lista instanceof List)
                            for (Object curso : (List<?>) lista)
                                if (curso instanceof String)
                                    cursos.add((String) curso);

                        new FotoPerfilPublicacao(db, auth::getUid).salvar(uid, jpeg, usuario.getDisplayName(), cursos)
                                .addOnSuccessListener(referencia -> {

                                    if (!valida(operacao, uid))
                                        return;

                                    ++versaoFoto;
                                    callback.sucesso(referencia);
                                }).addOnFailureListener(erro -> { if (valida(operacao,uid)) callback.erro(erro); });
                    }).addOnFailureListener(erro -> { if (valida(operacao,uid)) callback.erro(erro); });
                });
            } catch (Exception erro) { principal.post(() -> { if (valida(operacao,uid)) callback.erro(erro); }); }
        });
    }
    private boolean valida(int operacao, String uid) { return !encerrado && operacao == versaoOperacao && uid.equals(auth.getUid()); }
    private static byte[] lerImagem(File arquivo) throws IOException {
        try (InputStream entrada = new FileInputStream(arquivo); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            byte[] bloco = new byte[4096]; int quantidade;

            while ((quantidade = entrada.read(bloco)) != -1) {
                if (saida.size() + quantidade > FotoPerfilPublicacao.MAX_BYTES)
                    throw new IOException("Escolha uma imagem menor.");
                saida.write(bloco, 0, quantidade);
            }

            if (saida.size() == 0)
                throw new IOException("Imagem vazia.");
            return saida.toByteArray();
        }
    }
    @Override public void cancelar() { ++versaoOperacao; }
    @Override public void encerrar() { encerrado = true; cancelar(); executor.shutdownNow(); }
}
