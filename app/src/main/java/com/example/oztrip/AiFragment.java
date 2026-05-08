package com.example.oztrip;

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

import org.json.JSONArray;
import org.json.JSONObject;
import org.maplibre.android.geometry.LatLng;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
    // Лимиты OpenRouter (обновляются после каждого запроса)
    private int remainingRequests = -1;
    private String resetTime = null;
    private int remainingTokens = -1;

    private static final String API_KEY = "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";
    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String MODEL = "openrouter/free";
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

        // Приветствие
        chatAdapter.addMessage(new ChatMessage(getString(R.string.text_auto_1), false));

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

    /** Определяет, запрашивает ли пользователь свои персональные данные */
    private boolean isDataRequest(String message) {
        String lower = message.toLowerCase();
        return lower.contains(getString(R.string.text_auto_2)) || lower.contains(getString(R.string.text_auto_3)) || lower.contains(getString(R.string.text_auto_4))
                || lower.contains(getString(R.string.text_auto_5)) || lower.contains(getString(R.string.text_auto_6)) || lower.contains(getString(R.string.text_auto_7))
                || lower.contains(getString(R.string.text_auto_8)) || lower.contains(getString(R.string.text_auto_9));
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

        // Проверяем, нужна ли погода, и если да — получим её перед отправкой
        fetchWeatherIfNeeded(text, () -> {
            // Контекст с личными данными (только по запросу)
            if (isDataRequest(text) && (cachedContext == null || cachedContext.isEmpty())) {
                cachedContext = buildFullContext();
            } else if (!isDataRequest(text)) {
                cachedContext = "";
            }

            // Системный промпт (как выше)
            String systemPrompt =
                    getString(R.string.text_auto_10) +
                            getString(R.string.text_auto_11) +
                            getString(R.string.text_auto_12) +
                            getString(R.string.text_auto_13) +
                            getString(R.string.text_auto_14) +
                            getString(R.string.text_auto_15);

            if (!cachedContext.isEmpty()) {
                systemPrompt += getString(R.string.text_auto_19) + cachedContext + "\n";
            }

            // Добавляем погоду, если она была получена
            if (cachedWeather != null) {
                systemPrompt += getString(R.string.text_auto_20) + cachedWeather + "\n";
            }

            // Информация о лимитах
            if (remainingRequests >= 0) {
                systemPrompt += String.format(getString(R.string.text_auto_21),
                        remainingRequests, resetTime != null ? resetTime : getString(R.string.text_auto_22), remainingTokens);
            } else {
                systemPrompt += getString(R.string.text_auto_23);
            }

            String fullUserMessage = systemPrompt + getString(R.string.text_auto_24) + text;
            fetchAIResponse(fullUserMessage);
        });
    }

    /**
     * Собирает всю информацию из MainActivity в текстовый контекст.
     */
    private String buildFullContext() {
        MainActivity activity = (MainActivity) getActivity();
        if (activity == null) return getString(R.string.text_auto_25);

        StringBuilder ctx = new StringBuilder();

        // 1. Текущее местоположение
        Location loc = activity.getCurrentLocation();
        if (loc != null) {
            ctx.append(String.format(Locale.US,
                    getString(R.string.text_auto_26),
                    loc.getLatitude(), loc.getLongitude()));
        } else {
            ctx.append(getString(R.string.text_auto_27));
        }

        // 2. Список поездок (веток)
        List<TravelList> allLists = activity.getAllTravelLists();
        if (allLists != null && !allLists.isEmpty()) {
            ctx.append(getString(R.string.text_auto_28));
            for (int i = 0; i < allLists.size(); i++) {
                TravelList list = allLists.get(i);
                ctx.append(i + 1).append(". ").append(list.name);
                ctx.append(getString(R.string.text_auto_29)).append(list.locations != null ? list.locations.size() : 0);
                ctx.append(getString(R.string.text_auto_30)).append(list.pathPoints != null ? list.pathPoints.size() : 0).append(")\n");
            }
        }

        // 3. Активная поездка и её точки
        TravelList active = activity.getCurrentActiveList();
        if (active != null) {
            ctx.append(getString(R.string.text_auto_31)).append(active.name).append(" ===\n");
            // Точки
            List<SavedLocation> locations = active.locations;
            if (locations != null && !locations.isEmpty()) {
                ctx.append(getString(R.string.text_auto_32));
                for (int i = 0; i < locations.size(); i++) {
                    SavedLocation sl = locations.get(i);
                    ctx.append("  ").append(i + 1).append(". ");
                    ctx.append(sl.customName.isEmpty() ? getString(R.string.text_auto_33) : sl.customName);
                    ctx.append(getString(R.string.text_auto_34)).append(sl.level).append("]");
                    ctx.append(getString(R.string.text_auto_35)).append(String.format(Locale.US, "%.5f, %.5f", sl.latLng.getLatitude(), sl.latLng.getLongitude()));
                    if (!sl.note.isEmpty()) ctx.append(getString(R.string.text_auto_36)).append(sl.note).append("\"");
                    if (sl.date != null && !sl.date.isEmpty()) ctx.append(getString(R.string.text_auto_37)).append(sl.date);
                    ctx.append(getString(R.string.text_auto_38)).append(sl.rating);
                    ctx.append(getString(R.string.text_auto_39)).append(sl.photoPaths != null ? sl.photoPaths.size() : 0);
                    ctx.append("\n");
                }
            }
            // Маршрут
            List<LatLng> path = active.pathPoints;
            if (path != null && path.size() > 1) {
                ctx.append(getString(R.string.text_auto_40)).append(path.size()).append("): ");
                // Первую и последнюю точку для примера
                LatLng first = path.get(0);
                LatLng last = path.get(path.size() - 1);
                ctx.append(String.format(Locale.US, getString(R.string.text_auto_41),
                        first.getLatitude(), first.getLongitude(),
                        last.getLatitude(), last.getLongitude()));
            }
        }

        return ctx.toString();
    }
    private void fetchWeatherIfNeeded(String userMessage, Runnable onReady) {
        // Простая проверка: если пользователь упоминает погоду
        String lower = userMessage.toLowerCase();
        if (lower.contains(getString(R.string.text_auto_42)) || lower.contains(getString(R.string.text_auto_43)) || lower.contains(getString(R.string.text_auto_44))
                || lower.contains(getString(R.string.text_auto_45)) || lower.contains(getString(R.string.text_auto_46)) || lower.contains(getString(R.string.text_auto_47))) {

            MainActivity activity = (MainActivity) getActivity();
            if (activity == null) {
                onReady.run();
                return;
            }
            Location loc = activity.getCurrentLocation();
            if (loc == null) {
                cachedWeather = getString(R.string.text_auto_48);
                onReady.run();
                return;
            }
            // Делаем запрос к Open-Meteo
            String url = "https://api.open-meteo.com/v1/forecast?latitude=" + loc.getLatitude() +
                    "&longitude=" + loc.getLongitude() + "&current_weather=true";

            executor.execute(() -> {
                try {
                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(10, TimeUnit.SECONDS)
                            .build();
                    Request request = new Request.Builder().url(url).build();
                    Response response = client.newCall(request).execute();
                    if (response.isSuccessful()) {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONObject current = json.getJSONObject("current_weather");
                        double temp = current.getDouble("temperature");
                        double wind = current.getDouble("windspeed");
                        int weatherCode = current.getInt("weathercode");
                        String desc = getWeatherDescription(weatherCode);
                        cachedWeather = String.format(Locale.US,
                                getString(R.string.text_auto_49), temp, wind, desc);
                    } else {
                        cachedWeather = getString(R.string.text_auto_50);
                    }
                } catch (Exception e) {
                    cachedWeather = getString(R.string.text_auto_51);
                }
                mainHandler.post(onReady);
            });
        } else {
            // Погода не нужна, убираем кэш
            cachedWeather = null;
            onReady.run();
        }
    }
    private String getWeatherDescription(int code) {
        if (code <= 1) return getString(R.string.text_auto_52);
        if (code <= 3) return getString(R.string.text_auto_53);
        if (code <= 48) return getString(R.string.text_auto_54);
        if (code <= 57) return getString(R.string.text_auto_55);
        if (code <= 67) return getString(R.string.text_auto_56);
        if (code <= 77) return getString(R.string.text_auto_57);
        if (code <= 82) return getString(R.string.text_auto_58);
        if (code <= 86) return getString(R.string.text_auto_59);
        return getString(R.string.text_auto_60);
    }
    private void fetchAIResponse(String fullUserMessage) {
        executor.execute(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build();

                JSONObject payload = new JSONObject();
                payload.put("model", MODEL);

                JSONArray messages = new JSONArray();
                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", fullUserMessage);
                messages.put(userMsg);
                payload.put("messages", messages);

                RequestBody body = RequestBody.create(payload.toString(), JSON);
                Request request = new Request.Builder()
                        .url(API_URL)
                        .post(body)
                        .addHeader("Authorization", "Bearer " + API_KEY)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("HTTP-Referer", "https://oztrip.app")
                        .addHeader("X-Title", "OzTrip")
                        .build();

                Response response = client.newCall(request).execute();
                String responseBody = response.body().string();
                Log.d("OzTrip_AI", "HTTP: " + response.code() + " | Body: " + responseBody);

                // Извлекаем заголовки лимитов
                String remainReq = response.header("x-ratelimit-requests-remaining");
                String reset = response.header("x-ratelimit-requests-reset");
                String remainTok = response.header("x-ratelimit-tokens-remaining");

                if (remainReq != null) {
                    Log.d("OzTrip_Limits", "Requests remaining: " + remainReq + ", reset at: " + reset +
                            ", tokens remaining: " + remainTok);
                } else {
                    Log.d("OzTrip_Limits", "No rate-limit headers in response. Possibly free tier without headers.");
                }

                // Сохраняем лимиты в поля класса
                remainingRequests = remainReq != null ? Integer.parseInt(remainReq) : -1;
                resetTime = reset;
                remainingTokens = remainTok != null ? Integer.parseInt(remainTok) : -1;

                // Обрабатываем ответ
                String aiText;
                if (response.isSuccessful()) {
                    aiText = parseResponse(responseBody);
                } else {
                    try {
                        JSONObject err = new JSONObject(responseBody);
                        aiText = getString(R.string.text_auto_61) + err.getJSONObject("error").optString("message", getString(R.string.text_auto_22));
                    } catch (Exception e) {
                        aiText = getString(R.string.text_auto_62) + response.code();
                    }
                }
                String finalAiText = aiText;
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    chatAdapter.replaceLastMessage(new ChatMessage(finalAiText, false));
                });
            } catch (Exception e) {
                Log.e("OzTrip_AI", "Request error", e);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    chatAdapter.replaceLastMessage(new ChatMessage(getString(R.string.text_auto_63) + e.getMessage(), false));
                });
            }
        });
    }

    private String parseResponse(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            JSONArray choices = obj.getJSONArray("choices");
            return choices.getJSONObject(0).getJSONObject("message").getString("content");
        } catch (Exception e) {
            return getString(R.string.text_auto_64);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executor.shutdown();
    }
}