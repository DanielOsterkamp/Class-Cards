package com.example.classcardsaplicao.ui.deck.study;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.model.AvaliacaoDissertativa;
import com.example.classcardsaplicao.model.Flashcard;
import com.example.classcardsaplicao.model.Rating;

/**
 * Cuida da apresentação da tela dissertativa e encaminha os cliques ao ViewModel.
 * As regras de estudo, as chamadas à IA e a gravação ficam fora do Fragment.
 */
public class EstudarDeckDissertativoFragment extends Fragment {

    /**
     * Informa ao Fragment qual layout deve ser criado.
     */
    public EstudarDeckDissertativoFragment() {
        super(R.layout.fragment_estudar_deck_dissertativo);
    }

    /**
     * Liga os controles ao ViewModel e observa os dados durante a vida desta view.
     * Cada mudança de estado determina quais campos, resultados e botões ficam disponíveis.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Obtém o ViewModel associado a este Fragment; ele mantém a sessão durante uma rotação.
        EstudarDeckDissertativoViewModel vm = new ViewModelProvider(this)
        .get(EstudarDeckDissertativoViewModel.class);

        // Recebe da navegação o identificador do deck que deve ser estudado.
        String deckId = getArguments() == null ? null : getArguments().getString("deckId");
        EditText resposta = view.findViewById(R.id.editResposta);

        // O SavedStateHandle é a fonte do rascunho, inclusive ao restaurar um estado salvo pelo Android.
        resposta.setSaveEnabled(false);
        resposta.setText(vm.getResposta());

        // Mantém o rascunho do ViewModel sincronizado com a digitação.
        // Os outros dois métodos fazem parte do contrato TextWatcher e não precisam executar ações.
        resposta.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                vm.setResposta(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // Os cliques apenas solicitam ações; as decisões e consultas são feitas pelo ViewModel.
        view.findViewById(R.id.btnVoltar2).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
        view.findViewById(R.id.btnAvaliarResposta).setOnClickListener(v -> vm.avaliar());
        view.findViewById(R.id.btnTentarNovamente).setOnClickListener(v -> vm.carregar(deckId));

        // Associa os quatro botões, na mesma ordem, às classificações de repetição espaçada.
        int[] botoes = {R.id.btnErrei, R.id.btnDificil, R.id.btnBom, R.id.btnFacil};
        Rating[] ratings = {Rating.AGAIN, Rating.HARD, Rating.GOOD, Rating.EASY};
        for (int i = 0; i < botoes.length; i++) {
            Rating rating = ratings[i];
            view.findViewById(botoes[i]).setOnClickListener(v -> vm.confirmar(rating));
        }

        // Os observadores usam a vida da view para não tentar atualizar um layout já destruído.
        vm.getNome().observe(getViewLifecycleOwner(), nome ->
                ((TextView) view.findViewById(R.id.txtNomeDeck2)).setText(nome));
        vm.getErro().observe(getViewLifecycleOwner(), erro -> {
            TextView mensagem = view.findViewById(R.id.txtErroEstudo);
            mensagem.setText(erro);
            mensagem.setVisibility(erro == null ? View.GONE : View.VISIBLE);
        });

        // Transforma o estado da sessão em visibilidade, textos e habilitação dos controles.
        vm.getEstado().observe(getViewLifecycleOwner(), estado -> {

            // Enquanto salva, continua mostrando a avaliação, mas impede confirmar novamente.
            boolean resultado = estado == EstudarDeckDissertativoViewModel.Estado.RESULTADO
            || estado == EstudarDeckDissertativoViewModel.Estado.SALVANDO;
            boolean respondendo = estado == EstudarDeckDissertativoViewModel.Estado.RESPOSTA;
            boolean avaliando = estado == EstudarDeckDissertativoViewModel.Estado.AVALIANDO;
            boolean salvando = estado == EstudarDeckDissertativoViewModel.Estado.SALVANDO;
            boolean carregando = estado == EstudarDeckDissertativoViewModel.Estado.CARREGANDO;
            boolean concluida = estado == EstudarDeckDissertativoViewModel.Estado.CONCLUIDA;
            Flashcard atual = vm.getAtual();
            TextView pergunta = view.findViewById(R.id.txtPergunta);
            TextView contador = view.findViewById(R.id.txtContadorFlashcards);

            // Mostra uma mensagem adequada para sessão vazia, sessão concluída ou card em andamento.
            if (concluida && vm.getResultadoOfensiva() != null) {
                ConclusaoEstudo.voltarAoDeck(this, vm.getResultadoOfensiva());
                return;
            }
            if (concluida) {
                pergunta.setText(vm.getTotal() == 0 ? "Nenhum flashcard para revisar agora."
                        : "Sessão de estudo concluída!");
                contador.setText("Revisões concluídas");
            } else if (atual != null) {
                pergunta.setText(atual.getPergunta());
                contador.setText((vm.getIndice() + 1) + " / " + vm.getTotal());
            } else {
                pergunta.setText(carregando ? "Carregando pergunta..." : "Não foi possível iniciar o estudo.");
                contador.setText(carregando ? "Carregando flashcards..." : "Tente novamente");
            }

            // O estudante só pode editar quando a tela está no estado RESPOSTA.
            view.findViewById(R.id.containerResposta).setVisibility(
                    respondendo || avaliando || resultado ? View.VISIBLE : View.GONE);
            resposta.setEnabled(respondendo);

            // Atualiza o campo apenas se necessário, preservando a digitação durante as demais atualizações.
            if (!resposta.getText().toString().equals(vm.getResposta()))
                resposta.setText(vm.getResposta());
            view.findViewById(R.id.btnAvaliarResposta).setVisibility(respondendo ? View.VISIBLE : View.GONE);
            view.findViewById(R.id.containerResultado).setVisibility(resultado ? View.VISIBLE : View.GONE);
            view.findViewById(R.id.containerAvaliacaoFinal).setVisibility(resultado ? View.VISIBLE : View.GONE);
            view.findViewById(R.id.containerCarregamento).setVisibility(avaliando || salvando ? View.VISIBLE : View.GONE);
            ((TextView) view.findViewById(R.id.txtStatusAvaliacao)).setText(salvando
                    ? "Salvando revisão e conquistas..." : "A IA está avaliando sua resposta...");
            view.findViewById(R.id.btnTentarNovamente).setVisibility(
                    estado == EstudarDeckDissertativoViewModel.Estado.ERRO ? View.VISIBLE : View.GONE);

            // Bloqueia os botões de classificação durante o salvamento para evitar cliques repetidos.
            for (int id : botoes)
                view.findViewById(id).setEnabled(resultado && !salvando);

            // Só preenche a resposta esperada e a análise quando há resultado disponível.
            AvaliacaoDissertativa avaliacao = vm.getAvaliacao();
            if (resultado && atual != null && avaliacao != null) {
                ((TextView) view.findViewById(R.id.txtRespostaEsperada)).setText(atual.getResposta());
                ((TextView) view.findViewById(R.id.txtExplicacaoIA)).setText(avaliacao.getExplicacao());
                TextView sugestao = view.findViewById(R.id.txtSugestaoIA);
                String[] nomes = {"Errei", "Difícil", "Bom", "Fácil"};
                sugestao.setText("Sugestão da IA: " + nomes[avaliacao.getSugestao().getValor() - 1]);
                sugestao.setVisibility(View.VISIBLE);
            }
        });

        // Inicia a busca após configurar os observadores, para que a tela acompanhe as mudanças.
        vm.carregar(deckId);
    }
}
