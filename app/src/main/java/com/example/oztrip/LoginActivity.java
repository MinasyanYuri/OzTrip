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

public class LoginActivity extends BaseActivity  {

    private FirebaseAuth mAuth;
    private EditText etEmail, etPassword;
    private TextView tvError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        MaterialButton btnGuest = findViewById(R.id.btnGuestLogin);
        btnGuest.setOnClickListener(v -> enterGuestMode());
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
                showError(getString(R.string.text_auto_86));
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

    private void enterGuestMode() {
        getSharedPreferences("OzTripPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("guest_mode", true)
                .remove("guest_travels")
                .apply();

        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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
                            android.util.Log.d("OZTRIP_DEBUG", getString(R.string.text_auto_87) + email);

                            if (email != null && !email.isEmpty()) {
                                loginWithEmail(email, password);
                            } else {
                                showError(getString(R.string.text_auto_88));
                            }
                        } else {
                            // НИК НЕ НАЙДЕН - давай выведем в лог, что именно мы искали
                            android.util.Log.e("OZTRIP_DEBUG", getString(R.string.text_auto_89) + username + getString(R.string.text_auto_90));
                            showError(getString(R.string.text_auto_89) + username + getString(R.string.text_auto_91));
                        }
                    } else {
                        showError(getString(R.string.text_auto_92) + task.getException().getMessage());
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
                        MailHelper.sendSecurityAlert(this, email, "login");
                        getSharedPreferences("OzTripPrefs", MODE_PRIVATE).edit()
                                .putBoolean("registration_complete", true)
                                .apply();
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        // НИК ВЕРНЫЙ, НО ПАРОЛЬ ОШИБОЧНЫЙ (или другая ошибка)
                        showError(getString(R.string.text_auto_93));

                        // Чтобы было совсем getString(R.string.text_auto_94), можно подсветить поле пароля красным
                        etPassword.setError(getString(R.string.text_auto_95));
                    }
                });
    }

    private void showError(String text) {
        tvError.setText(text);
        tvError.setVisibility(View.VISIBLE);
    }
}