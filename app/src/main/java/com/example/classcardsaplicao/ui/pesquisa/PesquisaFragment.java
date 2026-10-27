package com.example.classcardsaplicao.ui.pesquisa;

import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.classcardsaplicao.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.util.EnumMap;
import java.util.Map;


public class PesquisaFragment extends Fragment {

    private final Map<PesquisaViewModel.Aba, Parcelable> posicoes = new EnumMap<>(PesquisaViewModel.Aba.class);
    private PesquisaViewModel viewModel;
    private PesquisaViewModel.Aba abaExibida;
    private PesquisaDeckAdapter decksAdapter;
    private PesquisaUsuarioAdapter usuariosAdapter;
    private RecyclerView recycler;
    private TextInputEditText campo;
    private MaterialButtonToggleGroup toggle;
    private TextWatcher observadorTexto;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_pesquisa, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(PesquisaViewModel.class);
        decksAdapter = new PesquisaDeckAdapter();
        usuariosAdapter = new PesquisaUsuarioAdapter();
        recycler = view.findViewById(R.id.recyclerPesquisa);
        campo = view.findViewById(R.id.campoPesquisa);
        toggle = view.findViewById(R.id.togglePesquisa);
        recycler.setItemAnimator(null);

        androidx.navigation.NavBackStackEntry entrada = NavHostFragment.findNavController(this).getCurrentBackStackEntry();
        if (entrada != null) {
            entrada.getSavedStateHandle().<String>getLiveData("deckExcluidoId").observe(getViewLifecycleOwner(), id -> {
                if (id == null) return;
                viewModel.removerDeckExcluido(id);
                entrada.getSavedStateHandle().remove("deckExcluidoId");
            });
        }

        // Envia somente o ID do resultado; a tela de destino carrega os dados correspondentes.
        decksAdapter.setOnDeckClickListener(deck -> {
            Bundle argumentos = new Bundle();
            argumentos.putString("deckId", deck.getId());
            abrirResultado(R.id.action_pesquisaFragment_to_deckSelecionadoFragment, argumentos);
        });

        usuariosAdapter.setOnUsuarioClickListener(usuario -> {
            Bundle argumentos = new Bundle();
            argumentos.putString("uid", usuario.getUid());
            abrirResultado(R.id.action_pesquisaFragment_to_perfilFragment, argumentos);
        });

        // ajusta o espaço quando o teclado abre ou fecha
        ViewCompat.setOnApplyWindowInsetsListener(view, (raiz, insets) -> {

            int teclado = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;

            int barra = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;

            // leva em conta os outros espaçamentos e não deixa ficar negativo
            raiz.setPadding(raiz.getPaddingLeft(), raiz.getPaddingTop(), raiz.getPaddingRight(), Math.max(0, teclado - barra));
            return insets;
        });

        ViewCompat.requestApplyInsets(view);

        // recupera a posição que os dados estavam quando o usuario troca de flashcard pra usuario
        if (savedInstanceState != null) {
            posicoes.put(PesquisaViewModel.Aba.FLASHCARDS, savedInstanceState.getParcelable("pesquisa_posicao_decks"));
            posicoes.put(PesquisaViewModel.Aba.USUARIOS, savedInstanceState.getParcelable("pesquisa_posicao_usuarios"));
        }

        String inicial = getArguments() == null ? "" : getArguments().getString("termo", "");

        if (viewModel.getTermo().isEmpty() && !inicial.isEmpty())
            viewModel.pesquisar(inicial);

        campo.setText(viewModel.getTermo());

        if (campo.getText() != null)
            campo.setSelection(campo.getText().length());


