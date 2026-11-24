package com.example.classcardsaplicao.ui.configuracoes;

import android.app.Application;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.lifecycle.*;
import com.example.classcardsaplicao.data.*;
import java.io.File;
import java.util.Objects;

public class ConfiguracoesViewModel extends AndroidViewModel {
    private final FotoPerfilFonte fonte;
    public final MutableLiveData<String> foto = new MutableLiveData<>("");
    public final MutableLiveData<File> previa = new MutableLiveData<>();
    public final MutableLiveData<Boolean> carregando = new MutableLiveData<>(false);
    public final MutableLiveData<Boolean> preparando = new MutableLiveData<>(false);
    public final MutableLiveData<Boolean> salvando = new MutableLiveData<>(false);
    public final MutableLiveData<String> erro = new MutableLiveData<>();
    public final MutableLiveData<Boolean> sucesso = new MutableLiveData<>(false);
    private Uri selecaoPendente;
    private String uid;
    private int versao;
    private boolean encerrado, carregado;
    public ConfiguracoesViewModel(@NonNull Application app) { this(app, new FotoPerfilRepository(app)); }
    ConfiguracoesViewModel(Application app, FotoPerfilFonte fonte) { super(app); this.fonte = fonte; }
    public boolean temConta() { return uid != null && uid.equals(fonte.uidAtual()); }
    public boolean ocupado() { return Boolean.TRUE.equals(preparando.getValue()) || Boolean.TRUE.equals(salvando.getValue()); }

    public void carregar() {
        String conta = fonte.uidAtual();
        if (!Objects.equals(uid,conta))
            limparConta();
        if (carregado || Boolean.TRUE.equals(carregando.getValue()) || ocupado())
            return;
        uid = conta;
        if (uid == null) { erro.setValue("Entre na sua conta para alterar a foto.");
            return;
        }

        int carga = ++versao;
        erro.setValue(null); carregando.setValue(true);

        fonte.carregar(uid, new FotoPerfilFonte.Callback<String>() {
            public void sucesso(String url) {
                if (!valida(carga)) return; foto.setValue(url); carregado = true; carregando.setValue(false);
                if (selecaoPendente != null) {
                    Uri pendente = selecaoPendente; selecaoPendente = null; selecionar(pendente);
                }
            }
            public void erro(Exception e) { if (!valida(carga)) return; carregando.setValue(false); erro.setValue("Não foi possível carregar sua foto. Tente novamente."); }
        });
    }
    public void selecionar(Uri uri) {
        if (uri == null || ocupado())
            return;

        if (Boolean.TRUE.equals(carregando.getValue())) {
            selecaoPendente = uri; return;
        }

        if (!Objects.equals(uid,fonte.uidAtual()) || uid == null) {
            limparConta(); return;
        }

        int carga = ++versao;
        preparando.setValue(true);
        erro.setValue(null);
        sucesso.setValue(false);

        fonte.preparar(uri,new FotoPerfilFonte.Callback<File>() {
            public void sucesso(File arquivo) {
                if (!valida(carga)) { arquivo.delete(); return; }
                descartarArquivo(); previa.setValue(arquivo); preparando.setValue(false);
            }
            public void erro(Exception e) { if (!valida(carga)) return; preparando.setValue(false); erro.setValue(e instanceof java.io.IOException ? e.getMessage() : "Não foi possível preparar a foto. Escolha outra imagem."); }
        });
    }
    public void descartar() { if (ocupado()) return; descartarArquivo(); previa.setValue(null); erro.setValue(null); sucesso.setValue(false); }
    public void salvar() {
        File arquivo = previa.getValue();

        if (arquivo == null || ocupado())
            return;

        if (uid == null || !uid.equals(fonte.uidAtual())) {
            limparConta();
            return;
        }

        int carga = ++versao;
        salvando.setValue(true);
        erro.setValue(null);
        sucesso.setValue(false);

        fonte.salvar(uid,arquivo,new FotoPerfilFonte.Callback<String>() {
            public void sucesso(String url) {

                if (!valida(carga))
                    return;

                foto.setValue(url);
                descartarArquivo();
                previa.setValue(null);
                salvando.setValue(false);
                sucesso.setValue(true);
            }
            public void erro(Exception e) {
                if (!valida(carga))
                    return;
                salvando.setValue(false); erro.setValue("Não foi possível salvar a foto. Confira sua conexão e tente novamente.");
            }
        });
    }
    public void conferirConta() {
        if (!Objects.equals(uid,fonte.uidAtual())) limparConta();
    }
    private boolean valida(int carga) {
        return !encerrado && carga==versao && uid!=null && uid.equals(fonte.uidAtual());
    }
    private void descartarArquivo() {
        File f=previa.getValue(); if(f!=null)f.delete();
    }
    private void limparConta() {
        ++versao;
        selecaoPendente = null;
        fonte.cancelar();
        descartarArquivo();
        previa.setValue(null);
        foto.setValue("");
        preparando.setValue(false);
        salvando.setValue(false);
        carregando.setValue(false);
        carregado=false;
        uid=fonte.uidAtual();
        sucesso.setValue(false);
        erro.setValue("Entre na sua conta para alterar a foto.");
    }
    @Override protected void onCleared() { encerrado=true; ++versao; descartarArquivo(); fonte.encerrar(); }
}
