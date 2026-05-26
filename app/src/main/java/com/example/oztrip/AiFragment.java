package com.example.oztrip;

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
import android.widget.Toast;

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
import java.util.List;
import java.util.Locale;
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

    // OpenRouter (запасной)
    private static final String OR_API_KEY = "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";
    private static final String OR_API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String OR_MODEL = "meta-llama/llama-3.3-70b-instruct:free";

    // Groq (основной)
    private static final String GROQ_API_KEY = "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";   // <-- ВАШ НОВЫЙ КЛЮЧ
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama-3.3-70b-versatile";

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

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
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
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

        chatAdapter.addMessage(new ChatMessage(text, true));
        messageInput.setText("");

        ChatMessage typing = new ChatMessage("...", false);
        chatAdapter.addMessage(typing);

        fetchWeatherIfNeeded(text, () -> {
            cachedContext = buildFullContext();
            String systemPrompt = buildSystemPrompt();

            // 1. Сначала Groq
            sendGroqRequest(systemPrompt, text, groqResponse -> {
                if (!groqResponse.startsWith("ERROR:")) {
                    displayResponse(groqResponse, typing);
                } else {
                    // 2. Groq недоступен → OpenRouter
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
        String prompt = "Ты — OzTrip AI, персональный гид пользователя по Армении. "
                + "Ты видишь все его поездки, сохранённые точки, заметки, рейтинги и местоположение. "
                + "Ты можешь предлагать любые места, даже если их нет в сохранённых данных. "
                + "Ты можешь управлять приложением через команды:\n"
                + "- создать поездку: [action:create_trip;Название]\n"
                + "- переименовать поездку: [action:rename_trip;старое_название;новое_название]\n"
                + "- удалить поездку: [action:delete_trip;Название]\n"
                + "- добавить точку в активную поездку: [action:add_point;широта;долгота;название]\n"
                + "- построить маршрут из точек: [action:build_route;широта1,долгота1;широта2,долгота2;...]\n"
                + "Команды вставляй в ответ ТОЛЬКО если пользователь явно просит выполнить действие (например, «создай поездку»). "
                + "Не добавляй команды в ответ без прямой просьбы пользователя. "
                + "Если команда не нужна, просто отвечай текстом и координатами. "
                + "Для новых мест, которые ты предлагаешь, НЕ используй координаты. Вместо этого пиши [place:Название] (без координат). "
                + "Для мест, которые уже есть в данных пользователя (из контекста), давай точные координаты как [coord:широта,долгота]. "
                + "Пример: 'Рекомендую посетить озеро Севан [place:Озеро Севан].' "
                + "Когда пользователь просит добавить точку, используй [action:add_point;широта;долгота;название]."
                + "Будь дружелюбным, кратким и полезным. Отвечай на языке вопроса.\n\n";

        if (cachedWeather != null) prompt += "Погода: " + cachedWeather + "\n";
        if (cachedContext != null && !cachedContext.isEmpty())
            prompt += "Данные пользователя:\n" + cachedContext + "\n";
        if (remainingRequests >= 0)
            prompt += String.format("Лимиты: запросов %d, сброс %s, токенов %d.\n",
                    remainingRequests, resetTime != null ? resetTime : "неизвестно", remainingTokens);
        return prompt;
    }

    // Безопасная обработка команд (ошибки не ломают ответ)
    private String processActions(String response) {
        if (response == null) return "";
        try {
            Pattern actionPattern = Pattern.compile("\\[action:([^\\]]+)\\]");
            Matcher matcher = actionPattern.matcher(response);
            boolean anyAction = false;
            while (matcher.find()) {
                String command = matcher.group(1);
                anyAction = true;
                // Выполняем команду в главном потоке
                mainHandler.post(() -> {
                    try {
                        executeAction(command);
                    } catch (Exception e) {
                        Log.e("AiAction", "Ошибка UI-команды: " + command, e);
                    }
                });
            }
            if (anyAction) {
                response = response.replaceAll("\\[action:[^\\]]+\\]", "").trim();
            }
        } catch (Exception e) {
            Log.e("AiAction", "Ошибка в processActions", e);
        }
        return response;
    }

    private void executeAction(String command) {
        MainActivity activity = (MainActivity) getActivity();
        if (activity == null) return;
        String[] parts = command.split(";");
        if (parts.length == 0) return;
        String actionType = parts[0].trim();

        // Переключаемся на карту, только если действие выполнено успешно
        boolean success = false;

        try {
            switch (actionType) {
                case "create_trip":
                    if (parts.length > 1) { activity.createTrip(parts[1].trim()); success = true; }
                    break;
                case "rename_trip":
                    if (parts.length > 2) { activity.renameTrip(parts[1].trim(), parts[2].trim()); success = true; }
                    break;
                case "delete_trip":
                    if (parts.length > 1) { activity.deleteTrip(parts[1].trim()); success = true; }
                    break;
                case "add_point":
                    if (parts.length > 3) {
                        double lat = Double.parseDouble(parts[1].trim());
                        double lng = Double.parseDouble(parts[2].trim());
                        activity.addPoint(lat, lng, parts[3].trim());
                        success = true;
                    }
                    break;
                case "build_route":
                    if (parts.length > 1) {
                        String[] coords = parts[1].split(",");
                        if (coords.length % 2 == 0) {
                            List<LatLng> points = new ArrayList<>();
                            for (int i = 0; i < coords.length; i += 2) {
                                double lat = Double.parseDouble(coords[i].trim());
                                double lng = Double.parseDouble(coords[i + 1].trim());
                                points.add(new LatLng(lat, lng));
                            }
                            if (points.size() >= 2) { activity.buildRoute(points); success = true; }
                        }
                    }
                    break;
            }
        } catch (Exception e) {
            Log.e("AiAction", "Ошибка выполнения команды: " + command, e);
        }

        if (success) {
            // После успешного действия переключаемся на карту
            if (activity.getLiquidNav() != null) {
                activity.getLiquidNav().setSelectedIndex(0);
            }
            activity.invalidateAiContext();
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
                        .url(GROQ_API_URL)
                        .post(body)
                        .addHeader("Authorization", "Bearer " + GROQ_API_KEY)
                        .addHeader("Content-Type", "application/json")
                        .build();

                Response response = httpClient.newCall(request).execute();
                String respBody = response.body().string();

                if (response.isSuccessful()) {
                    JSONObject obj = new JSONObject(respBody);
                    String text = obj.getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content");
                    String processed = processActions(text);
                    resolvePlaceCoordinates(processed, resolvedText -> {
                        mainHandler.post(() -> listener.onResponse(resolvedText));
                    });
                } else {
                    mainHandler.post(() -> listener.onResponse("ERROR: Groq " + respBody));
                }
            } catch (Exception e) {
                mainHandler.post(() -> listener.onResponse("ERROR: " + e.getMessage()));
            }
        });
    }
    private void resolvePlaceCoordinates(String response, OnAIResponseListener listener) {
        Pattern placePattern = Pattern.compile("\\[place:([^\\]]+)\\]");
        Matcher matcher = placePattern.matcher(response);
        List<String> placeNames = new ArrayList<>();
        while (matcher.find()) {
            placeNames.add(matcher.group(1).trim());
        }

        if (placeNames.isEmpty()) {
            listener.onResponse(response);
            return;
        }

        executor.execute(() -> {
            StringBuilder result = new StringBuilder(response);
            for (String placeName : placeNames) {
                String original = "[place:" + placeName + "]";
                try {
                    String encoded = java.net.URLEncoder.encode(placeName, "UTF-8");
                    String url = "https://nominatim.openstreetmap.org/search?q=" + encoded +
                            "&format=json&limit=1&accept-language=ru,en";
                    Request request = new Request.Builder()
                            .url(url)
                            .header("User-Agent", "OzTrip/1.0")
                            .build();
                    Response httpResponse = httpClient.newCall(request).execute();
                    if (httpResponse.isSuccessful() && httpResponse.body() != null) {
                        String body = httpResponse.body().string();
                        JSONArray json = new JSONArray(body);
                        if (json.length() > 0) {
                            JSONObject place = json.getJSONObject(0);
                            double lat = place.getDouble("lat");
                            double lng = place.getDouble("lon");
                            String coordStr = String.format(Locale.US, "[coord:%.5f,%.5f]%s", lat, lng, placeName);
                            int idx = result.indexOf(original);
                            if (idx != -1) {
                                result.replace(idx, idx + original.length(), coordStr);
                            }
                            continue; // успешно заменили, переходим к следующему месту
                        }
                    }
                    // Если геокодинг не дал результата – убираем [place:...] и оставляем просто название
                    int idx = result.indexOf(original);
                    if (idx != -1) {
                        result.replace(idx, idx + original.length(), placeName);
                    }
                } catch (Exception e) {
                    Log.e("AiFragment", "Geocoding error for " + placeName, e);
                    // Аналогично убираем метку
                    int idx = result.indexOf(original);
                    if (idx != -1) {
                        result.replace(idx, idx + original.length(), placeName);
                    }
                }
            }
            mainHandler.post(() -> listener.onResponse(result.toString()));
        });
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
                Request request = new Request.Builder()
                        .url(OR_API_URL)
                        .post(body)
                        .addHeader("Authorization", "Bearer " + OR_API_KEY)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("HTTP-Referer", "https://oztrip.app")
                        .addHeader("X-Title", "OzTrip")
                        .build();

                Response response = httpClient.newCall(request).execute();
                String respBody = response.body().string();

                if (response.isSuccessful()) {
                    JSONObject obj = new JSONObject(respBody);
                    String text = obj.getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content");
                    String processed = processActions(text);
                    resolvePlaceCoordinates(processed, resolvedText -> {
                        mainHandler.post(() -> listener.onResponse(resolvedText));
                    });
                } else {
                    mainHandler.post(() -> listener.onResponse("ERROR: OR " + respBody));
                }
            } catch (Exception e) {
                mainHandler.post(() -> listener.onResponse("ERROR: " + e.getMessage()));
            }
        });
    }

    // ==================== Контекст (все поездки) ====================
    private String buildFullContext() {
        MainActivity activity = (MainActivity) getActivity();
        if (activity == null) return getString(R.string.text_auto_25);

        StringBuilder ctx = new StringBuilder();
        SharedPreferences prefs = getActivity().getSharedPreferences("OzTripPrefs", Context.MODE_PRIVATE);
        String aboutMe = prefs.getString("about_me", "");
        if (!aboutMe.isEmpty()) {
            ctx.append("\nО пользователе: ").append(aboutMe).append("\n");
        }

        boolean isLoggedIn = FirebaseAuth.getInstance().getCurrentUser() != null;
        ctx.append("Пользователь ").append(isLoggedIn ? "авторизован" : "в гостевом режиме").append(".\n");

        Location loc = activity.getCurrentLocation();
        if (loc != null) {
            ctx.append(String.format(Locale.US, getString(R.string.text_auto_26), loc.getLatitude(), loc.getLongitude()));
        } else {
            ctx.append(getString(R.string.text_auto_27));
        }

        List<TravelList> allLists = activity.getAllTravelLists();
        if (allLists != null && !allLists.isEmpty()) {
            ctx.append("\n=== ВСЕ ПОЕЗДКИ ===\n");
            TravelList active = activity.getCurrentActiveList();

            for (int t = 0; t < allLists.size(); t++) {
                TravelList list = allLists.get(t);
                ctx.append(t + 1).append(". ").append(list.name);
                ctx.append(" (локаций: ").append(list.locations != null ? list.locations.size() : 0);
                ctx.append(", точек пути: ").append(list.pathPoints != null ? list.pathPoints.size() : 0).append(")");

                if (list == active) ctx.append(" ← АКТИВНАЯ\n");
                else ctx.append("\n");

                List<SavedLocation> locations = list.locations;
                if (locations != null && !locations.isEmpty()) {
                    int maxPoints = (list == active) ? locations.size() : Math.min(locations.size(), 3);
                    ctx.append("   Точки");
                    if (maxPoints < locations.size()) ctx.append(" (первые ").append(maxPoints).append(" из ").append(locations.size()).append(")");
                    ctx.append(":\n");

                    for (int i = 0; i < maxPoints; i++) {
                        SavedLocation sl = locations.get(i);
                        ctx.append("     ").append(i + 1).append(". ");
                        ctx.append(sl.customName.isEmpty() ? getString(R.string.text_auto_33) : sl.customName);
                        ctx.append(" [уровень ").append(sl.level).append("]");
                        ctx.append(" координаты: [coord:").append(String.format(Locale.US, "%.5f,%.5f]", sl.latLng.getLatitude(), sl.latLng.getLongitude()));
                        if (!sl.note.isEmpty()) ctx.append(", заметка: \"").append(sl.note).append("\"");
                        if (sl.date != null && !sl.date.isEmpty()) ctx.append(", дата: ").append(sl.date);
                        ctx.append(", рейтинг: ").append(sl.rating);
                        ctx.append(" фото: ").append(sl.photoPaths != null ? sl.photoPaths.size() : 0);
                        ctx.append("\n");
                    }
                }

                List<LatLng> path = list.pathPoints;
                if (path != null && path.size() > 1) {
                    ctx.append("   Маршрут: ").append(path.size()).append(" точек, ");
                    LatLng first = path.get(0);
                    LatLng last = path.get(path.size() - 1);
                    ctx.append(String.format(Locale.US, "от %.5f,%.5f до %.5f,%.5f\n", first.getLatitude(), first.getLongitude(), last.getLatitude(), last.getLongitude()));
                }
            }
        }

        return ctx.toString();
    }

    private void fetchWeatherIfNeeded(String userMessage, Runnable onReady) {
        String lower = userMessage.toLowerCase();
        if (lower.contains("погод") || lower.contains("температ") || lower.contains("weather")) {
            MainActivity activity = (MainActivity) getActivity();
            if (activity == null) { onReady.run(); return; }
            Location loc = activity.getCurrentLocation();
            if (loc == null) {
                cachedWeather = "Местоположение неизвестно, погода недоступна.";
                onReady.run();
                return;
            }
            String url = "https://api.open-meteo.com/v1/forecast?latitude=" + loc.getLatitude() + "&longitude=" + loc.getLongitude() + "&current_weather=true";
            executor.execute(() -> {
                try {
                    Request request = new Request.Builder().url(url).build();
                    Response response = httpClient.newCall(request).execute();
                    if (response.isSuccessful()) {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONObject current = json.getJSONObject("current_weather");
                        double temp = current.getDouble("temperature");
                        double wind = current.getDouble("windspeed");
                        int code = current.getInt("weathercode");
                        String desc = getWeatherDescription(code);
                        cachedWeather = String.format(Locale.US, "%.0f°C, ветер %.1f км/ч, %s.", temp, wind, desc);
                    } else {
                        cachedWeather = "Не удалось получить погоду.";
                    }
                } catch (Exception e) {
                    cachedWeather = "Ошибка получения погоды.";
                }
                mainHandler.post(onReady);
            });
        } else {
            cachedWeather = null;
            onReady.run();
        }
    }

    private String getWeatherDescription(int code) {
        if (code <= 1) return "ясно";
        if (code <= 3) return "облачно";
        if (code <= 48) return "туман";
        if (code <= 57) return "морось";
        if (code <= 67) return "дождь";
        if (code <= 77) return "снег";
        if (code <= 82) return "ливень";
        if (code <= 86) return "снегопад";
        return "гроза";
    }

    interface OnAIResponseListener {
        void onResponse(String text);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executor.shutdown();
    }
}