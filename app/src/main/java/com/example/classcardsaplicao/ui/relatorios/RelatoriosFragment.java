package com.example.classcardsaplicao.ui.relatorios;

import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.model.ResumoRelatorio;
import com.example.classcardsaplicao.model.ResumoRelatorio.Linha;
import com.example.classcardsaplicao.model.ResumoRelatorio.Registro;
import com.example.classcardsaplicao.data.moodle.MoodleApi.AlunoRelatorio;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.bumptech.glide.Glide;

import java.text.*;
import java.util.*;

public class RelatoriosFragment extends Fragment {

    private RelatoriosViewModel vm;
    private FirebaseAuth.AuthStateListener authListener;
    private androidx.appcompat.app.AlertDialog dialogo;
    private final List<ImageView> fotos = new ArrayList<>();
    private String conta;

    public RelatoriosFragment() {
        super(R.layout.fragment_relatorios);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        vm = new ViewModelProvider(this).get(RelatoriosViewModel.class);
        conta = FirebaseAuth.getInstance().getUid();

        view.findViewById(R.id.btnVoltar)
                .setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        view.findViewById(R.id.btnAtualizarRelatorios).setOnClickListener(v -> vm.atualizar());
        view.findViewById(R.id.btnTentarRelatorios).setOnClickListener(v -> vm.atualizar());
        view.findViewById(R.id.filtroTurmaRelatorios).setOnClickListener(v -> escolherTurma());
        view.findViewById(R.id.filtroPeriodoRelatorios).setOnClickListener(v -> escolherPeriodo());
        view.findViewById(R.id.filtroModoRelatorios).setOnClickListener(v -> {
            String[] nomes = {"Todos os modos", "Tradicional", "Dissertativo"};
            String[] modos = {"todos", "tradicional", "dissertativo"};
            dialogo = new MaterialAlertDialogBuilder(requireContext()).setTitle("Modo de estudo")
                    .setItems(nomes, (d, i) -> vm.filtrar(vm.curso(), vm.verificados(), modos[i])).show();
        });

        SwitchMaterial selo = view.findViewById(R.id.filtroVerificadosRelatorios);

        selo.setChecked(vm.verificados());
        selo.setOnCheckedChangeListener((botao, marcado) -> vm.filtrar(vm.curso(), marcado, vm.modo()));

        vm.carregando.observe(getViewLifecycleOwner(), valor -> estado(view));
        vm.erro.observe(getViewLifecycleOwner(), valor -> estado(view));
        vm.cursos.observe(getViewLifecycleOwner(), valor -> atualizarFiltros(view));
        vm.resumo.observe(getViewLifecycleOwner(), valor -> {
            desenhar(view);
            estado(view);
            atualizarFiltros(view);
        });
        vm.matriculados.observe(getViewLifecycleOwner(), valor -> desenhar(view));
        vm.avisoMatriculas.observe(getViewLifecycleOwner(), valor -> desenhar(view));

        authListener = auth -> {
            if (!Objects.equals(conta, auth.getUid())) {
                if (dialogo != null)
                    dialogo.dismiss();

                vm.limparConta();
            }
        };

        FirebaseAuth.getInstance().addAuthStateListener(authListener);
        vm.carregar();
    }

    private void estado(View view) {
        boolean carregando = Boolean.TRUE.equals(vm.carregando.getValue());
        boolean erro = vm.erro.getValue() != null;

        view.findViewById(R.id.progressRelatorios).setVisibility(carregando ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.conteudoRelatorios)
                .setVisibility(!carregando && !erro && vm.resumo.getValue() != null ? View.VISIBLE : View.GONE);

        TextView texto = view.findViewById(R.id.erroRelatorios);
        texto.setText(vm.erro.getValue());
        texto.setVisibility(erro ? View.VISIBLE : View.GONE);

        view.findViewById(R.id.btnTentarRelatorios).setVisibility(erro ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.btnAtualizarRelatorios).setEnabled(!carregando);

        for (int id : new int[]{R.id.filtroTurmaRelatorios, R.id.filtroPeriodoRelatorios, R.id.filtroModoRelatorios,
                R.id.filtroVerificadosRelatorios})
            view.findViewById(id)
                    .setEnabled(!carregando && vm.cursos.getValue() != null && !vm.cursos.getValue().isEmpty());
    }

