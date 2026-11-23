package com.example.classcardsaplicao.data;
import android.content.Context;
import android.graphics.*;
import android.media.ExifInterface;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class PreparadorFotoInstrumentedTest {
    private final Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private Uri uri(String nome){return Uri.parse("content://com.example.classcardsaplicao.test.fotos/"+nome);}
    @After public void limpar(){for(String nome:new String[]{"foto-grande.jpg","foto-orientada.jpg","foto-invalida.bin","foto-limite.bin"})app.getContentResolver().delete(uri(nome),null,null);}
    private Uri imagem(String nome,int w,int h,boolean png)throws Exception {
        Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);new Canvas(b).drawColor(Color.RED);
        Uri f=uri(nome);try(OutputStream o=app.getContentResolver().openOutputStream(f)){b.compress(png?Bitmap.CompressFormat.PNG:Bitmap.CompressFormat.JPEG,95,o);}finally{b.recycle();}return f;
    }
    @Test public void reduzImagemEProduzJpegQuadrado()throws Exception {
        imagem("foto-grande.jpg",1800,1200,false);
        File f=PreparadorFotoPerfil.preparar(app,uri("foto-grande.jpg"));
        try{BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),o);assertEquals(256,o.outWidth);assertEquals(256,o.outHeight);assertEquals("image/jpeg",o.outMimeType);assertTrue(f.length()<=50*1024);}finally{f.delete();}
    }
    @Test public void imagemComMuitosDetalhesRespeitaCinquentaKb()throws Exception {
        Bitmap b=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);int[] pixels=new int[256*256];java.util.Random random=new java.util.Random(1);
        for(int i=0;i<pixels.length;i++)pixels[i]=Color.rgb(random.nextInt(256),random.nextInt(256),random.nextInt(256));b.setPixels(pixels,0,256,0,0,256,256);
        try(OutputStream o=app.getContentResolver().openOutputStream(uri("foto-ruido.png"))){b.compress(Bitmap.CompressFormat.PNG,100,o);}b.recycle();
        File f=PreparadorFotoPerfil.preparar(app,uri("foto-ruido.png"));try{assertTrue(f.length()<=51200);}finally{f.delete();app.getContentResolver().delete(uri("foto-ruido.png"),null,null);}
    }

    @Test public void corrigeOrientacaoERemoveMetadados()throws Exception {
        Uri f=imagem("foto-orientada.jpg",100,200,false);Bitmap b=Bitmap.createBitmap(100,200,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint paint=new Paint();paint.setColor(Color.RED);c.drawRect(0,0,100,100,paint);paint.setColor(Color.BLUE);c.drawRect(0,100,100,200,paint);
        try(OutputStream out=app.getContentResolver().openOutputStream(f)){b.compress(Bitmap.CompressFormat.JPEG,100,out);}b.recycle();
        try(android.os.ParcelFileDescriptor fd=app.getContentResolver().openFileDescriptor(f,"rw")){ExifInterface e=new ExifInterface(fd.getFileDescriptor());e.setAttribute(ExifInterface.TAG_ORIENTATION,"6");e.setAttribute(ExifInterface.TAG_GPS_LATITUDE,"10/1,0/1,0/1");e.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF,"N");e.saveAttributes();}
        File pronta=PreparadorFotoPerfil.preparar(app,uri("foto-orientada.jpg"));
        try{Bitmap foto=BitmapFactory.decodeFile(pronta.getPath());int esquerda=foto.getPixel(10,50),direita=foto.getPixel(90,50);assertTrue(Color.BLUE==esquerda||Color.blue(esquerda)>Color.red(esquerda));assertTrue(Color.red(direita)>Color.blue(direita));foto.recycle();assertNull(new ExifInterface(pronta.getPath()).getAttribute(ExifInterface.TAG_GPS_LATITUDE));}finally{pronta.delete();}
    }
    @Test public void rejeitaArquivoInvalidoESemUriDoSeletor()throws Exception {
        try(OutputStream o=app.getContentResolver().openOutputStream(uri("foto-invalida.bin"))){o.write(new byte[]{1,2,3});}
        for(Uri u:new Uri[]{uri("foto-invalida.bin"),Uri.parse("file:///foto.jpg")})try{PreparadorFotoPerfil.preparar(app,u);fail("Deveria rejeitar");}catch(IOException esperado){}
    }
    @Test public void rejeitaEntradaMaiorQueVinteMb()throws Exception {
        try(OutputStream o=app.getContentResolver().openOutputStream(uri("foto-limite.bin"))){byte[] bloco=new byte[8192];for(int i=0;i<PreparadorFotoPerfil.MAX_ENTRADA/8192;i++)o.write(bloco);o.write(1);}
        try{PreparadorFotoPerfil.preparar(app,uri("foto-limite.bin"));fail("Deveria rejeitar");}catch(IOException esperado){assertTrue(esperado.getMessage().contains("20 MB"));}
    }
}
