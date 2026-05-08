package com.example.oztrip;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private TextView tvWelcome, tvUserNick, tvInitials;
    private ImageView ivAvatar;
    private MaterialCardView cardAvatarContainer;
    private MaterialButton btnEditProfile, btnLogout;
    private FirebaseFirestore db;
    private String uid;
    private boolean isGuest;

    private SwitchMaterial switchDarkTheme;
    private TextView tvCurrentUnit;
    private TextView tvCurrentLanguage;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 1. Применяем локаль ДО загрузки макета
        SharedPreferences prefs = getSharedPreferences("OzTripPrefs", MODE_PRIVATE);
        this.prefs = prefs;
        String lang = prefs.getString("language", "ru");
        setLocale(lang);

        // 2. Теперь загружаем макет
        setContentView(R.layout.activity_settings);


        // Определяем режим гостя
        isGuest = prefs.getBoolean("guest_mode", false);

        // Инициализация Firebase только если авторизованы
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null && !isGuest) {
            uid = auth.getCurrentUser().getUid();
            db = FirebaseFirestore.getInstance();
        } else {
            uid = null;
        }

        // Привязка View
        tvWelcome = findViewById(R.id.tvWelcome);
        tvUserNick = findViewById(R.id.tvUserNick);
        tvInitials = findViewById(R.id.tvInitials);
        ivAvatar = findViewById(R.id.ivAvatar);
        cardAvatarContainer = findViewById(R.id.cardAvatarContainer);
        btnEditProfile = findViewById(R.id.btnEditProfile);
        btnLogout = findViewById(R.id.btnLogoutSettings);

        switchDarkTheme = findViewById(R.id.switchDarkTheme);
        tvCurrentUnit = findViewById(R.id.tvCurrentUnit);
        tvCurrentLanguage = findViewById(R.id.tvCurrentLanguage);

        // Настройка интерфейса для гостя
        if (uid == null) {
            btnEditProfile.setVisibility(View.GONE);
            btnLogout.setText(getString(R.string.text_auto_166));
            tvWelcome.setText(getString(R.string.text_auto_167));
            tvUserNick.setText("@guest");
            tvInitials.setVisibility(View.VISIBLE);
            tvInitials.setText("G");
            cardAvatarContainer.setCardBackgroundColor(Color.LTGRAY);
            ivAvatar.setVisibility(View.GONE);
        } else {
            btnLogout.setText(getString(R.string.text_auto_168));
        }

        // Кнопка Изменить профиль
        if (btnEditProfile.getVisibility() == View.VISIBLE) {
            btnEditProfile.setOnClickListener(v -> showLuxuryAuthDialog());
        }

        // Кнопка выхода
        btnLogout.setOnClickListener(v -> {
            if (isGuest) {
                prefs.edit()
                        .remove("guest_mode")
                        .remove("guest_travels")
                        .apply();
            } else {
                FirebaseAuth.getInstance().signOut();
            }
            Intent intent = new Intent(SettingsActivity.this, RegisterActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        // Кнопка Назад
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // === НОВЫЕ НАСТРОЙКИ ===

        // 1. Тёмная тема
        boolean isDark = prefs.getBoolean("dark_theme", false);
        switchDarkTheme.setChecked(isDark);
        applyTheme();
        switchDarkTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("dark_theme", isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(
                    isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
            );
        });

        // 2. Единицы измерения
        String unit = prefs.getString("units", "km");
        tvCurrentUnit.setText(unit.equals("km") ? getString(R.string.text_auto_169) : getString(R.string.text_auto_170));
        findViewById(R.id.itemUnits).setOnClickListener(v -> {
            String[] options = {getString(R.string.text_auto_171), getString(R.string.text_auto_170)};
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.text_auto_172))
                    .setSingleChoiceItems(options, unit.equals("km") ? 0 : 1, (dialog, which) -> {
                        String chosen = which == 0 ? "km" : "mi";
                        prefs.edit().putString("units", chosen).apply();
                        tvCurrentUnit.setText(which == 0 ? getString(R.string.text_auto_169) : getString(R.string.text_auto_170));
                        dialog.dismiss();
                    })
                    .show();
        });

        // Внутри SettingsActivity, в методе onCreate, замените блок getString(R.string.text_auto_173) на этот:

// 3. Язык
        String currentLang = prefs.getString("language", "ru");
        String[] langCodes = {"ru", "en", "hy"};
        String[] langNames = {getString(R.string.text_86), getString(R.string.text_87), getString(R.string.text_88)};
        int langIndex = lang.equals("ru") ? 0 : lang.equals("en") ? 1 : 2;
        if (tvCurrentLanguage != null) tvCurrentLanguage.setText(langNames[langIndex]);
