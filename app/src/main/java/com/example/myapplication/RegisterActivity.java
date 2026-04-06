package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.databinding.ActivityRegisterBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();

        binding.loginHint.setOnClickListener(v -> finish());
        binding.registerButton.setOnClickListener(v -> register());
    }

    private void register() {
        String email = binding.emailInput.getText() != null ? binding.emailInput.getText().toString().trim() : "";
        String pass = binding.passwordInput.getText() != null ? binding.passwordInput.getText().toString() : "";

        if (email.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show();
            return;
        }
        if (pass.length() < 6) {
            Toast.makeText(this, "Пароль должен быть минимум 6 символов", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.statusText.setText("Создание аккаунта...");
        auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(res -> {
                    if (auth.getCurrentUser() == null) return;
                    String uid = auth.getCurrentUser().getUid();

                    FirebaseDatabase db = FirebaseDatabase.getInstance("https://nosql-562de-default-rtdb.firebaseio.com/");
                    Map<String, Object> profile = new HashMap<>();
                    profile.put("username", email);
                    profile.put("email", email);
                    profile.put("createdAt", System.currentTimeMillis());
                    db.getReference("users").child(uid).child("profile").setValue(profile);

                    // Отправляем письмо подтверждения
                    auth.getCurrentUser().sendEmailVerification()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Письмо подтверждения отправлено на почту", Toast.LENGTH_LONG).show();
                                binding.statusText.setText("Проверьте почту и подтвердите email, затем войдите.");
                                // Для безопасности выходим и отправляем на экран входа
                                auth.signOut();
                                Intent i = new Intent(this, AuthActivity.class);
                                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                i.putExtra("hint", "Подтвердите email и выполните вход.");
                                startActivity(i);
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                binding.statusText.setText("Не удалось отправить письмо: " + e.getMessage());
                                Toast.makeText(this, "Ошибка отправки письма", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> binding.statusText.setText("Ошибка: " + e.getMessage()));
    }
}

