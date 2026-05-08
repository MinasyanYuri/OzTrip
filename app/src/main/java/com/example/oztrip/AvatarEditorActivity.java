package com.example.oztrip;

import static android.content.Intent.getIntent;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
public class AvatarEditorActivity extends AppCompatActivity {

    private ImageView ivAvatarLarge;
    private MaterialCardView cardAvatarLarge;
    private Uri currentImageUri;
    private TextView tvAvatarPlaceholder;
    private LinearLayout llPaletteContainer;
    private HorizontalScrollView hsvPalette;
    private ProgressBar pbLoading;

    private String name, username, uid;
    private int selectedColor = Color.parseColor("?android:textColorPrimary");
    private int selectedIconRes = 0;

    private final int[] palette = {
            Color.parseColor("#cc0000"), Color.parseColor("#FF5252"), Color.parseColor("#FF4081"),
            Color.parseColor("#E040FB"), Color.parseColor("#7C4DFF"), Color.parseColor("#536DFE"),
            Color.parseColor("#448AFF"), Color.parseColor("#40C4FF"), Color.parseColor("#18FFFF"),
            Color.parseColor("#69F0AE"), Color.parseColor("#178700"), Color.parseColor("#B2FF59"),
            Color.parseColor("#EEFF41"), Color.parseColor("#FFD740"), Color.parseColor("#FFAB40"),
            Color.parseColor("#FF6E40"), Color.parseColor("#BDBDBD"), Color.parseColor("?android:textColorSecondary"),
            Color.parseColor("?android:textColorPrimary"), Color.parseColor("#5D4037"), Color.parseColor("#8D6E63"),
            Color.parseColor("#FFCCBC")
    };

    private final int[] pngIcons = {
            R.drawable.man, R.drawable.woman, R.drawable.astronaut,
            R.drawable.bear, R.drawable.cat, R.drawable.dog,
            R.drawable.bluedog, R.drawable.giraffe, R.drawable.lion,
            R.drawable.chicken, R.drawable.dinosaur, R.drawable.meerkat,
            R.drawable.panda, R.drawable.penguin, R.drawable.rabbit
    };

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    String type = getContentResolver().getType(uri);
                    if (type != null && type.startsWith("image/")) {
                        currentImageUri = uri;
                        selectedIconRes = 0;
                        updateAvatarDisplay();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_avatar_editor);
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(this, getString(R.string.text_auto_65), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            java.util.Map<String, String> config = new java.util.HashMap<>();
            config.put("cloud_name", "dzwqtyn5a");
            config.put("api_key", "288782578214651");
            config.put("api_secret", "9d3Am2nQFQR5D1P6mMue_8U1LJo");
            MediaManager.init(this, config);
        } catch (IllegalStateException e) {
            // Если MediaManager уже инициализирован, просто игнорируем
        }

        // Инициализация данных
        name = getIntent().getStringExtra("user_name");
        username = getIntent().getStringExtra("user_username");
        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Привязка View
        ivAvatarLarge = findViewById(R.id.ivAvatarLarge);
        cardAvatarLarge = findViewById(R.id.cardAvatarLarge);
        tvAvatarPlaceholder = findViewById(R.id.tvAvatarPlaceholder);
        llPaletteContainer = findViewById(R.id.llPaletteContainer);
        hsvPalette = findViewById(R.id.hsvPalette);
        pbLoading = findViewById(R.id.pbLoading);
        String currentAvatar = getIntent().getStringExtra("current_avatar");
        String currentColor = getIntent().getStringExtra("current_color");
        boolean isEditing = getIntent().getBooleanExtra("is_editing", false);
        setupPalette();

