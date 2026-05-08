package com.example.oztrip;

import android.app.Application;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public class OzTripApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Читаем сохранённую настройку
        SharedPreferences prefs = getSharedPreferences("OzTripPrefs", MODE_PRIVATE);
        boolean isDark = prefs.getBoolean("dark_theme", false); // по умолчанию false (светлая)

        // Принудительно устанавливаем тему для всего приложения
        AppCompatDelegate.setDefaultNightMode(
                isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
    }
}