    private void atualizarFiltros(View view) {
        Map<String, String> cursos = vm.cursos.getValue();
        String nome =
                vm.curso() == null ? getString(R.string.relatorios_todos) : cursos.getOrDefault(vm.curso(), vm.curso());
        ((TextView) view.findViewById(R.id.filtroTurmaRelatorios)).setText(getString(R.string.relatorios_curso, nome));
        ((TextView) view.findViewById(R.id.filtroPeriodoRelatorios)).setText(
                getString(R.string.relatorios_periodo, data(vm.inicio()), data(vm.fim())));
        ((TextView) view.findViewById(R.id.filtroModoRelatorios)).setText("Modo: " + (vm.modo().equals("todos") ? "Todos" : vm.modo().equals("tradicional") ? "Tradicional" : "Dissertativo"));
    }

    private static String data(String iso) {
        return iso.substring(8, 10) + "/" + iso.substring(5, 7) + "/" + iso.substring(0, 4);
    }

    private void escolherTurma() {
        List<String> chaves = new ArrayList<>(vm.cursos.getValue().keySet());
        List<String> nomes = new ArrayList<>();
        nomes.add(getString(R.string.relatorios_todos));

        for (String chave : chaves)
            nomes.add(vm.cursos.getValue().get(chave));

        dialogo = new MaterialAlertDialogBuilder(requireContext()).setTitle("Selecionar turma")
                .setItems(nomes.toArray(new String[0]),
                        (d, i) -> vm.filtrar(i == 0 ? null : chaves.get(i - 1), vm.verificados(), vm.modo())).show();
    }

    private void escolherPeriodo() {
        dialogo = new MaterialAlertDialogBuilder(requireContext()).setTitle("Período")
                .setItems(new String[]{"Últimos 7 dias", "Últimos 30 dias", "Personalizado (até 93 dias)"}, (d, i) -> {
                    if (i < 2) {
                        vm.periodo(RelatoriosViewModel.dia(System.currentTimeMillis() - (i == 0 ? 6L : 29L) * 86400000),
                                RelatoriosViewModel.dia(System.currentTimeMillis()));
                        return;
                    }

                    MaterialDatePicker<androidx.core.util.Pair<Long, Long>> picker = MaterialDatePicker.Builder.dateRangePicker()
                            .setTitleText("Selecionar período").build();
                    picker.addOnPositiveButtonClickListener(par -> {
                        if (par.first == null || par.second == null)
                            return;

                        if (par.second < par.first || par.second - par.first > 92L * 86400000 ||
                                par.second > MaterialDatePicker.todayInUtcMilliseconds()) {
                            Toast.makeText(requireContext(), "Escolha até 93 dias, sem datas futuras.",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        vm.periodo(RelatoriosViewModel.dia(par.first), RelatoriosViewModel.dia(par.second));
                    });
                    picker.show(getChildFragmentManager(), "periodoRelatorios");
                }).show();
    }

    private void limparFotos() {
        for (ImageView foto : fotos)
            Glide.with(this).clear(foto);

        fotos.clear();
    }

    private void desenhar(View view) {
        limparFotos();

        LinearLayout secoes = view.findViewById(R.id.secoesRelatorios);
        secoes.removeAllViews();
        LinearLayout metricas = view.findViewById(R.id.metricasRelatorios);
        metricas.removeAllViews();

        ResumoRelatorio r = vm.resumo.getValue();
        if (r == null)
            return;

        String[] labels = {getString(R.string.relatorios_alunos), getString(R.string.relatorios_cards),
                getString(R.string.relatorios_revisoes), getString(R.string.relatorios_erros)};
        String[] valores = {numero(r.total.alunos.size()), numero(r.total.cards.size()), numero(r.total.revisoes),
                percentual(r.total.taxaErro())};

        for (int linha = 0; linha < 2; linha++) {
            LinearLayout par = new LinearLayout(requireContext());
            par.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(8);
            par.setLayoutParams(lp);
            metricas.addView(par);

            for (int coluna = 0; coluna < 2; coluna++) {
                int i = linha * 2 + coluna;
                MaterialCardView card = new MaterialCardView(requireContext());
                card.setCardBackgroundColor(cor(R.color.card_background));
                card.setRadius(dp(16));
                card.setCardElevation(0);
                card.setStrokeWidth(0);

                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -1, 1);
                if (coluna == 0)
                    clp.setMarginEnd(dp(8));
                card.setLayoutParams(clp);

                LinearLayout conteudo = new LinearLayout(requireContext());
                conteudo.setOrientation(LinearLayout.VERTICAL);
                conteudo.setPadding(dp(16), dp(16), dp(16), dp(16));

                conteudo.addView(texto(labels[i], 14, false));
                conteudo.addView(texto(valores[i], 26, true));
                card.addView(conteudo);
                par.addView(card);
            }
        }

        view.findViewById(R.id.vazioRelatorios).setVisibility(r.total.revisoes == 0 ? View.VISIBLE : View.GONE);

        TextView aviso = view.findViewById(R.id.avisoMatriculasRelatorios);
        aviso.setText(vm.avisoMatriculas.getValue());
        aviso.setVisibility(vm.avisoMatriculas.getValue() == null ? View.GONE : View.VISIBLE);

        secao(secoes, getString(R.string.relatorios_atencao), r.dificuldades(), "aluno", true);

        secao(secoes, getString(R.string.relatorios_atividade), r.atividade(), "aluno", false);

        List<Linha> turmas = new ArrayList<>(r.turmas);
        turmas.sort(ResumoRelatorio.porDificuldade());

        secao(secoes, getString(R.string.relatorios_turmas), turmas, "turma", true);

        List<Linha> assuntos = new ArrayList<>(r.assuntos);
        assuntos.sort(ResumoRelatorio.porDificuldade());

        secao(secoes, getString(R.string.relatorios_assuntos), assuntos, "assunto", true);

        List<AlunoRelatorio> semAtividade = new ArrayList<>();

        for (AlunoRelatorio a : vm.matriculados.getValue())
            if (!r.total.alunos.contains(a.uid))
                semAtividade.add(a);

        secoes.addView(titulo(getString(R.string.relatorios_sem_atividade)));

        if (semAtividade.isEmpty())
            secoes.addView(texto(vm.avisoMatriculas.getValue() != null ?
                    "Lista de matriculados ainda não disponível por completo." :
                    "Nenhum aluno sem revisões nos filtros.", 14, false));

        for (int i = 0; i < Math.min(10, semAtividade.size()); i++)
            secoes.addView(texto(semAtividade.get(i).nome, 16, false));

        if (semAtividade.size() > 10) {
            com.google.android.material.button.MaterialButton todos = new com.google.android.material.button.MaterialButton(
                    requireContext());
            todos.setText(getString(R.string.relatorios_ver_todos, semAtividade.size()));
            todos.setTextColor(cor(R.color.text_primary));
            todos.setOnClickListener(v -> {
                String[] nomes = new String[semAtividade.size()];
                for (int i = 0; i < nomes.length; i++)
                    nomes[i] = semAtividade.get(i).nome;
                dialogo = new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.relatorios_sem_atividade)
                        .setItems(nomes, null).setPositiveButton(R.string.relatorios_fechar, null).show();
            });
            secoes.addView(todos);
        }
    }

