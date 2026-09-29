package com.example.classcardsaplicao.ui.deck.create;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.model.Deck;

import java.util.ArrayList;
import java.util.List;

public class DeckAdapter extends RecyclerView.Adapter<DeckAdapter.DeckViewHolder> {

    private final List<Deck> decks = new ArrayList<>();

    private OnDeckClickListener onDeckClickListener;

    @NonNull
    @Override
    public DeckViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.item_deck, parent, false);

        return new DeckViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull DeckViewHolder holder,
            int position
    ) {
        Deck deck = decks.get(position);

        holder.txtTituloDeck.setText(deck.getTitulo());

        int quantidade = deck.getQuantidadeCards();

        holder.txtQuantidadeCards.setText(
                quantidade == 1
                        ? "1 card"
                        : quantidade + " cards"
        );

        holder.itemView.setOnClickListener(v -> {
            if (onDeckClickListener != null) {
                onDeckClickListener.onDeckClick(deck);
            }
        });
    }

    @Override
    public int getItemCount() {
        return decks.size();
    }

    public interface OnDeckClickListener {
        void onDeckClick(Deck deck);
    }

    public void setOnDeckClickListener(OnDeckClickListener listener) {
        this.onDeckClickListener = listener;
    }

    public void atualizarDecks(List<Deck> novosDecks) {
        decks.clear();
        decks.addAll(novosDecks);
        notifyDataSetChanged();
    }

    static class DeckViewHolder extends RecyclerView.ViewHolder {

        TextView txtTituloDeck;
        TextView txtQuantidadeCards;

        public DeckViewHolder(@NonNull View itemView) {
            super(itemView);

            txtTituloDeck = itemView.findViewById(R.id.txtTituloDeck);
            txtQuantidadeCards = itemView.findViewById(R.id.txtQuantidadeCards);
        }
    }
}