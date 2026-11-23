package com.example.classcardsaplicao;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;


import com.example.classcardsaplicao.data.AuthRepository;
import com.google.android.material.navigation.NavigationView;

public class MainActivity extends AppCompatActivity {

    private AuthRepository authRepository;
    private com.google.firebase.auth.FirebaseAuth.AuthStateListener acessoRelatorios;
    private int versaoAcesso;

    private void atualizarMenuRelatorios() {
        NavigationView menu = findViewById(R.id.navigationView);
        if (menu == null) return;
        menu.getMenu().findItem(R.id.menuRelatorios).setVisible(false);
        int carga = ++versaoAcesso;
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        new com.example.classcardsaplicao.data.RelatoriosRepository().cursosProfessor(this)
                .addOnSuccessListener(cursos -> {
                    if (carga == versaoAcesso && uid != null && uid.equals(com.google.firebase.auth.FirebaseAuth.getInstance().getUid()))
                        menu.getMenu().findItem(R.id.menuRelatorios).setVisible(!cursos.isEmpty());
                });
    }

    @Override protected void onResume() { super.onResume(); atualizarMenuRelatorios(); }
    @Override protected void onDestroy() {
        ++versaoAcesso;
        if (acessoRelatorios != null) com.google.firebase.auth.FirebaseAuth.getInstance().removeAuthStateListener(acessoRelatorios);
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);

        FirebaseApp.initializeApp(this);

        FirebaseAppCheck firebaseAppCheck =
                FirebaseAppCheck.getInstance();

        firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
        );

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);



        authRepository = new AuthRepository();

        NavigationView navigationView = findViewById(R.id.navigationView);
        DrawerLayout drawerLayout = findViewById(R.id.drawerLayout);

        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.nav_host_fragment);

        assert navHostFragment != null;
        NavController navController = getNavController(navHostFragment, drawerLayout);

        acessoRelatorios = auth -> atualizarMenuRelatorios();
        com.google.firebase.auth.FirebaseAuth.getInstance().addAuthStateListener(acessoRelatorios);
        navigationView.setNavigationItemSelectedListener(item -> {
            if (item.getItemId() == R.id.menuConfiguracoes) {
                if (navController.getCurrentDestination() == null || navController.getCurrentDestination().getId() != R.id.configuracoesFragment)
                    navController.navigate(R.id.configuracoesFragment);
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            }
            if (item.getItemId() == R.id.menuRelatorios) {
                if (!item.isVisible()) return false;
                if (navController.getCurrentDestination() == null || navController.getCurrentDestination().getId() != R.id.relatoriosFragment)
                    navController.navigate(R.id.relatoriosFragment);
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            }


            if (item.getItemId() == R.id.menuInicio) {

                navController.popBackStack(R.id.homeFragment, false);
                drawerLayout.closeDrawer(GravityCompat.START);

                return true;
            }

            if (item.getItemId() == R.id.menuPerfil) {

                navController.navigate(R.id.perfilFragment);
                drawerLayout.closeDrawer(GravityCompat.START);

                return true;
            }

            if (item.getItemId() == R.id.menuRankings) {
                if (navController.getCurrentDestination() == null
                        || navController.getCurrentDestination().getId() != R.id.rankingsFragment) {
                    navController.navigate(R.id.rankingsFragment);
                }
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            }

            if (item.getItemId() == R.id.menuSair) {

                authRepository.logout();

                drawerLayout.closeDrawer(GravityCompat.START);

                NavOptions navOptions = new NavOptions.Builder()
                        .setPopUpTo(navController.getGraph().getId(), true)
                        .build();

                navController.navigate(
                        R.id.loginFragment,
                        null,
                        navOptions
                );

                return true;
            }

            return false;
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.nav_host_fragment), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @NonNull
    private static NavController getNavController(NavHostFragment navHostFragment, DrawerLayout drawerLayout) {
        NavController navController = navHostFragment.getNavController();

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {

            int destinationId = destination.getId();

            if (destinationId == R.id.loginFragment
                    || destinationId == R.id.cadastroFragment
                    || destinationId == R.id.startupFragment) {

                drawerLayout.setDrawerLockMode(
                        DrawerLayout.LOCK_MODE_LOCKED_CLOSED
                );

            } else {

                drawerLayout.setDrawerLockMode(
                        DrawerLayout.LOCK_MODE_UNLOCKED
                );
            }
        });
        return navController;
    }
}