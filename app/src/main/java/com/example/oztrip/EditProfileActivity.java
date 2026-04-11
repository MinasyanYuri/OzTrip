package com.example.oztrip;

import android.annotation.SuppressLint;
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
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity {

    private EditText etName, etUsername, etEmail, etPassword;
    private ImageView ivAvatar;
    private TextView tvInitials;
    private MaterialCardView cardAvatarContainer;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String uid;
    private String currentAvatarUrl = "initials"; // Храним текущую аватарку
    private String currentColorHex = "#FF9500";   // Храним текущий цвет

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

// Внутри onCreate замени инициализацию полей на эту:
        // 1. ВОТ ЭТОГО НЕ ХВАТАЕТ:
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (mAuth.getCurrentUser() != null) {
            uid = mAuth.getCurrentUser().getUid();
        } else {
            // Если юзер не залогинен, не даем ему тут находиться
            finish();
            return;
        }
        etName = findViewById(R.id.etEditName);
        etUsername = findViewById(R.id.etEditUsername);
        etEmail = findViewById(R.id.etEditEmail);
        etPassword = findViewById(R.id.etEditPassword);

        ivAvatar = findViewById(R.id.ivEditAvatar);
        tvInitials = findViewById(R.id.tvEditInitials);
        cardAvatarContainer = findViewById(R.id.cardEditAvatarContainer);

// Кнопка выбора аватара теперь на FloatingActionButton (но ID тот же)
        findViewById(R.id.btnOpenAvatarEditor).setOnClickListener(v -> {
            Intent intent = new Intent(this, AvatarEditorActivity.class);
            intent.putExtra("user_name", etName.getText().toString());
            intent.putExtra("current_avatar", currentAvatarUrl);
            intent.putExtra("current_color", currentColorHex);
            intent.putExtra("is_editing", true);
            startActivity(intent);
        });

        // Заполняем пароль из временного хранилища
        String savedPass = getSharedPreferences("OzTrip_Prefs", MODE_PRIVATE)
                .getString("temp_pass", "");
        if (!savedPass.isEmpty()) {
            etPassword.setText(savedPass);
        }

        loadUserData();

        // Кнопка сохранения
        findViewById(R.id.btnSaveAll).setOnClickListener(v -> saveProfileChanges());
    }

    private void loadUserData() {
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                String name = doc.getString("name");
                String username = doc.getString("username");
                String email = doc.getString("email");

                // КЛЮЧЕВОЙ МОМЕНТ: Сохраняем данные в переменные класса
                currentAvatarUrl = doc.getString("avatar");
                currentColorHex = doc.getString("avatarColor");

                etName.setText(name);
                etUsername.setText(username);
                etEmail.setText(email);
                // В loadUserData после etEmail.setText(email);
                etEmail.setFocusable(false);
                etEmail.setAlpha(0.6f); // Делаем его чуть тусклее

                displayAvatar(currentAvatarUrl, currentColorHex, name);
            }
        });
    }
    private void displayAvatar(String avatar, String colorHex, String name) {
        ivAvatar.setVisibility(View.GONE);
        tvInitials.setVisibility(View.VISIBLE);

        if (avatar == null || avatar.isEmpty() || avatar.equals("initials")) {
            // СЛУЧАЙ 1: Просто буквы
            showInitials(name, colorHex);
        }
        else if (avatar.startsWith("http")) {
            // СЛУЧАЙ 2: Ссылка из интернета
            tvInitials.setVisibility(View.GONE);
            ivAvatar.setVisibility(View.VISIBLE);
            Glide.with(this).load(avatar).circleCrop().into(ivAvatar);
        }
        else {
            // СЛУЧАЙ 3: Твой "Жираф" (ID ресурса или имя ресурса)
            try {
                // Пробуем преобразовать строку "2131230962" в число
                int resId = Integer.parseInt(avatar);
                tvInitials.setVisibility(View.GONE);
                ivAvatar.setVisibility(View.VISIBLE);

                Glide.with(this)
                        .load(resId) // Загружаем по ID
                        .circleCrop()
                        .into(ivAvatar);
            } catch (NumberFormatException e) {
                // Если в базе вдруг имя файла "giraffe", а не ID
                showInitials(name, colorHex);
            }
        }
    }

    // Вынес отрисовку букв в отдельный метод для чистоты
    private void showInitials(String name, String colorHex) {
        ivAvatar.setVisibility(View.GONE);
        tvInitials.setVisibility(View.VISIBLE);
        if (name != null && !name.isEmpty()) {
            tvInitials.setText(name.substring(0, 1).toUpperCase());
        }
        tvInitials.setTextColor(Color.WHITE);
        try {
            cardAvatarContainer.setCardBackgroundColor(Color.parseColor(colorHex));
        } catch (Exception e) {
            cardAvatarContainer.setCardBackgroundColor(Color.parseColor("#FF9800"));
        }
    }

    private void executeFinalUpdate(String name, String username, String email) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("username", username);
        updates.put("email", email);

        // 1. Сначала обновляем текстовые данные в Firestore
        db.collection("users").document(uid).update(updates)
                .addOnSuccessListener(aVoid -> {
                    String newPass = etPassword.getText().toString().trim();

                    // Достаем старый пароль из SharedPreferences для сравнения
                    String initialPass = getSharedPreferences("OzTrip_Prefs", MODE_PRIVATE)
                            .getString("temp_pass", "");

                    // 2. Проверяем: введен ли новый пароль, не пустой ли он и изменился ли он
                    if (!newPass.isEmpty() && !newPass.equals(initialPass)) {

                        // ПРОВЕРКА НА 6 СИМВОЛОВ
                        if (newPass.length() < 6) {
                            etPassword.setError("Пароль должен быть не менее 6 символов");
                            Toast.makeText(this, "Пароль слишком короткий!", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // 3. Обновляем пароль в Firebase Auth
                        mAuth.getCurrentUser().updatePassword(newPass)
                                .addOnSuccessListener(unused -> {
                                    // Очищаем временный пароль, так как он больше не актуален
                                    getSharedPreferences("OzTrip_Prefs", MODE_PRIVATE).edit().remove("temp_pass").apply();

                                    Toast.makeText(this, "Профиль и пароль успешно обновлены!", Toast.LENGTH_SHORT).show();
                                    finish(); // Закрываем экран и возвращаемся в Settings
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(this, "Ошибка обновления пароля: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                });
                    } else {
                        // Если пароль не меняли, просто закрываем экран
                        Toast.makeText(this, "Данные обновлены!", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Ошибка Firestore: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void saveProfileChanges() {
        String newName = etName.getText().toString().trim();
        String newUsername = etUsername.getText().toString().trim();
        String newEmail = etEmail.getText().toString().trim();

        if (newUsername.isEmpty() || newName.isEmpty()) {
            Toast.makeText(this, "Заполни все поля", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("users").whereEqualTo("username", newUsername).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                boolean isTaken = false;
                for (QueryDocumentSnapshot document : task.getResult()) {
                    if (!document.getId().equals(uid)) {
                        isTaken = true;
                        break;
                    }
                }
                if (isTaken) {
                    String suggested = generateNicknameSuggestion(newUsername);
                    etUsername.setError("Ник занят. Попробуй: " + suggested);
                } else {
                    checkEmailAndSave(newName, newUsername, newEmail);
                }
            }
        });
    }

    private void checkEmailAndSave(String name, String username, String email) {
        db.collection("users").whereEqualTo("email", email).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                boolean emailTaken = false;
                for (QueryDocumentSnapshot document : task.getResult()) {
                    if (!document.getId().equals(uid)) {
                        emailTaken = true;
                        break;
                    }
                }
                if (emailTaken) {
                    etEmail.setError("Почта занята");
                } else {
                    executeFinalUpdate(name, username, email);
                }
            }
        });
    }

    private String generateNicknameSuggestion(String baseName) {
        return baseName.toLowerCase() + ((int) (Math.random() * 900) + 100);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Каждый раз, когда мы возвращаемся на этот экран,
        // данные будут подтягиваться из Firestore заново
        loadUserData();
    }

}
