package com.example.oztrip;

import android.content.Intent;

import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextWatcher;
import android.text.style.AbsoluteSizeSpan;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.TooltipCompat;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.maplibre.android.style.layers.Layer;
import org.maplibre.android.style.layers.Property;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.Point;
import org.maplibre.android.geometry.LatLng;
import org.json.JSONArray;
import org.json.JSONObject;
import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;

import com.google.android.material.slider.Slider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.gson.GsonBuilder;
import org.maplibre.android.location.LocationComponent;
import org.maplibre.android.location.LocationComponentActivationOptions;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import com.bumptech.glide.Glide;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Locale;
import java.util.concurrent.Executors;

import android.content.SharedPreferences;
import android.content.Context;
import androidx.core.app.ActivityCompat;

public class MainActivity extends BaseActivity {
    private Bundle savedState;
    private ProgressBar pbGlobalLoading;
    private boolean isMapReady = false;

    private boolean isFirstResume = true;
    private LiquidSegmentedControl liquidNav;
    private View infoCard;
    private String currentLanguage;
    private View mapContainer, btnSaveLocation, topPanel, centerMarker, sideButtons;
    private FrameLayout mapContentContainer, aiContainer;
    private TravelRepository travelRepository;
    private boolean isDataLoaded = false; // флаг, чтобы не дублировать загрузку
    private ArrayList<TravelList> allTravelLists = new ArrayList<>(); // Список всех веток
    private int currentActiveIndex = 0; // Номер ветки, на которой мы сейчас
    private java.util.List<org.maplibre.android.geometry.LatLng> pathPoints = new java.util.ArrayList<>();
    private LatLng homeLatLng = new LatLng(40.1792, 44.5134); // Та самая база
    private MapView mapView;
    public MapLibreMap mapLibre;
    // Список всех сохраненных точек (баз)
// Удаляем: private java.util.List<Feature> savedFeatures = new java.util.ArrayList<>();
    private SavedLocation currentlyEditingLocation;
    // Добавляем: Список уникальных getString(R.string.text_auto_103) с уровнями
    private java.util.List<SavedLocation> uniqueLocations = new java.util.ArrayList<>();
    private static final String SAVED_POINTS_SOURCE = "saved-points-source";
    private static final String SAVED_POINTS_LAYER = "saved-points-layer";
    private com.google.android.material.bottomsheet.BottomSheetBehavior sheetBehavior;
    private org.maplibre.android.annotations.Polyline runningLine;
    private boolean isSearching = false;
    // В MainActivity:
    private TravelListAdapter listAdapter;
    private RecyclerView rvTravelLists;
    private long lastCameraUpdate = 0;
    private List<TravelList> allLists = new ArrayList<>();

    private TravelList currentActiveList;
    private final Handler saveHandler = new Handler(Looper.getMainLooper());
    private final Runnable saveRunnable = new Runnable() {
        @Override
        public void run() {
            // Выполняем ФАКТИЧЕСКОЕ сохранение
            saveAllData();
            syncAllDataToCloud();
        }
    };

    private void scheduleSave() {
        // Удаляем предыдущий отложенный вызов (debounce)
        saveHandler.removeCallbacks(saveRunnable);
        // Планируем новый через 2 секунды
        saveHandler.postDelayed(saveRunnable, 2000);
    }

    private org.maplibre.android.annotations.Icon createPremiumMarker(SavedLocation loc) {
        if (loc.cachedIcon != null && !loc.hasNewPhoto()) {
            return loc.cachedIcon;
        }

        int canvasSize = (int) dpToPx(74); // Чуть увеличим под тени
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(canvasSize, canvasSize, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);

        float center = canvasSize / 2f;
        float mainCircleRadius = dpToPx(20);
        float arcRadius = mainCircleRadius + dpToPx(4);
        float strokeWidth = (float) dpToPx(2.5f);

        // --- ПРИГОТОВЛЕНИЕ КРАСОК ---

        // Глубокая тень для парения
        android.graphics.Paint shadowPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.WHITE);
        shadowPaint.setShadowLayer(dpToPx(12), 0, dpToPx(5), Color.parseColor("#44000000"));

        // Оранжевый неон
        android.graphics.Paint strokePaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        strokePaint.setColor(Color.parseColor("#FF9800"));
        strokePaint.setStyle(android.graphics.Paint.Style.STROKE);
        strokePaint.setStrokeWidth(strokeWidth);
        strokePaint.setStrokeCap(android.graphics.Paint.Cap.ROUND);

        // Белая обводка вокруг самого фото (чтобы отделить его от оранжевого кольца)
        android.graphics.Paint whiteBorderPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        whiteBorderPaint.setColor(Color.WHITE);
        whiteBorderPaint.setStyle(android.graphics.Paint.Style.STROKE);
        whiteBorderPaint.setStrokeWidth(dpToPx(2));

        // --- ЛОГИКА ЗАГРУЗКИ ФОТО ---
        android.graphics.Paint photoPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        boolean hasPhoto = !loc.photoPaths.isEmpty();

        if (hasPhoto) {
            try {
                String path = loc.photoPaths.get(loc.photoPaths.size() - 1);
                File file = new File(path);
                if (file.exists()) {
                    Bitmap source = BitmapFactory.decodeFile(path);
                    if (source != null) {

                        // 1. Делаем фото квадратным (Center Crop)
                        int size = Math.min(source.getWidth(), source.getHeight());
                        int x = (source.getWidth() - size) / 2;
                        int y = (source.getHeight() - size) / 2;
                        android.graphics.Bitmap squared = android.graphics.Bitmap.createBitmap(source, x, y, size, size);

                        // 2. Масштабируем под размер нашего маркера
                        int targetDim = (int) (mainCircleRadius * 2);
                        android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(squared, targetDim, targetDim, true);

                        // 3. Создаем шейдер для круглой отрисовки
                        android.graphics.BitmapShader shader = new android.graphics.BitmapShader(scaled, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP);
                        photoPaint.setShader(shader);
                    } else {
                        hasPhoto = false;
                    }
                } else {
                    hasPhoto = false;
                }
            } catch (Exception e) {
                hasPhoto = false;
            }
        }

        // --- РИСОВАНИЕ ---

        canvas.drawCircle(center, center, mainCircleRadius, shadowPaint);

// 2. РИСУЕМ ФОТО (Или белое ядро)
        if (hasPhoto) {
            canvas.save();
            canvas.translate(center - mainCircleRadius, center - mainCircleRadius);
            canvas.drawCircle(mainCircleRadius, mainCircleRadius, mainCircleRadius, photoPaint);
            canvas.restore();
            // Белая кайма поверх фото, чтобы оно выглядело аккуратно
            canvas.drawCircle(center, center, mainCircleRadius, whiteBorderPaint);
        } else {
            android.graphics.Paint innerPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            innerPaint.setColor(Color.WHITE);
            canvas.drawCircle(center, center, mainCircleRadius, innerPaint);
        }

// 3. ТЕПЕРЬ РИСУЕМ ШЛЯПУ (PROGRESS ARC & BADGE)
// Важно: это должно быть ПОСЛЕ отрисовки фото, чтобы быть сверху!

        android.graphics.RectF arcRect = new android.graphics.RectF(
                center - arcRadius, center - arcRadius,
                center + arcRadius, center + arcRadius
        );

