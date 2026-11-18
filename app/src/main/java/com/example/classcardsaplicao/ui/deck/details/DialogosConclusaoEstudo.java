package com.example.classcardsaplicao.ui.deck.details;

import android.os.Bundle;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.annotation.NonNull;

import com.example.classcardsaplicao.ui.deck.study.ConclusaoEstudo;
import com.google.firebase.auth.FirebaseAuth;

/** Apresenta ofensiva e resumo em sequência no deck, mantendo a etapa ao recriar a tela. */
public final class DialogosConclusaoEstudo {
    private final Fragment tela;
    private final SavedStateHandle salvo;

    public DialogosConclusaoEstudo(Fragment tela, SavedStateHandle salvo) {
        this.tela = tela;
        this.salvo = salvo;
        FragmentManager manager = tela.getChildFragmentManager();
        // dismiss() pode notificar antes de a remoção do DialogFragment terminar.
        // Retoma a fila após a remoção para não perder o próximo diálogo nem sobrepor janelas.
        FragmentManager.FragmentLifecycleCallbacks fechamentos = new FragmentManager.FragmentLifecycleCallbacks() {
            @Override public void onFragmentDetached(@NonNull FragmentManager fm, @NonNull Fragment fragment) {
                if (fragment instanceof DialogConclusaoEstudo) agendar();
            }
        };
        manager.registerFragmentLifecycleCallbacks(fechamentos, false);
        tela.getViewLifecycleOwner().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override public void onDestroy(@NonNull LifecycleOwner owner) {
                manager.unregisterFragmentLifecycleCallbacks(fechamentos);
            }
        });
        manager.setFragmentResultListener(OfensivaConquistadaDialog.FECHADO, tela.getViewLifecycleOwner(),
                (chave, resultado) -> {
                    Bundle dados = pendente(resultado);
                    if (dados == null) return;
                    Bundle proximo = new Bundle(dados);
                    proximo.putBoolean("ofensivaFechada", true);
                    salvo.set(ConclusaoEstudo.CHAVE, proximo);
                    agendar();
                });
        manager.setFragmentResultListener(ResumoEstudoDialog.FECHADO, tela.getViewLifecycleOwner(),
                (chave, resultado) -> {
                    if (pendente(resultado) != null) salvo.remove(ConclusaoEstudo.CHAVE);
                });
    }

    private Bundle pendente(Bundle resultado) {
        Bundle dados = salvo.get(ConclusaoEstudo.CHAVE);
        return dados != null && dados.getString("sessaoId", "").equals(resultado.getString("sessaoId", ""))
                ? dados : null;
    }

    public void agendar() {
        View view = tela.getView();
        if (view != null) view.post(() -> {
            if (tela.getView() == view) mostrar();
        });
    }

    public void mostrar() {
        if (!tela.isAdded() || tela.getView() == null
                || !tela.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) return;
        FragmentManager manager = tela.getChildFragmentManager();
        if (manager.isStateSaved()) return;
        Bundle dados = salvo.get(ConclusaoEstudo.CHAVE);
        if (dados == null) return;
        String uid = dados.getString("uid");
        if (uid != null && !uid.equals(FirebaseAuth.getInstance().getUid())) {
            salvo.remove(ConclusaoEstudo.CHAVE);
            return;
        }
        if (manager.findFragmentByTag(OfensivaConquistadaDialog.TAG) != null
                || manager.findFragmentByTag(ResumoEstudoDialog.TAG) != null) return;

        DialogConclusaoEstudo dialog = dados.getBoolean("conquistada") && !dados.getBoolean("ofensivaFechada")
                ? new OfensivaConquistadaDialog() : new ResumoEstudoDialog();
        dialog.setArguments(new Bundle(dados));
        dialog.showNow(manager, dialog instanceof OfensivaConquistadaDialog
                ? OfensivaConquistadaDialog.TAG : ResumoEstudoDialog.TAG);
    }
}
