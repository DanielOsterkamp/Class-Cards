package com.example.classcardsaplicao.model;
import org.junit.Test;
import static org.junit.Assert.*;
public class AvatarModelosTest {
    @Test public void pesquisaPreservaReferenciaSemIncluirBytes(){String r=ReferenciaAvatar.criar("u","12345678-1234-1234-1234-123456789abc");ResultadoUsuarioPesquisa p=new ResultadoUsuarioPesquisa("u","Ana",r);assertEquals(r,p.getFotoUrl());assertEquals("",new ResultadoUsuarioPesquisa("outro","B",r).getFotoUrl());}
    @Test public void rankingPreservaFotoDoFirestore(){String r=ReferenciaAvatar.criar("u","12345678-1234-1234-1234-123456789abc");UsuarioRanking u=new UsuarioRanking("u","Ana",r,1,1,1,"","UTC");assertEquals(r,u.getFotoUrl());}
}
