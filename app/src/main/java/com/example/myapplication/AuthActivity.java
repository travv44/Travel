package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.databinding.ActivityAuthBinding;
import com.example.myapplication.utils.LoginSecurityManager;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class AuthActivity extends AppCompatActivity {

    private static final int RC_GOOGLE = 9001;

    private ActivityAuthBinding binding;
    private FirebaseAuth auth;
    private GoogleSignInClient googleClient;
    private LoginSecurityManager securityManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        securityManager = new LoginSecurityManager();

        if (auth.getCurrentUser() != null) {
            if (isEmailVerifiedOrNotNeeded(auth.getCurrentUser())) {
                goMain();
            } else {
                auth.signOut();
            }
            return;
        }

        setupGoogle();
        String hint = getIntent().getStringExtra("hint");
        if (hint != null && !hint.isEmpty()) {
            binding.statusText.setText(hint);
        }

        binding.registerHint.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });

        binding.loginButton.setOnClickListener(v -> loginWithEmail());
        binding.googleButton.setOnClickListener(v -> startGoogle());
        binding.resendVerification.setOnClickListener(v -> resendVerification());
    }

    private void setupGoogle() {
        int id = getResources().getIdentifier("default_web_client_id", "string", getPackageName());
        String webClientId = id != 0 ? getString(id) : null;
        if (webClientId == null || webClientId.trim().isEmpty()) {
            // Если default_web_client_id не сгенерирован google-services, отключаем Google вход
            binding.googleButton.setEnabled(false);
            binding.googleButton.setText("Google вход недоступен");
            binding.statusText.setText(
                    "Google вход отключён: в google-services.json нет OAuth client.\n" +
                    "Добавьте SHA-1 в Firebase Console и скачайте новый google-services.json."
            );
            return;
        }
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build();
        googleClient = GoogleSignIn.getClient(this, gso);
    }

    private void startGoogle() {
        if (googleClient == null) {
            Toast.makeText(this, "Google вход не настроен", Toast.LENGTH_SHORT).show();
            return;
        }
        // Чтобы каждый раз показывался выбор аккаунта — очищаем прошлую сессию Google
        googleClient.signOut();
        Intent signInIntent = googleClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_GOOGLE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_GOOGLE) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) {
                    firebaseAuthWithGoogle(account.getIdToken());
                } else {
                    Toast.makeText(this, "Google вход отменён", Toast.LENGTH_SHORT).show();
                }
            } catch (ApiException e) {
                Toast.makeText(this, "Ошибка Google входа: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnSuccessListener(res -> {
                    ensureUserProfile();
                    goMain();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Ошибка входа: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void loginWithEmail() {
        String email = binding.emailInput.getText() != null ? binding.emailInput.getText().toString().trim() : "";
        String pass = binding.passwordInput.getText() != null ? binding.passwordInput.getText().toString() : "";
        if (email.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Введите email и пароль", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.statusText.setText("Проверка безопасности...");
        securityManager.checkBan(email, new LoginSecurityManager.BanCheckCallback() {
            @Override
            public void onAllowed() {
                runOnUiThread(() -> doEmailSignIn(email, pass));
            }

            @Override
            public void onBanned(long bannedUntilMs) {
                long leftSec = Math.max(0, (bannedUntilMs - System.currentTimeMillis()) / 1000);
                runOnUiThread(() -> binding.statusText.setText("Аккаунт временно заблокирован. Осталось: " + leftSec + " сек."));
            }

            @Override
            public void onError(String error) {
                // Если проверка бана в БД недоступна (например, из-за правил),
                // не блокируем вход целиком — продолжаем попытку авторизации.
                runOnUiThread(() -> {
                    binding.statusText.setText("Проверка бана недоступна, продолжаем вход...");
                    doEmailSignIn(email, pass);
                });
            }
        });
    }

    private void doEmailSignIn(String email, String pass) {
        binding.statusText.setText("Вход...");
        auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(res -> {
                    securityManager.clearAttempts(email);
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null && !user.isEmailVerified()) {
                        binding.statusText.setText("Подтвердите email по ссылке в письме. После подтверждения войдите снова.");
                        binding.resendVerification.setVisibility(android.view.View.VISIBLE);
                        auth.signOut();
                        return;
                    }
                    ensureUserProfile();
                    goMain();
                })
                .addOnFailureListener(e -> {
                    securityManager.recordFailedAttempt(email);
                    binding.statusText.setText("Ошибка входа: " + e.getMessage());
                });
    }

    private void resendVerification() {
        String email = binding.emailInput.getText() != null ? binding.emailInput.getText().toString().trim() : "";
        if (email.isEmpty()) {
            Toast.makeText(this, "Введите email и пароль, затем нажмите Войти", Toast.LENGTH_SHORT).show();
            return;
        }
        // Чтобы отправить verification, нужно сначала залогиниться, поэтому делаем мягкую подсказку.
        Toast.makeText(this, "Сначала выполните вход, если email не подтвержден — появится кнопка повторной отправки", Toast.LENGTH_LONG).show();
    }

    private boolean isEmailVerifiedOrNotNeeded(FirebaseUser user) {
        if (user == null) return false;
        // Для email/password требуем подтверждение
        for (com.google.firebase.auth.UserInfo info : user.getProviderData()) {
            if ("password".equals(info.getProviderId())) {
                return user.isEmailVerified();
            }
        }
        // Для Google и других провайдеров подтверждение не требуем
        return true;
    }

    private void ensureUserProfile() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();
        String email = auth.getCurrentUser().getEmail();

        FirebaseDatabase db = FirebaseDatabase.getInstance("https://nosql-562de-default-rtdb.firebaseio.com/");
        Map<String, Object> profile = new HashMap<>();
        profile.put("email", email);
        profile.put("username", email);
        profile.put("createdAt", System.currentTimeMillis());
        profile.put("provider", auth.getCurrentUser().getProviderId());
        profile.put("emailVerified", auth.getCurrentUser().isEmailVerified());

        db.getReference("users").child(uid).child("profile").updateChildren(profile);
    }

    private void goMain() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }
}

