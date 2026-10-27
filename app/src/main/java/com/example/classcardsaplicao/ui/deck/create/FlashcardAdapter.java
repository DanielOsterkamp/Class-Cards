package com.example.classcardsaplicao.ui.deck.create;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.model.Flashcard;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class FlashcardAdapter
        extends RecyclerView.Adapter<FlashcardAdapter.FlashcardViewHolder> {

    private final List<Flashcard> flashcards = new ArrayList<>();
    private boolean edicaoHabilitada = true;

    public void setEdicaoHabilitada(boolean habilitada) {
        if (edicaoHabilitada == habilitada) return;
        edicaoHabilitada = habilitada;
        notifyItemRangeChanged(0, getItemCount());
    }

    private OnEditarFlashcardListener onEditarFlashcardListener;

    @NonNull
    @Override
    public FlashcardViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_flashcard, parent, false);

        return new FlashcardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull FlashcardViewHolder holder,
            int position
    ) {
        Flashcard flashcard = flashcards.get(position);
        holder.btnEditarFlashcard.setEnabled(edicaoHabilitada);
        holder.btnExcluir.setEnabled(edicaoHabilitada);

        holder.btnEditarFlashcard.setOnClickListener(v -> {


            int atual = holder.getBindingAdapterPosition();
            if (edicaoHabilitada && atual != RecyclerView.NO_POSITION
                    && onEditarFlashcardListener != null) {

                onEditarFlashcardListener.onEditar(
                        atual,
                        flashcards.get(atual)
                );
            }
        });

        holder.txtPergunta.setText(flashcard.getPergunta());
        holder.txtResposta.setText(flashcard.getResposta());

        holder.btnExcluir.setOnClickListener(v -> {

            int atual = holder.getBindingAdapterPosition();
            if (edicaoHabilitada && atual != RecyclerView.NO_POSITION
                    && onExcluirFlashcardListener != null) {

                onExcluirFlashcardListener.onExcluir(atual);
            }
        });
    }

    @Override
    public int getItemCount() {
        return flashcards.size();
    }

    private OnExcluirFlashcardListener onExcluirFlashcardListener;

    public void atualizarFlashcards(List<Flashcard> novosFlashcards) {
        flashcards.clear();
        flashcards.addAll(novosFlashcards);
        notifyDataSetChanged();
    }

    public void setOnExcluirFlashcardListener(
            OnExcluirFlashcardListener listener
    ) {
        this.onExcluirFlashcardListener = listener;
    }

    public interface OnExcluirFlashcardListener {
        void onExcluir(int position);
    }

    static class FlashcardViewHolder extends RecyclerView.ViewHolder {

        MaterialButton btnEditarFlashcard;
        TextView txtPergunta;
        TextView txtResposta;
        MaterialButton btnExcluir;

        public FlashcardViewHolder(@NonNull View itemView) {
            super(itemView);

            btnEditarFlashcard =
                    itemView.findViewById(R.id.btnEditarFlashcard);

            txtPergunta =
                    itemView.findViewById(R.id.txtPerguntaFlashcard);

            txtResposta =
                    itemView.findViewById(R.id.txtRespostaFlashcard);

            btnExcluir =
                    itemView.findViewById(R.id.btnExcluirFlashcard);
        }
    }

    public void setOnEditarFlashcardListener(
            OnEditarFlashcardListener listener
    ) {
        this.onEditarFlashcardListener = listener;
    }

    public interface OnEditarFlashcardListener {
        void onEditar(int position, Flashcard flashcard);
    }
}
