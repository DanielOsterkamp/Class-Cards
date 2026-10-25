package com.example.classcardsaplicao.ui.rankings;

import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.classcardsaplicao.data.RankingRepository;
import com.example.classcardsaplicao.model.RankingCalculadora;
import com.example.classcardsaplicao.model.RankingSecao;
import com.example.classcardsaplicao.model.UsuarioRanking;
import com.google.firebase.auth.FirebaseAuth;
import java.util.ArrayList;
import java.util.List;

public class RankingsViewModel extends ViewModel {
    public enum Estado { CARREGANDO, PRONTO, ERRO }
    private final RankingRepository repository = new RankingRepository();
    private final MutableLiveData<Estado> estado = new MutableLiveData<>(Estado.CARREGANDO);
    private final MutableLiveData<List<RankingSecao>> secoes = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> erro = new MutableLiveData<>();
    private final Handler relogio = new Handler(Looper.getMainLooper());
    private List<UsuarioRanking> participantes = new ArrayList<>();
    private boolean carregando;
    private boolean carregado;
    private String uidAtual;

    // a versão faz não mostrarmos dados antigos
    private int versao;

    // o recalcular faz com que a cada minuto atualiza tabela automaticamente
    private final Runnable recalcular = new Runnable() {
        @Override public void run() {
            if (carregado && uidAtual != null && uidAtual.equals(FirebaseAuth.getInstance().getUid())) {
                secoes.setValue(RankingCalculadora.montar(participantes, uidAtual, System.currentTimeMillis()));
                relogio.postDelayed(this, 60_000);
            }
        }
    };

    public MutableLiveData<String> getErro() {
        return erro;
    }

    public MutableLiveData<List<RankingSecao>> getSecoes() {
        return secoes;
    }

    public MutableLiveData<Estado> getEstado() {
        return estado;
    }

    public void carregar() {
        if (!carregado && !carregando) atualizar();
    }

    public void atualizar() {

        if (carregando) return;

        String uid = FirebaseAuth.getInstance().getUid();

        if (uid == null) {
            falhar("Entre na sua conta para consultar os rankings");
            return;
        }

        if (uidAtual != null && !uid.equals(uidAtual)) {
            participantes.clear();
            secoes.setValue(new ArrayList<>());
        }

        uidAtual = uid;
        carregando = true;
        erro.setValue(null);
        estado.setValue(Estado.CARREGANDO);
        int carga = ++versao;


        repository.sincronizarAtual().addOnSuccessListener(ignorado -> {

            if (!valida(carga, uid)) return;

            repository.buscarParticipantes().addOnSuccessListener(usuarios -> {

                if (!valida(carga, uid)) return;

                boolean encontrouAtual = false;

                for (UsuarioRanking usuario : usuarios)
                    if (uid.equals(usuario.getUid())) {
                        encontrouAtual = true;
                        break;
                    }

                if (!encontrouAtual) {
                    falhar("Não foi possível atualizar sua posição");
                    return;
                }

                participantes = usuarios;
                carregando = false;
                carregado = true;
                secoes.setValue(RankingCalculadora.montar(usuarios, uid, System.currentTimeMillis()));
                estado.setValue(Estado.PRONTO);
                relogio.removeCallbacks(recalcular);
                relogio.postDelayed(recalcular, 60_000);

            }).addOnFailureListener(e -> {
                if (valida(carga, uid)) falhar("Não foi possível carregar o rankings");
            });
        }).addOnFailureListener(e -> {
            if (valida(carga, uid)) falhar("Não foi possível atualizar o ranking");
        });
    }

    private boolean valida(int carga, String uid) {

        if (carga != versao) return false;

        if (!uid.equals(FirebaseAuth.getInstance().getUid())) {
            participantes.clear();
            secoes.setValue(new ArrayList<>());
            falhar("A conta mudou. Abra os rankings novamente.");
            return false;
        }

        return true;

    }
    private void falhar(String mensagem) {
        carregando = false;
        erro.setValue(mensagem);
        estado.setValue(Estado.ERRO);
    }
    @Override protected void onCleared() {
        versao++;
        relogio.removeCallbacks(recalcular);
    }
}
