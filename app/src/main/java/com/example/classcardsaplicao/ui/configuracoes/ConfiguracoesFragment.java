package com.example.classcardsaplicao.ui.configuracoes;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.annotation.*;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.bumptech.glide.Glide;
import com.example.classcardsaplicao.R;
import com.google.firebase.auth.FirebaseAuth;
import java.io.File;

public class ConfiguracoesFragment extends Fragment {
    private ConfiguracoesViewModel vm;
    private FirebaseAuth.AuthStateListener authListener;
    private Object imagemExibida;
    // O contrato usa o seletor nativo e faz fallback em aparelhos mais antigos.
    private final ActivityResultLauncher<PickVisualMediaRequest> seletor = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (vm == null) vm = new ViewModelProvider(this).get(ConfiguracoesViewModel.class);
                vm.selecionar(uri);
            });
    public ConfiguracoesFragment() { super(R.layout.fragment_configuracoes); }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        super.onViewCreated(view,saved);
        vm = new ViewModelProvider(this).get(ConfiguracoesViewModel.class);
        OnBackPressedCallback aguardar = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() { Toast.makeText(requireContext(),R.string.configuracoes_aguardar,Toast.LENGTH_SHORT).show(); }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),aguardar);
        view.findViewById(R.id.btnVoltar).setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        view.findViewById(R.id.btnEscolherFoto).setOnClickListener(v -> seletor.launch(
                new PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build()));
        view.findViewById(R.id.btnSalvarFoto).setOnClickListener(v -> vm.salvar());
        view.findViewById(R.id.btnDescartarFoto).setOnClickListener(v -> vm.descartar());
        view.findViewById(R.id.btnTentarFoto).setOnClickListener(v -> vm.carregar());
        Runnable atualizar = () -> {
            boolean salvando=Boolean.TRUE.equals(vm.salvando.getValue());
            boolean preparando=Boolean.TRUE.equals(vm.preparando.getValue());
            boolean carregando=Boolean.TRUE.equals(vm.carregando.getValue());
            boolean ocupado=salvando||preparando||carregando;
            File previa=vm.previa.getValue();
            view.findViewById(R.id.btnEscolherFoto).setEnabled(!ocupado && vm.temConta());
            view.findViewById(R.id.btnSalvarFoto).setEnabled(!ocupado && previa!=null);
            view.findViewById(R.id.btnDescartarFoto).setVisibility(previa!=null?View.VISIBLE:View.GONE);
            view.findViewById(R.id.btnDescartarFoto).setEnabled(!ocupado);
            view.findViewById(R.id.btnVoltar).setEnabled(!salvando);
            aguardar.setEnabled(salvando);
            view.findViewById(R.id.containerCarregandoFoto).setVisibility(ocupado?View.VISIBLE:View.GONE);
            ((TextView)view.findViewById(R.id.txtEstadoFoto)).setText(salvando?R.string.configuracoes_salvando:preparando?R.string.configuracoes_preparando:R.string.configuracoes_carregando);
            String erro=vm.erro.getValue();
            boolean sucesso=Boolean.TRUE.equals(vm.sucesso.getValue());
            TextView mensagem=view.findViewById(R.id.txtMensagemFoto);
            mensagem.setVisibility(erro!=null||sucesso?View.VISIBLE:View.GONE);
            mensagem.setText(erro!=null?erro:sucesso?getString(R.string.configuracoes_sucesso):"");
            view.findViewById(R.id.btnTentarFoto).setVisibility(erro!=null && previa==null && !ocupado?View.VISIBLE:View.GONE);
            Object imagem=previa==null?vm.foto.getValue():previa;
            ImageView foto=view.findViewById(R.id.imgFotoConfiguracoes);
            foto.setContentDescription(getString(previa==null?R.string.configuracoes_foto_atual:R.string.configuracoes_foto_previa));
            if (!java.util.Objects.equals(imagemExibida,imagem)) {
                imagemExibida=imagem;
                Glide.with(this).load(imagem).placeholder(R.drawable.ranking_avatar_placeholder).error(R.drawable.ranking_avatar_placeholder).circleCrop().into(foto);
            }
        };
        vm.foto.observe(getViewLifecycleOwner(),v -> atualizar.run());
        vm.previa.observe(getViewLifecycleOwner(),v -> atualizar.run());
        vm.carregando.observe(getViewLifecycleOwner(),v -> atualizar.run());
        vm.preparando.observe(getViewLifecycleOwner(),v -> atualizar.run());
        vm.salvando.observe(getViewLifecycleOwner(),v -> atualizar.run());
        vm.erro.observe(getViewLifecycleOwner(),v -> atualizar.run());
        vm.sucesso.observe(getViewLifecycleOwner(),v -> atualizar.run());
        authListener=auth -> { vm.conferirConta(); atualizar.run(); };
        FirebaseAuth.getInstance().addAuthStateListener(authListener);
        vm.carregar();
    }
    @Override public void onDestroyView() {
        if(authListener!=null)FirebaseAuth.getInstance().removeAuthStateListener(authListener); authListener=null;
        if(getView()!=null)Glide.with(this).clear((ImageView)getView().findViewById(R.id.imgFotoConfiguracoes));
        imagemExibida=null; super.onDestroyView();
    }
}
