package com.example.oztrip;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private EditText etEmail, etPassword;
    private TextView tvError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        etEmail = findViewById(R.id.etEmailLogin);
        etPassword = findViewById(R.id.etPasswordLogin);
        tvError = findViewById(R.id.tvErrorLogin);
        MaterialButton btnLogin = findViewById(R.id.btnLogin);
        TextView tvRegisterLink = findViewById(R.id.tvRegisterLink);
        ImageButton btnToggle = findViewById(R.id.btnTogglePassword);
        final boolean[] isPasswordVisible = {false};

        btnToggle.setOnClickListener(v -> {
            if (isPasswordVisible[0]) {
                // Скрываем пароль
                etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                btnToggle.setImageResource(R.drawable.ic_eye_off); // Иконка закрытого глаза
            } else {
                // Показываем пароль
                etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                btnToggle.setImageResource(R.drawable.ic_eye); // Иконка открытого глаза
            }
            isPasswordVisible[0] = !isPasswordVisible[0];

            // Переносим курсор в конец текста, чтобы он не прыгал в начало
            etPassword.setSelection(etPassword.getText().length());
        });
        btnLogin.setOnClickListener(v -> {
            String input = etEmail.getText().toString().trim(); // Это может быть почта или ник
            String password = etPassword.getText().toString().trim();

            if (input.isEmpty() || password.isEmpty()) {
                showError("Заполните все поля");
                return;
            }

            // Если в строке есть '@', значит это почта — входим сразу
            if (input.contains("@")) {
                loginWithEmail(input, password);
            } else {
                // Если '@' нет, значит это никнейм — ищем Email в Firestore
                findEmailByUsername(input, password);
            }
        });

        tvRegisterLink.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            finish();
        });
    }
    private void findEmailByUsername(String username, String password) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users")
                .whereEqualTo("username", username)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        if (!task.getResult().isEmpty()) {
                            // НИК НАЙДЕН
                            String email = task.getResult().getDocuments().get(0).getString("email");
                            android.util.Log.d("OZTRIP_DEBUG", "Нашел email для ника: " + email);

                            if (email != null && !email.isEmpty()) {
                                loginWithEmail(email, password);
                            } else {
                                showError("В базе у этого ника нет почты!");
                            }
                        } else {
                            // НИК НЕ НАЙДЕН - давай выведем в лог, что именно мы искали
                            android.util.Log.e("OZTRIP_DEBUG", "Ник '" + username + "' не найден в БД");
                            showError("Ник '" + username + "' не зарегистрирован");
                        }
                    } else {
                        showError("Ошибка БД: " + task.getException().getMessage());
                    }
                });
    }
    private void loginWithEmail(String email, String password) {
        // Показываем лоадинг (если есть ProgressBar)
        // pbLoading.setVisibility(View.VISIBLE);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    // pbLoading.setVisibility(View.GONE);

                    if (task.isSuccessful()) {
                        // ОТПРАВЛЯЕМ ПИСЬМО О ВХОДЕ
                        MailHelper.sendSecurityAlert(email, "login");

                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        // НИК ВЕРНЫЙ, НО ПАРОЛЬ ОШИБОЧНЫЙ (или другая ошибка)
                        showError("Неверный пароль. Попробуйте еще раз");

                        // Чтобы было совсем "люкс", можно подсветить поле пароля красным
                        etPassword.setError("Проверьте пароль");
                    }
                });
    }

    private void showError(String text) {
        tvError.setText(text);
        tvError.setVisibility(View.VISIBLE);
    }
}