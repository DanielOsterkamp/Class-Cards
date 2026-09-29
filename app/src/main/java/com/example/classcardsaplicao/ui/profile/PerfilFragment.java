package com.example.classcardsaplicao.ui.profile;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.ui.deck.create.DeckAdapter;
import com.google.android.material.imageview.ShapeableImageView;

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

        ImageButton btnMenu = view.findViewById(R.id.btnMenu);

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

        btnMenu.setOnClickListener(v -> {
            DrawerLayout drawerLayout =
                    requireActivity().findViewById(R.id.drawerLayout);

            drawerLayout.openDrawer(GravityCompat.START);
        });

        TextView txtNomeUsuario = view.findViewById(R.id.txtNomeUsuario);

        PerfilViewModel viewModel =
                new ViewModelProvider(this).get(PerfilViewModel.class);

        viewModel.getDecks().observe(
                getViewLifecycleOwner(),
                decks -> deckAdapter.atualizarDecks(decks)
        );

        deckAdapter.setOnDeckClickListener(deck -> {

            Bundle bundle = new Bundle();
            bundle.putString("deckId", deck.getId());

            NavHostFragment.findNavController(this)
                    .navigate(
                            R.id.deckSelecionadoFragment,
                            bundle
                    );
        });

        viewModel.getFotoUrl().observe(getViewLifecycleOwner(), fotoUrl -> {

            if (fotoUrl != null && !fotoUrl.isEmpty()) {
                Glide.with(this)
                        .load(fotoUrl)
                        .into(imgPerfil);
            }
        });

        viewModel.getNome().observe(getViewLifecycleOwner(), nome -> {
            txtNomeUsuario.setText(nome);
        });

        viewModel.getErro().observe(getViewLifecycleOwner(), erro -> {
            Toast.makeText(requireContext(), erro, Toast.LENGTH_SHORT).show();
        });

        viewModel.carregarPerfil();
    }
}