        if (name != null && !name.isEmpty()) {
            tvAvatarPlaceholder.setText(name.substring(0, 1).toUpperCase());
        }
        if (currentColor != null) {
            selectedColor = Color.parseColor(currentColor);
        }
        if (currentAvatar != null && currentAvatar.startsWith("http")) {
            // Если это ссылка на фото (Cloudinary)
            ivAvatarLarge.setVisibility(View.VISIBLE);
            tvAvatarPlaceholder.setVisibility(View.GONE);
            Glide.with(this).load(currentAvatar).into(ivAvatarLarge);
        } else if (currentAvatar != null && !currentAvatar.equals("initials")) {
            // Если это иконка-персонаж (хранится как ID ресурса)
            try {
                selectedIconRes = Integer.parseInt(currentAvatar);
            } catch (Exception e) { selectedIconRes = 0; }
        }
        findViewById(R.id.btnChooseNew).setOnClickListener(v -> showAvatarOptions());
        findViewById(R.id.btnSaveAvatar).setOnClickListener(v -> uploadAllDataToFirebase());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        updateAvatarDisplay();
    }

    private void setupPalette() {
        llPaletteContainer.removeAllViews();
        for (int color : palette) {
            MaterialCardView colorCircle = new MaterialCardView(this);
            int size = (int) (40 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(12, 0, 12, 0);

            colorCircle.setLayoutParams(params);
            colorCircle.setRadius(size / 2f);
            colorCircle.setCardBackgroundColor(color);
            colorCircle.setStrokeWidth(0);
            colorCircle.setClickable(true);

            colorCircle.setOnClickListener(v -> {
                selectedColor = color;
                currentImageUri = null;
                selectedIconRes = 0;
                updateAvatarDisplay();

                for (int i = 0; i < llPaletteContainer.getChildCount(); i++) {
                    ((MaterialCardView) llPaletteContainer.getChildAt(i)).setStrokeWidth(0);
                }
                colorCircle.setStrokeWidth(6);
                colorCircle.setStrokeColor(Color.WHITE);
            });
            llPaletteContainer.addView(colorCircle);
        }
    }

    private void uploadAllDataToFirebase() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            // Гость – просто завершаем настройку профиля без загрузки в облако
            Toast.makeText(this, getString(R.string.text_auto_66), Toast.LENGTH_SHORT).show();
            finishRegistration("initials", "?colorOnPrimary"); // или любой дефолтный цвет
            return;
        }
        if (pbLoading != null) pbLoading.setVisibility(View.VISIBLE);

        String hexColor = String.format("#%06X", (0xFFFFFF & selectedColor));

        if (currentImageUri != null) {
            // Используем Cloudinary вместо Firebase Storage
            uploadImageToCloudinary(currentImageUri);
        } else if (selectedIconRes != 0) {
            finishRegistration(String.valueOf(selectedIconRes), "?colorOnPrimary");
        } else {
            finishRegistration("initials", hexColor);
        }
    }

    private void uploadImageToCloudinary(Uri imageUri) {
        MediaManager.get().upload(imageUri)
                .option("folder", "user_avatars")
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {}

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {}

                    @Override
                    public void onSuccess(String requestId, java.util.Map resultData) {
                        // Это прямая ссылка на фото, которую мы сохраним в Firestore
                        String imageUrl = (String) resultData.get("secure_url");
                        finishRegistration(imageUrl, "?colorOnPrimary");
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                        Toast.makeText(AvatarEditorActivity.this, getString(R.string.text_auto_67) + error.getDescription(), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {}
                }).dispatch();
    }


    private void finishRegistration(String selectedAvatar, String avatarColor) {
        if (pbLoading != null) pbLoading.setVisibility(View.VISIBLE);

        // Проверяем, откуда мы пришли
        boolean isEditing = getIntent().getBooleanExtra("is_editing", false);
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Данные для обновления/сохранения
        java.util.Map<String, Object> userUpdates = new java.util.HashMap<>();
        userUpdates.put("avatar", selectedAvatar);
        userUpdates.put("avatarColor", avatarColor);

        if (isEditing) {
            // --- СЦЕНАРИЙ РЕДАКТИРОВАНИЯ ---
            FirebaseFirestore.getInstance().collection("users").document(uid)
                    .update(userUpdates) // Используем update, чтобы не затереть пароль/ник
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, getString(R.string.text_auto_68), Toast.LENGTH_SHORT).show();
                        finish(); // Просто закрываем этот экран и возвращаемся в профиль
                    })
                    .addOnFailureListener(e -> {
                        pbLoading.setVisibility(View.GONE);
                        Toast.makeText(this, getString(R.string.text_auto_61) + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });

        } else {
            // --- СЦЕНАРИЙ РЕГИСТРАЦИИ (Твой старый код) ---
            userUpdates.put("uid", uid);
            userUpdates.put("email", getIntent().getStringExtra("user_email"));
            userUpdates.put("username", getIntent().getStringExtra("user_username"));
            userUpdates.put("name", getIntent().getStringExtra("user_name"));

            FirebaseFirestore.getInstance().collection("users").document(uid)
                    .set(userUpdates) // Тут используем set, так как документа еще нет
                    .addOnSuccessListener(aVoid -> {
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    });
        }
    }

    private void showAvatarOptions() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View view = getLayoutInflater().inflate(R.layout.layout_avatar_options, null);

        RecyclerView rvIcons = view.findViewById(R.id.rvIcons);
        MaterialButton btnGallery = view.findViewById(R.id.btnOpenGallery);
        MaterialButton btnReset = view.findViewById(R.id.btnResetToInitials);

        rvIcons.setLayoutManager(new GridLayoutManager(this, 3));
        rvIcons.setAdapter(new IconAdapter(pngIcons, iconRes -> {
            selectedIconRes = iconRes;
            currentImageUri = null;
            updateAvatarDisplay();
            bottomSheet.dismiss();
        }));

        btnGallery.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            imagePickerLauncher.launch(intent);
            bottomSheet.dismiss();
        });

        btnReset.setOnClickListener(v -> {
            currentImageUri = null;
            selectedIconRes = 0;
            updateAvatarDisplay();
            bottomSheet.dismiss();
        });

        bottomSheet.setContentView(view);
        bottomSheet.show();
    }

    private void updateAvatarDisplay() {
        Animation fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
        fadeIn.setDuration(400);

        if (currentImageUri != null || selectedIconRes != 0) {
            tvAvatarPlaceholder.setVisibility(View.GONE);
            ivAvatarLarge.setVisibility(View.VISIBLE);

            if (currentImageUri != null) {
                ivAvatarLarge.setImageURI(currentImageUri);
                ivAvatarLarge.setScaleType(ImageView.ScaleType.CENTER_CROP);
                ivAvatarLarge.setPadding(0, 0, 0, 0);
            } else {
                ivAvatarLarge.setImageResource(selectedIconRes);
                ivAvatarLarge.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                ivAvatarLarge.setPadding(30, 30, 30, 30);
            }

            ivAvatarLarge.startAnimation(fadeIn);
            cardAvatarLarge.setCardBackgroundColor(Color.WHITE);
            cardAvatarLarge.setStrokeWidth(0);
            hsvPalette.setVisibility(View.GONE);
            findViewById(R.id.tvPaletteHint).setVisibility(View.GONE);

        } else {
            tvAvatarPlaceholder.setVisibility(View.VISIBLE);
            ivAvatarLarge.setVisibility(View.GONE);
            tvAvatarPlaceholder.startAnimation(fadeIn);

            cardAvatarLarge.setCardBackgroundColor(selectedColor);
            cardAvatarLarge.setStrokeWidth(4);
            cardAvatarLarge.setStrokeColor(Color.parseColor("#FF9800"));
            hsvPalette.setVisibility(View.VISIBLE);
            findViewById(R.id.tvPaletteHint).setVisibility(View.VISIBLE);
        }
    }
}