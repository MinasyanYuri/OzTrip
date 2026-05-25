package com.example.oztrip;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;

public class AboutMeActivity extends BaseActivity  {

    private EditText etAboutMe;
    private SharedPreferences prefs;
    private String originalText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about_me);

        prefs = getSharedPreferences("OzTripPrefs", MODE_PRIVATE);
        etAboutMe = findViewById(R.id.etAboutMe);
        ImageView btnSave = findViewById(R.id.btnSaveAbout);
        ImageView btnClear = findViewById(R.id.btnClearAbout);
        ImageView btnBack = findViewById(R.id.btnBackAbout);

        originalText = prefs.getString("about_me", "");
        etAboutMe.setText(originalText);
        etAboutMe.setSelection(etAboutMe.getText().length());

        btnSave.setOnClickListener(v -> {
            prefs.edit().putString("about_me", etAboutMe.getText().toString().trim()).apply();
            finish();
        });

        btnClear.setOnClickListener(v -> {
            etAboutMe.setText("");
        });

        btnBack.setOnClickListener(v -> {
            if (etAboutMe.getText().toString().equals(originalText)) {
                finish();
            } else {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Сохранить изменения?")
                        .setPositiveButton("Сохранить", (d, w) -> {
                            prefs.edit().putString("about_me", etAboutMe.getText().toString().trim()).apply();
                            finish();
                        })
                        .setNegativeButton("Не сохранять", (d, w) -> finish())
                        .setNeutralButton("Продолжить редактирование", null)
                        .show();
            }
        });
    }
}