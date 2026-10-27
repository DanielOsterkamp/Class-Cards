package com.example.classcardsaplicao.ui.deck.edit;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
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
import com.example.classcardsaplicao.ui.deck.create.FlashcardAdapter;

public class EditarDeckFragment extends Fragment {
    private boolean camposPreenchidos;
    public EditarDeckFragment() { super(R.layout.fragment_editar_deck); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        EditarDeckViewModel viewModel = new ViewModelProvider(this).get(EditarDeckViewModel.class);
        FormularioDeck formulario = new FormularioDeck(view);
        EditText nome = view.findViewById(R.id.inputNomeDeck);
        EditText materia = view.findViewById(R.id.inputMateriaDeck);
        EditText pergunta = view.findViewById(R.id.inputPergunta);
        EditText resposta = view.findViewById(R.id.inputResposta);
        EditText descricao = view.findViewById(R.id.inputDescricaoIA);
        EditText quantidade = view.findViewById(R.id.inputNumeroCards);
        String deckId = requireArguments().getString("deckId");
        if (savedInstanceState != null) camposPreenchidos = savedInstanceState.getBoolean("camposDeckPreenchidos", false);
        ((TextView) view.findViewById(R.id.btnSalvarDeck)).setText(R.string.deck_salvar_alteracoes);
        FlashcardAdapter adapter = new FlashcardAdapter();
        RecyclerView recycler = view.findViewById(R.id.recyclerFlashcards);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        adapter.setOnExcluirFlashcardListener(viewModel::removerFlashcard);
        adapter.setOnEditarFlashcardListener((posicao, card) -> FlashcardDialogHelper.abrirDialogEditar(
                requireContext(), card, (novaPergunta, novaResposta) ->
                        viewModel.editarFlashcard(posicao, novaPergunta, novaResposta)));
        viewModel.getFlashcards().observe(getViewLifecycleOwner(), adapter::atualizarFlashcards);
        viewModel.getDeck().observe(getViewLifecycleOwner(), deck -> {
            // Na recriação, o Android restaura os campos, incluindo alterações ainda não salvas.
            if (!camposPreenchidos) {
                nome.setText(deck.getTitulo());
                materia.setText(deck.getMateria());
                camposPreenchidos = true;
            }
        });

        view.findViewById(R.id.btnVoltar).setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        view.findViewById(R.id.btnTentarCarregarDeck).setOnClickListener(v -> viewModel.carregarDeck(deckId));
        view.findViewById(R.id.btnGerarFlashcards).setOnClickListener(v -> viewModel.gerarFlashcards(
                materia.getText().toString(), descricao.getText().toString(), quantidade.getText().toString()));
        view.findViewById(R.id.btnSalvarDeck).setOnClickListener(v -> viewModel.salvarAlteracoes(
                nome.getText().toString(), materia.getText().toString()));
        view.findViewById(R.id.btnCriarFlashcard).setOnClickListener(v -> {
            String p = pergunta.getText().toString().trim();
            String r = resposta.getText().toString().trim();
            if (p.isEmpty()) { pergunta.setError("Digite uma pergunta."); return; }
            if (r.isEmpty()) { resposta.setError("Digite uma resposta."); return; }
            viewModel.adicionarFlashcard(p, r);
            pergunta.setText("");
            resposta.setText("");
        });

        Runnable atualizar = () -> {
            boolean gerando = Boolean.TRUE.equals(viewModel.getGerandoFlashcards().getValue());
            boolean salvando = Boolean.TRUE.equals(viewModel.getSalvando().getValue());
            boolean pronto = Boolean.TRUE.equals(viewModel.getPronto().getValue());
            boolean carregando = Boolean.TRUE.equals(viewModel.getCarregando().getValue());
            formulario.atualizar(gerando, salvando, pronto);
            adapter.setEdicaoHabilitada(pronto && !gerando && !salvando);
            view.findViewById(R.id.progressCarregarDeck).setVisibility(carregando ? View.VISIBLE : View.GONE);
            boolean falha = !pronto && !carregando && viewModel.getErro().getValue() != null;
            TextView mensagem = view.findViewById(R.id.txtErroCarregarDeck);
            mensagem.setText(viewModel.getErro().getValue());
            mensagem.setVisibility(falha ? View.VISIBLE : View.GONE);
            view.findViewById(R.id.btnTentarCarregarDeck).setVisibility(falha ? View.VISIBLE : View.GONE);
        };
        viewModel.getGerandoFlashcards().observe(getViewLifecycleOwner(), valor -> atualizar.run());
        viewModel.getSalvando().observe(getViewLifecycleOwner(), valor -> atualizar.run());
        viewModel.getPronto().observe(getViewLifecycleOwner(), valor -> atualizar.run());
        viewModel.getCarregando().observe(getViewLifecycleOwner(), valor -> atualizar.run());
        viewModel.getErro().observe(getViewLifecycleOwner(), mensagem -> {
            atualizar.run();
            if (mensagem != null && Boolean.TRUE.equals(viewModel.getPronto().getValue())) {
                Toast.makeText(requireContext(), mensagem, Toast.LENGTH_SHORT).show();
            }
        });
        viewModel.getSalvo().observe(getViewLifecycleOwner(), salvo -> {
            if (!Boolean.TRUE.equals(salvo)) return;
            androidx.navigation.NavController nav = NavHostFragment.findNavController(this);
            if (nav.getCurrentDestination() != null && nav.getCurrentDestination().getId() == R.id.editarDeckFragment) {
                Toast.makeText(requireContext(), "Deck atualizado com sucesso.", Toast.LENGTH_SHORT).show();
                nav.popBackStack();
            }
        });
        viewModel.carregarDeck(deckId);
    }

    @Override public void onSaveInstanceState(@NonNull Bundle estado) {
        estado.putBoolean("camposDeckPreenchidos", camposPreenchidos);
        super.onSaveInstanceState(estado);
    }
}
