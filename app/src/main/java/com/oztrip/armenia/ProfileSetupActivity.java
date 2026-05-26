package com.oztrip.armenia;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileSetupActivity extends BaseActivity  {
    private MaterialButton btnSave;
    private EditText etUsername, etName;
    // Убираем TextInputLayout, так как их больше нет в XML
    private ChipGroup cgSuggestions;
    private CardView llSuggestions;
    private FirebaseFirestore db;
    private ProgressBar pbLoading;
    private TextView tvTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_setup);

        db = FirebaseFirestore.getInstance();

        // 1. Инициализация (ОБЯЗАТЕЛЬНО ВСЕ, ЧТО ЕСТЬ В XML)
        tvTitle = findViewById(R.id.tvTitle);
        etUsername = findViewById(R.id.etUsername);
        etName = findViewById(R.id.etName);
        llSuggestions = findViewById(R.id.llSuggestions);
        cgSuggestions = findViewById(R.id.cgSuggestions);
        btnSave = findViewById(R.id.btnSave);
        pbLoading = findViewById(R.id.pbLoading); // Добавил это!

        // Анимация заголовка
        tvTitle.setAlpha(0f);
        tvTitle.setTranslationY(20f);
        tvTitle.animate().alpha(1f).translationY(0f).setDuration(800).start();

        btnSave.setOnClickListener(v -> saveProfile());

        etUsername.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // tilUsername.setError(null); <- УДАЛИ ЭТУ СТРОКУ, она вызывала вылет
                llSuggestions.setVisibility(View.GONE);
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void afterTextChanged(Editable s) {}
        });

        etName.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // tilName.setError(null); <- УДАЛИ ЭТУ СТРОКУ
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void afterTextChanged(Editable s) {}
        });
        // В onCreate добавь:
        etUsername.setFilters(new android.text.InputFilter[] {
                (source, start, end, dest, dstart, dend) -> {
                    for (int i = start; i < end; i++) {
                        if (!Character.isLetterOrDigit(source.charAt(i)) && source.charAt(i) != '_') {
                            return "";
                        }
                    }
                    return null;
                }
        });
    }

    private void saveProfile() {
        String username = etUsername.getText().toString().trim();
        String name = etName.getText().toString().trim();
        // Получаем почту, которую прислал RegisterActivity
        String email = getIntent().getStringExtra("user_email");

        if (username.length() < 3 || name.isEmpty()) {
            Toast.makeText(this, getString(R.string.text_auto_86), Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("users").whereEqualTo("username", username).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().isEmpty()) {
                        // НИК СВОБОДЕН -> Идем в редактор аватара
                        Intent intent = new Intent(this, AvatarEditorActivity.class);
                        intent.putExtra("user_name", name);
                        intent.putExtra("user_username", username);
                        intent.putExtra("user_email", email); // НЕ ТЕРЯЕМ ПОЧТУ!
                        startActivity(intent);
                    } else {
                        Toast.makeText(this, getString(R.string.text_auto_160), Toast.LENGTH_SHORT).show();
                        suggestAlternatives(username);
                    }
                });
    }

    // Метод suggestAlternatives и generateAlternative оставляем как были


    private void suggestAlternatives(String baseUsername) {
        // Тряска (нужен shake.xml в res/anim)
        llSuggestions.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake));

        cgSuggestions.removeAllViews();
        llSuggestions.setVisibility(View.VISIBLE);
        llSuggestions.setAlpha(0f);
        llSuggestions.animate().alpha(1f).setDuration(300).start();

        for (int i = 0; i < 3; i++) {
            generateAlternative(baseUsername);
        }
    }

    private void generateAlternative(String base) {
        String alternative = base + (new java.util.Random().nextInt(899) + 100);
        db.collection("users").whereEqualTo("username", alternative).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().isEmpty()) {
                        com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(this);
                        chip.setText(alternative);
                        chip.setOnClickListener(v -> {
                            etUsername.setText(alternative);
                            llSuggestions.setVisibility(View.GONE);
                        });
                        cgSuggestions.addView(chip);
                    }
                });
    }

}


