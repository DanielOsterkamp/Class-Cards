package com.example.classcardsaplicao.data;
import com.example.classcardsaplicao.model.ReferenciaAvatar;
import org.junit.Test;
import static org.junit.Assert.*;
public class FotoPerfilPublicacaoTest {
    private static final String V="12345678-1234-1234-1234-123456789abc";
    @Test public void referenciaInternaPertenceAoUid(){String r=ReferenciaAvatar.criar("u",V);assertTrue(ReferenciaAvatar.valida(r));assertEquals("u",ReferenciaAvatar.ler(r).uid);assertEquals(V,ReferenciaAvatar.ler(r).versao);assertEquals(r,ReferenciaAvatar.validarPara("u",r));assertEquals("",ReferenciaAvatar.validarPara("outro",r));}
    @Test public void nuncaPublicaUriDoAparelho(){assertEquals("",ReferenciaAvatar.validarPara("u","content://galeria/1"));assertEquals("",ReferenciaAvatar.validarPara("u","file:///foto.jpg"));assertEquals("",ReferenciaAvatar.validarPara("u","http://foto"));assertEquals("https://foto",ReferenciaAvatar.validarPara("u","https://foto"));}
    @Test public void rejeitaReferenciaMalformada(){for(String r:new String[]{"avatar-firestore:u/invalido","avatar-firestore:../"+V,"avatar-firestore:u/../"+V,"avatar-firestore:/"+V})assertFalse(r,ReferenciaAvatar.valida(r));}
    @Test public void uidNuloNaoProduzReferencia(){try{ReferenciaAvatar.criar(null,V);fail();}catch(IllegalArgumentException esperado){}}
}
