package com.example.classcardsaplicao.ui.deck.create;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.ui.deck.FlashcardDialogHelper;
import com.example.classcardsaplicao.ui.deck.FormularioDeck;
import com.google.firebase.auth.FirebaseAuth;

public class CriarDeckFragment extends Fragment {
    public CriarDeckFragment() { super(R.layout.fragment_criar_deck); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        CriarDeckViewModel viewModel = new ViewModelProvider(this).get(CriarDeckViewModel.class);
        FormularioDeck formulario = new FormularioDeck(view);
        EditText nome = view.findViewById(R.id.inputNomeDeck);
        EditText materia = view.findViewById(R.id.inputMateriaDeck);
        EditText pergunta = view.findViewById(R.id.inputPergunta);
        EditText resposta = view.findViewById(R.id.inputResposta);
        EditText descricao = view.findViewById(R.id.inputDescricaoIA);
        EditText quantidade = view.findViewById(R.id.inputNumeroCards);
        FlashcardAdapter adapter = new FlashcardAdapter();
        RecyclerView recycler = view.findViewById(R.id.recyclerFlashcards);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        adapter.setOnExcluirFlashcardListener(viewModel::removerFlashcard);
        adapter.setOnEditarFlashcardListener((posicao, card) -> FlashcardDialogHelper.abrirDialogEditar(
                requireContext(), card, (novaPergunta, novaResposta) ->
                        viewModel.editarFlashcard(posicao, novaPergunta, novaResposta)));
        viewModel.getFlashcards().observe(getViewLifecycleOwner(), adapter::atualizarFlashcards);

        view.findViewById(R.id.btnVoltar).setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        view.findViewById(R.id.btnGerarFlashcards).setOnClickListener(v -> viewModel.gerarFlashcards(
                materia.getText().toString(), descricao.getText().toString(), quantidade.getText().toString()));
        view.findViewById(R.id.btnCriarFlashcard).setOnClickListener(v -> {
            String p = pergunta.getText().toString().trim();
            String r = resposta.getText().toString().trim();
            if (p.isEmpty()) { pergunta.setError("Digite uma pergunta."); return; }
            if (r.isEmpty()) { resposta.setError("Digite uma resposta."); return; }
            viewModel.adicionarFlashcard(p, r);
            pergunta.setText("");
            resposta.setText("");
        });
        view.findViewById(R.id.btnSalvarDeck).setOnClickListener(v -> {
            String uid = FirebaseAuth.getInstance().getUid();
            if (uid == null) {
                Toast.makeText(requireContext(), "Usuário não autenticado.", Toast.LENGTH_SHORT).show();
                return;
            }
            viewModel.salvarDeck(uid, nome.getText().toString(), materia.getText().toString());
        });

        Runnable atualizar = () -> {
            boolean gerando = Boolean.TRUE.equals(viewModel.getGerandoFlashcards().getValue());
            boolean salvando = Boolean.TRUE.equals(viewModel.getSalvando().getValue());
            formulario.atualizar(gerando, salvando, true);
            adapter.setEdicaoHabilitada(!gerando && !salvando);
        };
        viewModel.getGerandoFlashcards().observe(getViewLifecycleOwner(), valor -> atualizar.run());
        viewModel.getSalvando().observe(getViewLifecycleOwner(), valor -> atualizar.run());
        viewModel.getErro().observe(getViewLifecycleOwner(), mensagem -> {
            if (mensagem != null) Toast.makeText(requireContext(), mensagem, Toast.LENGTH_SHORT).show();
        });
        viewModel.getDeckCriado().observe(getViewLifecycleOwner(), id -> {
            if (id == null) return;
            androidx.navigation.NavController nav = NavHostFragment.findNavController(this);
            if (nav.getCurrentDestination() != null && nav.getCurrentDestination().getId() == R.id.criarDeckFragment) {
                Toast.makeText(requireContext(), "Deck criado com sucesso.", Toast.LENGTH_SHORT).show();
                nav.navigate(R.id.action_criarDeckFragment_to_homeFragment);
            }
        });
    }
}