    private void secao(LinearLayout destino, String titulo, List<Linha> linhas, String tipo, boolean erros) {
        destino.addView(titulo(titulo));
        if (linhas.isEmpty()) {
            destino.addView(texto(tipo.equals("aluno") && erros ? getString(R.string.relatorios_sem_amostra) :
                    "Sem dados neste período.", 14, false));
            return;
        }

        for (int i = 0; i < Math.min(5, linhas.size()); i++) {
            Linha l = linhas.get(i);
            View row = getLayoutInflater().inflate(R.layout.item_relatorio_linha, destino, false);

            ((TextView) row.findViewById(R.id.nomeRelatorio)).setText(l.nome);
            ((TextView) row.findViewById(R.id.dadosRelatorio)).setText(descricao(l, erros));

            ImageView foto = row.findViewById(R.id.fotoRelatorio);
            foto.setVisibility(tipo.equals("aluno") ? View.VISIBLE : View.GONE);

            if (tipo.equals("aluno")) {
                fotos.add(foto);
                Glide.with(this).load(com.example.classcardsaplicao.model.ReferenciaAvatar.validarPara(l.chave, l.foto))
                        .placeholder(R.drawable.ranking_avatar_placeholder).error(R.drawable.ranking_avatar_placeholder)
                        .circleCrop().into(foto);
            }

            ProgressBar barra = row.findViewById(R.id.barraErroRelatorio);
            barra.setVisibility(erros && l.temAmostra() ? View.VISIBLE : View.GONE);
            barra.setProgress((int) Math.round(l.taxaErro() * 100));
            row.setOnClickListener(v -> detalhe(tipo, l));
            destino.addView(row);
        }

        if (linhas.size() > 5) {
            com.google.android.material.button.MaterialButton todos = new com.google.android.material.button.MaterialButton(
                    requireContext());
            todos.setText(getString(R.string.relatorios_ver_todos, linhas.size()));
            todos.setTextColor(cor(R.color.text_primary));
            todos.setOnClickListener(v -> {
                String[] itens = new String[linhas.size()];
                for (int i = 0; i < itens.length; i++)
                    itens[i] = linhas.get(i).nome + " — " + descricao(linhas.get(i), erros);
                dialogo = new MaterialAlertDialogBuilder(requireContext()).setTitle(titulo)
                        .setItems(itens, (d, i) -> detalhe(tipo, linhas.get(i)))
                        .setNegativeButton(R.string.relatorios_fechar, null).show();
            });
            destino.addView(todos);
        }
    }

