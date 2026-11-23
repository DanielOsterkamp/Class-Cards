package com.example.classcardsaplicao.data;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;
public class FotoTesteProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    public String getType(Uri uri){return "image/jpeg";}
    public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    public int delete(Uri u,String s,String[] a){String nome=u.getLastPathSegment();if(nome==null||!nome.matches("[a-z-]+[.](jpg|png|bin)"))return 0;return new File(getContext().getFilesDir(),nome).delete()?1:0;}
    public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
    public ParcelFileDescriptor openFile(Uri uri,String modo)throws FileNotFoundException {
        String nome=uri.getLastPathSegment();
        if(nome==null||!nome.matches("[a-z-]+[.](jpg|png|bin)"))throw new FileNotFoundException();
        int flags="r".equals(modo)?ParcelFileDescriptor.MODE_READ_ONLY:ParcelFileDescriptor.MODE_READ_WRITE|ParcelFileDescriptor.MODE_CREATE;
        if("w".equals(modo)||"wt".equals(modo))flags|=ParcelFileDescriptor.MODE_TRUNCATE;
        return ParcelFileDescriptor.open(new File(getContext().getFilesDir(),nome),flags);
    }
}
