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
        chatAdapter.addMessage(new ChatMessage("Привет! Я OzTrip AI. Теперь я знаю всё о твоих поездках. Спрашивай!", false));

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
        return lower.contains("мои") || lower.contains("поездки") || lower.contains("точки")
                || lower.contains("маршрут") || lower.contains("данные") || lower.contains("где я")
                || lower.contains("рядом") || lower.contains("сохранённые");
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

        // Контекст с личными данными (только по запросу)
        if (isDataRequest(text) && (cachedContext == null || cachedContext.isEmpty())) {
            cachedContext = buildFullContext();
        } else if (!isDataRequest(text)) {
            cachedContext = "";
        }

        // Системный промпт с правилами и информацией о лимитах
        String systemPrompt =
                "Ты — OzTrip AI, персональный гид по Армении и ассистент путешественника.\n" +
                        "Ты имеешь доступ к личным данным пользователя: его текущему местоположению, списку поездок, " +
                        "сохранённым точкам на карте, заметкам и рейтингам.\n\n" +
                        "ВАЖНЫЕ ПРАВИЛА:\n" +
                        "1. ИСПОЛЬЗУЙ ЛИЧНЫЕ ДАННЫЕ ТОЛЬКО ТОГДА, КОГДА ПОЛЬЗОВАТЕЛЬ ЯВНО ПРОСИТ ОБ ЭТОМ.\n" +
                        "   Например: «посоветуй маршрут по моим точкам», «что рядом с моим местоположением», «расскажи о моих поездках».\n" +
                        "2. ВО ВСЕХ ОСТАЛЬНЫХ СЛУЧАЯХ ОТВЕЧАЙ КАК ОБЫЧНЫЙ ТУРИСТИЧЕСКИЙ ПОМОЩНИК, не упоминая координаты, названия сохранённых точек и прочую личную информацию.\n" +
                        "3. Если сомневаешься — НЕ используй личные данные.\n" +
                        "4. Никогда не перечисляй все сохранённые точки подряд, если только пользователь не попросит 'покажи все мои места'.\n\n";

        if (!cachedContext.isEmpty()) {
            systemPrompt += "Личные данные пользователя (конфиденциально):\n" + cachedContext + "\n";
        }

        // Информация о лимитах (без ключевых слов, модель сама поймёт, когда её использовать)
        if (remainingRequests >= 0) {
            systemPrompt += String.format(
                    "Текущие лимиты запросов OpenRouter: осталось запросов: %d, сброс через: %s, осталось токенов: %d.\n",
                    remainingRequests,
                    (resetTime != null ? resetTime : "неизвестно"),
                    remainingTokens
            );
            systemPrompt += "Если пользователь спросит о лимитах, используй эти числа.\n";
        } else {
            systemPrompt += "Информация о лимитах запросов пока не загружена (возможно, из-за бесплатного тарифа).\n";
            systemPrompt += "Если пользователь спросит о лимитах, скажи, что данные ещё не получены, но можно проверить их вручную на openrouter.ai/limits. Не говори «функция не реализована».\n";
        }

        String fullUserMessage = systemPrompt + "\nПользователь: " + text;
        fetchAIResponse(fullUserMessage);
    }

    /**
     * Собирает всю информацию из MainActivity в текстовый контекст.
     */
    private String buildFullContext() {
        MainActivity activity = (MainActivity) getActivity();
        if (activity == null) return "Нет данных.";

        StringBuilder ctx = new StringBuilder();

        // 1. Текущее местоположение
        Location loc = activity.getCurrentLocation();
        if (loc != null) {
            ctx.append(String.format(Locale.US,
                    "Текущее местоположение: широта=%.6f, долгота=%.6f\n",
                    loc.getLatitude(), loc.getLongitude()));
        } else {
            ctx.append("Местоположение неизвестно.\n");
        }

        // 2. Список поездок (веток)
        List<TravelList> allLists = activity.getAllTravelLists();
        if (allLists != null && !allLists.isEmpty()) {
            ctx.append("\n=== ПОЕЗДКИ ===\n");
            for (int i = 0; i < allLists.size(); i++) {
                TravelList list = allLists.get(i);
                ctx.append(i + 1).append(". ").append(list.name);
                ctx.append(" (локаций: ").append(list.locations != null ? list.locations.size() : 0);
                ctx.append(", точек пути: ").append(list.pathPoints != null ? list.pathPoints.size() : 0).append(")\n");
            }
        }

        // 3. Активная поездка и её точки
        TravelList active = activity.getCurrentActiveList();
        if (active != null) {
            ctx.append("\n=== АКТИВНАЯ ПОЕЗДКА: ").append(active.name).append(" ===\n");
            // Точки
            List<SavedLocation> locations = active.locations;
            if (locations != null && !locations.isEmpty()) {
                ctx.append("Сохранённые точки:\n");
                for (int i = 0; i < locations.size(); i++) {
                    SavedLocation sl = locations.get(i);
                    ctx.append("  ").append(i + 1).append(". ");
                    ctx.append(sl.customName.isEmpty() ? "Без названия" : sl.customName);
                    ctx.append(" [уровень ").append(sl.level).append("]");
                    ctx.append(" координаты: ").append(String.format(Locale.US, "%.5f, %.5f", sl.latLng.getLatitude(), sl.latLng.getLongitude()));
                    if (!sl.note.isEmpty()) ctx.append(", заметка: \"").append(sl.note).append("\"");
                    if (sl.date != null && !sl.date.isEmpty()) ctx.append(", дата: ").append(sl.date);
                    ctx.append(", рейтинг: ").append(sl.rating);
                    ctx.append(" фото: ").append(sl.photoPaths != null ? sl.photoPaths.size() : 0);
                    ctx.append("\n");
                }
            }
            // Маршрут
            List<LatLng> path = active.pathPoints;
            if (path != null && path.size() > 1) {
                ctx.append("Маршрут (точек: ").append(path.size()).append("): ");
                // Первую и последнюю точку для примера
                LatLng first = path.get(0);
                LatLng last = path.get(path.size() - 1);
                ctx.append(String.format(Locale.US, "от %.5f,%.5f до %.5f,%.5f\n",
                        first.getLatitude(), first.getLongitude(),
                        last.getLatitude(), last.getLongitude()));
            }
        }

        return ctx.toString();
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
                        aiText = "Ошибка: " + err.getJSONObject("error").optString("message", "неизвестно");
                    } catch (Exception e) {
                        aiText = "Ошибка HTTP " + response.code();
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
                    chatAdapter.replaceLastMessage(new ChatMessage("Ошибка сети: " + e.getMessage(), false));
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
            return "Не удалось обработать ответ";
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executor.shutdown();
    }
}