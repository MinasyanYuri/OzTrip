package com.example.oztrip;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import java.util.ArrayList;
import java.util.List;

public class LiquidSegmentedControl extends View {
    private float bgWidth;   // ширина видимого фона (300dp)
    private float bgLeft;    // отступ слева для центрирования
    private Paint bgPaint, sliderPaint, textPaint, strokePaint;
    private RectF bgRect, sliderRect;
    private List<String> tabs = new ArrayList<>();
    private int selectedIndex = 0;
    private float sliderPosition = 0; // 0.0 to tabs.size() - 1
    private ValueAnimator animator;
    private OnTabSelectedListener listener;

    public interface OnTabSelectedListener {
        void onTabSelected(int index);
    }
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        bgWidth = dpToPx(300);
        bgLeft = (w - bgWidth) / 2f;
    }
    public LiquidSegmentedControl(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // 1. Фон "Стекло" (полупрозрачный белый с градиентом)
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(Color.parseColor("#B3FFFFFF")); // 70% белый

        // 2. Обводка "Грань стекла" (тонкая белая)
        strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2f);
        strokePaint.setColor(Color.parseColor("#E6FFFFFF")); // 90% белый

        // 3. Активный ползунок "Жидкая капля" (белый с мягкой тенью)
        sliderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        sliderPaint.setStyle(Paint.Style.FILL);
        sliderPaint.setColor(Color.WHITE);
        sliderPaint.setShadowLayer(15f, 0f, dpToPx(2), Color.parseColor("#26000000")); // Мягкая тень

        // 4. Текст (серый/оранжевый)
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(dpToPx(16));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        bgRect = new RectF();
        sliderRect = new RectF();

        // Тестовые вкладки
        tabs.add("Карта");
        tabs.add("Ии");


        setLayerType(LAYER_TYPE_SOFTWARE, null); // Для теней на старых Android
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float h = getHeight();

        // Сдвигаем рисование на bgLeft, чтобы фон был по центру
        canvas.save();
        canvas.translate(bgLeft, 0);

        float w = bgWidth;      // работаем с шириной фона
        float r = h / 2f;

        // --- 1. Рисуем фон стекла (только внутри 300dp) ---
        bgRect.set(0, 0, w, h);
        LinearGradient glassShader = new LinearGradient(0, 0, 0, h,
                new int[]{Color.parseColor("#E6FFFFFF"), Color.parseColor("#B3FFFFFF")},
                null, Shader.TileMode.CLAMP);
        bgPaint.setShader(glassShader);
        canvas.drawRoundRect(bgRect, r, r, bgPaint);
        canvas.drawRoundRect(bgRect, r, r, strokePaint);

        // --- 2. Рисуем активный ползунок ---
        float tabWidth = w / tabs.size();
        float sliderPadding = dpToPx(6f);   // используем float-версию dpToPx

        float sliderLeft = sliderPadding + (sliderPosition * tabWidth);
        float sliderRight = (sliderPadding + tabWidth) + (sliderPosition * tabWidth) - (sliderPadding * 2);
        sliderRect.set(sliderLeft, sliderPadding, sliderRight, h - sliderPadding);

        float currentAnimValue = animator != null ? (float) animator.getAnimatedValue() : 1f;
        canvas.drawRoundRect(sliderRect, r - sliderPadding, r - sliderPadding, sliderPaint);

        // --- 3. Рисуем текст ---
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = h / 2f - (fm.descent + fm.ascent) / 2f;
        ArgbEvaluator colorEval = new ArgbEvaluator();

        for (int i = 0; i < tabs.size(); i++) {
            float tabCenterX = (tabWidth * i) + (tabWidth / 2f);
            float ratio = 1.0f - Math.abs(sliderPosition - i);
            if (ratio < 0) ratio = 0;
            int textColor = (int) colorEval.evaluate(ratio, Color.parseColor("#666666"), Color.parseColor("#FF9800"));
            textPaint.setColor(textColor);
            canvas.drawText(tabs.get(i), tabCenterX, textY, textPaint);
        }

        canvas.restore();  // восстанавливаем исходное состояние canvas
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX() - bgLeft;   // переводим в координаты фона
            if (x < 0 || x > bgWidth) return false; // игнорируем касания вне фона

            float tabWidth = bgWidth / tabs.size();
            int clickedIndex = (int) (x / tabWidth);
            if (clickedIndex != selectedIndex && clickedIndex < tabs.size()) {
                setSelectedIndex(clickedIndex);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    public void setSelectedIndex(int index) {
        if (animator != null) animator.cancel();
        selectedIndex = index;

        // Анимация Liquid перемещения с эффектом Overshoot
        animator = ValueAnimator.ofFloat(sliderPosition, index);
        animator.setDuration(450);
        animator.setInterpolator(new OvershootInterpolator(1.4f)); // "Резиновый" эффект
        animator.addUpdateListener(animation -> {
            sliderPosition = (float) animation.getAnimatedValue();
            invalidate(); // Перерисовать кадр
        });
        animator.start();

        if (listener != null) listener.onTabSelected(index);
    }

    public void setOnTabSelectedListener(OnTabSelectedListener listener) {
        this.listener = listener;
    }

    private float dpToPx(int dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
    private float dpToPx(float dp) { return dp * getResources().getDisplayMetrics().density; }
}