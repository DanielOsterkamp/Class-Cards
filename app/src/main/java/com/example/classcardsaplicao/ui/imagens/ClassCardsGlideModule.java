package com.example.classcardsaplicao.ui.imagens;

import android.content.Context;
import androidx.annotation.NonNull;
import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.module.AppGlideModule;
import java.io.InputStream;

/** Um único registro atende perfil, configurações, pesquisa, ranking e demais fotos do app. */
@GlideModule
public final class ClassCardsGlideModule extends AppGlideModule {
    @Override public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        registry.prepend(String.class, InputStream.class, new AvatarModelLoader.Factory());
    }
    @Override public boolean isManifestParsingEnabled() { return false; }
}
