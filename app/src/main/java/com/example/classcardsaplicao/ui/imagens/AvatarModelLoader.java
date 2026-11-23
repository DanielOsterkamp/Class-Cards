package com.example.classcardsaplicao.ui.imagens;

import android.graphics.BitmapFactory;
import androidx.annotation.NonNull;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.*;
import com.bumptech.glide.signature.ObjectKey;
import com.example.classcardsaplicao.data.FotoPerfilPublicacao;
import com.example.classcardsaplicao.model.ReferenciaAvatar;
import com.google.firebase.firestore.*;
import java.io.*;

/** Resolve apenas referências internas de avatar. URLs HTTPS continuam com o carregador padrão. */
public final class AvatarModelLoader implements ModelLoader<String, InputStream> {
    private final FirebaseFirestore db;
    public AvatarModelLoader(FirebaseFirestore db) { this.db = db; }
    @Override public boolean handles(@NonNull String modelo) { return ReferenciaAvatar.valida(modelo); }
    @Override public LoadData<InputStream> buildLoadData(@NonNull String modelo, int largura, int altura, @NonNull Options opcoes) {
        ReferenciaAvatar avatar = ReferenciaAvatar.ler(modelo);
        return avatar == null ? null : new LoadData<>(new ObjectKey(modelo), new Fetcher(db, avatar));
    }
    public static final class Factory implements ModelLoaderFactory<String, InputStream> {
        @NonNull @Override public ModelLoader<String, InputStream> build(@NonNull MultiModelLoaderFactory fabrica) {
            return new AvatarModelLoader(FirebaseFirestore.getInstance());
        }
        @Override public void teardown() { }
    }
    private static final class Fetcher implements DataFetcher<InputStream> {
        private final FirebaseFirestore db;
        private final ReferenciaAvatar avatar;
        private volatile boolean cancelado;
        private InputStream entrada;
        Fetcher(FirebaseFirestore db, ReferenciaAvatar avatar) { this.db = db; this.avatar = avatar; }
        @Override public void loadData(@NonNull Priority prioridade, @NonNull DataCallback<? super InputStream> callback) {
            db.collection("avatares").document(avatar.uid).get().addOnSuccessListener(doc -> {
                if (cancelado) return;
                try {
                    Blob conteudo = doc.getBlob("conteudo");
                    if (!doc.exists() || !avatar.uid.equals(doc.getString("uid")) || conteudo == null)
                        throw new IOException("Avatar indisponível.");
                    byte[] bytes = conteudo.toBytes();
                    if (bytes.length == 0 || bytes.length > FotoPerfilPublicacao.MAX_BYTES) throw new IOException("Avatar inválido.");
                    // Evita decodificar uma imagem malformada com dimensões muito maiores que o avatar.
                    BitmapFactory.Options limites = new BitmapFactory.Options(); limites.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, limites);
                    if (!"image/jpeg".equals(limites.outMimeType) || limites.outWidth <= 0 || limites.outHeight <= 0
                            || limites.outWidth > 256 || limites.outHeight > 256) throw new IOException("Avatar inválido.");
                    // Relatórios antigos podem guardar uma referência anterior: exibem o avatar atual do UID.
                    entrada = new ByteArrayInputStream(bytes);
                    if (!cancelado) callback.onDataReady(entrada);
                } catch (Exception erro) { if (!cancelado) callback.onLoadFailed(erro); }
            }).addOnFailureListener(erro -> { if (!cancelado) callback.onLoadFailed(erro); });
        }
        @Override public void cancel() { cancelado = true; }
        @Override public void cleanup() {
            cancelado = true;
            if (entrada != null) try { entrada.close(); } catch (IOException ignorado) { }
        }
        @NonNull @Override public Class<InputStream> getDataClass() { return InputStream.class; }
        @NonNull @Override public DataSource getDataSource() { return DataSource.REMOTE; }
    }
}
