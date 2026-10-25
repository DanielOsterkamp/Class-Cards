package com.example.classcardsaplicao.ui.rankings;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.model.EntradaRanking;
import com.example.classcardsaplicao.model.RankingSecao;
import com.example.classcardsaplicao.model.TipoRanking;
import com.example.classcardsaplicao.model.UsuarioRanking;
import com.google.android.material.imageview.ShapeableImageView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RankingsAdapter extends RecyclerView.Adapter<RankingsAdapter.SecaoHolder> {

    private final List<RankingSecao> secoes = new ArrayList<>();
    private final NumberFormat numeros = NumberFormat.getIntegerInstance(Locale.forLanguageTag("pt-BR"));

    public RankingsAdapter() {
        setHasStableIds(true);
    }

    public void atualizar(List<RankingSecao> novasSecoes) {
        secoes.clear();
        if (novasSecoes != null) {
            secoes.addAll(novasSecoes);
        }
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return secoes.get(position).getTipo().ordinal();
    }

    @NonNull
    @Override
    public SecaoHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ranking_secao, parent, false);
        return new SecaoHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SecaoHolder holder, int position) {
        RankingSecao secao = secoes.get(position);
        Context context = holder.itemView.getContext();
        TipoRanking tipo = secao.getTipo();

        holder.titulo.setText(titulo(tipo));
        holder.icone.setImageResource(icone(tipo));

        limparFotos(holder.usuarios);
        holder.usuarios.removeAllViews();
        List<EntradaRanking> entradas = secao.getEntradas();
        boolean vazio = entradas == null || entradas.isEmpty();
        holder.vazio.setVisibility(vazio ? View.VISIBLE : View.GONE);
        if (vazio) {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(context);
        for (EntradaRanking entrada : entradas) {
            if (entrada.isUsuarioAtual() && entrada.getPosicao() > 5) {
                adicionarSeparador(holder.usuarios);
            }
            View linha = inflater.inflate(R.layout.item_ranking_usuario, holder.usuarios, false);
            preencherLinha(linha, entrada, tipo);
            holder.usuarios.addView(linha);
        }
    }

    private void preencherLinha(View linha, EntradaRanking entrada, TipoRanking tipo) {
        Context context = linha.getContext();
        UsuarioRanking usuario = entrada.getUsuario();
        TextView posicao = linha.findViewById(R.id.txtPosicaoRanking);
        TextView nome = linha.findViewById(R.id.txtUsuarioRanking);
        TextView voce = linha.findViewById(R.id.txtVoceRanking);
        TextView valor = linha.findViewById(R.id.txtValorRanking);
        TextView unidade = linha.findViewById(R.id.txtUnidadeRanking);
        ShapeableImageView foto = linha.findViewById(R.id.imgFotoRanking);

        posicao.setText(context.getString(R.string.ranking_posicao, entrada.getPosicao()));
        int cor = R.color.text_secondary;
        if (entrada.getPosicao() == 1) cor = R.color.ranking_gold;
        else if (entrada.getPosicao() == 2) cor = R.color.ranking_silver;
        else if (entrada.getPosicao() == 3) cor = R.color.ranking_bronze;
        posicao.setTextColor(ContextCompat.getColor(context, cor));

        String nomeUsuario = usuario.getNome();
        nome.setText(nomeUsuario == null || nomeUsuario.trim().isEmpty()
                ? context.getString(R.string.ranking_nome_indisponivel) : nomeUsuario);
        voce.setVisibility(entrada.isUsuarioAtual() ? View.VISIBLE : View.GONE);
        if (entrada.isUsuarioAtual()) {
            linha.setBackgroundResource(R.drawable.ranking_linha_atual);
        }
        valor.setText(numeros.format(usuario.valor(tipo)));
        unidade.setText(unidade(tipo));

        Glide.with(foto)
                .load(usuario.getFotoUrl())
                .placeholder(R.drawable.ranking_avatar_placeholder)
                .error(R.drawable.ranking_avatar_placeholder)
                .circleCrop()
                .into(foto);
    }


    // esse metodo adiciona a linha separando o top 5 de você, caso não esteja nele
    private void adicionarSeparador(LinearLayout container) {
        Context context = container.getContext();
        View divisor = new View(context);
        LinearLayout.LayoutParams linha = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1));
        linha.topMargin = dp(context, 12);
        linha.bottomMargin = dp(context, 12);
        divisor.setLayoutParams(linha);
        divisor.setBackgroundColor(ContextCompat.getColor(context, R.color.ranking_divider));
        container.addView(divisor);

        TextView legenda = new TextView(context);
        legenda.setText(R.string.ranking_sua_posicao);
        legenda.setTextSize(12);
        legenda.setTextColor(ContextCompat.getColor(context, R.color.ranking_accent));
        legenda.setPadding(dp(context, 4), 0, 0, dp(context, 6));
        container.addView(legenda);
    }

    private static int dp(Context context, int valor) {
        return Math.round(valor * context.getResources().getDisplayMetrics().density);
    }

    private void limparFotos(LinearLayout container) {
        for (int i = 0; i < container.getChildCount(); i++) {
            ImageView foto = container.getChildAt(i).findViewById(R.id.imgFotoRanking);
            if (foto != null) Glide.with(foto).clear(foto);
        }
    }

    @Override
    public void onViewRecycled(@NonNull SecaoHolder holder) {
        limparFotos(holder.usuarios);
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return secoes.size();
    }

    private int titulo(TipoRanking tipo) {
        switch (tipo) {
            case OFENSIVAS: return R.string.ranking_ofensivas_titulo;
            case PONTOS: return R.string.ranking_pontos_titulo;
            default: return R.string.ranking_cards_titulo;
        }
    }

    private int icone(TipoRanking tipo) {
        switch (tipo) {
            case OFENSIVAS: return R.drawable.ic_ranking_ofensiva;
            case PONTOS: return R.drawable.ic_ranking_pontos;
            default: return R.drawable.ic_ranking_cards;
        }
    }

    private int unidade(TipoRanking tipo) {
        switch (tipo) {
            case OFENSIVAS: return R.string.ranking_unidade_dias;
            case PONTOS: return R.string.ranking_unidade_xp;
            default: return R.string.ranking_unidade_cards;
        }
    }

    static class SecaoHolder extends RecyclerView.ViewHolder {
        final TextView titulo;
        final ImageView icone;
        final LinearLayout usuarios;
        final TextView vazio;

        SecaoHolder(View view) {
            super(view);
            titulo = view.findViewById(R.id.txtNomeRanking);
            icone = view.findViewById(R.id.imgIconeRanking);
            usuarios = view.findViewById(R.id.containerUsuariosRanking);
            vazio = view.findViewById(R.id.txtRankingVazio);
        }
    }
}
