package com.example.classcardsaplicao.ui.auth.moodle;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.classcardsaplicao.MainActivity;
import com.example.classcardsaplicao.R;
import com.example.classcardsaplicao.data.UserRepository;
import com.example.classcardsaplicao.data.moodle.MoodleApi;
import com.example.classcardsaplicao.data.moodle.MoodleConfig;
import com.example.classcardsaplicao.data.moodle.MoodleException;
import com.example.classcardsaplicao.data.moodle.MoodleLogin;
import com.example.classcardsaplicao.data.moodle.MoodleSessao;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ponto de chegada do login pelo Moodle: o Chrome abre classcards://token=... e o Android entrega aqui.
 *
 * 1. confere o passport e extrai o token do Moodle (MoodleLogin)
 * 2. pede ao plugin local_classcards o passe do Firebase (MoodleApi)
 * 3. entra no Firebase com o passe (signInWithCustomToken)
 * 4. cria o documento do usuário, com o mesmo método do login com Google (UserRepository)
 * 5. reabre o app; o StartupFragment vê que há usuário logado e vai para a Home
 *
 * O MainActivity não é alterado. Tokens nunca vão para os logs.
 */
public class MoodleRetornoActivity extends AppCompatActivity {

    private static final String TAG = "MOODLE";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler principal = new Handler(Looper.getMainLooper());

    private ProgressBar progresso;
    private TextView texto;
    private Button btnVoltar;
    private boolean emAndamento;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moodle_retorno);
        progresso = findViewById(R.id.progressoMoodle);
        texto = findViewById(R.id.textoMoodle);
        btnVoltar = findViewById(R.id.btnVoltarMoodle);
        btnVoltar.setOnClickListener(v -> abrirApp());
        processar(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        processar(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    private void processar(Intent intent) {
        if (emAndamento) {
            return;
        }
        if (!MoodleConfig.habilitado(this)) {
            mostrarErro("O login pelo Moodle não está disponível nesta versão do app.", "desligado");
            return;
        }
        emAndamento = true;
        mostrarCarregando();

        Uri retorno = intent != null ? intent.getData() : null;
        executor.execute(() -> {
            try {
                String tokenMoodle = MoodleLogin.validarRetorno(this, retorno);
                MoodleApi.PasseFirebase passe = new MoodleApi(this).obterPasseFirebase(tokenMoodle);
                principal.post(() -> entrarNoFirebase(tokenMoodle, passe));
            } catch (MoodleException e) {
                principal.post(() -> mostrarErro(e.getMessage(), e.getCodigo()));
            }
        });
    }

    private void entrarNoFirebase(String tokenMoodle, MoodleApi.PasseFirebase passe) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        auth.signInWithCustomToken(passe.customToken)
                .addOnSuccessListener(resultado -> {
                    FirebaseUser usuario = resultado.getUser();
                    if (usuario == null) {
                        falharDepoisDoLogin("Não foi possível obter os dados do usuário.", "sem_usuario");
                        return;
                    }
                    MoodleSessao.salvar(this, usuario.getUid(), tokenMoodle, passe.cursos);
                    atualizarNome(usuario, passe.nome);
                    new UserRepository().criarUsuarioSeNaoExistir(
                            usuario.getUid(), passe.nome, passe.email,
                            new UserRepository.UserCallback() {
                                @Override
                                public void onSuccess() {
                                    abrirApp();
                                }

                                @Override
                                public void onError(Exception exception) {
                                    Log.w(TAG, "Falha ao criar o usuário no Firestore: " + exception.getMessage());
                                    falharDepoisDoLogin("Não foi possível criar seu perfil no ClassCards.", "firestore");
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "signInWithCustomToken falhou: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    mostrarErro("Não foi possível entrar no ClassCards com a conta do Moodle.", "firebase_auth");
                });
    }

    /** O Firebase não recebe o nome junto com o passe; guardamos o nome do Moodle no perfil do usuário. */
    private void atualizarNome(FirebaseUser usuario, String nome) {
        if (nome == null || nome.isEmpty() || nome.equals(usuario.getDisplayName())) {
            return;
        }
        usuario.updateProfile(new UserProfileChangeRequest.Builder().setDisplayName(nome).build())
                .addOnFailureListener(e -> Log.w(TAG, "Não foi possível salvar o nome no perfil: " + e.getMessage()));
    }

    /** Se algo falhou depois de entrar no Firebase, sai de novo para não deixar um login pela metade. */
    private void falharDepoisDoLogin(String mensagem, String codigo) {
        FirebaseAuth.getInstance().signOut();
        MoodleSessao.limpar(this);
        mostrarErro(mensagem, codigo);
    }

    private void mostrarCarregando() {
        progresso.setVisibility(View.VISIBLE);
        btnVoltar.setVisibility(View.GONE);
        texto.setText("Entrando com o Moodle…");
    }

    private void mostrarErro(String mensagem, String codigo) {
        emAndamento = false;
        Log.w(TAG, "Login pelo Moodle não concluído: " + codigo);
        progresso.setVisibility(View.GONE);
        texto.setText(mensagem);
        btnVoltar.setVisibility(View.VISIBLE);
    }

    /** Reabre o app do começo: logado vai para a Home, senão volta para o login. */
    private void abrirApp() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
