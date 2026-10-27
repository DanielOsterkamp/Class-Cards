package com.example.classcardsaplicao.ui.pesquisa;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.model.ResultadoDeckPesquisa;

/** Exibe os decks encontrados e informa ao Fragment qual resultado foi selecionado. */
public class PesquisaDeckAdapter extends ListAdapter<ResultadoDeckPesquisa, PesquisaDeckAdapter.DeckHolder> {

    private OnDeckClickListener onDeckClickListener;

    public interface OnDeckClickListener {
        void onDeckClick(ResultadoDeckPesquisa deck);
    }

    public void setOnDeckClickListener(OnDeckClickListener listener) {
        onDeckClickListener = listener;
    }

    public PesquisaDeckAdapter() {
        super(new DiffUtil.ItemCallback<ResultadoDeckPesquisa>() {
            @Override
            public boolean areItemsTheSame(@NonNull ResultadoDeckPesquisa antigo,
                    @NonNull ResultadoDeckPesquisa novo) {
                return antigo.getId().equals(novo.getId());
            }

            @Override
            public boolean areContentsTheSame(@NonNull ResultadoDeckPesquisa antigo,
                    @NonNull ResultadoDeckPesquisa novo) {
                return antigo.equals(novo);
            }
        });
        setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
    }

    @NonNull
    @Override
    public DeckHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_deck, parent, false);

        // Na pesquisa, o card acompanha a largura da coluna em vez dos 120dp da lista horizontal.
        ViewGroup.LayoutParams parametros = view.getLayoutParams();
        parametros.width = ViewGroup.LayoutParams.MATCH_PARENT;
        if (parametros instanceof ViewGroup.MarginLayoutParams) {
            int espaco = parent.getResources().getDimensionPixelSize(R.dimen.spacing_xs);
            ViewGroup.MarginLayoutParams margens = (ViewGroup.MarginLayoutParams) parametros;
            margens.setMargins(espaco, espaco, espaco, espaco);
            margens.setMarginStart(espaco);
            margens.setMarginEnd(espaco);
        }
        view.setLayoutParams(parametros);
        DeckHolder holder = new DeckHolder(view);
        view.setFocusable(true);
        view.setOnClickListener(v -> {
            // Consulta a posição atual: a lista pode ter mudado desde que o item foi desenhado.
            int posicao = holder.getBindingAdapterPosition();
            if (posicao != RecyclerView.NO_POSITION && onDeckClickListener != null) {
                onDeckClickListener.onDeckClick(getItem(posicao));
            }
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull DeckHolder holder, int position) {
        ResultadoDeckPesquisa deck = getItem(position);
        holder.titulo.setText(deck.getTitulo());
        long quantidade = deck.getQuantidadeCards();
        holder.cards.setText(holder.itemView.getResources().getQuantityString(
                R.plurals.pesquisa_quantidade_cards,
                (int) Math.min(quantidade, Integer.MAX_VALUE), quantidade));
    }

    static class DeckHolder extends RecyclerView.ViewHolder {
        final TextView titulo;
        final TextView cards;

        DeckHolder(@NonNull View itemView) {
            super(itemView);
            titulo = itemView.findViewById(R.id.txtTituloDeck);
            cards = itemView.findViewById(R.id.txtQuantidadeCards);
        }
    }
}
