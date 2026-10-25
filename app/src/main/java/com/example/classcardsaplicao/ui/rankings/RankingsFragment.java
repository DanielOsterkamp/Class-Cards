package com.example.classcardsaplicao.ui.rankings;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.classcardsaplicao.R;

public class RankingsFragment extends Fragment {

    public RankingsFragment() {
        super(R.layout.fragment_rankings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RankingsViewModel viewModel = new ViewModelProvider(this).get(RankingsViewModel.class);
        RankingsAdapter adapter = new RankingsAdapter();
        RecyclerView recycler = view.findViewById(R.id.recyclerRankings);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        recycler.setHasFixedSize(false);

        view.findViewById(R.id.btnVoltar).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
        view.findViewById(R.id.btnAtualizarRankings).setOnClickListener(v -> viewModel.atualizar());
        view.findViewById(R.id.btnTentarRankings).setOnClickListener(v -> viewModel.atualizar());

        viewModel.getSecoes().observe(getViewLifecycleOwner(), secoes -> {
            adapter.atualizar(secoes);
            apresentarEstado(view, adapter, viewModel.getEstado().getValue());
        });

        viewModel.getErro().observe(getViewLifecycleOwner(), erro -> {
            String mensagem = erro == null || erro.trim().isEmpty()
                    ? getString(R.string.rankings_erro_generico) : erro;
            ((TextView) view.findViewById(R.id.txtErroRankings)).setText(mensagem);
            ((TextView) view.findViewById(R.id.txtAvisoRankings)).setText(mensagem);
        });

        viewModel.getEstado().observe(getViewLifecycleOwner(), estado ->
                apresentarEstado(view, adapter, estado));

        viewModel.carregar();
    }

    private void apresentarEstado(View view, RankingsAdapter adapter, RankingsViewModel.Estado estado) {

        boolean carregando = estado == null || estado == RankingsViewModel.Estado.CARREGANDO;
        boolean erro = estado == RankingsViewModel.Estado.ERRO;
        boolean temDados = adapter.getItemCount() > 0;

        view.findViewById(R.id.recyclerRankings).setVisibility(temDados ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.containerCarregandoRankings).setVisibility(carregando && !temDados ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.containerErroRankings).setVisibility(erro && !temDados ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.txtAvisoRankings).setVisibility(erro && temDados ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.btnAtualizarRankings).setVisibility(carregando ? View.INVISIBLE : View.VISIBLE);
        view.findViewById(R.id.btnAtualizarRankings).setEnabled(!carregando);
        view.findViewById(R.id.progressAtualizarRankings).setVisibility(carregando ? View.VISIBLE : View.GONE);
    }
}