    private String descricao(Linha l, boolean erros) {
        if (erros)
            return percentual(l.taxaErro()) + " Errei · " + numero(l.errei) + " de " + numero(l.revisoes) +
                    " revisões\n" + numero(l.dificil) + " Difícil · " +
                    (l.alunos.contains(l.chave) ? numero(l.cards.size()) + " cards distintos" :
                            numero(l.alunos.size()) + " aluno(s)") + (l.temAmostra() ? "" : " · Poucos dados");
        return numero(l.cards.size()) + " cards distintos · " + numero(l.revisoes) + " revisões\n" +
                numero(l.dias.size()) + " dias de estudo";
    }

    private void detalhe(String tipo, Linha linha) {
        ResumoRelatorio resumo = vm.resumo.getValue();
        if (resumo == null)
            return;

        ResumoRelatorio detalhe = resumo.detalhe(tipo, linha.chave, vm.cursos.getValue());
        StringBuilder mensagem = new StringBuilder(descricao(linha, false)).append("\n").append(descricao(linha, true));
        if (linha.ultima > 0) {
            SimpleDateFormat formato = new SimpleDateFormat("dd/MM/yyyy HH:mm 'UTC'", new Locale("pt", "BR"));
            formato.setTimeZone(TimeZone.getTimeZone("UTC"));
            mensagem.append("\nÚltima atividade: ").append(formato.format(new Date(linha.ultima)));
        }

        mensagem.append("\n\n").append(tipo.equals("aluno") ? "Assuntos estudados" : "Alunos com revisões")
                .append(":\n");

        List<Linha> partes = tipo.equals("aluno") ? detalhe.assuntos : detalhe.alunos;
        partes.sort(ResumoRelatorio.porDificuldade());
        for (Linha l : partes)
            mensagem.append("• ").append(l.nome).append(": ").append(descricao(l, true)).append("\n");

        Set<String> decks = new LinkedHashSet<>();
        for (Registro r : detalhe.registros)
            decks.add(r.titulo.isEmpty() ? r.deck : r.titulo);

        mensagem.append("\nDecks estudados:\n");
        for (String deck : decks)
            mensagem.append("• ").append(deck).append("\n");

        dialogo = new MaterialAlertDialogBuilder(requireContext()).setTitle(linha.nome).setMessage(mensagem)
                .setPositiveButton(R.string.relatorios_fechar, null).show();
    }

    private TextView titulo(String valor) {
        TextView t = texto(valor, 20, true);
        t.setPadding(0, dp(24), 0, dp(8));
        return t;
    }

    private TextView texto(String valor, int tamanho, boolean negrito) {
        TextView t = new TextView(requireContext());
        t.setText(valor);
        t.setTextSize(tamanho);
        t.setTextColor(cor(R.color.text_primary));
        if (negrito)
            t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private String numero(long n) {
        return NumberFormat.getIntegerInstance(new Locale("pt", "BR")).format(n);
    }

    private String percentual(double n) {
        return String.format(new Locale("pt", "BR"), "%.1f%%", n * 100);
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private int cor(int id) {
        return androidx.core.content.ContextCompat.getColor(requireContext(), id);
    }

    @Override
    public void onDestroyView() {
        if (dialogo != null)
            dialogo.dismiss();
        dialogo = null;

        if (authListener != null)
            FirebaseAuth.getInstance().removeAuthStateListener(authListener);
        authListener = null;

        limparFotos();
        super.onDestroyView();
    }
}
