package com.example.classcardsaplicao.data;

import android.content.Context;
import android.graphics.*;
import android.media.ExifInterface;
import android.net.Uri;
import java.io.*;

public final class PreparadorFotoPerfil {
    public static final int MAX_ENTRADA = 20 * 1024 * 1024;
    public static final int MAX_SAIDA = 50 * 1024;
    public static final int LADO = 256;
    private PreparadorFotoPerfil() { }

    public static File preparar(Context context, Uri uri) throws IOException {
        if (uri == null || !"content".equals(uri.getScheme()))
            throw new IOException("Escolha uma imagem pelo seletor de fotos.");

        File origem = File.createTempFile("foto-origem-", ".tmp", context.getCacheDir());
        File destino = null;
        Bitmap original = null, orientada = null, finalizada = null;

        try {
            try (InputStream entrada = context.getContentResolver().openInputStream(uri);
                 OutputStream saida = new FileOutputStream(origem)) {

                if (entrada == null)
                    throw new IOException("Não foi possível abrir a imagem.");

                byte[] bloco = new byte[8192]; int total = 0, quantidade;
                while ((quantidade = entrada.read(bloco)) != -1) {
                    if (Thread.currentThread().isInterrupted())
                        throw new IOException("Seleção cancelada.");

                    total += quantidade;

                    if (total > MAX_ENTRADA)
                        throw new IOException("Escolha uma imagem de até 20 MB.");

                    saida.write(bloco, 0, quantidade);
                }
            }

            BitmapFactory.Options limites = new BitmapFactory.Options(); limites.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(origem.getPath(), limites);

            if (limites.outWidth <= 0 || limites.outHeight <= 0 || (long) limites.outWidth * limites.outHeight > 40_000_000)
                throw new IOException("Imagem inválida ou muito grande. Escolha outra foto.");

            BitmapFactory.Options opcoes = new BitmapFactory.Options(); opcoes.inSampleSize = 1;

            while (Math.max(limites.outWidth, limites.outHeight) / opcoes.inSampleSize > 1024)
                opcoes.inSampleSize *= 2;

            original = BitmapFactory.decodeFile(origem.getPath(), opcoes);

            if (original == null)
                throw new IOException("Não foi possível ler a imagem. Escolha outra foto.");

            Matrix matriz = new Matrix();
            int orientacao = ExifInterface.ORIENTATION_NORMAL;
            try { orientacao = new ExifInterface(origem.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL); }
            catch (IOException ignorado) { /* O formato pode ser legível sem possuir EXIF. */ }

            switch (orientacao) {
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matriz.setScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_180: matriz.setRotate(180); break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL: matriz.setScale(1, -1); break;
                case ExifInterface.ORIENTATION_TRANSPOSE: matriz.setRotate(90); matriz.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_90: matriz.setRotate(90); break;
                case ExifInterface.ORIENTATION_TRANSVERSE: matriz.setRotate(-90); matriz.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_270: matriz.setRotate(-90); break;
                default: break;
            }

            orientada = Bitmap.createBitmap(original, 0, 0, original.getWidth(), original.getHeight(), matriz, true);

            int menor = Math.min(orientada.getWidth(), orientada.getHeight());
            int lado = Math.min(LADO, menor);

            finalizada = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(finalizada); canvas.drawColor(Color.WHITE);

            Rect recorte = new Rect((orientada.getWidth()-menor)/2, (orientada.getHeight()-menor)/2,
                    (orientada.getWidth()+menor)/2, (orientada.getHeight()+menor)/2);

            canvas.drawBitmap(orientada, recorte, new Rect(0, 0, lado, lado), new Paint(Paint.FILTER_BITMAP_FLAG));
            destino = File.createTempFile("foto-perfil-", ".jpg", context.getCacheDir());

            for (int qualidade : new int[]{85, 75, 65, 55, 45}) {
                try (OutputStream saida = new FileOutputStream(destino)) {
                    if (!finalizada.compress(Bitmap.CompressFormat.JPEG, qualidade, saida)) throw new IOException("Não foi possível preparar a foto.");
                }
                if (destino.length() <= MAX_SAIDA) break;
            }
            if (destino.length() > MAX_SAIDA)
                throw new IOException("Escolha uma foto menor.");

            File resultado = destino; destino = null; return resultado;
        } finally {
            origem.delete(); if (destino != null) destino.delete();
            if (finalizada != null)
                finalizada.recycle();
            if (orientada != null && orientada != original)
                orientada.recycle();
            if (original != null)
                original.recycle();
        }
    }
}
