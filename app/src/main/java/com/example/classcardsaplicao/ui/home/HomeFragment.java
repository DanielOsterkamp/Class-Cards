package com.example.classcardsaplicao.ui.home;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.ui.deck.create.DeckAdapter;
import com.google.android.material.button.MaterialButton;

public class HomeFragment extends Fragment {

    private HomeViewModel mViewModel;

    public static HomeFragment newInstance() {
        return new HomeFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);



        RecyclerView recyclerSeusDecks = view.findViewById(R.id.recyclerSeusDecks);

        DeckAdapter deckAdapter = new DeckAdapter();

        HomeViewModel viewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        deckAdapter.setOnDeckClickListener(deck -> {

            Bundle bundle = new Bundle();
            bundle.putString("deckId", deck.getId());

            NavHostFragment.findNavController(this)
                    .navigate(
                            R.id.deckSelecionadoFragment,
                            bundle
                    );
        });

        viewModel.getDecks().observe(
                getViewLifecycleOwner(),
                decks -> deckAdapter.atualizarDecks(decks)
        );

        viewModel.getErro().observe(
                getViewLifecycleOwner(),
                erro -> Toast.makeText(
                        requireContext(),
                        erro,
                        Toast.LENGTH_SHORT
                ).show()
        );

        viewModel.carregarDecks();

        recyclerSeusDecks.setLayoutManager(
                new LinearLayoutManager(
                        requireContext(),
                        LinearLayoutManager.HORIZONTAL,
                        false
                )
        );

        recyclerSeusDecks.setAdapter(deckAdapter);


        ImageButton btnMenu = view.findViewById(R.id.btnMenu);

        btnMenu.setOnClickListener(v -> {
            DrawerLayout drawerLayout =
                    requireActivity().findViewById(R.id.drawerLayout);

            drawerLayout.openDrawer(GravityCompat.START);
        });

        MaterialButton btnCriarDeck = view.findViewById(R.id.btnCriarDeck);

        btnCriarDeck.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_homeFragment_to_criarDeckFragment);
        });
    }

}