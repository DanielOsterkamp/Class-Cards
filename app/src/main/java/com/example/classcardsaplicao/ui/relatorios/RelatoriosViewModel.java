package com.example.classcardsaplicao.ui.relatorios;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.*;

import com.example.classcardsaplicao.data.RelatoriosRepository;
import com.example.classcardsaplicao.model.ResumoRelatorio;
import com.example.classcardsaplicao.model.ResumoRelatorio.Registro;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.*;

public class RelatoriosViewModel extends AndroidViewModel {

    public final MutableLiveData<Boolean> carregando = new MutableLiveData<>(false);
    public final MutableLiveData<String> erro = new MutableLiveData<>();
    public final MutableLiveData<Map<String, String>> cursos = new MutableLiveData<>(new LinkedHashMap<>());
    public final MutableLiveData<ResumoRelatorio> resumo = new MutableLiveData<>();
    public final MutableLiveData<List<com.example.classcardsaplicao.data.moodle.MoodleApi.AlunoRelatorio>> matriculados = new MutableLiveData<>(
            new ArrayList<>());
    public final MutableLiveData<String> avisoMatriculas = new MutableLiveData<>();

    private final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final android.os.Handler principal = new android.os.Handler(android.os.Looper.getMainLooper());
    private final RelatoriosRepository repository = new RelatoriosRepository();
    private final SavedStateHandle estado;

    private List<Registro> registros = new ArrayList<>();
    private int versao;
    private boolean encerrado;
    private String uid;

    public RelatoriosViewModel(@NonNull Application app, SavedStateHandle estado) {
        super(app);
        this.estado = estado;

        if (!estado.contains("inicio")) {
            estado.set("inicio", dia(System.currentTimeMillis() - 29L * 86400000));
            estado.set("fim", dia(System.currentTimeMillis()));
        }
    }

    public String inicio() {
        return estado.get("inicio");
    }

    public String fim() {
        return estado.get("fim");
    }

    public String curso() {
        return estado.get("curso");
    }

    public boolean verificados() {
        return Boolean.TRUE.equals(estado.get("verificados"));
    }

    public String modo() {
        String m = estado.get("modo");
        return m == null ? "todos" : m;
    }

    public void filtrar(String curso, boolean verificados, String modo) {
        boolean novaConsulta = !Objects.equals(curso(), curso);

        estado.set("curso", curso);
        estado.set("verificados", verificados);
        estado.set("modo", modo);

        if (novaConsulta)
            atualizar();
        else
            publicar();
    }

    public void periodo(String inicio, String fim) {
        estado.set("inicio", inicio);
        estado.set("fim", fim);
        atualizar();
    }

    public void carregar() {
        if (resumo.getValue() == null && !Boolean.TRUE.equals(carregando.getValue()))
            atualizar();
    }

    public void atualizar() {
        String conta = FirebaseAuth.getInstance().getUid();
        int carga = ++versao;
        uid = conta;

        registros.clear();
        matriculados.setValue(new ArrayList<>());
        avisoMatriculas.setValue("Consultando matriculados no Moodle…");
        resumo.setValue(null);
        erro.setValue(null);
        carregando.setValue(true);

        repository.cursosProfessor(getApplication()).addOnSuccessListener(lista -> {
            if (!valida(carga, conta))
                return;

            cursos.setValue(lista);

            if (lista.isEmpty()) {
                falhar("Somente professores podem acessar os relatórios das suas turmas.");
                return;
            }

            String filtro = curso();
            if (filtro != null && !lista.containsKey(filtro)) {
                estado.set("curso", null);
                filtro = null;
            }

            List<String> chaves = filtro == null ? new ArrayList<>(lista.keySet()) : List.of(filtro);
            buscarMatriculas(chaves, carga, conta);

            repository.buscar(chaves, inicio(), fim(), conta, () -> valida(carga, conta))
                    .addOnSuccessListener(dados -> {
                        if (!valida(carga, conta))
                            return;

                        registros = dados;
                        carregando.setValue(false);
                        publicar();
                    }).addOnFailureListener(e -> {
                        if (valida(carga, conta))
                            falhar("Não foi possível carregar os relatórios. Confira a conexão e a publicação das regras do Firebase.");
                    });
        }).addOnFailureListener(e -> {
            if (valida(carga, conta))
                falhar("Não foi possível confirmar seu acesso de professor. Entre novamente ou tente atualizar.");
        });
    }

    private void buscarMatriculas(List<String> chaves, int carga, String conta) {
        String token = com.example.classcardsaplicao.data.moodle.MoodleSessao.token(getApplication(), conta);

        executor.execute(() -> {
            Map<String, com.example.classcardsaplicao.data.moodle.MoodleApi.AlunoRelatorio> alunos = new LinkedHashMap<>();
            boolean falhou = token == null;

            if (token != null)
                for (String chave : chaves) {
                    if (Thread.currentThread().isInterrupted())
                        return;

                    try {
                        int separador = chave.lastIndexOf(':');
                        for (com.example.classcardsaplicao.data.moodle.MoodleApi.AlunoRelatorio a :
                                new com.example.classcardsaplicao.data.moodle.MoodleApi(getApplication()).listarAlunos(
                                        token,
                                        Long.parseLong(chave.substring(separador + 1)), chave.substring(0, separador)))
                            alunos.put(a.uid, a);
                    } catch (Exception e) {
                        falhou = true;
                    }
                }

            boolean indisponivel = falhou;

            principal.post(() -> {
                if (!valida(carga, conta))
                    return;

                matriculados.setValue(new ArrayList<>(alunos.values()));
                avisoMatriculas.setValue(indisponivel
                        ? "Lista de matriculados incompleta ou indisponível. As estatísticas mostram os alunos com revisões registradas. Confira o serviço Moodle."
                        : null);
            });
        });
    }

    private void publicar() {
        List<Registro> dados = new ArrayList<>();

        for (Registro r : registros)
            if ((!verificados() || r.verificado) && (modo().equals("todos") || modo().equals(r.modo)))
                dados.add(r);

        resumo.setValue(new ResumoRelatorio(dados, cursos.getValue()));
    }

    private boolean valida(int carga, String conta) {
        return !encerrado && carga == versao && conta != null && conta.equals(FirebaseAuth.getInstance().getUid());
    }

    public void limparConta() {
        ++versao;
        registros.clear();
        matriculados.setValue(new ArrayList<>());
        avisoMatriculas.setValue(null);
        resumo.setValue(null);
        cursos.setValue(new LinkedHashMap<>());
        carregando.setValue(false);
        erro.setValue("A conta mudou. Abra os relatórios novamente.");
    }

    private void falhar(String mensagem) {
        carregando.setValue(false);
        erro.setValue(mensagem);
    }

    public static String dia(long millis) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(millis));
    }

    @Override
    protected void onCleared() {
        encerrado = true;
        ++versao;
        executor.shutdownNow();
        principal.removeCallbacksAndMessages(null);
    }
}
