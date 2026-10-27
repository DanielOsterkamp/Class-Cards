package com.example.classcardsaplicao.ui.profile;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.ui.deck.create.DeckAdapter;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;

public class PerfilFragment extends Fragment {

    public PerfilFragment() {
        super(R.layout.fragment_perfil);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        ShapeableImageView imgPerfil = view.findViewById(R.id.imgPerfil);

        ImageButton btnVoltar = view.findViewById(R.id.btnVoltar);

        RecyclerView recyclerDecksUsuario =
                view.findViewById(R.id.recyclerDecksUsuario);

        DeckAdapter deckAdapter = new DeckAdapter();

        recyclerDecksUsuario.setLayoutManager(
                new LinearLayoutManager(
                        requireContext(),
                        LinearLayoutManager.HORIZONTAL,
                        false
                )
        );

        recyclerDecksUsuario.setAdapter(deckAdapter);

        btnVoltar.setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());

        TextView txtNomeUsuario = view.findViewById(R.id.txtNomeUsuario);

        PerfilViewModel viewModel =
                new ViewModelProvider(this).get(PerfilViewModel.class);

        String uid = getArguments() == null ? null : getArguments().getString("uid");
        boolean outroUsuario = uid != null && !uid.isEmpty()
                && !uid.equals(FirebaseAuth.getInstance().getUid());
        ((TextView) view.findViewById(R.id.txtTituloDecks)).setText(outroUsuario
                ? R.string.perfil_decks_usuario : R.string.perfil_decks_proprios);
        view.findViewById(R.id.btnTentarPerfil).setOnClickListener(v -> viewModel.carregarPerfil(uid));

        viewModel.getDecks().observe(
                getViewLifecycleOwner(),
                decks -> {
                    deckAdapter.atualizarDecks(decks);
                    apresentarEstado(view, viewModel);
                }
        );

        deckAdapter.setOnDeckClickListener(deck -> {

            if (NavHostFragment.findNavController(this).getCurrentDestination() == null
                    || NavHostFragment.findNavController(this).getCurrentDestination().getId()
                    != R.id.perfilFragment) return;

            Bundle bundle = new Bundle();
            bundle.putString("deckId", deck.getId());

            NavHostFragment.findNavController(this)
                    .navigate(
                            R.id.deckSelecionadoFragment,
                            bundle
                    );
        });

        viewModel.getFotoUrl().observe(getViewLifecycleOwner(), fotoUrl -> {
            Glide.with(this)
                    .load(fotoUrl == null || fotoUrl.isEmpty() ? null : fotoUrl)
                    .placeholder(R.drawable.ranking_avatar_placeholder)
                    .error(R.drawable.ranking_avatar_placeholder)
                    .fallback(R.drawable.ranking_avatar_placeholder)
                    .circleCrop()
                    .into(imgPerfil);
        });

        viewModel.getNome().observe(getViewLifecycleOwner(), nome -> {
            txtNomeUsuario.setText(nome);
        });

        viewModel.getErro().observe(getViewLifecycleOwner(), erro -> apresentarEstado(view, viewModel));
        viewModel.getCarregando().observe(getViewLifecycleOwner(), carregando -> apresentarEstado(view, viewModel));

        java.text.NumberFormat numeros = java.text.NumberFormat.getIntegerInstance(
                java.util.Locale.forLanguageTag("pt-BR"));
        TextView txtOfensiva = view.findViewById(R.id.txtOfensiva);
        TextView txtEstudados = view.findViewById(R.id.txtQuantidadeEstudados);
        TextView txtXp = view.findViewById(R.id.txtXp);
        viewModel.getOfensiva().observe(getViewLifecycleOwner(), valor -> {
            txtOfensiva.setText(valor == null ? "—" : numeros.format(valor));
            txtOfensiva.setContentDescription(valor == null ? "Ofensiva indisponível"
                    : numeros.format(valor) + " dias de ofensiva");
        });
        viewModel.getCardsEstudados().observe(getViewLifecycleOwner(), valor ->
                txtEstudados.setText(valor == null ? "—" : numeros.format(valor)));
        viewModel.getXp().observe(getViewLifecycleOwner(), valor ->
                txtXp.setText(valor == null ? "—" : numeros.format(valor)));
    }

    private void apresentarEstado(View view, PerfilViewModel viewModel) {
        boolean carregando = Boolean.TRUE.equals(viewModel.getCarregando().getValue());
        String erro = viewModel.getErro().getValue();
        boolean temErro = erro != null && !erro.isEmpty();
        boolean vazio = viewModel.getDecks().getValue() != null
                && viewModel.getDecks().getValue().isEmpty();
        view.findViewById(R.id.layoutEstadoPerfil).setVisibility(
                carregando || temErro || vazio ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.progressPerfil).setVisibility(carregando ? View.VISIBLE : View.GONE);
        TextView mensagem = view.findViewById(R.id.txtEstadoPerfil);
        mensagem.setVisibility(!carregando && (temErro || vazio) ? View.VISIBLE : View.GONE);
        if (temErro) mensagem.setText(erro);
        else mensagem.setText(R.string.perfil_sem_decks);
        view.findViewById(R.id.btnTentarPerfil).setVisibility(
                temErro && !carregando ? View.VISIBLE : View.GONE);
    }

    @Override public void onResume() {
        super.onResume();
        // Recalcula a ofensiva ao voltar ao perfil, inclusive após a mudança do dia.
        String uid = getArguments() == null ? null : getArguments().getString("uid");
        new ViewModelProvider(this).get(PerfilViewModel.class).carregarPerfil(uid);
    }
}
