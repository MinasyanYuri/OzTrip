package com.example.oztrip;

import android.content.Intent;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.firebase.auth.FirebaseAuth;

public class SettingsFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Здесь мы загружаем твой красный XML
        setPreferencesFromResource(R.xml.preferences, rootKey);

        // Логика выхода
        findPreference("logout_key").setOnPreferenceClickListener(preference -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(getActivity(), RegisterActivity.class));
            getActivity().finish();
            return true;
        });
    }
}