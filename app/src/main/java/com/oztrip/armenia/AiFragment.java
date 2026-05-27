package com.oztrip.armenia;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;

import org.json.JSONArray;
import org.json.JSONObject;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AiFragment extends Fragment {
    private String cachedWeather = null;
    private RecyclerView chatRecyclerView;
    private EditText messageInput;
    private ImageView sendButton;
    private ChatAdapter chatAdapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String cachedContext = null;
    private int remainingRequests = -1;
    private String resetTime = null;
    private int remainingTokens = -1;
    private String currentLang = "ru";
    private OkHttpClient httpClient;
    private String lastUserQuery = "";
    private String cachedCountryCode = null;

    // ИИ-сервисы (ваши рабочие ключи)
    private static final String GROQ_API_KEY = "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";
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama-3.3-70b-versatile";

    private static final String OR_API_KEY = "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";
    private static final String OR_API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String OR_MODEL = "meta-llama/llama-3.3-70b-instruct:free";

    // LocationIQ (геокодер)
    private static final String LOCATIONIQ_API_KEY = "pk.d6ccc4e4c8a5c46c74c7e67578874a7c";
    private static final String LOCATIONIQ_SEARCH_URL = "https://api.locationiq.com/v1/search.php";
    private static final String LOCATIONIQ_REVERSE_URL = "https://api.locationiq.com/v1/reverse.php";

    // Overpass API (запасной геопоиск)
    private static final String OVERPASS_URL = "https://overpass-api.de/api/interpreter";

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final String[] PLACE_TRIGGERS = {
            "где", "какие", "какой", "какая", "найти", "поблизости", "рядом",
            "спортзал", "аптека", "кафе", "ресторан", "магазин", "столовая",
            "пиццерия", "суши", "бургер", "завтрак", "обед", "ужин", "пицца",
            "кофейня", "пекарня", "гостиница", "отель", "музей", "театр",
            "кинотеатр", "парк", "больница", "поликлиника", "банкомат", "заправка"
    };

    private static final String[] PLACE_CATEGORIES = {
            "спортзал", "аптека", "кафе", "ресторан", "пиццерия", "суши",
            "бургер", "кофейня", "пекарня", "гостиница", "отель", "музей",
            "театр", "кинотеатр", "парк", "больница", "поликлиника", "магазин",
            "банкомат", "заправка", "столовая"
    };

    private static final Map<String, String[]> CATEGORY_TO_OSM_TAGS = new HashMap<>();
    static {
        CATEGORY_TO_OSM_TAGS.put("спортзал", new String[]{"amenity=gym", "leisure=fitness_centre"});
        CATEGORY_TO_OSM_TAGS.put("аптека", new String[]{"amenity=pharmacy"});
        CATEGORY_TO_OSM_TAGS.put("кафе", new String[]{"amenity=cafe"});
        CATEGORY_TO_OSM_TAGS.put("ресторан", new String[]{"amenity=restaurant"});
        CATEGORY_TO_OSM_TAGS.put("пиццерия", new String[]{"amenity=restaurant", "cuisine=pizza"});
        CATEGORY_TO_OSM_TAGS.put("суши", new String[]{"amenity=restaurant", "cuisine=sushi"});
        CATEGORY_TO_OSM_TAGS.put("бургер", new String[]{"amenity=fast_food", "cuisine=burger"});
        CATEGORY_TO_OSM_TAGS.put("кофейня", new String[]{"amenity=cafe", "shop=coffee"});
        CATEGORY_TO_OSM_TAGS.put("пекарня", new String[]{"shop=bakery"});
        CATEGORY_TO_OSM_TAGS.put("гостиница", new String[]{"tourism=hotel"});
        CATEGORY_TO_OSM_TAGS.put("отель", new String[]{"tourism=hotel"});
        CATEGORY_TO_OSM_TAGS.put("музей", new String[]{"tourism=museum"});
        CATEGORY_TO_OSM_TAGS.put("театр", new String[]{"amenity=theatre"});
        CATEGORY_TO_OSM_TAGS.put("кинотеатр", new String[]{"amenity=cinema"});
        CATEGORY_TO_OSM_TAGS.put("парк", new String[]{"leisure=park"});
        CATEGORY_TO_OSM_TAGS.put("больница", new String[]{"amenity=hospital"});
        CATEGORY_TO_OSM_TAGS.put("поликлиника", new String[]{"amenity=clinic"});
        CATEGORY_TO_OSM_TAGS.put("магазин", new String[]{"shop=supermarket", "shop=convenience"});
        CATEGORY_TO_OSM_TAGS.put("банкомат", new String[]{"amenity=atm"});
        CATEGORY_TO_OSM_TAGS.put("заправка", new String[]{"amenity=fuel"});
        CATEGORY_TO_OSM_TAGS.put("столовая", new String[]{"amenity=canteen", "amenity=restaurant"});
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_ai, container, false);

        chatRecyclerView = root.findViewById(R.id.chatRecyclerView);
        messageInput = root.findViewById(R.id.messageInput);
        sendButton = root.findViewById(R.id.sendButton);

        chatAdapter = new ChatAdapter();
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        chatRecyclerView.setItemAnimator(null);
        chatRecyclerView.setItemViewCacheSize(20);
        chatRecyclerView.setAdapter(chatAdapter);

        chatAdapter.setOnLocationClickListener((lat, lng) -> {
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null && activity.getLiquidNav() != null) {
                activity.getLiquidNav().setSelectedIndex(0);
            }
            if (activity != null && activity.mapLibre != null) {
                activity.mapLibre.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), 15), 1000);
            }
        });

        chatAdapter.addMessage(new ChatMessage(getString(R.string.text_auto_1), false));

        if (getActivity() != null) {
            SharedPreferences prefs = getActivity().getSharedPreferences("OzTripPrefs", Context.MODE_PRIVATE);
            currentLang = prefs.getString("language", "ru");
        }

        httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();

        sendButton.setOnClickListener(v -> sendMessage());
        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        return root;
    }

    public void clearCachedContext() {
        cachedContext = null;
    }

    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        lastUserQuery = text;
        chatAdapter.addMessage(new ChatMessage(text, true));
        messageInput.setText("");

        ChatMessage typing = new ChatMessage("...", false);
        chatAdapter.addMessage(typing);

        fetchWeatherIfNeeded(text, () -> {
            cachedContext = buildFullContext();
            String systemPrompt = buildSystemPrompt();

            // Сначала пробуем Groq (основной)
            sendGroqRequest(systemPrompt, text, groqResponse -> {
                if (!groqResponse.startsWith("ERROR:")) {
                    displayResponse(groqResponse, typing);
                } else {
                    // Если Groq не сработал – переключаемся на OpenRouter
                    sendOpenRouterRequest(systemPrompt, text, orResponse -> {
                        if (!orResponse.startsWith("ERROR:")) {
                            displayResponse("(OpenRouter) " + orResponse, typing);
                        } else {
                            displayResponse("Извините, сервис временно недоступен. Попробуйте позже.", typing);
                        }
                    });
                }
            });
        });
    }

    private void displayResponse(String text, ChatMessage typing) {
        mainHandler.post(() -> {
            if (!isAdded()) return;
            chatAdapter.replaceLastMessage(new ChatMessage(text, false));
        });
    }

    private String buildSystemPrompt() {
        String prompt = "Ты — OzTrip AI, персональный гид пользователя. "
                + "ВАЖНО: Ты обязан отвечать строго на том языке, на котором написан последний вопрос пользователя. "
                + "Ты видишь все его поездки, сохранённые точки, заметки, рейтинги и текущее местоположение. "
                + "Всегда используй его для поиска ближайших мест, если пользователь не попросил конкретный город или регион. "
                + "Ты можешь предлагать любые места, даже если их нет в сохранённых данных. "
                + "Ты можешь управлять приложением через команды:\n"
                + "- создать поездку: [action:create_trip;Название]\n"
                + "- переименовать поездку: [action:rename_trip;старое_название;новое_название]\n"
                + "- удалить поездку: [action:delete_trip;Название]\n"
                + "- добавить точку в активную поездку: [action:add_point;широта;долгота;название]\n"
                + "- построить маршрут из точек: [action:build_route;широта1,долгота1;широта2,долгота2;...]\n"
                + "Команды вставляй в ответ ТОЛЬКО если пользователь явно просит выполнить действие. "
                + "ОЧЕНЬ ВАЖНО: Когда пользователь спрашивает 'где поесть', 'какие спортзалы рядом' и т.п. без указания города, "
                + "ты ОБЯЗАН рекомендовать конкретные заведения, которые СУЩЕСТВУЮТ поблизости (в радиусе 5 км). "
                + "После каждого названия обязательно ставь [place:Название]. "
                + "Если ты предлагаешь место, которое уже есть в сохранённых данных пользователя, используй [coord:широта,долгота]. "
                + "Пример: 'Рядом с вами есть ресторан «Кавказская пленница» [place:Кавказская пленница] и кафе «Уют» [coord:40.1792,44.5134].'\n"
                + "Будь дружелюбным, кратким и полезным.\n\n";

        if (cachedWeather != null) prompt += "Погода: " + cachedWeather + "\n";
        if (cachedContext != null && !cachedContext.isEmpty())
            prompt += "Данные пользователя:\n" + cachedContext + "\n";
        if (remainingRequests >= 0)
            prompt += String.format("Лимиты: запросов %d, сброс %s, токенов %d.\n",
                    remainingRequests, resetTime != null ? resetTime : "неизвестно", remainingTokens);
        return prompt;
    }

    private String processActions(String response) {
        if (response == null) return "";
        try {
            Pattern actionPattern = Pattern.compile("\\[action:([^\\]]+)\\]");
            Matcher matcher = actionPattern.matcher(response);
            while (matcher.find()) {
                String command = matcher.group(1);
                mainHandler.post(() -> executeAction(command));
            }
            response = response.replaceAll("\\[action:[^\\]]+\\]", "").trim();
        } catch (Exception e) {
            Log.e("AiAction", "processActions error", e);
        }
        return response;
    }

    private void executeAction(String command) {
        MainActivity activity = (MainActivity) getActivity();
        if (activity == null) return;
        String[] parts = command.split(";");
        if (parts.length == 0) return;
        String actionType = parts[0].trim();
        try {
            switch (actionType) {
                case "create_trip":
                    if (parts.length > 1) activity.createTrip(parts[1].trim());
                    break;
                case "rename_trip":
                    if (parts.length > 2) activity.renameTrip(parts[1].trim(), parts[2].trim());
                    break;
                case "delete_trip":
                    if (parts.length > 1) activity.deleteTrip(parts[1].trim());
                    break;
                case "add_point":
                    if (parts.length > 3) activity.addPoint(Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), parts[3].trim());
                    break;
                case "build_route":
                    if (parts.length > 1) {
                        String[] coords = parts[1].split(",");
                        if (coords.length % 2 == 0) {
                            List<LatLng> pts = new ArrayList<>();
                            for (int i = 0; i < coords.length; i += 2)
                                pts.add(new LatLng(Double.parseDouble(coords[i]), Double.parseDouble(coords[i + 1])));
                            if (pts.size() >= 2) activity.buildRoute(pts);
                        }
                    }
                    break;
            }
        } catch (Exception e) {
            Log.e("AiAction", "executeAction error", e);
        }
    }

    // ==================== Groq ====================
    private void sendGroqRequest(String systemPrompt, String userText, OnAIResponseListener listener) {
        executor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("model", GROQ_MODEL);
                JSONArray messages = new JSONArray();
                messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
                messages.put(new JSONObject().put("role", "user").put("content", userText));
                payload.put("messages", messages);

                RequestBody body = RequestBody.create(payload.toString(), JSON);
                Request request = new Request.Builder()
                        .url(GROQ_API_URL).post(body)
                        .addHeader("Authorization", "Bearer " + GROQ_API_KEY)
                        .addHeader("Content-Type", "application/json").build();

                Response response = httpClient.newCall(request).execute();
                String respBody = response.body().string();
                if (response.isSuccessful()) {
                    JSONObject obj = new JSONObject(respBody);
                    String text = obj.getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content");
                    String processed = processActions(text);
                    resolvePlaceCoordinates(processed, resolved ->
                            mainHandler.post(() -> listener.onResponse(resolved)));
                } else {
                    mainHandler.post(() -> listener.onResponse("ERROR: Groq " + respBody));
                }
            } catch (Exception e) {
                mainHandler.post(() -> listener.onResponse("ERROR: " + e.getMessage()));
            }
        });
    }

    // ==================== ГЕОПОИСК ====================
    private void resolvePlaceCoordinates(String response, OnAIResponseListener listener) {
        Pattern placePattern = Pattern.compile("\\[place:([^\\]]+)\\]");
        Matcher matcher = placePattern.matcher(response);
        List<String> placeNames = new ArrayList<>();
        while (matcher.find()) placeNames.add(matcher.group(1).trim());

        if (placeNames.isEmpty() && isUserLookingForPlace()) {
            String keyword = extractCategoryFromQuery();
            if (!keyword.isEmpty()) {
                response += " [place:" + keyword + "]";
                placeNames.add(keyword);
            }
        }

        Set<String> unique = new HashSet<>(placeNames);
        String finalResponse = response;
        List<String> finalNames = new ArrayList<>(unique);

        executor.execute(() -> {
            double userLat = 0, userLng = 0;
            boolean hasLocation = false;
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                Location loc = activity.getCurrentLocation();
                if (loc != null) {
                    userLat = loc.getLatitude();
                    userLng = loc.getLongitude();
                    hasLocation = true;
                }
            }

            if (hasLocation && cachedCountryCode == null) {
                cachedCountryCode = fetchCountryCode(userLat, userLng);
            }

            StringBuilder result = new StringBuilder(finalResponse);
            for (String placeName : finalNames) {
                String original = "[place:" + placeName + "]";
                String replacement = null;

                boolean hasExplicitLocation = placeName.contains(",");

                if (hasLocation && !hasExplicitLocation) {
                    replacement = findBestNearby(placeName, userLat, userLng, cachedCountryCode);
                } else if (hasLocation && hasExplicitLocation) {
                    replacement = locationIQGlobalSearch(placeName);
                }

                int idx = result.indexOf(original);
                if (idx != -1) {
                    if (replacement != null) {
                        result.replace(idx, idx + original.length(), replacement);
                    } else {
                        result.replace(idx, idx + original.length(), "");
                    }
                }
            }
            mainHandler.post(() -> listener.onResponse(result.toString()));
        });
    }

    private String findBestNearby(String placeName, double userLat, double userLng, String countryCode) {
        boolean isCategory = isCategoryWord(placeName);

        String coord = locationIQRequest(placeName, userLat, userLng, countryCode);
        if (coord != null) return coord;

        coord = locationIQRequest(placeName, userLat, userLng, null);
        if (coord != null) return coord;

        String en = translateToEnglish(placeName);
        if (!en.equals(placeName)) {
            coord = locationIQRequest(en, userLat, userLng, countryCode);
            if (coord != null) return coord;
            coord = locationIQRequest(en, userLat, userLng, null);
            if (coord != null) return coord;
        }

        if (isCategory) {
            String[] tags = CATEGORY_TO_OSM_TAGS.get(placeName.toLowerCase());
            if (tags != null) {
                for (String tag : tags) {
                    coord = overpassSearch(tag, userLat, userLng);
                    if (coord != null) return coord;
                }
            }
            if (!en.equals(placeName)) {
                String[] enTags = CATEGORY_TO_OSM_TAGS.get(en.toLowerCase());
                if (enTags != null) {
                    for (String tag : enTags) {
                        coord = overpassSearch(tag, userLat, userLng);
                        if (coord != null) return coord;
                    }
                }
            }
        }
        return null;
    }

    private boolean isCategoryWord(String word) {
        for (String cat : PLACE_CATEGORIES) {
            if (cat.equalsIgnoreCase(word)) return true;
        }
        return false;
    }

    private String fetchCountryCode(double lat, double lng) {
        try {
            String url = LOCATIONIQ_REVERSE_URL + "?key=" + LOCATIONIQ_API_KEY +
                    "&lat=" + lat + "&lon=" + lng + "&format=json&accept-language=ru";
            Request request = new Request.Builder().url(url).header("User-Agent", "OzTrip/1.0").build();
            Response response = httpClient.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                JSONObject json = new JSONObject(response.body().string());
                JSONObject address = json.optJSONObject("address");
                if (address != null) {
                    String code = address.optString("country_code", "").toLowerCase();
                    if (!code.isEmpty()) return code;
                }
            }
        } catch (Exception e) {
            Log.e("AiFragment", "Ошибка определения страны", e);
        }
        return null;
    }

    private boolean isUserLookingForPlace() {
        if (TextUtils.isEmpty(lastUserQuery)) return false;
        String lower = lastUserQuery.toLowerCase();
        for (String t : PLACE_TRIGGERS) if (lower.contains(t)) return true;
        return false;
    }

    private String extractCategoryFromQuery() {
        String lower = lastUserQuery.toLowerCase();
        for (String cat : PLACE_CATEGORIES) if (lower.contains(cat)) return cat;
        return "";
    }

    private String locationIQRequest(String query, double lat, double lng, String countryCode) {
        try {
            String encoded = java.net.URLEncoder.encode(query, "UTF-8");
            String url = LOCATIONIQ_SEARCH_URL + "?key=" + LOCATIONIQ_API_KEY +
                    "&q=" + encoded +
                    "&limit=5&format=json&accept-language=ru";

            if (!TextUtils.isEmpty(countryCode)) {
                url += "&countrycodes=" + countryCode;
            }

            Request request = new Request.Builder().url(url).header("User-Agent", "OzTrip/1.0").build();
            Response response = httpClient.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                String body = response.body().string();
                JSONArray json = new JSONArray(body);
                if (json.length() == 0) return null;

                double bestLat = 0, bestLng = 0;
                double bestDist = Double.MAX_VALUE;
                for (int i = 0; i < json.length(); i++) {
                    JSONObject obj = json.getJSONObject(i);
                    double placeLat = obj.getDouble("lat");
                    double placeLng = obj.getDouble("lon");
                    double dist = distance(lat, lng, placeLat, placeLng);
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestLat = placeLat;
                        bestLng = placeLng;
                    }
                }

                if (bestDist <= 50_000) {
                    return String.format(Locale.US, "[coord:%.5f,%.5f]", bestLat, bestLng);
                }
            }
        } catch (Exception e) {
            Log.e("LocationIQ", "Search error", e);
        }
        return null;
    }

    private String locationIQGlobalSearch(String placeName) {
        try {
            String encoded = java.net.URLEncoder.encode(placeName, "UTF-8");
            String url = LOCATIONIQ_SEARCH_URL + "?key=" + LOCATIONIQ_API_KEY +
                    "&q=" + encoded + "&limit=1&format=json&accept-language=ru";
            Request request = new Request.Builder().url(url).header("User-Agent", "OzTrip/1.0").build();
            Response response = httpClient.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                JSONArray json = new JSONArray(response.body().string());
                if (json.length() > 0) {
                    JSONObject obj = json.getJSONObject(0);
                    double lat = obj.getDouble("lat");
                    double lng = obj.getDouble("lon");
                    return String.format(Locale.US, "[coord:%.5f,%.5f]", lat, lng);
                }
            }
        } catch (Exception e) {
            Log.e("LocationIQ", "Global search error", e);
        }
        return null;
    }

    private String overpassSearch(String osmTag, double userLat, double userLng) {
        try {
            double delta = 0.45;
            double south = userLat - delta;
            double north = userLat + delta;
            double west = userLng - delta;
            double east = userLng + delta;

            String query = String.format(Locale.US,
                    "[out:json];node[%s](%.5f,%.5f,%.5f,%.5f);out center 5;",
                    osmTag, south, west, north, east);

            Request request = new Request.Builder()
                    .url(OVERPASS_URL)
                    .post(RequestBody.create(query, okhttp3.MediaType.get("text/plain")))
                    .build();

            Response response = httpClient.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                String body = response.body().string();
                JSONObject json = new JSONObject(body);
                JSONArray elements = json.getJSONArray("elements");
                if (elements.length() == 0) return null;

                double bestLat = 0, bestLng = 0;
                double bestDist = Double.MAX_VALUE;
                for (int i = 0; i < elements.length(); i++) {
                    JSONObject el = elements.getJSONObject(i);
                    double lat = el.optDouble("lat", el.getJSONObject("center").optDouble("lat"));
                    double lng = el.optDouble("lon", el.getJSONObject("center").optDouble("lon"));
                    double dist = distance(userLat, userLng, lat, lng);
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestLat = lat;
                        bestLng = lng;
                    }
                }
                return String.format(Locale.US, "[coord:%.5f,%.5f]", bestLat, bestLng);
            }
        } catch (Exception e) {
            Log.e("Overpass", "Search error", e);
        }
        return null;
    }

    private String translateToEnglish(String russianWord) {
        Map<String, String> map = new HashMap<>();
        map.put("спортзал", "gym");
        map.put("аптека", "pharmacy");
        map.put("кафе", "cafe");
        map.put("ресторан", "restaurant");
        map.put("магазин", "shop");
        return map.getOrDefault(russianWord.toLowerCase(), russianWord);
    }

    private double distance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2)*Math.sin(dLat/2) + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.sin(dLon/2)*Math.sin(dLon/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a)) * 1000;
    }

    // ==================== OpenRouter (запасной) ====================
    private void sendOpenRouterRequest(String systemPrompt, String userText, OnAIResponseListener listener) {
        executor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("model", OR_MODEL);
                JSONArray messages = new JSONArray();
                messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
                messages.put(new JSONObject().put("role", "user").put("content", userText));
                payload.put("messages", messages);

                RequestBody body = RequestBody.create(payload.toString(), JSON);
                Request request = new Request.Builder().url(OR_API_URL).post(body)
                        .addHeader("Authorization", "Bearer " + OR_API_KEY)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("HTTP-Referer", "https://oztrip.app")
                        .addHeader("X-Title", "OzTrip").build();

                Response resp = httpClient.newCall(request).execute();
                if (resp.isSuccessful()) {
                    String text = new JSONObject(resp.body().string()).getJSONArray("choices")
                            .getJSONObject(0).getJSONObject("message").getString("content");
                    String processed = processActions(text);
                    resolvePlaceCoordinates(processed, r -> mainHandler.post(() -> listener.onResponse(r)));
                } else mainHandler.post(() -> listener.onResponse("ERROR: OR"));
            } catch (Exception e) {
                mainHandler.post(() -> listener.onResponse("ERROR: " + e.getMessage()));
            }
        });
    }

    // ==================== Контекст и погода ====================
    private String buildFullContext() {
        MainActivity a = (MainActivity) getActivity();
        if (a == null) return "";
        StringBuilder ctx = new StringBuilder();
        Location loc = a.getCurrentLocation();
        if (loc != null) ctx.append(String.format(Locale.US, "Текущее местоположение: %.5f, %.5f\n", loc.getLatitude(), loc.getLongitude()));
        List<TravelList> lists = a.getAllTravelLists();
        if (lists != null) {
            for (TravelList l : lists) {
                ctx.append("Поездка: ").append(l.name).append(" (точек: ").append(l.locations.size()).append(")\n");
                for (SavedLocation sl : l.locations) {
                    ctx.append(" - ").append(sl.customName.isEmpty() ? "без названия" : sl.customName)
                            .append(" [").append(sl.latLng.getLatitude()).append(",").append(sl.latLng.getLongitude()).append("]");
                    if (!sl.note.isEmpty()) ctx.append(" заметка: ").append(sl.note);
                    ctx.append("\n");
                }
            }
        }
        return ctx.toString();
    }

    private void fetchWeatherIfNeeded(String msg, Runnable onReady) {
        if (msg.toLowerCase().contains("погод")) {
            MainActivity a = (MainActivity) getActivity();
            if (a == null) { onReady.run(); return; }
            Location loc = a.getCurrentLocation();
            if (loc == null) { onReady.run(); return; }
            executor.execute(() -> {
                try {
                    Request req = new Request.Builder().url("https://api.open-meteo.com/v1/forecast?latitude="+loc.getLatitude()+"&longitude="+loc.getLongitude()+"&current_weather=true").build();
                    Response res = httpClient.newCall(req).execute();
                    if (res.isSuccessful()) {
                        JSONObject cur = new JSONObject(res.body().string()).getJSONObject("current_weather");
                        cachedWeather = String.format("%.0f°C, ветер %.1f км/ч", cur.getDouble("temperature"), cur.getDouble("windspeed"));
                    }
                } catch (Exception e) {}
                mainHandler.post(onReady);
            });
        } else { onReady.run(); }
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        executor.shutdown();
    }

    interface OnAIResponseListener { void onResponse(String text); }
}