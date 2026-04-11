package com.example.oztrip;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.maplibre.android.geometry.LatLng; // Проверь этот импорт для точек

public class TravelList {
    public String name;
    public ArrayList<SavedLocation> locations = new ArrayList<>();
    // ДОБАВЬ ЭТО: теперь у каждого листа своя линия
    public ArrayList<org.maplibre.android.geometry.LatLng> pathPoints = new ArrayList<>();

    public TravelList(String name) { this.name = name; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);

        // Сохраняем locations как список мапов
        List<Map<String, Object>> locMaps = new ArrayList<>();
        for (SavedLocation loc : locations) {
            locMaps.add(loc.toMap());
        }
        map.put("locations", locMaps);

        // Сохраняем pathPoints как список объектов с lat/lng
        List<Map<String, Double>> points = new ArrayList<>();
        for (LatLng p : pathPoints) {
            Map<String, Double> point = new HashMap<>();
            point.put("lat", p.getLatitude());
            point.put("lng", p.getLongitude());
            points.add(point);
        }
        map.put("pathPoints", points);
        return map;
    }

    public static TravelList fromMap(String id, Map<String, Object> map) {
        String name = (String) map.get("name");
        TravelList list = new TravelList(name);

        // Восстанавливаем locations
        List<Map<String, Object>> locMaps = (List<Map<String, Object>>) map.get("locations");
        if (locMaps != null) {
            for (Map<String, Object> locMap : locMaps) {
                list.locations.add(SavedLocation.fromMap(locMap, null));
            }
        }

        // Восстанавливаем pathPoints
        List<Map<String, Double>> points = (List<Map<String, Double>>) map.get("pathPoints");
        if (points != null) {
            for (Map<String, Double> point : points) {
                list.pathPoints.add(new LatLng(point.get("lat"), point.get("lng")));
            }
        }
        return list;
    }
}