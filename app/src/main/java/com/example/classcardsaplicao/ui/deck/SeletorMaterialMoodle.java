package com.example.classcardsaplicao.ui.deck;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.data.moodle.CursoMoodle;
import com.example.classcardsaplicao.data.moodle.MaterialMoodle;
import com.example.classcardsaplicao.data.moodle.MoodleApi;
import com.example.classcardsaplicao.data.moodle.MoodleConfig;
import com.example.classcardsaplicao.data.moodle.MoodleException;
import com.example.classcardsaplicao.data.moodle.MoodleSessao;
import com.example.classcardsaplicao.data.moodle.StatusClassCards;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Campo "Material do Moodle" do modo automático da criação/edição de baralho (Fase 8).
 *
 * - Só existe para quem entrou pelo Moodle.
 * - Na primeira vez que a pessoa entra no modo automático, mostra o aviso sobre o envio do PDF à IA;
 *   ela precisa confirmar ("Entendi") para o campo aparecer. "Agora não" deixa o modo automático só com texto.
 * - O padrão é "Nenhum". A lista mostra "Nenhum" + os PDFs do curso escolhido na Matéria.
 * - Curso não liberado: o aluno é avisado; o professor pode liberar (aceitando o termo) ou retirar.
 * - O PDF escolhido é baixado na hora, para o botão Gerar já ter o arquivo.
 *
 * Toda a segurança (inscrição, liberação, versão do termo) é conferida no servidor pelo plugin.
 */
public final class SeletorMaterialMoodle {

    private static final String PREFS = "moodle_aviso_material";
    private static final String TEXTO_NENHUM = "Material: Nenhum";
    private static final String AVISO_PADRAO = "O PDF escolhido será enviado ao serviço de inteligência artificial "
            + "Gemini, do Google, apenas para gerar os seus flashcards. Use os flashcards só para estudo e não "
            + "redistribua o material.";

    private final Context context;
    private final String uid;
    private final String token;
    private final EditText campoMateria;
    private final EditText campoMaterial;
    private final View botaoGerar;
    private final MoodleApi api;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler principal = new Handler(Looper.getMainLooper());

    private MaterialMoodle selecionado;
    private boolean automaticoAntes;
    private boolean dialogoAberto;
    private boolean ocupado;

    /** Liga o campo de material no formulário, se a pessoa entrou pelo Moodle. Devolve null se não se aplica. */
    public static SeletorMaterialMoodle aplicar(View raiz) {
        EditText materia = raiz.findViewById(R.id.inputMateriaDeck);
        EditText material = raiz.findViewById(R.id.inputMaterialMoodle);
        if (materia == null || material == null) {
            return null;
        }
        Context context = raiz.getContext();
        String uid = FirebaseAuth.getInstance().getUid();
        if (!MoodleConfig.habilitado(context) || MoodleSessao.cursos(context, uid).isEmpty()) {
            return null;
        }
        String token = MoodleSessao.token(context, uid);
        if (token == null) {
            return null;
        }
        SeletorMaterialMoodle seletor = new SeletorMaterialMoodle(context, uid, token, materia, material,
                raiz.findViewById(R.id.btnGerarFlashcards));
        material.setTag(R.id.inputMaterialMoodle, seletor);
        return seletor;
    }

    /** Material escolhido no formulário (já baixado), ou null para "Nenhum". */
    public static MaterialMoodle selecionado(View raiz) {
        View material = raiz.findViewById(R.id.inputMaterialMoodle);
        Object seletor = material == null ? null : material.getTag(R.id.inputMaterialMoodle);
        return seletor instanceof SeletorMaterialMoodle ? ((SeletorMaterialMoodle) seletor).selecionado : null;
    }

    private SeletorMaterialMoodle(Context context, String uid, String token, EditText campoMateria,
                                  EditText campoMaterial, View botaoGerar) {
        this.context = context;
        this.uid = uid;
        this.token = token;
        this.campoMateria = campoMateria;
        this.campoMaterial = campoMaterial;
        this.botaoGerar = botaoGerar;
        this.api = new MoodleApi(context);

        campoMaterial.setKeyListener(null);
        campoMaterial.setFocusable(false);
        campoMaterial.setCursorVisible(false);
        campoMaterial.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, android.R.drawable.arrow_down_float, 0);
        campoMaterial.setText(TEXTO_NENHUM);
        campoMaterial.setOnClickListener(v -> abrir());

