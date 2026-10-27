package com.example.classcardsaplicao.ui.pesquisa;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.model.ResultadoUsuarioPesquisa;

/** Utiliza somente os dados públicos do participante: UID, nome e foto. */
public class PesquisaUsuarioAdapter extends ListAdapter<ResultadoUsuarioPesquisa, PesquisaUsuarioAdapter.UsuarioHolder> {

    private OnUsuarioClickListener onUsuarioClickListener;

    public interface OnUsuarioClickListener {
        void onUsuarioClick(ResultadoUsuarioPesquisa usuario);
    }

    public void setOnUsuarioClickListener(OnUsuarioClickListener listener) {
        onUsuarioClickListener = listener;
    }

    public PesquisaUsuarioAdapter() {
        super(new DiffUtil.ItemCallback<ResultadoUsuarioPesquisa>() {
            @Override
            public boolean areItemsTheSame(@NonNull ResultadoUsuarioPesquisa antigo,
                    @NonNull ResultadoUsuarioPesquisa novo) {
                return antigo.getUid().equals(novo.getUid());
            }

            @Override
            public boolean areContentsTheSame(@NonNull ResultadoUsuarioPesquisa antigo,
                    @NonNull ResultadoUsuarioPesquisa novo) {
                return antigo.equals(novo);
            }
        });
        setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
    }

    @NonNull
    @Override
    public UsuarioHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pesquisa_usuario, parent, false);
        UsuarioHolder holder = new UsuarioHolder(view);
        view.setFocusable(true);
        view.setOnClickListener(v -> {
            int posicao = holder.getBindingAdapterPosition();
            if (posicao != RecyclerView.NO_POSITION && onUsuarioClickListener != null) {
                onUsuarioClickListener.onUsuarioClick(getItem(posicao));
            }
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull UsuarioHolder holder, int position) {
        ResultadoUsuarioPesquisa usuario = getItem(position);
        holder.nome.setText(usuario.getNome());
        holder.imagens.load(usuario.getFotoUrl().isEmpty() ? null : usuario.getFotoUrl())
                .placeholder(R.drawable.ranking_avatar_placeholder)
                .error(R.drawable.ranking_avatar_placeholder)
                .fallback(R.drawable.ranking_avatar_placeholder)
                .circleCrop()
                .into(holder.foto);
    }

    @Override
    public void onViewRecycled(@NonNull UsuarioHolder holder) {
        // Reutiliza o gerenciador existente: a Activity pode já estar encerrando ao reciclar a linha.
        holder.imagens.clear(holder.foto);
        super.onViewRecycled(holder);
    }

    static class UsuarioHolder extends RecyclerView.ViewHolder {
        final ImageView foto;
        final TextView nome;
        final RequestManager imagens;

        UsuarioHolder(@NonNull View itemView) {
            super(itemView);
            imagens = Glide.with(itemView);
            foto = itemView.findViewById(R.id.imgUsuarioPesquisa);
            nome = itemView.findViewById(R.id.txtNomeUsuarioPesquisa);
        }
    }
}
