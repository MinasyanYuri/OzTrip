package com.example.oztrip;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class SettingsActivity extends AppCompatActivity {

    private TextView tvWelcome, tvUserNick, tvInitials;
    private ImageView ivAvatar;
    private MaterialCardView cardAvatarContainer;
    private FirebaseFirestore db;
    private String uid;
    private MaterialButton btnEditProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 1. Инициализация
        db = FirebaseFirestore.getInstance();
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        tvWelcome = findViewById(R.id.tvWelcome);
        tvUserNick = findViewById(R.id.tvUserNick);
        tvInitials = findViewById(R.id.tvInitials);
        ivAvatar = findViewById(R.id.ivAvatar);
        cardAvatarContainer = findViewById(R.id.cardAvatarContainer);
        btnEditProfile = findViewById(R.id.btnEditProfile);

        // 2. Кнопка Изменить профиль
        btnEditProfile.setOnClickListener(v -> showLuxuryAuthDialog());

        // 3. Кнопка выхода
        findViewById(R.id.btnLogoutSettings).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(SettingsActivity.this, RegisterActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        // 4. Кнопка Назад
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    // КЛЮЧЕВОЙ МОМЕНТ: Обновление данных при возврате на экран
    @Override
    protected void onResume() {
        super.onResume();
        if (uid != null) {
            loadUserProfile();
        }
    }

    private void loadUserProfile() {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String name = document.getString("name");
                        String username = document.getString("username");
                        String avatar = document.getString("avatar");
                        String colorHex = document.getString("avatarColor");

                        // Обновляем приветствие
                        if (name != null && !name.isEmpty()) {
                            tvWelcome.setText("Привет, " + name + "!");
                        } else {
                            tvWelcome.setText("Привет!");
                        }

                        // Обновляем Никнейм
                        if (username != null) {
                            tvUserNick.setText("@" + username);
                        }

                        // Обновляем Аватарку
                        displayAvatar(avatar, colorHex, name);
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Ошибка загрузки", Toast.LENGTH_SHORT).show());
    }

    private void displayAvatar(String avatar, String colorHex, String name) {
        ivAvatar.setVisibility(View.GONE);
        tvInitials.setVisibility(View.VISIBLE);

        if (avatar != null && avatar.startsWith("http")) {
            tvInitials.setVisibility(View.GONE);
            ivAvatar.setVisibility(View.VISIBLE);

            Glide.with(this)
                    .load(avatar)
                    .circleCrop()
                    // Подпись signature заставляет Glide перекачать фото, если оно изменилось в Cloudinary
                    .signature(new com.bumptech.glide.signature.ObjectKey(System.currentTimeMillis()))
                    .into(ivAvatar);
        } else {
            // Логика для иконок-персонажей или инициалов
            try {
                int iconRes = Integer.parseInt(avatar); // Проверяем, не ID ли это ресурса
                tvInitials.setVisibility(View.GONE);
                ivAvatar.setVisibility(View.VISIBLE);
                ivAvatar.setImageResource(iconRes);
                cardAvatarContainer.setCardBackgroundColor(Color.WHITE);
            } catch (Exception e) {
                // Если не число и не ссылка — показываем инициалы
                if (name != null && !name.isEmpty()) {
                    tvInitials.setText(name.substring(0, 1).toUpperCase());
                }
                if (colorHex != null && !colorHex.isEmpty()) {
                    try {
                        cardAvatarContainer.setCardBackgroundColor(Color.parseColor(colorHex));
                    } catch (Exception ex) {
                        cardAvatarContainer.setCardBackgroundColor(Color.GRAY);
                    }
                }
            }
        }
    }

    private void showLuxuryAuthDialog() {
        // 1. Создаем билдер каждый раз заново
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_confirm_auth, null);
        builder.setView(view);

        android.app.AlertDialog dialog = builder.create();

        // Делаем фон прозрачным для красоты
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        EditText etPass = view.findViewById(R.id.etConfirmPass);
        MaterialButton btnConfirm = view.findViewById(R.id.btnConfirmAuth);

        // Очищаем поле, если там что-то было
        etPass.setText("");

        btnConfirm.setOnClickListener(v -> {
            String inputPassword = etPass.getText().toString().trim();

            if (inputPassword.isEmpty()) {
                etPass.setError("Введите пароль");
                return;
            }

            // Блокируем кнопку, чтобы не нажали дважды пока идет запрос
            btnConfirm.setEnabled(false);

            String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
            if (email == null) {
                btnConfirm.setEnabled(true);
                return;
            }

            AuthCredential credential = EmailAuthProvider.getCredential(email, inputPassword);

            FirebaseAuth.getInstance().getCurrentUser().reauthenticate(credential)
                    .addOnSuccessListener(aVoid -> {
                        // Сохраняем пароль
                        getSharedPreferences("OzTrip_Prefs", MODE_PRIVATE)
                                .edit()
                                .putString("temp_pass", inputPassword)
                                .apply();

                        dialog.dismiss();
                        btnConfirm.setEnabled(true);

                        // Переходим в редактирование
                        Intent intent = new Intent(SettingsActivity.this, EditProfileActivity.class);
                        startActivity(intent);
                    })
                    .addOnFailureListener(e -> {
                        btnConfirm.setEnabled(true);
                        etPass.setError("Неверный пароль");
                        Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        });

        dialog.show();
    }
}