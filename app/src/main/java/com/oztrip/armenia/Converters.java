package com.oztrip.armenia;

import androidx.room.TypeConverter;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import org.maplibre.android.geometry.LatLng;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class Converters {
    @TypeConverter
    public static String fromLatLngList(List<LatLng> list) {
        if (list == null) return null;
        StringBuilder sb = new StringBuilder();
        for (org.maplibre.android.geometry.LatLng loc : list) {
            sb.append(loc.getLatitude()).append(",").append(loc.getLongitude()).append(";");
        }
        return sb.toString();
    }

    @TypeConverter
    public static List<org.maplibre.android.geometry.LatLng> toLatLngList(String data) {
        if (data == null || data.isEmpty()) return new ArrayList<>();
        List<org.maplibre.android.geometry.LatLng> list = new ArrayList<>();
        String[] points = data.split(";");
        for (String p : points) {
            String[] latLng = p.split(",");
            if (latLng.length == 2) {
                list.add(new org.maplibre.android.geometry.LatLng(
                        Double.parseDouble(latLng[0]), Double.parseDouble(latLng[1])));
            }
        }
        return list;
    }

    @TypeConverter
    public static List<String> fromString(String value) {
        Type listType = new TypeToken<ArrayList<String>>() {}.getType();
        return new Gson().fromJson(value, listType);
    }

    @TypeConverter
    public static String fromList(List<String> list) {
        Gson gson = new Gson();
        return gson.toJson(list);
    }
}