// и для всех findViewById, которые могут отсутствовать

        findViewById(R.id.itemLanguage).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.text_auto_174))
                    .setSingleChoiceItems(langNames, langIndex, (dialog, which) -> {
                        String selected = langCodes[which];
                        prefs.edit().putString("language", selected).apply();
                        // Обновляем метку мгновенно, не дожидаясь recreate
                        if (tvCurrentLanguage != null) tvCurrentLanguage.setText(langNames[which]);
                        setLocale(selected);
                        dialog.dismiss();
                        recreate();   // пересоздаём активность с новым языком
                    })
                    .show();
        });

// Для единиц измерения также используем строки ресурсов (опционально):
// getString(R.string.text_auto_171) и getString(R.string.text_auto_170) лучше заменить на ресурсы text_89, text_90, но сейчас не критично.

        // 5. О приложении
        findViewById(R.id.itemAbout).setOnClickListener(v -> {
            new AlertDialog.Builder(this, R.style.PremiumDialogTheme)
                    .setTitle("OzTrip")
                    .setMessage(getString(R.string.text_auto_175))
                    .setPositiveButton("OK", null)
                    .show();
        });
    }

    // Установка новой локали
    private void setLocale(String lang) {
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Resources res = getResources();
        Configuration config = res.getConfiguration();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale);
        } else {
            config.locale = locale;
        }
        res.updateConfiguration(config, res.getDisplayMetrics());
    }

    private void restartApp() {
        recreate();   // пересоздаст только SettingsActivity с новым языком
    }



    private void applyTheme() {
        boolean isDark = prefs.getBoolean("dark_theme", false);
        View root = findViewById(android.R.id.content);
        if (root != null) {
            root.setBackgroundColor(isDark ? Color.parseColor("#0A0A0F") : Color.parseColor("#FFFDE7"));
        }

        MaterialCardView settingsCard = findViewById(R.id.settingsCard);
        if (settingsCard != null) {
            settingsCard.setCardBackgroundColor(isDark ? Color.parseColor("#1E1E1E") : Color.WHITE);
        }

        // Цвета текста внутри карточки
        int textColor = isDark ? Color.WHITE : Color.parseColor("#212121");
        applyTextColorToAll(settingsCard, textColor);

        // Разделители
        if (settingsCard != null) {
            LinearLayout container = (LinearLayout) settingsCard.getChildAt(0);
            if (container != null) {
                for (int i = 0; i < container.getChildCount(); i++) {
                    View child = container.getChildAt(i);
                    if (child instanceof View && child.getTag() == null && child.getBackground() != null) {
                        child.setBackgroundColor(isDark ? Color.parseColor("#2A2A2A") : Color.parseColor("#F5F5F5"));
                    }
                }
            }
        }

        // Текст в профиле
        if (tvWelcome != null) tvWelcome.setTextColor(Color.WHITE);
        if (tvUserNick != null) tvUserNick.setTextColor(Color.parseColor("#E0E0E0"));
    }
    // Рекурсивный метод для установки цвета текста всем TextView внутри ViewGroup
    private void applyTextColorToAll(ViewGroup parent, int color) {
        if (parent == null) return;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setTextColor(color);
            } else if (child instanceof ViewGroup) {
                applyTextColorToAll((ViewGroup) child, color);
            }
        }
    }
    private void deleteCache() {
        try {
            File cacheDir = getCacheDir();
            deleteDir(cacheDir);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new File(dir, child));
                    if (!success) return false;
                }
            }
        }
        return dir.delete();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (uid != null) {
            loadUserProfile();
        }
    }

    private void loadUserProfile() {
        if (uid == null || db == null) return;
        db.collection("users").document(uid).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String name = document.getString("name");
                        String username = document.getString("username");
                        String avatar = document.getString("avatar");
                        String colorHex = document.getString("avatarColor");

                        if (name != null && !name.isEmpty()) {
                            tvWelcome.setText(getString(R.string.text_auto_176) + name + "!");
                        } else {
                            tvWelcome.setText(getString(R.string.text_auto_177));
                        }
                        if (username != null) {
                            tvUserNick.setText("@" + username);
                        }
                        displayAvatar(avatar, colorHex, name);
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, getString(R.string.text_auto_178), Toast.LENGTH_SHORT).show());
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
                    .signature(new com.bumptech.glide.signature.ObjectKey(System.currentTimeMillis()))
                    .into(ivAvatar);
        } else {
            try {
                int iconRes = Integer.parseInt(avatar);
                tvInitials.setVisibility(View.GONE);
                ivAvatar.setVisibility(View.VISIBLE);
                ivAvatar.setImageResource(iconRes);
                cardAvatarContainer.setCardBackgroundColor(Color.WHITE);
            } catch (Exception e) {
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
                etPass.setError(getString(R.string.text_auto_179));
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
                        etPass.setError(getString(R.string.text_auto_180));
                        Toast.makeText(this, getString(R.string.text_auto_61) + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        });

        dialog.show();
    }
}