        observadorTexto = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence texto, int inicio, int quantidade, int depois) { }
            @Override public void onTextChanged(CharSequence texto, int inicio, int antes, int quantidade) { }

            @Override
            public void afterTextChanged(Editable texto) {
                if (!texto.toString().equals(viewModel.getTermo())) {
                    posicoes.clear();
                    if (recycler != null) recycler.scrollToPosition(0);
                    viewModel.pesquisar(texto.toString());
                }
            }
        };

        campo.addTextChangedListener(observadorTexto);


        campo.setOnEditorActionListener((texto, acao, evento) -> {
            if (acao == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.buscarAgora();
                esconderTeclado(view);
                return true;
            }
            return false;
        });

       // força pra que o teclado não fique aberto quando mudamos de tela
        view.findViewById(R.id.btnVoltarPesquisa).setOnClickListener(v -> {
            esconderTeclado(view);
            NavHostFragment.findNavController(this).popBackStack();
        });


        toggle.addOnButtonCheckedListener((grupo, id, selecionado) -> {

            if (selecionado) {
                PesquisaViewModel.Aba selecionada = id == R.id.btnPesquisaUsuarios
                        ? PesquisaViewModel.Aba.USUARIOS : PesquisaViewModel.Aba.FLASHCARDS;

                if (selecionada != viewModel.getAba().getValue())
                    viewModel.selecionarAba(selecionada);
            }

        });

        view.findViewById(R.id.btnTentarPesquisa).setOnClickListener(v -> viewModel.buscarAgora());


        view.findViewById(R.id.btnCarregarMaisPesquisa).setOnClickListener(v -> {
            PesquisaViewModel.EstadoTela estado = viewModel.getEstado().getValue();

            if (estado != null && estado.erro != null)
                viewModel.tentarNovamente();

            else viewModel.carregarMais();
        });

        viewModel.getDecks().observe(getViewLifecycleOwner(), decks -> {
            decksAdapter.submitList(decks);
            apresentarEstado(view);
        });

        viewModel.getUsuarios().observe(getViewLifecycleOwner(), usuarios -> {
            usuariosAdapter.submitList(usuarios);
            apresentarEstado(view);
        });

        viewModel.getAba().observe(getViewLifecycleOwner(), aba -> {
            mostrarAba(aba);
            apresentarEstado(view);
        });

        viewModel.getEstado().observe(getViewLifecycleOwner(), estado -> apresentarEstado(view));


        if (savedInstanceState == null && viewModel.getTermo().isEmpty() && (getArguments() == null || getArguments().getBoolean("focar", true))) {
            campo.post(() -> {
                if (getView() == view && campo != null && campo.requestFocus()) {
                    WindowInsetsControllerCompat controle = ViewCompat.getWindowInsetsController(campo);
                    if (controle != null) controle.show(WindowInsetsCompat.Type.ime());
                }
            });
        }
    }

    private void abrirResultado(int acao, Bundle argumentos) {
        androidx.navigation.NavController nav = NavHostFragment.findNavController(this);

        if (getView() == null || nav.getCurrentDestination() == null
                || nav.getCurrentDestination().getId() != R.id.pesquisaFragment) return;
        salvarPosicao();
        esconderTeclado(requireView());
        nav.navigate(acao, argumentos);
    }

    private void mostrarAba(PesquisaViewModel.Aba aba) {

        if (aba == null || recycler == null || aba == abaExibida) return;

        salvarPosicao();
        abaExibida = aba;
        recycler.setLayoutManager(aba == PesquisaViewModel.Aba.FLASHCARDS
                ? new GridLayoutManager(requireContext(), 3)
                : new LinearLayoutManager(requireContext()));

        recycler.setAdapter(aba == PesquisaViewModel.Aba.FLASHCARDS ? decksAdapter : usuariosAdapter);
        Parcelable posicao = posicoes.get(aba);

        if (posicao != null && recycler.getLayoutManager() != null) {
            recycler.getLayoutManager().onRestoreInstanceState(posicao);
        }

        toggle.check(aba == PesquisaViewModel.Aba.FLASHCARDS ? R.id.btnPesquisaFlashcards : R.id.btnPesquisaUsuarios);
    }

    private void apresentarEstado(View view) {
        PesquisaViewModel.EstadoTela estado = viewModel.getEstado().getValue();
        PesquisaViewModel.Aba aba = viewModel.getAba().getValue();

        if (estado == null || aba == null)
            return;

        int quantidade = aba == PesquisaViewModel.Aba.FLASHCARDS
                ? (viewModel.getDecks().getValue() == null ? 0 : viewModel.getDecks().getValue().size())
                : (viewModel.getUsuarios().getValue() == null ? 0 : viewModel.getUsuarios().getValue().size());
        apresentarEstado(view, estado, aba, quantidade);
    }

    static void apresentarEstado(View view, PesquisaViewModel.EstadoTela estado, PesquisaViewModel.Aba aba, int quantidade) {

        boolean temDados = quantidade > 0 && estado.termoValido;
        boolean temErro = estado.erro != null && !estado.erro.isEmpty();

        view.findViewById(R.id.recyclerPesquisa).setVisibility(temDados ? View.VISIBLE : View.GONE);

        view.findViewById(R.id.containerCarregandoPesquisa).setVisibility(estado.carregando && !temDados ? View.VISIBLE : View.GONE);

        view.findViewById(R.id.containerEstadoPesquisa).setVisibility(!temDados && !estado.carregando ? View.VISIBLE : View.GONE);

        TextView titulo = view.findViewById(R.id.txtEstadoPesquisa);

        if (!estado.termoValido) {
            titulo.setText(R.string.pesquisa_comecar_titulo);
        } else if (temErro) {
            titulo.setText(R.string.pesquisa_erro_titulo);
        } else {
            titulo.setText(aba == PesquisaViewModel.Aba.FLASHCARDS ? R.string.pesquisa_vazio_decks : R.string.pesquisa_vazio_usuarios);
        }

        MaterialButton tentar = view.findViewById(R.id.btnTentarPesquisa);
        tentar.setVisibility(estado.termoValido && !temDados && !estado.carregando ? View.VISIBLE : View.GONE);
        tentar.setText(temErro ? R.string.pesquisa_tentar_novamente : R.string.pesquisa_buscar_novamente);


        TextView aviso = view.findViewById(R.id.txtAvisoPesquisa);
        aviso.setVisibility(temErro && temDados ? View.VISIBLE : View.GONE);
        aviso.setText(estado.erro);

        boolean carregandoMais = estado.carregandoMais || (estado.carregando && temDados);

        view.findViewById(R.id.progressMaisPesquisa).setVisibility(carregandoMais ? View.VISIBLE : View.GONE);
        MaterialButton mais = view.findViewById(R.id.btnCarregarMaisPesquisa);
        mais.setVisibility(temDados && !carregandoMais && (estado.temMais || temErro) ? View.VISIBLE : View.GONE);
        mais.setText(temErro ? R.string.pesquisa_tentar_novamente : R.string.pesquisa_carregar_mais);
    }

    private void salvarPosicao() {
        if (abaExibida != null && recycler != null && recycler.getLayoutManager() != null) {
            posicoes.put(abaExibida, recycler.getLayoutManager().onSaveInstanceState());
        }
    }

    private static void esconderTeclado(View view) {
        WindowInsetsControllerCompat controle = ViewCompat.getWindowInsetsController(view);
        if (controle != null) controle.hide(WindowInsetsCompat.Type.ime());
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        salvarPosicao();
        outState.putParcelable("pesquisa_posicao_decks", posicoes.get(PesquisaViewModel.Aba.FLASHCARDS));
        outState.putParcelable("pesquisa_posicao_usuarios", posicoes.get(PesquisaViewModel.Aba.USUARIOS));
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        salvarPosicao();
        View raiz = getView();
        if (raiz != null) ViewCompat.setOnApplyWindowInsetsListener(raiz, null);
        if (campo != null && observadorTexto != null) campo.removeTextChangedListener(observadorTexto);
        if (campo != null) campo.setOnEditorActionListener(null);
        if (toggle != null) toggle.clearOnButtonCheckedListeners();
        if (recycler != null) recycler.setAdapter(null);
        recycler = null;
        campo = null;
        toggle = null;
        decksAdapter = null;
        usuariosAdapter = null;
        observadorTexto = null;
        abaExibida = null;
        super.onDestroyView();
    }
}
