//package com.example.oztrip;
//
//import androidx.room.Entity;
//import androidx.room.Ignore;
//import androidx.room.PrimaryKey;
//import org.maplibre.android.geometry.LatLng;
//import org.maplibre.android.annotations.Marker; // Импорт маркера
//import java.util.ArrayList;
//import java.util.List;
//
//@Entity(tableName = "travel_points")
//public class TravelPoint {
//    @PrimaryKey(autoGenerate = true)
//    public int id;
//    public int listId;
//
//    public String title;
//    public String customName;
//    public String note;
//    public String date;
//    public float rating;
//    public double latitude;
//    public double longitude;
//    public int level = 1;
//
//    // --- ЛЮКСОВЫЕ ПОЛЯ (Игнорируем для базы данных Room) ---
//
//    @Ignore
//    public Marker marker; // Тот самый маркер, который мы будем менять
//
//    @Ignore
//    public org.maplibre.android.annotations.Icon cachedIcon;
//
//    @Ignore
//    public List<String> photoPaths = new ArrayList<>();
//
//    @Ignore
//    public LatLng latLng;
//
//    // Конструктор
//    public TravelPoint() {}
//
//    public LatLng getLatLng() {
//        if (latLng == null) latLng = new LatLng(latitude, longitude);
//        return latLng;
//    }
//}