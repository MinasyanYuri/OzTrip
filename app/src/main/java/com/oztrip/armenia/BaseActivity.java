package com.oztrip.armenia;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        // Применяем сохранённую локаль к базовому контексту
        SharedPreferences prefs = newBase.getSharedPreferences("OzTripPrefs", MODE_PRIVATE);
        String lang = prefs.getString("language", "ru");
        super.attachBaseContext(applyLocale(newBase, lang));
    }

    private Context applyLocale(Context context, String lang) {
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Resources res = context.getResources();
        Configuration config = res.getConfiguration();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale);
        } else {
            config.locale = locale;
        }
        res.updateConfiguration(config, res.getDisplayMetrics());
        return context;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Ещё раз применяем локаль перед загрузкой макета
        SharedPreferences prefs = getSharedPreferences("OzTripPrefs", MODE_PRIVATE);
        String lang = prefs.getString("language", "ru");
        applyLocale(this, lang);
        super.onCreate(savedInstanceState);
    }
}