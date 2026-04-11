package com.example.oztrip;

import org.maplibre.android.geometry.LatLng;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Уникальное место сохранения
public class SavedLocation {
    public transient org.maplibre.android.annotations.Icon cachedIcon = null;
    public float rating = 50f; // Оценка по умолчанию (середина)
    public LatLng latLng;
    public int level;
    public String note = ""; // Для текста
    public String customName = "";
    public String date = ""; // По умолчанию пусто
    public java.util.List<String> photoPaths = new java.util.ArrayList<>(); // Для картинок

    public SavedLocation(LatLng latLng) {
        this.latLng = latLng;
        this.level = 1;
    }

    // В классе SavedLocation
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("lat", latLng.getLatitude());
        map.put("lng", latLng.getLongitude());
        map.put("level", level);
        map.put("note", note);
        map.put("customName", customName);
        map.put("date", date);
        map.put("rating", rating);
        map.put("photoPaths", photoPaths);
        return map;
    }

    public static SavedLocation fromMap(Map<String, Object> map, String id) {
        double lat = (double) map.get("lat");
        double lng = (double) map.get("lng");
        LatLng latLng = new LatLng(lat, lng);
        SavedLocation loc = new SavedLocation(latLng);
        loc.level = ((Long) map.get("level")).intValue();
        loc.note = (String) map.get("note");
        loc.customName = (String) map.get("customName");
        loc.date = (String) map.get("date");
        loc.rating = ((Double) map.get("rating")).floatValue();
        loc.photoPaths = (List<String>) map.get("photoPaths");
        return loc;
    }
}