package com.example.classcardsaplicao.ui.deck.study;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.model.Flashcard;
import com.example.classcardsaplicao.model.Rating;
import com.google.android.material.card.MaterialCardView;

public class EstudarDeckFragment extends Fragment {
    public EstudarDeckFragment() { super(R.layout.fragment_estudar_deck); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        EstudarDeckViewModel vm = new ViewModelProvider(this).get(EstudarDeckViewModel.class);
        String deckId = requireArguments().getString("deckId");
        LinearLayout avaliacoes = view.findViewById(R.id.layoutAvaliacoes);
        MaterialCardView card = view.findViewById(R.id.cardFlashcard);
        TextView conteudo = view.findViewById(R.id.txtConteudoFlashcard);
        TextView instrucao = view.findViewById(R.id.txtInstrucao);
        TextView progresso = view.findViewById(R.id.txtProgresso);
        TextView nome = view.findViewById(R.id.txtNomeDeck);
        TextView erro = view.findViewById(R.id.txtErroEstudo);
        Button tentar = view.findViewById(R.id.btnTentarNovamente);
        Button[] botoes = {view.findViewById(R.id.btnErrei), view.findViewById(R.id.btnDificil),
                view.findViewById(R.id.btnBom), view.findViewById(R.id.btnFacil)};
        Rating[] ratings = {Rating.AGAIN, Rating.HARD, Rating.GOOD, Rating.EASY};
        for (int i = 0; i < botoes.length; i++) {
            Rating rating = ratings[i];
            botoes[i].setOnClickListener(v -> vm.avaliarFlashcard(rating));
        }
        view.findViewById(R.id.btnVoltar).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
        tentar.setOnClickListener(v -> vm.tentarNovamente(deckId));
        card.setOnClickListener(v -> vm.alternarLado());
        vm.getDeck().observe(getViewLifecycleOwner(), deck -> {
            if (deck != null) nome.setText(deck.getTitulo());
        });
        vm.getFlashcards().observe(getViewLifecycleOwner(), fila -> {
            Flashcard atual = vm.getFlashcardAtual();
            boolean ativo = atual != null && vm.getResultado().getValue() == null;
            card.setEnabled(ativo && !Boolean.TRUE.equals(vm.getSalvandoProgresso().getValue()));
            avaliacoes.setVisibility(ativo && vm.isMostrandoResposta() ? View.VISIBLE : View.GONE);
            if (!ativo) {
                conteudo.setText(R.string.sem_revisoes);
                progresso.setText("");
                instrucao.setText("");
                return;
            }
            conteudo.setText(vm.isMostrandoResposta() ? atual.getResposta() : atual.getPergunta());
            progresso.setText(getString(R.string.progresso_estudo, vm.getIndiceAtual() + 1, fila.size()));
            instrucao.setText(vm.isMostrandoResposta() ? R.string.avaliar_lembranca : R.string.revelar_resposta);
        });
        vm.getSalvandoProgresso().observe(getViewLifecycleOwner(), salvando -> {
            boolean livre = !Boolean.TRUE.equals(salvando);
            for (Button botao : botoes) botao.setEnabled(livre && vm.getFlashcardAtual() != null);
            card.setEnabled(livre && vm.getFlashcardAtual() != null && vm.getResultado().getValue() == null);
            tentar.setEnabled(livre);
        });
        vm.getErro().observe(getViewLifecycleOwner(), mensagem -> {
            erro.setText(mensagem);
            erro.setVisibility(mensagem == null ? View.GONE : View.VISIBLE);
            tentar.setVisibility(mensagem == null ? View.GONE : View.VISIBLE);
        });
        vm.getResultado().observe(getViewLifecycleOwner(), resultado -> {
            if (resultado == null) return;
            NavController nav = NavHostFragment.findNavController(this);
            if (nav.getCurrentDestination() == null
                    || nav.getCurrentDestination().getId() != R.id.estudarDeckFragment) return;
            Bundle args = new Bundle();
            args.putInt("ofensivaAtual", resultado.getOfensivaAtual());
            args.putBoolean("conquistou", resultado.isOfensivaAlterada());
            args.putInt("flashcardsEstudados", vm.getIndiceAtual());
            nav.navigate(R.id.ofensivaConquistadaFragment, args,
                    new NavOptions.Builder().setPopUpTo(R.id.estudarDeckFragment, true).build());
        });
        vm.carregarDeck(deckId);
    }
}
