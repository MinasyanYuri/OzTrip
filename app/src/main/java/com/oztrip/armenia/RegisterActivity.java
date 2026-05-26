package com.oztrip.armenia;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;

public class RegisterActivity extends BaseActivity  {

    private FirebaseAuth mAuth;
    private EditText etEmail, etPassword;
    private TextView tvError;
    private ProgressBar pbLoading;
    private MaterialButton btnRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        MaterialButton btnGuest = findViewById(R.id.btnGuestRegister);
        btnGuest.setOnClickListener(v -> enterGuestMode());
        // Инициализация Firebase
        mAuth = FirebaseAuth.getInstance();

        // Инициализация View
        tvError = findViewById(R.id.tvError);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);

        btnRegister = findViewById(R.id.btnRegister);
        pbLoading = findViewById(R.id.pbLoading);
        TextView tvLoginLink = findViewById(R.id.tvLoginLink);

        // Очистка ошибок при вводе
        setupTextWatchers();

        // Кнопка регистрации
        btnRegister.setOnClickListener(v -> performRegistration());
        ImageButton btnToggle = findViewById(R.id.btnTogglePasswordRegister);
        final boolean[] isPasswordVisible = {false};

        btnToggle.setOnClickListener(v -> {
            if (isPasswordVisible[0]) {
                // Скрываем пароль
                etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                btnToggle.setImageResource(R.drawable.ic_eye_off);
            } else {
                // Показываем пароль
                etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                btnToggle.setImageResource(R.drawable.ic_eye);
            }
            isPasswordVisible[0] = !isPasswordVisible[0];

            // Чтобы курсор не прыгал в начало строки
            etPassword.setSelection(etPassword.getText().length());
        });
        // Кнопка перехода на Login (если создашь такую Activity)
        tvLoginLink.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
        });

        mAuth.addAuthStateListener(auth -> {
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                // Пользователь авторизован – проверяем флаг завершения регистрации
                SharedPreferences prefs = getSharedPreferences("OzTripPrefs", MODE_PRIVATE);
                boolean complete = prefs.getBoolean("registration_complete", false);

                Intent intent;
                if (complete) {
                    intent = new Intent(RegisterActivity.this, MainActivity.class);
                } else {
                    intent = new Intent(RegisterActivity.this, ProfileSetupActivity.class);
                    intent.putExtra("user_email", user.getEmail());
                }
                startActivity(intent);
                finish();
            }
        });
    }

    private void enterGuestMode() {
        getSharedPreferences("OzTripPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("guest_mode", true)
                .remove("guest_travels")
                .apply();

        Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // Вспомогательный метод для запуска проверки в фоне
    private void performRegistration() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty()) {
            showError(getString(R.string.text_auto_161));
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Введите корректный email");
            return;
        }
        if (password.length() < 6) {
            showError(getString(R.string.text_auto_162));
            return;
        }

        pbLoading.setVisibility(View.VISIBLE);
        btnRegister.setEnabled(false);

        // Проверка реальности почты (без API-ключей!)
        AsyncTask.execute(() -> {
            String result = EmailValidator.verify(email);
            runOnUiThread(() -> {
                pbLoading.setVisibility(View.GONE);
                btnRegister.setEnabled(true);
                if ("valid".equals(result)) {
                    createFirebaseAccount(email, password);
                } else if ("invalid".equals(result)) {
                    showError("Такой email не существует. Проверьте адрес.");
                } else {
                    Toast.makeText(RegisterActivity.this,
                            "Не удалось проверить почту. Продолжаем без проверки.",
                            Toast.LENGTH_LONG).show();
                    createFirebaseAccount(email, password);
                }
            });
        });
    }

    private void createFirebaseAccount(String email, String password) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    pbLoading.setVisibility(View.GONE);
                    btnRegister.setEnabled(true);

                    if (task.isSuccessful()) {
                        Intent intent = new Intent(RegisterActivity.this, ProfileSetupActivity.class);
                        intent.putExtra("user_email", email);
                        startActivity(intent);
                        finish();
                    } else {
                        handleFirebaseError(task.getException());
                    }
                });
    }


    private void waitForEmailVerification(FirebaseUser user) {
        pbLoading.setVisibility(View.VISIBLE);
        btnRegister.setEnabled(false);

        // Каждые 2 секунды проверяем, подтверждён ли email
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                user.reload()
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                if (user.isEmailVerified()) {
                                    // Подтверждено – можно продолжать
                                    pbLoading.setVisibility(View.GONE);
                                    btnRegister.setEnabled(true);
                                    Toast.makeText(RegisterActivity.this,
                                            "Email подтверждён!", Toast.LENGTH_SHORT).show();
                                    // Переходим в ProfileSetupActivity
                                    Intent intent = new Intent(RegisterActivity.this, ProfileSetupActivity.class);
                                    intent.putExtra("user_email", user.getEmail());
                                    startActivity(intent);
                                    finish();
                                    return;
                                }
                            }
                            // Ещё не подтверждено – повторяем проверку
                            if (!isDestroyed()) {
                                waitForEmailVerification(user);
                            }
                        });
            }
        }, 2000);
    }

    private void handleFirebaseError(Exception e) {
        tvError.setVisibility(View.VISIBLE);
        if (e instanceof FirebaseAuthUserCollisionException) {
            tvError.setText(getString(R.string.text_auto_163));
        } else if (e instanceof FirebaseAuthWeakPasswordException) {
            tvError.setText(getString(R.string.text_auto_164));
        } else {
            tvError.setText(getString(R.string.text_auto_165));
        }
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }

    private void setupTextWatchers() {
        TextWatcher tw = new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                tvError.setVisibility(View.GONE);
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void afterTextChanged(Editable s) {}
        };
        etEmail.addTextChangedListener(tw);
        etPassword.addTextChangedListener(tw);
    }


}