        if (loc.level <= 1) {
            // Уровень 1: Просто полное кольцо вокруг
            canvas.drawCircle(center, center, arcRadius, strokePaint);
        } else {
            // Уровень 2+: Разрывное кольцо и Badge с цифрой
            canvas.drawArc(arcRect, -70, 320, false, strokePaint);

            // Рисуем оранжевый Badge (кружок для цифры)
            android.graphics.Paint badgePaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            badgePaint.setColor(Color.parseColor("#FF9800"));
            float badgeRadius = dpToPx(9);
            float badgeY = center - arcRadius; // Позиция строго сверху

            // Рисуем сам кружок badge
            canvas.drawCircle(center, badgeY, badgeRadius, badgePaint);

            // Рисуем число уровня внутри badge
            android.graphics.Paint textPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(dpToPx(11));
            textPaint.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL));
            textPaint.setTextAlign(android.graphics.Paint.Align.CENTER);

            float textY = badgeY - ((textPaint.descent() + textPaint.ascent()) / 2f);
            canvas.drawText(String.valueOf(loc.level), center, textY, textPaint);
        }

        // --- СОХРАНЕНИЕ В КЭШ ---
        loc.cachedIcon = org.maplibre.android.annotations.IconFactory.getInstance(this).fromBitmap(bitmap);
        return loc.cachedIcon;
    }

    private void checkPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1002);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        allLists = new ArrayList<>();
        uniqueLocations = new ArrayList<>();
        pathPoints = new ArrayList<>();

        // Загружаем данные из памяти
        loadAllData();

        if (allLists.isEmpty()) {
            TravelList defaultList = new TravelList(getString(R.string.text_auto_104));
            allLists.add(defaultList);
            currentActiveList = defaultList;
        }

        try {
            MapLibre.getInstance(this);
            SharedPreferences prefs = getSharedPreferences("OzTripPrefs", MODE_PRIVATE);
            String lang = prefs.getString("language", "ru");
            setLocale(lang);
            currentLanguage = lang;

            setContentView(R.layout.activity_main);

            // Проверка разрешения геолокации
            if (!hasLocationPermission()) {
                // Если разрешения нет – прячем все элементы и запрашиваем
                findViewById(R.id.mapContainer).setVisibility(View.GONE);
                findViewById(R.id.aiContainer).setVisibility(View.GONE);
                findViewById(R.id.liquid_nav).setVisibility(View.GONE);
                requestLocationPermission();
            } else {
                // Разрешение уже есть – запускаем полную инициализацию
                this.savedState = savedInstanceState;
                initializeApp();
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.text_auto_108) + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // Теперь эти методы – часть класса, а не onCreate

    private boolean hasLocationPermission() {
        return androidx.core.app.ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        androidx.core.app.ActivityCompat.requestPermissions(this,
                new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1001);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Пользователь дал разрешение – перезапускаем активность
            recreate();
        } else {
            Toast.makeText(this, "Без геолокации приложение не работает", Toast.LENGTH_LONG).show();
            finishAffinity();
            System.exit(0);
        }
    }

    // Метод, который содержит всю инициализацию (ранее всё было в onCreate)
    private void initializeApp() {
        // ======== Добавляем AiFragment =========
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.aiContainer, new AiFragment())
                .commit();

        // Группы View, которые относятся к карте
        mapContainer = findViewById(R.id.mapContainer);
        btnSaveLocation = findViewById(R.id.btnSaveLocation);
        topPanel = findViewById(R.id.topPanel);
        centerMarker = findViewById(R.id.centerMarker);
        sideButtons = findViewById(R.id.sideButtons);
        infoCard = findViewById(R.id.infoCard);
        mapContentContainer = findViewById(R.id.mapContentContainer);
        aiContainer = findViewById(R.id.aiContainer);

        sheetBehavior = BottomSheetBehavior.from(infoCard);
        sheetBehavior.setHideable(true);
        sheetBehavior.setPeekHeight(450);
        sheetBehavior.setHalfExpandedRatio(0.4f);
        sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        setupBottomSheetCallbacks();

        // Переключатель вкладок
        liquidNav = findViewById(R.id.liquid_nav);
        boolean isDark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        liquidNav.setDarkMode(isDark);
        if (liquidNav != null) {
            liquidNav.setOnTabSelectedListener(index -> {
                if (mapView != null) {
                    mapView.onResume();
                }
                float screenWidth = getResources().getDisplayMetrics().widthPixels;
                if (index == 0) {
                    aiContainer.animate()
                            .translationX(screenWidth)
                            .alpha(0f)
                            .setDuration(400)
                            .withEndAction(() -> {
                                aiContainer.setVisibility(View.GONE);
                                aiContainer.setTranslationX(0f);
                            });
                    mapContentContainer.setVisibility(View.VISIBLE);
                    mapContentContainer.setTranslationX(-screenWidth);
                    mapContentContainer.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(400)
                            .start();
                    sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                    if (mapLibre != null) mapLibre.getUiSettings().setAllGesturesEnabled(true);
                } else {
                    if (mapView != null) {
                        mapView.onPause();
                    }
                    mapContentContainer.animate()
                            .translationX(-screenWidth)
                            .alpha(0f)
                            .setDuration(400)
                            .withEndAction(() -> {
                                mapContentContainer.setVisibility(View.INVISIBLE);
                                mapContentContainer.setTranslationX(0f);
                            });
                    aiContainer.setVisibility(View.VISIBLE);
                    aiContainer.setTranslationX(screenWidth);
                    aiContainer.setAlpha(0f);
                    aiContainer.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(400)
                            .start();
                    sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                }
            });
        }

        setupRecyclerView();
        rvTravelLists.setFadingEdgeLength((int) dpToPx(40));
        rvTravelLists.setHorizontalFadingEdgeEnabled(true);
        if (listAdapter != null) listAdapter.notifyDataSetChanged();

        findViewById(R.id.btnSettings).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        mapView = findViewById(R.id.mapView);
        if (mapView != null) mapView.onCreate(savedState);

        FirebaseAuth auth = FirebaseAuth.getInstance();
        boolean isGuest = getSharedPreferences("OzTripPrefs", MODE_PRIVATE)
                .getBoolean("guest_mode", false);
        if (auth.getCurrentUser() == null && !isGuest) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        if (auth.getCurrentUser() != null) {
            travelRepository = new TravelRepository();
        }
        loadTravelDataFromCloud();
        setupButtons();

        if (isFirstLaunch()) {
            showOnboardingDialog();
        }
        if (mapView != null) {
            mapView.getMapAsync(map -> {
                this.mapLibre = map;
                // ПРОВЕРКА: Если стиль уже есть (мы просто вернулись с другого экрана)
                if (map.getStyle() != null && map.getStyle().isFullyLoaded()) {
                    // НИЧЕГО НЕ ДЕЛАЕМ. Карта сама всё помнит: и ветки, и границы.
                    return;
                }

                String styleUrl = isDark
                        ? "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
                        : "https://tiles.openfreemap.org/styles/liberty";
                map.setStyle(styleUrl, style -> {
                    // Скрываем UI элементы
                    map.getUiSettings().setCompassEnabled(false);
                    map.getUiSettings().setAttributionEnabled(false);
                    map.getUiSettings().setLogoEnabled(false); // <-- ЭТО КЛЮЧЕВОЙ МЕТОД

                    // Полет в Ереван только при первом запуске
                    if (map.getCameraPosition().zoom < 3) {
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(40.1792, 44.5134), 12), 2000);
                    }
                    refreshSavedPoints();
                    enableLocation(style);
                    restoreMyData(style);
                });
                mapLibre.setOnMarkerClickListener(marker -> {
                    // 1. Находим, на какую именно базу нажал пользователь
                    SavedLocation clickedLoc = null;
                    for (SavedLocation loc : uniqueLocations) {
                        if (loc.latLng.equals(marker.getPosition())) {
                            clickedLoc = loc;
                            break;
                        }
                    }

                    if (clickedLoc != null) {
                        // 2. Показываем карточку с данными этой базы
                        showLocationCard(clickedLoc);
                    }

                    return true; // "true" значит, что мы сами обработали нажатие
                });

                // ДОБАВЛЯЕМ КЛИК ПО КАРТЕ
// СЛУШАТЕЛЬ ДЛИННОГО НАЖАТИЯ
                map.addOnMapLongClickListener(point -> {
                    findViewById(android.R.id.content).performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    // 1. Показываем визуальный getString(R.string.text_auto_105) (пин) на карте
                    showMarkerAt(point.getLatitude(), point.getLongitude());

                    // 2. Запускаем поиск данных (тот метод, что мы создали ранее)
                    reverseSearch(point.getLatitude(), point.getLongitude());

                    // true означает, что событие обработано
                    return true;
                });

                // 2. ОБЫЧНЫЙ КЛИК (Очистка карты)
                map.addOnMapClickListener(point -> {
                    android.graphics.PointF screenPoint = mapLibre.getProjection().toScreenLocation(point);
                    java.util.List<Feature> features = mapLibre.queryRenderedFeatures(screenPoint);

                    if (!features.isEmpty()) {
                        Feature feat = features.get(0);

                        // Скрываем оранжевые слои
                        hideManualOverlays();

                        if (feat.geometry() instanceof Point) {
                            Point p = (Point) feat.geometry();
                            // Вместо мгновенного вызова карточки, запускаем поиск адреса
                            // чтобы получить город и страну для этой аптеки/магазина
                            reverseSearch(p.latitude(), p.longitude());
                        }
                    } else {
                        clearMapOverlays();
                    }
                    return true;
                });

                map.addOnCameraMoveListener(() -> {
                    long now = System.currentTimeMillis();
                    if (now - lastCameraUpdate < 50) return;
                    lastCameraUpdate = now;
                    double bearing = map.getCameraPosition().bearing;

                    // БЫЛО: FloatingActionButton
                    // СТАЛО: ImageView
                    ImageView fabCompass = findViewById(R.id.fabCompass);
                    if (fabCompass != null) {
                        fabCompass.setRotation((float) -bearing);
                    }

                    if (hasLocationPermission()) {
                        LocationComponent locationComponent = map.getLocationComponent();
                        if (centerMarker != null && centerMarker.getVisibility() == View.VISIBLE
                                && locationComponent != null && locationComponent.getLastKnownLocation() != null) {

                            // 2. Получаем экранные координаты центра метки
                            android.graphics.PointF markerPoint = new android.graphics.PointF(
                                    centerMarker.getLeft() + centerMarker.getWidth() / 2f,
                                    centerMarker.getTop() + centerMarker.getHeight() / 2f
                            );

                            // 3. Получаем экранные координаты пользователя
                            LatLng userLatLng = new LatLng(
                                    locationComponent.getLastKnownLocation().getLatitude(),
                                    locationComponent.getLastKnownLocation().getLongitude()
                            );
                            android.graphics.PointF userPoint = map.getProjection().toScreenLocation(userLatLng);

                            // 4. Рассчитываем расстояние между ними (в пикселях)
                            double distance = Math.sqrt(
                                    Math.pow(markerPoint.x - userPoint.x, 2) +
                                            Math.pow(markerPoint.y - userPoint.y, 2)
                            );

                            // 5. ЛОГИКА СЛИЯНИЯ (Порог слияния - например, 50 пикселей)
                            float threshold = dpToPx(50); // Используем твой метод dpToPx

                            if (distance < threshold) {
                                // Расчет коэффициента слияния (от 0.0 до 1.0)
                                float fusionFactor = 1.0f - (float) (distance / threshold);

                                // --- ЭФФЕКТ СЛИЯНИЯ ---

                                // А. Метка немного сжимается, getString(R.string.text_auto_106) точку пользователя
                                float scale = 1.0f - (fusionFactor * 0.15f); // Сжатие до 85%
                                centerMarker.setScaleX(scale);
                                centerMarker.setScaleY(scale);

                                // Б. Метка становится немного ярче/насыщеннее (или добавляем свечение через Alpha)
                                centerMarker.setAlpha(0.8f + (fusionFactor * 0.2f)); // Поднимаем Alpha до 1.0

                                // В. Метка немного опускается, чтобы центр перекрестия совпал с точкой
                                // (Корректируем translationY, учитывая исходное смещение -2dp)
                                float translationY = dpToPx(-2) + (fusionFactor * dpToPx(2));
                                centerMarker.setTranslationY(translationY);

                            } else {
                                // --- ВОЗВРАТ К ОБЫЧНОМУ СОСТОЯНИЮ (Если пользователь далеко) ---
                                // Плавная анимация возврата (чтобы не дергалось)
                                centerMarker.animate().scaleX(1.0f).scaleY(1.0f).translationY(dpToPx(-2)).alpha(1.0f).setDuration(200).start();
                            }
                        }
                    }

                    if (!isSearching) {
                        // 1. Берем координаты центра экрана (в LatLng и в Пикселях)
                        org.maplibre.android.geometry.LatLng centerLatLng = mapLibre.getCameraPosition().target;
                        android.graphics.PointF centerPoint = mapLibre.getProjection().toScreenLocation(centerLatLng);

                        for (SavedLocation loc : uniqueLocations) {
                            // 2. Переводим координаты каждой базы из LatLng в Пиксели экрана
                            android.graphics.PointF locPoint = mapLibre.getProjection().toScreenLocation(loc.latLng);

                            // 3. Считаем расстояние в пикселях (Геометрия: корень из суммы квадратов)
                            float dx = centerPoint.x - locPoint.x;
                            float dy = centerPoint.y - locPoint.y;
                            double distanceInPx = Math.sqrt(dx * dx + dy * dy);

                            // 4. Если палец (центр) ближе 40-50 пикселей к маркеру
                            if (distanceInPx < 50 && distanceInPx > 2) {
                                // Магнитим камеру прямо в центр объекта
                                mapLibre.easeCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLng(loc.latLng), 100);

                                // Вибрация getString(R.string.text_auto_107)
                                findViewById(android.R.id.content).performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                                break;
                            }
                        }
                    }
                });


            });
        }
    }

    private boolean isFirstLaunch() {
        SharedPreferences prefs = getSharedPreferences("OzTripPrefs", MODE_PRIVATE);
        boolean first = prefs.getBoolean("first_launch", true);
        if (first) {
            prefs.edit().putBoolean("first_launch", false).apply();
        }
        return first;
    }

    private void showOnboardingDialog() {
        // Используем твой же стиль PremiumDialogTheme
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.PremiumDialogTheme);
        View view = getLayoutInflater().inflate(R.layout.dialog_onboarding, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        view.findViewById(R.id.btnGotIt).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
    private void setLocale(String lang) {
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Resources res = getResources();
        Configuration config = res.getConfiguration();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale);
        } else {
            config.locale = locale;
        }
        res.updateConfiguration(config, res.getDisplayMetrics());
    }

    private String getStorageKey() {
        boolean isGuest = getSharedPreferences("OzTripPrefs", MODE_PRIVATE)
                .getBoolean("guest_mode", false);
        return isGuest ? "guest_travels" : "saved_travels";
    }



    // Вернуть список всех поездок
    public List<TravelList> getAllTravelLists() {
        return allLists;
    }

    // Вернуть текущую активную поездку
    public TravelList getCurrentActiveList() {
        return currentActiveList;
    }

    // Вернуть уникальные локации активной поездки
    public List<SavedLocation> getUniqueLocations() {
        return uniqueLocations;
    }

    // Вернуть путь активной поездки
    public List<LatLng> getPathPoints() {
        return pathPoints;
    }

    // Получить текущее местоположение (если геолокация включена)
    public android.location.Location getCurrentLocation() {
        if (!hasLocationPermission()) return null;
        if (mapLibre != null && mapLibre.getLocationComponent().isLocationComponentActivated()
                && mapLibre.getLocationComponent().getLastKnownLocation() != null) {
            return mapLibre.getLocationComponent().getLastKnownLocation();
        }
        return null;
    }



    private void fadeOutView(View view, int duration) {
        view.animate().alpha(0f).setDuration(duration)
                .withEndAction(() -> view.setVisibility(View.INVISIBLE));
    }
    private void setupBottomSheetCallbacks() {
        sheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED || newState == BottomSheetBehavior.STATE_HIDDEN) {
                    findViewById(R.id.btnSaveLocation).setEnabled(true);
                } else {
                    findViewById(R.id.btnSaveLocation).setEnabled(true);
                }

                // 🔥 Разрешаем карте принимать жесты, когда BottomSheet свёрнут
                if (infoCard != null) {
                    if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                        // В свёрнутом виде – делаем некликабельной, чтобы карта получала жесты
                        infoCard.setClickable(false);
                        infoCard.setFocusable(false);
                    } else {
                        // В развёрнутом или скрытом состоянии – можно кликать
                        infoCard.setClickable(true);
                        infoCard.setFocusable(true);
                    }
                }

                if (mapLibre != null) {
                    boolean gesturesEnabled = (newState != BottomSheetBehavior.STATE_EXPANDED);
                    mapLibre.getUiSettings().setAllGesturesEnabled(gesturesEnabled);
                }

                if (liquidNav != null) {
                    liquidNav.setEnabled(newState == BottomSheetBehavior.STATE_HIDDEN);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                View saveBtn = findViewById(R.id.btnSaveLocation);
                if (saveBtn != null) {
                    float sheetTop = bottomSheet.getTop();
                    int screenHeight = getResources().getDisplayMetrics().heightPixels;
                    float buttonDefaultTop = screenHeight - dpToPx(110) - saveBtn.getHeight();
                    float gap = dpToPx(15);
                    if (sheetTop < (buttonDefaultTop + saveBtn.getHeight() + gap)) {
                        float targetY = sheetTop - gap - saveBtn.getHeight();
                        float offset = targetY - buttonDefaultTop;
                        saveBtn.setTranslationY(offset);
                    } else {
                        saveBtn.setTranslationY(0);
                    }
                }
            }
        });
    }

    public void invalidateAiContext() {
        AiFragment aiFragment = (AiFragment) getSupportFragmentManager().findFragmentById(R.id.aiContainer);
        if (aiFragment != null) aiFragment.clearCachedContext();
    }


    private void loadTravelDataFromCloud() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            // Гость – просто загружаем локальные данные, не лезем в облако
            loadLocalBackup();
            isDataLoaded = true;
            return;
        }
        Executors.newSingleThreadExecutor().execute(() -> {
            travelRepository.loadAllLists(new TravelRepository.OnDataLoadedListener() {
                @Override
                public void onLoaded(List<TravelList> lists) {
                    runOnUiThread(() -> {
                        // сюда скопируй весь код, который был внутри onLoaded
                        if (lists != null && !lists.isEmpty()) {
                            allLists.clear();
                            allLists.addAll(lists);
                            removeDuplicateLists();
                            Collections.sort(allLists, (a, b) -> a.name.compareToIgnoreCase(b.name));
                        } else {
                            allLists.clear();
                            allLists.add(new TravelList(getString(R.string.text_auto_109)));
                            syncAllDataToCloud();
                        }
                        currentActiveList = allLists.get(0);
                        uniqueLocations = currentActiveList.locations;
                        pathPoints = currentActiveList.pathPoints;
                        saveAllData();
                        if (listAdapter != null) listAdapter.notifyDataSetChanged();
                        refreshSavedPoints();
                        isDataLoaded = true;
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Log.e("Firestore", error);
                        loadLocalBackup();
                    });
                }
            });
        });
    }
    private void removeDuplicateLists() {
        List<TravelList> unique = new ArrayList<>();
        for (TravelList list : allLists) {
            boolean exists = false;
            for (TravelList existing : unique) {
                if (existing.name.equals(list.name)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                unique.add(list);
            }
        }
        allLists.clear();
        allLists.addAll(unique);
    }
    private void loadLocalBackup() {

        loadAllData(); // загружает из SharedPreferences
        if (allLists != null && !allLists.isEmpty()) {
            currentActiveList = allLists.get(0);
            uniqueLocations = currentActiveList.locations;
            pathPoints = currentActiveList.pathPoints;
            if (listAdapter != null) listAdapter.notifyDataSetChanged();
            isDataLoaded = true;
            refreshSavedPoints();
            Toast.makeText(this, getString(R.string.text_auto_110), Toast.LENGTH_SHORT).show();
            // При первой возможности сохраним их в облако
            if (travelRepository != null) {
                syncAllDataToCloud();
            }
        }
    }
    private void syncAllDataToCloud() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        if (!isDataLoaded) return; // ещё не загрузились, не надо сохранять
        travelRepository.saveAllLists(allLists, new TravelRepository.OnSaveListener() {
            @Override
            public void onSuccess() {
                // можно ничего не делать, или показать тост раз в несколько сохранений
            }
            @Override
            public void onError(String error) {
                Log.e("Firestore", getString(R.string.text_auto_111) + error);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, getString(R.string.text_auto_61) + error, Toast.LENGTH_LONG).show());
            }
        });
    }
    private void setupRecyclerView() {
        rvTravelLists = findViewById(R.id.rvTravelLists); // Убедись, что ID совпадает с XML
        TextView tvCount = findViewById(R.id.tvTravelCount);
        updateTravelCount(); // метод, который установит цифру

        tvCount.setOnClickListener(v -> {
            TravelListSheetFragment sheet = new TravelListSheetFragment();
            sheet.show(getSupportFragmentManager(), "travel_list_sheet");
        });

// Обновление цифры при изменениях
        if (rvTravelLists != null) {
            // 1. Настраиваем менеджер компоновки (Горизонтальный список)
            LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
            rvTravelLists.setLayoutManager(layoutManager);

            // 2. Создаем адаптер и передаем туда наш список всех поездок
            listAdapter = new TravelListAdapter(allLists, new TravelListAdapter.OnListClickListener() {
                @Override
                public void onListClick(int position) {
                    // Переключаемся на другую ветку (поездку)
                    switchTravelList(position);
                }

                @Override
                public void onListRename(int position, String oldName) {
                    // Вызываем твой диалог переименования
                    showRenameDialog(position, allLists.get(position).name,null);
                }
            });

            rvTravelLists.setAdapter(listAdapter);

            // Чтобы список не getString(R.string.text_auto_112)
            //rvTravelLists.setHasFixedSize(true);
        }
    }
    public void removeTravelListByIndex(int index) {
        if (index >= 0 && index < allLists.size()) {
            deleteTravelList(index); // уже умеет удалять и обновлять UI
        }
    }
    public int getActiveListIndex() {
        return allLists.indexOf(currentActiveList);
    }
    public void switchTravelList(int position) {
        saveAllData();

        currentActiveList = allLists.get(position);

        if (currentActiveList.pathPoints == null)
            currentActiveList.pathPoints = new ArrayList<>();
        if (currentActiveList.locations == null)
            currentActiveList.locations = new ArrayList<>();

        pathPoints = currentActiveList.pathPoints;
        uniqueLocations = currentActiveList.locations;
        invalidateAiContext();
        refreshSavedPoints();

        // 👇 Обновляем выделение в ползунке
        if (listAdapter != null) {
            listAdapter.setSelectedIndex(position);
        }

        if (!uniqueLocations.isEmpty()) {
            mapLibre.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(uniqueLocations.get(0).latLng, 14), 1000);
        }
    }



    private void restoreMyData(org.maplibre.android.maps.Style style) {
        // А. Восстанавливаем маркеры (Базы)
        // Если ты используешь старые Annotations:
        for (SavedLocation loc : uniqueLocations) {
            mapLibre.addMarker(new org.maplibre.android.annotations.MarkerOptions()
                    .position(loc.latLng)
                    .icon(createPremiumMarker(loc))
                    .title(loc.customName));
        }

        // Б. Восстанавливаем Ветки (Пути)
        if (pathPoints.size() > 1) {
            // Если используешь Polyline:
            mapLibre.addPolyline(new org.maplibre.android.annotations.PolylineOptions()
                    .addAll(pathPoints)
                    .color(Color.parseColor("#FF9800"))
                    .width(dpToPx(4)));
        }
    }
    private void updateTravelCount() {
        TextView tvCount = findViewById(R.id.tvTravelCount);
        if (tvCount != null) {
            tvCount.setText(String.valueOf(allLists.size()));
        }
    }
    private void showLocationCard(SavedLocation loc) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.TransparentBottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.layout_location_card, null);
        dialog.setContentView(view);

        TextView titleView = view.findViewById(R.id.cardTitle);
        View btnEdit = view.findViewById(R.id.btnEditTitle);
        EditText descInput = view.findViewById(R.id.cardDescription);
        LinearLayout photoContainer = view.findViewById(R.id.photoContainer);
        ImageView btnAddPhoto = view.findViewById(R.id.btnAddPhoto);
        Slider ratingSlider = view.findViewById(R.id.ratingSlider);
        View btnPickDate = view.findViewById(R.id.btnPickDate);
        TextView tvDateDisplay = view.findViewById(R.id.tvDateDisplay);

        // Заголовок
        titleView.setText(loc.customName.isEmpty() ? getString(R.string.text_auto_113) : loc.customName);
        btnEdit.setOnClickListener(v -> showNameEditDialog(loc, titleView));

        Button btnDeleteLocation = view.findViewById(R.id.btnDeleteLocation);
        // Описание
        descInput.setText(loc.note);
        descInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                loc.note = s.toString();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Отображаем список фото
        renderPhotosInCard(loc, photoContainer);

        // Кнопка добавления фото (квадрат с плюсом)
        btnAddPhoto.setOnClickListener(v -> {
            currentlyEditingLocation = loc;
            openGallery();
            dialog.dismiss();
        });

        // Рейтинг
        ratingSlider.setValue(loc.rating);
        ratingSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) loc.rating = value;
        });

        // Дата
        if (loc.date != null && !loc.date.isEmpty()) {
            tvDateDisplay.setText(loc.date);
            tvDateDisplay.setTextColor(Color.parseColor("#FF9800"));
        }
        btnPickDate.setOnClickListener(v -> showDatePicker(loc, tvDateDisplay));



        btnDeleteLocation.setOnClickListener(v -> {
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_delete_point, null);

            AlertDialog deleteDialog = new AlertDialog.Builder(this, R.style.PremiumDialogTheme)
                    .setView(dialogView)
                    .create();

            // Исправлено: deleteView вместо view
            dialogView.findViewById(R.id.btnConfirmDelete).setOnClickListener(deleteView -> {
                // 1. Удаляем физические файлы фото
                if (loc.photoPaths != null) {
                    for (String photoPath : loc.photoPaths) {
                        File file = new File(photoPath);
                        if (file.exists()) file.delete();
                    }
                }

                // 2. Находим координаты удаляемой точки
                LatLng coordToRemove = loc.latLng;

                // 3. Удаляем координату из pathPoints (по значению)
                java.util.Iterator<LatLng> iterator = pathPoints.iterator();
                while (iterator.hasNext()) {
                    LatLng point = iterator.next();
                    if (point.equals(coordToRemove)) {
                        iterator.remove();
                        break;
                    }
                }

                // 4. Удаляем точку из списка
                uniqueLocations.remove(loc);

                // 5. Синхронизируем текущий активный список
                currentActiveList.locations = new ArrayList<>(uniqueLocations);
                currentActiveList.pathPoints = new ArrayList<>(pathPoints);

                // 6. Обновляем карту
                refreshSavedPoints();

                // 7. Сохраняем изменения
                scheduleSave();

                // 8. Закрываем диалоги
                deleteDialog.dismiss();
                dialog.dismiss();

                Toast.makeText(this, getString(R.string.text_auto_114), Toast.LENGTH_SHORT).show();
            });

            dialogView.findViewById(R.id.btnCancelDelete).setOnClickListener(deleteView -> deleteDialog.dismiss());

            deleteDialog.show();
        });

        dialog.setOnDismissListener(d -> {
            scheduleSave();
        });
        dialog.show();
    }

    private void renderPhotosInCard(SavedLocation loc, LinearLayout container) {
        if (container == null) return;
        container.removeAllViews();

        int imgSize = (int) dpToPx(120);
        int margin = (int) dpToPx(12);
        int deleteBtnSize = (int) dpToPx(32);

        for (int i = 0; i < loc.photoPaths.size(); i++) {
            final int photoIndex = i;
            String photoPath = loc.photoPaths.get(i);
            File file = new File(photoPath);
            Log.d("Photo", getString(R.string.text_auto_115) + i + ": " + photoPath + getString(R.string.text_auto_116) + file.exists());
            if (!file.exists()) continue;

            // Карточка фото
            MaterialCardView card = new MaterialCardView(this);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(imgSize, imgSize);
            cardParams.setMargins(0, 0, margin, 0);
            card.setLayoutParams(cardParams);
            card.setRadius(dpToPx(16));
            card.setCardElevation(dpToPx(4));
            card.setCardBackgroundColor(Color.parseColor("#66FFFFFF"));
            card.setStrokeColor(Color.parseColor("#FF9800"));
            card.setStrokeWidth(dpToPx(2));
            card.setClipToOutline(true);

            // Само фото
            ImageView imageView = new ImageView(this);
            imageView.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(this).load(file).centerCrop().into(imageView);
            // ========== ДОБАВЬ ЭТУ СТРОЧКУ ==========
            imageView.setOnClickListener(v -> showFullscreenPhoto(photoPath));
            // ======================================
            card.addView(imageView);

            // Кнопка удаления (красивая корзина)
            ImageView btnDelete = new ImageView(this);
            FrameLayout.LayoutParams delParams = new FrameLayout.LayoutParams(deleteBtnSize, deleteBtnSize);
            delParams.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
            delParams.setMargins(0, (int) dpToPx(8), (int) dpToPx(8), 0);
            btnDelete.setLayoutParams(delParams);
            btnDelete.setImageResource(R.drawable.ic_trash_premium);
            btnDelete.setColorFilter(Color.WHITE);

            // Красивый полупрозрачный фон для кнопки удаления
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            bg.setColor(Color.parseColor("#CCFF5252"));
            btnDelete.setBackground(bg);
            btnDelete.setPadding((int) dpToPx(6), (int) dpToPx(6), (int) dpToPx(6), (int) dpToPx(6));
            btnDelete.setClickable(true);
            btnDelete.setFocusable(true);

            btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(this, R.style.PremiumDialogTheme)
                        .setTitle(getString(R.string.text_auto_117))
                        .setMessage(getString(R.string.text_auto_118))
                        .setPositiveButton(getString(R.string.text_auto_119), (dialog, which) -> {
                            // 1. Удаляем физический файл с телефона
                            File photoFile = new File(photoPath);
                            if (photoFile.exists()) {
                                photoFile.delete();
                            }

                            // 2. Удаляем из списка в памяти
                            loc.photoPaths.remove(photoIndex);
                            loc.cachedIcon = null;

                            // 3. Обновляем UI
                            renderPhotosInCard(loc, container);
                            refreshSavedPoints();

                            // 4. Сохраняем изменения
                            scheduleSave();

                            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                            Toast.makeText(this, getString(R.string.text_auto_120), Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton(getString(R.string.text_auto_121), null)
                        .show();
            });


            card.addView(btnDelete);
            container.addView(card);
        }
    }
    private void showFullscreenPhoto(String photoPath) {
        File file = new File(photoPath);
        if (!file.exists()) {
            Toast.makeText(this, getString(R.string.text_auto_122), Toast.LENGTH_SHORT).show();
            return;
        }

        // Создаём диалог
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.FullscreenDialogTheme);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_fullscreen_photo, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        ImageView ivPhoto = dialogView.findViewById(R.id.ivFullscreenPhoto);
        ImageView btnClose = dialogView.findViewById(R.id.btnCloseFullscreen);

        // Загружаем фото с правильными настройками
        Glide.with(this)
                .load(file)
                .fitCenter()
                .override(1080, 1920)  // Максимальный размер
                .into(ivPhoto);

        btnClose.setOnClickListener(v -> dialog.dismiss());
        ivPhoto.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
    // Новый способ: открывает именно фото-пикер с альбомами
    private void showDatePicker(SavedLocation loc, TextView tvDateDisplay) {
        // 1. Берем текущую дату из календаря как точку отсчета
        java.util.Calendar c = java.util.Calendar.getInstance();
        int year = c.get(java.util.Calendar.YEAR);
        int month = c.get(java.util.Calendar.MONTH);
        int day = c.get(java.util.Calendar.DAY_OF_MONTH);

        // 2. Создаем диалог
        android.app.DatePickerDialog datePickerDialog = new android.app.DatePickerDialog(
                this,
                // Используем системную тему для диалога
                android.R.style.Theme_DeviceDefault_Dialog_Alert,
                (view, selectedYear, monthOfYear, dayOfMonth) -> {

                    // 3. Форматируем выбранную дату красиво
                    java.util.Calendar selectedCal = java.util.Calendar.getInstance();
                    selectedCal.set(selectedYear, monthOfYear, dayOfMonth);

                    // "dd MMMM yyyy" превратит дату в getString(R.string.text_auto_123)
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale.getDefault());
                    String formattedDate = sdf.format(selectedCal.getTime());

                    // 4. Обновляем данные в объекте и на экране
                    loc.date = formattedDate;
                    tvDateDisplay.setText(formattedDate);
                    tvDateDisplay.setTextColor(android.graphics.Color.parseColor("#FF9800")); // Оранжевый акцент

                    // 5. Сохраняем в память
                    scheduleSave();
                },
                year, month, day);

        datePickerDialog.show();
    }
    private void showNameEditDialog(SavedLocation loc, TextView titleView) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setText(loc.customName.isEmpty() ? titleView.getText() : loc.customName);
        input.setSelection(input.getText().length());

        // Добавим отступы внутри поля ввода для красоты
        int padding = (int) dpToPx(20);
        input.setPadding(padding, padding, padding, padding);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.text_auto_124))
                .setView(input)
                .setPositiveButton(getString(R.string.text_auto_125), (d, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        loc.customName = newName;
                        titleView.setText(newName);

                        // Сбрасываем кэш иконки и перерисовываем маркеры на карте
                        loc.cachedIcon = null;
                        refreshSavedPoints();
                        scheduleSave();

                        // Прячем клавиатуру
                        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
                    }
                })
                .setNegativeButton(getString(R.string.text_auto_121), null)
                .show();
    }

    // Исправленный метод openGallery
    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Это подсказка системе показать именно внутреннее хранилище
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);

        // Запускаем через наш Launcher
        galleryLauncher.launch(intent);
    }
    // Регистратор для выбора картинки
    // 1. Объявляем лончер, который умеет принимать Intent
    private final androidx.activity.result.ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null && currentlyEditingLocation != null) {
                        String localPath = copyPhotoToPrivateStorage(uri);
                        if (localPath != null) {
                            currentlyEditingLocation.photoPaths.add(localPath);
                            currentlyEditingLocation.cachedIcon = null;
                            refreshSavedPoints();
                            showLocationCard(currentlyEditingLocation);
                        } else {
                            Toast.makeText(this, getString(R.string.text_auto_126), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
    );

    private void hideManualOverlays() {
        mapLibre.getStyle(style -> {
            // Прячем ручную точку
// В hideManualOverlays и clearMapOverlays:
            Layer marker = style.getLayer("marker-layer");
            Layer shadow = style.getLayer("marker-shadow");
            if (marker != null) marker.setProperties(PropertyFactory.visibility(Property.VISIBLE));
            if (shadow != null) shadow.setProperties(PropertyFactory.visibility(Property.VISIBLE));
            // Прячем границы районов
            if (style.getSource("boundary-source") != null) {
                style.removeLayer("boundary-layer");
                style.removeLayer("outline-layer");
                style.removeSource("boundary-source");
            }
        });
    }

    private void clearMapOverlays() {
        if (mapLibre == null) return;
        mapLibre.getUiSettings().setAllGesturesEnabled(true); // Включаем обратно

        mapLibre.getStyle(style -> {
            // 1. Скрываем указатель (пин)
            // В hideManualOverlays и clearMapOverlays:
            Layer marker = style.getLayer("marker-layer");
            Layer shadow = style.getLayer("marker-shadow");
            if (marker != null) marker.setProperties(PropertyFactory.visibility(Property.VISIBLE));
            if (shadow != null) shadow.setProperties(PropertyFactory.visibility(Property.VISIBLE));
            // 2. Убираем границы районов
            if (style.getSource("boundary-source") != null) {
                style.removeLayer("boundary-layer");
                style.removeLayer("outline-layer");
                style.removeSource("boundary-source");
            }
        });

        // 3. Прячем карточку информации
        if (sheetBehavior != null) {
            sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        }

        // Сбрасываем положение кнопки getString(R.string.text_auto_125)
        View saveBtn = findViewById(R.id.btnSaveLocation);
        if (saveBtn != null) {
            saveBtn.animate().translationY(0).setDuration(300).start();
        }
    }

    private void showMarkerAt(double lat, double lon) {
        mapLibre.getStyle(style -> {
            // ПЕРВЫМ ДЕЛОМ: Удаляем старые границы, если они были
            if (style.getSource("boundary-source") != null) {
                style.removeLayer("boundary-layer");
                style.removeLayer("outline-layer");
                style.removeSource("boundary-source");
            }

            String geoJson = "{ \"type\": \"FeatureCollection\", \"features\": [{ \"type\": \"Feature\", \"geometry\": { \"type\": \"Point\", \"coordinates\": [" + lon + ", " + lat + "] } }] }";
            org.maplibre.android.style.sources.GeoJsonSource markerSource = (org.maplibre.android.style.sources.GeoJsonSource) style.getSource("selected-point-source");

            if (markerSource != null) {
                markerSource.setGeoJson(geoJson);
            } else {
                style.addSource(new org.maplibre.android.style.sources.GeoJsonSource("selected-point-source", geoJson));
                // Слой тени
                style.addLayer(new org.maplibre.android.style.layers.CircleLayer("marker-shadow", "selected-point-source")
                        .withProperties(
                                org.maplibre.android.style.layers.PropertyFactory.circleColor(Color.parseColor("#FF9800")),
                                org.maplibre.android.style.layers.PropertyFactory.circleRadius(12f),
                                org.maplibre.android.style.layers.PropertyFactory.circleBlur(0.8f),
                                org.maplibre.android.style.layers.PropertyFactory.circleOpacity(0.6f)
                        ));
                // Основной слой
                style.addLayer(new org.maplibre.android.style.layers.CircleLayer("marker-layer", "selected-point-source")
                        .withProperties(
                                org.maplibre.android.style.layers.PropertyFactory.circleColor(Color.parseColor("#FF9800")),
                                org.maplibre.android.style.layers.PropertyFactory.circleRadius(8f),
                                org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor(Color.WHITE),
                                org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth(3f)
                        ));
            }

            // Гарантируем видимость точки (на случай если она была скрыта прошлым поиском района)
            org.maplibre.android.style.layers.Layer mainLayer = style.getLayer("marker-layer");
            if (mainLayer != null)
                mainLayer.setProperties(org.maplibre.android.style.layers.PropertyFactory.visibility(org.maplibre.android.style.layers.Property.VISIBLE));
        });
    }

    private void reverseSearch(double lat, double lon) {
        new Thread(() -> {
            try {
                String urlString = "https://nominatim.openstreetmap.org/reverse?format=json"
                        + "&lat=" + lat + "&lon=" + lon + "&zoom=18&addressdetails=1&accept-language=ru,en";

                URL url = new URL(urlString);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestProperty("User-Agent", "OzTrip_App");

                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                StringBuilder res = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) res.append(line);
                in.close();

                JSONObject result = new JSONObject(res.toString());
                String displayName = result.optString("display_name", getString(R.string.text_auto_127));
                String shortName = displayName.split(",")[0];
                String type = result.optString("type", "point"); // ТИП ТУТ
                JSONObject geojson = result.optJSONObject("geojson");

                runOnUiThread(() -> {
                    // 1. Центрируем камеру
                    // Замени строку в runOnUiThread внутри reverseSearch:
                    CameraPosition pos = new CameraPosition.Builder()
                            .target(new LatLng(lat, lon))
                            .zoom(15.5f) // Чуть ближе для деталей
                            .tilt(0)    // Легкий наклон для 3D эффекта
                            .build();

                    mapLibre.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 1200);

                    // 2. Определяем типы для карточки
                    String displayType = getString(R.string.text_auto_113);
                    // Внутри reverseSearch -> runOnUiThread
                    JSONObject address = result.optJSONObject("address");
                    String city = "";
                    String country = "";

                    if (address != null) {
                        // Nominatim может называть город по-разному: city, town, village или suburb
                        city = address.optString("city", address.optString("town", address.optString("village", address.optString("suburb", ""))));
                        country = address.optString("country", "");
                    }

// Формируем строку для нижней плашки (txtFlora)
                    String locationInfo = country;
                    if (!city.isEmpty()) {
                        locationInfo = city + ", " + country;
                    }
                    if (locationInfo.isEmpty()) locationInfo = getString(R.string.text_auto_128);


                    // Вызываем карточку (передаем locationInfo в параметр floraType)
                    showPremiumCard(shortName, displayType, locationInfo.toUpperCase(), lat, lon);


                    // 4. Рисуем границы, если есть
                    if (geojson != null) drawBoundary(geojson);

                    // 5. УПРАВЛЕНИЕ УКАЗАТЕЛЕМ (Теперь внутри потока UI и после получения type)
                    mapLibre.getStyle(style -> {
                        org.maplibre.android.style.layers.Layer mainLayer = style.getLayer("marker-layer");
                        org.maplibre.android.style.layers.Layer shadowLayer = style.getLayer("marker-shadow");

                        if (mainLayer != null && shadowLayer != null) {
                            if (type.matches(".*(administrative|suburb|district|city|town).*")) {
                                // Если это территория — скрываем точку
                                mainLayer.setProperties(org.maplibre.android.style.layers.PropertyFactory.visibility(org.maplibre.android.style.layers.Property.NONE));
                                shadowLayer.setProperties(org.maplibre.android.style.layers.PropertyFactory.visibility(org.maplibre.android.style.layers.Property.NONE));
                            } else {
                                // Если это объект — показываем точку
                                mainLayer.setProperties(org.maplibre.android.style.layers.PropertyFactory.visibility(org.maplibre.android.style.layers.Property.VISIBLE));
                                shadowLayer.setProperties(org.maplibre.android.style.layers.PropertyFactory.visibility(org.maplibre.android.style.layers.Property.VISIBLE));
                            }
                        }
                    });
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.text_auto_129), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void setupButtons() {


// Кнопка ПЛЮСИК (btnAddList)
        findViewById(R.id.btnAddList).setOnClickListener(v -> {
            Log.d("OzTrip1", getString(R.string.text_auto_130) + allLists.size());
            TravelList newList = new TravelList(getString(R.string.text_auto_131) + " " + (allLists.size() + 1));
            allLists.add(newList);
            Log.d("OzTrip1", getString(R.string.text_auto_132) + allLists.size());

            int newIndex = allLists.size() - 1;
            listAdapter.notifyItemInserted(newIndex);
            Log.d("OzTrip1", getString(R.string.text_auto_133) + newIndex);

            rvTravelLists.smoothScrollToPosition(newIndex);

            currentActiveList = newList;
            uniqueLocations = currentActiveList.locations;
            pathPoints = currentActiveList.pathPoints;

            listAdapter.setSelectedIndex(newIndex);

            refreshSavedPoints();
            scheduleSave();
            updateTravelCount();
        });
        // Поиск
        // Поиск – кастомный премиум-диалог
        View btnSearch = findViewById(R.id.btnSearch);
        if (btnSearch != null) {
            btnSearch.setOnClickListener(v -> {
                View dialogView = getLayoutInflater().inflate(R.layout.dialog_search, null);
                EditText etQuery = dialogView.findViewById(R.id.etSearchQuery);
                String hintText = getString(R.string.text_auto_134);
                SpannableString ss = new SpannableString(hintText);
                AbsoluteSizeSpan sizeSpan = new AbsoluteSizeSpan(14, true); // размер в sp
                ss.setSpan(sizeSpan, 0, ss.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                etQuery.setHint(new SpannedString(ss));

                AlertDialog searchDialog = new AlertDialog.Builder(this, R.style.PremiumDialogTheme)
                        .setView(dialogView)
                        .create();

                // Кнопка getString(R.string.text_auto_135)
                dialogView.findViewById(R.id.btnStartSearch).setOnClickListener(view -> {
                    String query = etQuery.getText().toString().trim();
                    if (!query.isEmpty()) {
                        searchDialog.dismiss();
                        searchLocation(query);
                    } else {
                        Toast.makeText(this, getString(R.string.text_auto_136), Toast.LENGTH_SHORT).show();
                    }
                });

                // Кнопка getString(R.string.text_auto_121)
                dialogView.findViewById(R.id.btnCancelSearch).setOnClickListener(view -> searchDialog.dismiss());

                // Поиск по нажатию Enter на клавиатуре
                etQuery.setOnEditorActionListener((textView, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                        String query = etQuery.getText().toString().trim();
                        if (!query.isEmpty()) {
                            searchDialog.dismiss();
                            searchLocation(query);
                        } else {
                            Toast.makeText(this, getString(R.string.text_auto_136), Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    }
                    return false;
                });

                searchDialog.show();
            });
        }
        View saveBtn = findViewById(R.id.btnSaveLocation);
        if (saveBtn != null) {
            saveBtn.setOnClickListener(v -> {
                LatLng currentPos = mapLibre.getCameraPosition().target;
                SavedLocation newLoc = new SavedLocation(currentPos);

                uniqueLocations.add(newLoc);
                pathPoints.add(currentPos);

                invalidateAiContext();   // чтобы ИИ увидел новую точку
                refreshSavedPoints();
                scheduleSave();
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);

                // Закрываем информационную карточку (если открыта)
                if (sheetBehavior != null && sheetBehavior.getState() != BottomSheetBehavior.STATE_HIDDEN) {
                    sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                }

                // Мгновенно открываем редактор для только что созданной точки
                showLocationCard(newLoc);
            });
        }

        // Кнопка закрытия карточки
        View btnClose = findViewById(R.id.btnCloseInfo);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                if (sheetBehavior != null) {
                    sheetBehavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN);
                }
            });
        }
        if (mapLibre != null) {
            mapLibre.getUiSettings().setAllGesturesEnabled(true); // Включаем обратно
        }
        // Кнопка Компаса (выравнивание на Север)
        // Внутри setupButtons()
        View fabCompass = findViewById(R.id.fabCompass); // Здесь можно оставить View
        if (fabCompass != null) {
            fabCompass.setOnClickListener(v -> {
                if (mapLibre != null) {
                    CameraPosition pos = new CameraPosition.Builder(mapLibre.getCameraPosition())
                            .bearing(0)
                            .tilt(0)
                            .build();
                    mapLibre.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 1000);
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                }
            });
            TooltipCompat.setTooltipText(fabCompass, "Компас: повернуть карту на север");
        }
// Находим нашу новую оранжевую кнопку по ID
        final ImageView btnAction = findViewById(R.id.fabAction);

        if (btnAction != null) {
            btnAction.setOnClickListener(v -> {
                if (!hasLocationPermission()) {
                    Toast.makeText(this, "Нет доступа к геолокации", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (mapLibre != null && mapLibre.getLocationComponent() != null
                        && mapLibre.getLocationComponent().isLocationComponentActivated()
                        && mapLibre.getLocationComponent().getLastKnownLocation() != null) {

                    // 1. АНИМАЦИЯ НАЖАТИЯ
                    btnAction.animate().scaleX(0.8f).scaleY(0.8f).setDuration(150)
                            .withEndAction(() -> btnAction.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start());

                    // 2. ВИБРАЦИЯ
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);

                    // 3. ПОЛЕТ КАМЕРЫ
                    android.location.Location loc = mapLibre.getLocationComponent().getLastKnownLocation();
                    CameraPosition pos = new CameraPosition.Builder()
                            .target(new LatLng(loc.getLatitude(), loc.getLongitude()))
                            .zoom(16f)
                            .bearing(0)
                            .tilt(0)
                            .build();

                    mapLibre.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 2000);

                    Toast.makeText(this, getString(R.string.text_auto_137), Toast.LENGTH_SHORT).show();

                } else {
                    Toast.makeText(this, getString(R.string.text_auto_138), Toast.LENGTH_SHORT).show();
                }
            });
            btnAction.setTooltipText("Переместить карту к моему местоположению");
        }

// 1. Находим кнопку ОДИН РАЗ
        final ImageView btnTarget = findViewById(R.id.fabTarget);

        if (btnTarget != null) {
            btnTarget.setOnClickListener(v -> {
                if (mapLibre != null) {
                    // 1. ЭФФЕКТ: Анимация кнопки (упругий клик)
                    btnTarget.animate().scaleX(0.85f).scaleY(0.85f).setDuration(100)
                            .withEndAction(() -> btnTarget.animate().scaleX(1.0f).scaleY(1.0f).start());

                    // 2. Вибрация
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);

                    // 3. ПРЯМОЙ ПОЛЕТ К БАЗЕ (homeLatLng)
                    // Мы убираем проверку GPS, чтобы кнопка всегда вела в одну точку
                    CameraPosition position = new CameraPosition.Builder()
                            .target(homeLatLng) // Твои координаты 40.1792, 44.5134
                            .zoom(15.5f)
                            .bearing(0)
                            .tilt(0)
                            .build();

                    mapLibre.animateCamera(CameraUpdateFactory.newCameraPosition(position), 2000);

                    Toast.makeText(this, getString(R.string.text_auto_139), Toast.LENGTH_SHORT).show();
                }
            });
            // Кнопка "местоположение объекта"
            btnAction.setTooltipText("Переместить карту к местоположению объекта");
        }


// Находим элементы


// Находим элементы
        final View centerMarker = findViewById(R.id.centerMarker);
        final ImageView imgToggle = findViewById(R.id.fabToggleMarker); // ImageView!

        if (imgToggle != null && centerMarker != null) {
            // 1. НАСТРОЙКА НАЧАЛЬНОГО СОСТОЯНИЯ (Default)
            centerMarker.setVisibility(View.VISIBLE);

            // Очищаем фильтры, ставим открытый глаз, кнопка яркая
            imgToggle.setImageResource(R.drawable.ic_btn_eye_open);
            imgToggle.clearColorFilter();
            imgToggle.setColorFilter(Color.parseColor("#757575"));
            imgToggle.setAlpha(1.0f);


            // 2. ОБРАБОТЧИК КЛИКА
            imgToggle.setOnClickListener(v -> {
                if (centerMarker.getVisibility() == View.VISIBLE) {
                    // СКРЫВАЕМ МЕТКУ С АНИМАЦИЕЙ
                    centerMarker.animate()
                            .alpha(0f)
                            .scaleX(0.5f)
                            .scaleY(0.5f)
                            .setDuration(200)
                            .withEndAction(() -> centerMarker.setVisibility(View.GONE));

                    // --- ЭФФЕКТ getString(R.string.text_auto_140) И ПЕРЕЧЕРКИВАНИЯ КНОПКИ ---
                    // Анимация подмены иконки (сжатие и возврат)
                    imgToggle.animate().scaleX(0.8f).scaleY(0.8f).setDuration(100)
                            .withEndAction(() -> {
                                // Ставим перечеркнутый глаз
                                imgToggle.setImageResource(R.drawable.ic_btn_eye_closed);

                                // Применяем серый фильтр, чтобы кнопка getString(R.string.text_auto_141)
                                imgToggle.setColorFilter(Color.parseColor("#757575"));
                                imgToggle.setAlpha(0.6f);

                                imgToggle.animate().scaleX(1.0f).scaleY(1.0f).start();
                            });

                    v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);

                    Toast.makeText(this, getString(R.string.text_auto_142), Toast.LENGTH_SHORT).show();

                } else {
                    // ПОКАЗЫВАЕМ МЕТКУ
                    centerMarker.setVisibility(View.VISIBLE);
                    centerMarker.setScaleX(0.5f);
                    centerMarker.setScaleY(0.5f);
                    centerMarker.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(250);

                    // --- ЭФФЕКТ getString(R.string.text_auto_143) И ОТКРЫТИЯ КНОПКИ ---
                    // Анимация вспышки (увеличение и возврат)
                    imgToggle.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150)
                            .withEndAction(() -> {
                                // Ставим открытый глаз
                                imgToggle.setImageResource(R.drawable.ic_btn_eye_open);

                                // Убираем серый фильтр, возвращая яркий оранжевый
                                imgToggle.clearColorFilter();
                                imgToggle.setAlpha(1.0f);

                                imgToggle.animate().scaleX(1.0f).scaleY(1.0f).start();
                            });

                    v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);

                    Toast.makeText(this, getString(R.string.text_auto_144), Toast.LENGTH_SHORT).show();
                }
            });
            imgToggle.setTooltipText("Скрыть или показать центральную метку");
        }


    }
    public LiquidSegmentedControl getLiquidNav() {
        return liquidNav;
    }
    public void createTrip(String name) {
        TravelList newList = new TravelList(name);
        allLists.add(newList);
        currentActiveList = newList;
        uniqueLocations = newList.locations;
        pathPoints = newList.pathPoints;
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
            listAdapter.setSelectedIndex(allLists.size() - 1);
        }
        refreshSavedPoints();
        scheduleSave();
        updateTravelCount();
        invalidateAiContext();
    }

    public void renameTrip(String oldName, String newName) {
        for (TravelList list : allLists) {
            if (list.name.equals(oldName)) {
                list.name = newName;
                if (listAdapter != null) listAdapter.notifyDataSetChanged();
                scheduleSave();
                invalidateAiContext();
                break;
            }
        }
    }

    public void deleteTrip(String name) {
        for (int i = 0; i < allLists.size(); i++) {
            if (allLists.get(i).name.equals(name)) {
                deleteTravelList(i);
                invalidateAiContext();
                break;
            }
        }
    }

    public void addPoint(double lat, double lng, String name) {
        LatLng point = new LatLng(lat, lng);
        SavedLocation loc = new SavedLocation(point);
        loc.customName = name;
        uniqueLocations.add(loc);
        pathPoints.add(point);
        refreshSavedPoints();
        scheduleSave();
        invalidateAiContext();
    }

    public void buildRoute(List<LatLng> points) {
        pathPoints.clear();
        pathPoints.addAll(points);
        uniqueLocations.clear();
        for (LatLng p : points) {
            SavedLocation loc = new SavedLocation(p);
            uniqueLocations.add(loc);
        }
        currentActiveList.locations = new ArrayList<>(uniqueLocations);
        currentActiveList.pathPoints = new ArrayList<>(pathPoints);
        refreshSavedPoints();
        scheduleSave();
        invalidateAiContext();
    }
    private String copyPhotoToPrivateStorage(Uri sourceUri) {
        try {
            // Папка для фото в приватном хранилище
            File photoDir = new File(getFilesDir(), "photos");
            if (!photoDir.exists()) photoDir.mkdirs();

            // Генерируем уникальное имя
            String fileName = System.currentTimeMillis() + ".jpg";
            File destFile = new File(photoDir, fileName);

            // Копируем содержимое
            InputStream in = getContentResolver().openInputStream(sourceUri);
            OutputStream out = new FileOutputStream(destFile);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            in.close();
            out.close();

            // Возвращаем абсолютный путь (file://...)
            return destFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e("MarkerPhoto", getString(R.string.text_auto_145), e);
            return null;
        }
    }
    public void showRenameDialog(int position, String oldName, @Nullable Runnable onRenamed) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_rename, null);
        EditText etName = dialogView.findViewById(R.id.etNewName);
        etName.setText(oldName);
        etName.setSelection(oldName.length());

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.PremiumDialogTheme)
                .setView(dialogView)
                .create();

        // Кнопка Сохранить
        dialogView.findViewById(R.id.btnSave).setOnClickListener(v -> {
            String newName = etName.getText().toString().trim();
            if (!newName.isEmpty()) {
                allLists.get(position).name = newName;
                if (listAdapter != null) listAdapter.notifyItemChanged(position);
                scheduleSave();
                updateTravelCount();
                if (onRenamed != null) onRenamed.run();
            }
            dialog.dismiss();
        });

        // Кнопка Отмена
        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        // Кнопка Удалить – показываем стильный диалог подтверждения
        dialogView.findViewById(R.id.btnDelete).setOnClickListener(v -> {
            dialog.dismiss();
            showConfirmDeleteDialog(position, oldName);
        });

        dialog.show();

        etName.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(etName, InputMethodManager.SHOW_IMPLICIT);
    }
    private void showConfirmDeleteDialog(int position, String listName) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_confirm_delete, null);
        TextView tvMessage = dialogView.findViewById(R.id.tvConfirmMessage);
        tvMessage.setText(getString(R.string.text_auto_146) + listName + getString(R.string.text_auto_147));

        AlertDialog confirmDialog = new AlertDialog.Builder(this, R.style.PremiumDialogTheme)
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.btnConfirmDelete).setOnClickListener(v -> {
            confirmDialog.dismiss();
            deleteTravelList(position);
        });

        dialogView.findViewById(R.id.btnConfirmCancel).setOnClickListener(v -> confirmDialog.dismiss());

        confirmDialog.show();
    }
    private void deleteTravelList(int position) {
        TravelList listToDelete = allLists.get(position);

        // 1. Удаляем локальные фото, связанные с точками этого списка
        deletePhotosFromList(listToDelete);

        // 2. Удаляем список из памяти
        allLists.remove(position);

        // 3. Если удалённый список был активным, переключаемся на первый доступный
        if (currentActiveList == listToDelete) {
            if (!allLists.isEmpty()) {
                currentActiveList = allLists.get(0);
            } else {
                // Если не осталось списков, создаём новый по умолчанию
                TravelList defaultList = new TravelList(getString(R.string.text_auto_109));
                allLists.add(defaultList);
                currentActiveList = defaultList;
            }
            uniqueLocations = currentActiveList.locations;
            pathPoints = currentActiveList.pathPoints;
            invalidateAiContext();
        }

        // 4. Обновляем адаптер и карту
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
        refreshSavedPoints();
        updateTravelCount();
        // 5. Сохраняем изменения локально и в облаке
        scheduleSave();

        Toast.makeText(this, getString(R.string.text_auto_148), Toast.LENGTH_SHORT).show();
    }
    private void deletePhotosFromList(TravelList list) {
        if (list == null || list.locations == null) return;
        for (SavedLocation loc : list.locations) {
            if (loc.photoPaths != null) {
                for (String photoPath : loc.photoPaths) {
                    if (photoPath != null && !photoPath.isEmpty()) {
                        File file = new File(photoPath);
                        if (file.exists()) {
                            file.delete();
                        }
                    }
                }
            }
        }
    }
    private void addSavedMarker(LatLng point) {
        if (mapLibre == null) return;

        // 1. Создаем временный объект для этой точки
        SavedLocation tempLoc = new SavedLocation(point);
        tempLoc.level = 1; // Устанавливаем уровень вручную

        // 2. Теперь передаем ОБЪЕКТ в метод отрисовки
        org.maplibre.android.annotations.Icon premiumIcon = createPremiumMarker(tempLoc);

        mapLibre.addMarker(new org.maplibre.android.annotations.MarkerOptions()
                .position(point)
                .icon(premiumIcon));
    }

    private void searchLocation(String query) {
        new Thread(() -> {
            try {
                // 1. Правильно кодируем запрос, чтобы пробелы не ломали URL
                String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
                String urlString = "https://nominatim.openstreetmap.org/search?q=" + encodedQuery
                        + "&format=json&polygon_geojson=1&limit=1&accept-language=ru,en";

                URL url = new URL(urlString);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();

                // 2. Обязательные заголовки, чтобы сервер нас не заблокировал
                con.setRequestProperty("User-Agent", "OzTrip_App");
                con.setRequestProperty("Accept-Language", "ru,en");

                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                StringBuilder res = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) res.append(line);
                in.close();

                JSONArray results = new JSONArray(res.toString());

                if (results.length() > 0) {
                    JSONObject place = results.getJSONObject(0);
                    double lat = place.getDouble("lat");
                    double lon = place.getDouble("lon");
                    String fullDisplayName = place.optString("display_name", "");
                    String shortName = fullDisplayName.split(",")[0];
                    String type = place.optString("type", "Point");
                    JSONObject geojson = place.optJSONObject("geojson");

                    runOnUiThread(() -> {
                        isSearching = true;
                        // Плавный полет к найденной цели
                        mapLibre.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lon), 15), 2500,
                                new org.maplibre.android.maps.MapLibreMap.CancelableCallback() { // ИСПОЛЬЗУЙ ЭТОТ ПУТЬ
                                    @Override
                                    public void onFinish() {
                                        // Даем пользователю 2 секунды осмотреться, прежде чем магнит снова оживет
                                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                            isSearching = false;
                                        }, 2000);
                                    }

                                    @Override
                                    public void onCancel() {
                                        isSearching = false;
                                    }
                                });

                        if (geojson != null) drawBoundary(geojson);

                        // Определяем тип места для карточки
                        String displayType = getString(R.string.text_auto_149);

                        if (type != null && !type.isEmpty()) {
                            String t = type.toLowerCase();

                            // --- ЛОГИКА ДЛЯ ПОДЗАГОЛОВКА (displayType) ---
                            if (t.matches(".*(city|town|administrative|suburb).*")) {
                                displayType = getString(R.string.text_auto_150);
                            } else if (t.equals("village") || t.equals("hamlet")) {
                                displayType = getString(R.string.text_auto_151);
                            } else if (t.contains("park") || t.contains("forest") || t.contains("wood") || t.equals("garden")) {
                                displayType = getString(R.string.text_auto_152);
                            } else if (t.matches(".*(peak|mountain|volcano|hill).*")) {
                                displayType = getString(R.string.text_auto_153);
                            } else if (t.matches(".*(water|river|lake|reservoir|canal).*")) {
                                displayType = getString(R.string.text_auto_154);
                            } else if (t.matches(".*(monastery|church|castle|fortress|ruins).*")) {
                                displayType = getString(R.string.text_auto_155);
                            }



                        }
                        // Внутри searchLocation -> runOnUiThread
                        String[] parts = fullDisplayName.split(",");
                        String countryCity = "";

                        if (parts.length >= 2) {
                            // Берем последние два элемента (обычно это Город и Страна)
                            String last1 = parts[parts.length - 1].trim(); // Страна
                            String last2 = parts[parts.length - 2].trim(); // Город / Область
                            countryCity = last2 + ", " + last1;
                        } else {
                            countryCity = fullDisplayName;
                        }

                        showPremiumCard(shortName, displayType, countryCity.toUpperCase(), lat, lon);

                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, getString(R.string.text_auto_156), Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.text_auto_157), Toast.LENGTH_SHORT).show());
            }
        }).start();

    }

    private void showPremiumCard(String name, String subtitle, String type, double lat, double lon) {
        TextView title = findViewById(R.id.infoTitle);
        TextView desc = findViewById(R.id.infoDescription);
        ImageView img = findViewById(R.id.infoImage);
        TextView txtTemp = findViewById(R.id.txtTemp);
        TextView txtHeight = findViewById(R.id.txtHeight);
        TextView txtFlora = findViewById(R.id.txtFlora);
        // ПРИНУДИТЕЛЬНО ПОКАЗЫВАЕМ КНОПКУ ПЕРЕД ОТКРЫТИЕМ КАРТОЧКИ
        View saveBtn = findViewById(R.id.btnSaveLocation);
        if (saveBtn != null) {
            saveBtn.setVisibility(View.VISIBLE);
            saveBtn.setAlpha(1f);
        }


        if (sheetBehavior != null) {
            // Принудительно сообщаем BottomSheetBehaviour актуальную высоту
            sheetBehavior.setPeekHeight(450);
            sheetBehavior.setPeekHeight(450);

            sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            infoCard.requestLayout();  // гарантируем пересчёт лэйаута
            // ВОТ ЭТА СТРОЧКА:
            infoCard.bringToFront();

            boolean isDark = (getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

            if (infoCard instanceof MaterialCardView) {
                ((MaterialCardView) infoCard).setCardBackgroundColor(
                        isDark ? Color.parseColor("#1E1E1E") : Color.parseColor("#E6212121"));
            }
            if (title != null) title.setTextColor(isDark ? Color.WHITE : Color.parseColor("#FFFFFF"));
            if (desc != null) desc.setTextColor(isDark ? Color.parseColor("#B0B0B0") : Color.parseColor("#CCFFFFFF"));
// txtFlora, txtTemp, txtHeight можно оставить как есть
        }



        // 2. Заполняем тексты
        if (title != null) title.setText(name);
        if (desc != null) desc.setText("📍 " + subtitle);
        if (txtFlora != null) {
            txtFlora.setText(type);
            txtFlora.setSelected(true); // Это запустит бегущую строку (marquee), если настроено в XML
        };


        // Высота (рандом для красоты или можно взять из API)
        if (txtHeight != null) txtHeight.setText("--m");
        // Внутри метода, где мы фиксируем локацию:
        this.homeLatLng = new LatLng(lat, lon);
        // 3. Спутниковый снимок (Яндекс.Карты)
        String hdUrl = "https://static-maps.yandex.ru/1.x/?ll=" + lon + "," + lat + "&z=16&l=sat&size=600,400";
        Glide.with(this).load(hdUrl).centerCrop().into(img);

        // 4. Погода
        if (txtTemp != null) fetchWeatherForCard(lat, lon, txtTemp);
    }

    // Добавь этот метод вниз класса
    private void fetchWeatherForCard(double lat, double lon, TextView tempView) {

        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&current_weather=true";



        new Thread(() -> {

            try {

                java.net.URL obj = new java.net.URL(url);

                java.util.Scanner s = new java.util.Scanner(obj.openStream()).useDelimiter("\\A");

                String result = s.hasNext() ? s.next() : "";



                JSONObject json = new JSONObject(result);

                double temp = json.getJSONObject("current_weather").getDouble("temperature");



                runOnUiThread(() -> {

                    tempView.setText((int)temp + "°C");



// Получаем реальную высоту из JSON (Open-Meteo это умеет)

                    double elevation = json.optDouble("elevation", 0);

                    TextView heightView = findViewById(R.id.txtHeight);

                    if (heightView != null) heightView.setText((int)elevation + "m");});

            } catch (Exception e) {

                runOnUiThread(() -> tempView.setText("??°C"));

            }

        }).start();

    }



    private void refreshSavedPoints() {
        if (mapLibre == null) return;
        mapLibre.clear();

        // Сбрасываем кэш иконок перед перерисовкой

        for (SavedLocation loc : uniqueLocations) {
            mapLibre.addMarker(new org.maplibre.android.annotations.MarkerOptions()
                    .position(loc.latLng)
                    .icon(createPremiumMarker(loc)));
        }

        if (pathPoints.size() > 1) {
            mapLibre.addPolyline(new org.maplibre.android.annotations.PolylineOptions()
                    .addAll(pathPoints)
                    .color(android.graphics.Color.parseColor("#A62C2C2C"))
                    .width(dpToPx(2)));
        }

        // В конце refreshSavedPoints()
        View pb = findViewById(R.id.pbMapLoading);
        if (pb != null) pb.setVisibility(View.GONE);
    }

    private void drawBoundary(JSONObject geojson) {

        mapLibre.getStyle(style -> {

// 1. ПРЯЧЕМ УКАЗАТЕЛЬ (Точку), так как теперь смотрим на район

            org.maplibre.android.style.layers.Layer marker = style.getLayer("marker-layer");

            org.maplibre.android.style.layers.Layer shadow = style.getLayer("marker-shadow");

            if (marker != null) marker.setProperties(org.maplibre.android.style.layers.PropertyFactory.visibility(org.maplibre.android.style.layers.Property.NONE));

            if (shadow != null) shadow.setProperties(org.maplibre.android.style.layers.PropertyFactory.visibility(org.maplibre.android.style.layers.Property.NONE));



// 2. РИСУЕМ ГРАНИЦЫ

            if (style.getSource("boundary-source") != null) {

                style.removeLayer("boundary-layer");

                style.removeLayer("outline-layer");

                style.removeSource("boundary-source");

            }

            style.addSource(new org.maplibre.android.style.sources.GeoJsonSource("boundary-source", geojson.toString()));

            style.addLayer(new org.maplibre.android.style.layers.FillLayer("boundary-layer", "boundary-source")

                    .withProperties(org.maplibre.android.style.layers.PropertyFactory.fillColor(Color.parseColor("#33FF9800"))));

            style.addLayer(new org.maplibre.android.style.layers.LineLayer("outline-layer", "boundary-source")

                    .withProperties(org.maplibre.android.style.layers.PropertyFactory.lineColor(Color.parseColor("#FF9800")),

                            org.maplibre.android.style.layers.PropertyFactory.lineWidth(2f)));

        });

    }



// Этот метод сработает, когда ты пишешь dpToPx(52)

    private int dpToPx(int dp) {

        return (int) (dp * getResources().getDisplayMetrics().density);

    }



// Этот метод сработает, когда ты пишешь dpToPx(1.5f)

    private float dpToPx(float dp) {

        return dp * getResources().getDisplayMetrics().density;

    }




    @SuppressWarnings({"MissingPermission"})
    private void enableLocation(Style style) {
        // Проверяем, есть ли разрешение
        if (androidx.core.app.ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {

            // Запрашиваем разрешение у пользователя
            androidx.core.app.ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    1001);
            return; // Выходим, не включая геолокацию
        }

        LocationComponent locationComponent = mapLibre.getLocationComponent();
        locationComponent.activateLocationComponent(
                LocationComponentActivationOptions.builder(this, style).build());
        locationComponent.setLocationComponentEnabled(true);
        locationComponent.setRenderMode(org.maplibre.android.location.modes.RenderMode.COMPASS);
    }

    private void saveAllData() {
        try {
            SharedPreferences prefs = getSharedPreferences("OzTripPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            // Важно: Мы сохраняем ВЕСЬ список allLists, в котором лежат все поездки
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(LatLng.class, new LatLngAdapter())
                    .create();

            String json = gson.toJson(allLists);
            String key = getStorageKey();
            editor.putString(key, json);
            editor.apply();

            android.util.Log.d("OzTrip", getString(R.string.text_auto_158) + allLists.size());
        } catch (Exception e) {
            android.util.Log.e("OzTrip", getString(R.string.text_auto_111) + e.getMessage());
        }
    }

    private void loadAllData() {
        SharedPreferences prefs = getSharedPreferences("OzTripPrefs", MODE_PRIVATE);
        String key = getStorageKey();
        String json = prefs.getString(key, null);

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(LatLng.class, new LatLngAdapter())
                .create();

        if (json != null && !json.isEmpty()) {
            Type type = new TypeToken<ArrayList<TravelList>>(){}.getType();
            ArrayList<TravelList> loadedData = gson.fromJson(json, type);
            if (loadedData != null && !loadedData.isEmpty()) {
                allLists.clear();
                allLists.addAll(loadedData);
                removeDuplicateLists();
                Collections.sort(allLists, (a, b) -> a.name.compareToIgnoreCase(b.name));
            } else {
                allLists.clear();
                allLists.add(new TravelList(getString(R.string.text_auto_159)));
            }
        }

        // Если после загрузки всё равно пусто (первый запуск)
        if (allLists == null || allLists.isEmpty()) {
            allLists = new ArrayList<>();
            allLists.add(new TravelList(getString(R.string.text_auto_159)));
        }

        // ВСЕГДА привязываем текущую активную поездку к первой в списке
        currentActiveList = allLists.get(0);

        // ПРИВЯЗКА РАБОЧИХ СПИСКОВ (Чтобы всё, что ты добавляешь, шло внутрь TravelList)
        if (currentActiveList.pathPoints == null) currentActiveList.pathPoints = new ArrayList<>();
        if (currentActiveList.locations == null) currentActiveList.locations = new ArrayList<>();

        pathPoints = currentActiveList.pathPoints;
        uniqueLocations = currentActiveList.locations;
    }



    @Override protected void onStart() { super.onStart(); if (mapView != null) mapView.onStart(); }

    @Override
    protected void onResume() {
        super.onResume();
        String lang = getSharedPreferences("OzTripPrefs", MODE_PRIVATE).getString("language", "ru");
        if (!lang.equals(currentLanguage)) {
            // Пересоздаём, если язык изменился
            recreate();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
        saveAllData(); // Сохраняем всё перед выходом
    }


    @Override protected void onStop() { super.onStop(); if (mapView != null) mapView.onStop(); }

    @Override protected void onSaveInstanceState(@NonNull Bundle outState) { super.onSaveInstanceState(outState); if (mapView != null) mapView.onSaveInstanceState(outState); }

    @Override public void onLowMemory() { super.onLowMemory(); if (mapView != null) mapView.onLowMemory(); }

    @Override protected void onDestroy() { super.onDestroy(); if (mapView != null) mapView.onDestroy(); }

}