        // O PDF é de um curso: trocou a matéria, o material volta para "Nenhum".
        campoMateria.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (selecionado != null) {
                    limpar();
                }
            }
        });
    }

    /** Chamado pelo FormularioDeck sempre que o modo é apresentado (Manual/Automático). */
    public void aoMudarModo(boolean automatico) {
        boolean entrouNoAutomatico = automatico && !automaticoAntes;
        automaticoAntes = automatico;
        if (!automatico) {
            return;
        }
        if (avisoConfirmado()) {
            campoMaterial.setVisibility(View.VISIBLE);
            return;
        }
        campoMaterial.setVisibility(View.GONE);
        if (entrouNoAutomatico && !dialogoAberto) {
            mostrarAviso();
        }
    }

    private void mostrarAviso() {
        dialogoAberto = true;
        executor.execute(() -> {
            String aviso;
            try {
                String doServidor = api.obterStatus(token).avisoAluno;
                aviso = doServidor == null || doServidor.trim().isEmpty() ? AVISO_PADRAO : doServidor;
            } catch (MoodleException e) {
                aviso = AVISO_PADRAO;
            }
            String texto = aviso;
            naTela(() -> new MaterialAlertDialogBuilder(context)
                    .setTitle("Materiais do Moodle")
                    .setMessage(texto)
                    .setCancelable(false)
                    .setPositiveButton("Entendi", (d, w) -> {
                        dialogoAberto = false;
                        confirmarAviso();
                        campoMaterial.setVisibility(View.VISIBLE);
                    })
                    .setNegativeButton("Agora não", (d, w) -> dialogoAberto = false)
                    .show());
        });
    }

    private void abrir() {
        if (ocupado || (botaoGerar != null && !botaoGerar.isEnabled())) {
            return;
        }
        CursoMoodle curso = cursoDaMateria();
        if (curso == null) {
            Toast.makeText(context, "Escolha a matéria primeiro.", Toast.LENGTH_SHORT).show();
            return;
        }
        ocupar("Carregando materiais…");
        executor.execute(() -> {
            try {
                StatusClassCards status = api.obterStatus(token);
                StatusClassCards.Curso situacao = status.curso(curso.getId());
                if (situacao == null) {
                    throw new MoodleException("Você não está inscrito neste curso no Moodle.", "sem_curso");
                }
                if (situacao.autorizado) {
                    List<MaterialMoodle> materiais = api.listarMateriais(token, curso.getId());
                    naTela(() -> { liberar(); mostrarLista(situacao, materiais, status); });
                } else if (situacao.podeAutorizar) {
                    naTela(() -> { liberar(); mostrarTermo(situacao, status); });
                } else {
                    naTela(() -> {
                        liberar();
                        new MaterialAlertDialogBuilder(context)
                                .setTitle("Materiais não liberados")
                                .setMessage("O professor ainda não liberou os materiais deste curso no ClassCards. "
                                        + "Você pode gerar flashcards com a descrição, como antes.")
                                .setPositiveButton("Ok", null)
                                .show();
                    });
                }
            } catch (MoodleException e) {
                naTela(() -> { liberar(); Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show(); });
            }
        });
    }

    private void mostrarLista(StatusClassCards.Curso situacao, List<MaterialMoodle> materiais, StatusClassCards status) {
        String[] opcoes = new String[materiais.size() + 1];
        opcoes[0] = "Nenhum";
        int marcado = 0;
        for (int i = 0; i < materiais.size(); i++) {
            MaterialMoodle m = materiais.get(i);
            opcoes[i + 1] = m.getNome() + (m.grandeDemais() ? " (mais de 10 MB)" : "");
            if (selecionado != null && selecionado.getCmid() == m.getCmid()) {
                marcado = i + 1;
            }
        }
        MaterialAlertDialogBuilder dialogo = new MaterialAlertDialogBuilder(context)
                .setTitle(materiais.isEmpty() ? "Nenhum PDF neste curso" : "Escolha o material")
                .setSingleChoiceItems(opcoes, marcado, (d, escolhido) -> {
                    d.dismiss();
                    if (escolhido == 0) {
                        limpar();
                        return;
                    }
                    MaterialMoodle material = materiais.get(escolhido - 1);
                    if (material.grandeDemais()) {
                        Toast.makeText(context, "Este PDF passa de 10 MB, o limite para a IA.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    baixar(material);
                })
                .setNegativeButton("Cancelar", null);
        if (situacao.podeAutorizar) {
            dialogo.setNeutralButton("Retirar autorização", (d, w) -> confirmarRetirada(situacao));
        }
        dialogo.show();
    }

    private void mostrarTermo(StatusClassCards.Curso situacao, StatusClassCards status) {
        new MaterialAlertDialogBuilder(context)
                .setTitle("Liberar os materiais de " + situacao.nome + "?")
                .setMessage(status.termosTexto)
                .setPositiveButton("Concordo e liberar", (d, w) -> {
                    ocupar("Liberando…");
                    executor.execute(() -> {
                        try {
                            api.definirAutorizacao(token, situacao.id, true, status.termosVersao);
                            List<MaterialMoodle> materiais = api.listarMateriais(token, situacao.id);
                            StatusClassCards.Curso liberado = new StatusClassCards.Curso(situacao.id, situacao.nome,
                                    situacao.papel, true, situacao.podeAutorizar);
                            naTela(() -> {
                                liberar();
                                Toast.makeText(context, "Materiais liberados para este curso.", Toast.LENGTH_SHORT).show();
                                mostrarLista(liberado, materiais, status);
                            });
                        } catch (MoodleException e) {
                            naTela(() -> { liberar(); Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show(); });
                        }
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmarRetirada(StatusClassCards.Curso situacao) {
        new MaterialAlertDialogBuilder(context)
                .setTitle("Retirar a autorização?")
                .setMessage("Os materiais de " + situacao.nome + " deixam de ficar disponíveis no ClassCards. "
                        + "Os flashcards já criados não são apagados.")
                .setPositiveButton("Retirar", (d, w) -> {
                    ocupar("Retirando…");
                    executor.execute(() -> {
                        try {
                            api.definirAutorizacao(token, situacao.id, false, 0);
                            naTela(() -> {
                                liberar();
                                limpar();
                                Toast.makeText(context, "Autorização retirada.", Toast.LENGTH_SHORT).show();
                            });
                        } catch (MoodleException e) {
                            naTela(() -> { liberar(); Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show(); });
                        }
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void baixar(MaterialMoodle material) {
        ocupar("Baixando " + material.getNome() + "…");
        executor.execute(() -> {
            try {
                api.baixar(material, token);
                naTela(() -> { selecionado = material; liberar(); });
            } catch (MoodleException e) {
                naTela(() -> { liberar(); Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show(); });
            }
        });
    }

    private CursoMoodle cursoDaMateria() {
        String nome = campoMateria.getText() == null ? "" : campoMateria.getText().toString().trim();
        for (CursoMoodle curso : MoodleSessao.cursos(context, uid)) {
            if (curso.getNome().equals(nome)) {
                return curso;
            }
        }
        return null;
    }

    private void ocupar(String texto) {
        ocupado = true;
        campoMaterial.setText(texto);
    }

    /** Termina uma operação e volta a mostrar o material escolhido (ou "Nenhum"). */
    private void liberar() {
        ocupado = false;
        campoMaterial.setText(selecionado == null ? TEXTO_NENHUM : "Material: " + selecionado.getNome());
    }

    private void limpar() {
        selecionado = null;
        if (!ocupado) {
            campoMaterial.setText(TEXTO_NENHUM);
        }
    }

    private boolean avisoConfirmado() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("confirmado_" + uid, false);
    }

    private void confirmarAviso() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("confirmado_" + uid, true).apply();
    }

    /** Executa na thread principal, só se a tela ainda estiver aberta. */
    private void naTela(Runnable acao) {
        principal.post(() -> {
            Activity activity = activity(context);
            if (activity == null || activity.isFinishing() || activity.isDestroyed() || !campoMaterial.isAttachedToWindow()) {
                dialogoAberto = false;
                ocupado = false;
                return;
            }
            acao.run();
        });
    }

    private static Activity activity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
