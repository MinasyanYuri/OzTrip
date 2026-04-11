package com.example.oztrip;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.maplibre.android.geometry.LatLng;
import java.io.IOException;

// Этот класс говорит Гсону: "Когда видишь LatLng, просто запиши lat и lon"
public class LatLngAdapter extends TypeAdapter<LatLng> {
    @Override
    public void write(JsonWriter out, LatLng value) throws IOException {
        out.beginObject();
        out.name("lat").value(value.getLatitude());
        out.name("lon").value(value.getLongitude());
        out.endObject();
    }

    @Override
    public LatLng read(JsonReader reader) throws IOException {
        double lat = 0, lng = 0;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("lat")) {           // <-- было "latitude"
                lat = reader.nextDouble();
            } else if (name.equals("lon")) {    // <-- было "longitude"
                lng = reader.nextDouble();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return new LatLng(lat, lng);
    }

}