package com.example.oztrip;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class OnboardingCoachMark extends FrameLayout {
    private final List<Step> steps = new ArrayList<>();
    private int currentStep = 0;
    private final SharedPreferences prefs;
    private View targetView;
    private final RectF targetRect = new RectF();
    private final Paint dimPaint;
    private final Paint clearPaint;
    private LinearLayout tipLayout;
    private TextView tipText;
    private Button btnNext;
    private Button btnSkip;
    private final int tipMargin = 24;

    public static class Step {
        final int viewId;
        final String description;

        public Step(int viewId, String description) {
            this.viewId = viewId;
            this.description = description;
        }
    }

    public OnboardingCoachMark(Context context, List<Step> steps) {
        super(context);
        this.steps.addAll(steps);
        prefs = context.getSharedPreferences("OzTripPrefs", Context.MODE_PRIVATE);
        setWillNotDraw(false);

        dimPaint = new Paint();
        dimPaint.setColor(0xCC000000); // полупрозрачный чёрный
        clearPaint = new Paint();
        clearPaint.setColor(Color.TRANSPARENT);
        clearPaint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));

        // Создаём подсказку
        tipLayout = (LinearLayout) LayoutInflater.from(context)
                .inflate(R.layout.onboarding_tip, this, false);
        tipText = tipLayout.findViewById(R.id.tipText);
        btnNext = tipLayout.findViewById(R.id.btnNext);
        btnSkip = tipLayout.findViewById(R.id.btnSkip);
        addView(tipLayout);
        tipLayout.setVisibility(GONE);

        btnNext.setOnClickListener(v -> {
            currentStep++;
            showStep(currentStep);
        });
        btnSkip.setOnClickListener(v -> finish());
    }

    public void start() {
        if (prefs.getBoolean("onboarding_complete", false)) return;
        if (steps.isEmpty()) return;
        showStep(0);
    }

    private void showStep(int index) {
        if (index >= steps.size()) {
            finish();
            return;
        }
        Step step = steps.get(index);
        targetView = ((View) getParent()).findViewById(step.viewId);

        // Проверяем, что view существует и видима (имеет размер)
        if (targetView == null || targetView.getWidth() == 0 || targetView.getHeight() == 0) {
            // Пропускаем этот шаг и переходим к следующему
            currentStep++;
            showStep(currentStep);
            return;
        }

        tipText.setText(step.description);
        tipLayout.setVisibility(VISIBLE);
        positionTip();      // пересчитываем позицию подсказки и окошка
        invalidate();       // перерисовываем затемнение с новым окошком
    }

    private void positionTip() {
        targetView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                targetView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                // Получаем прямоугольник видимой области targetView на экране
                android.graphics.Rect rect = new android.graphics.Rect();
                targetView.getGlobalVisibleRect(rect);
                targetRect.set(rect);

                // Измеряем подсказку
                tipLayout.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
                int tipW = tipLayout.getMeasuredWidth();
                int tipH = tipLayout.getMeasuredHeight();

                int screenW = getWidth();
                int x = (int) (targetRect.centerX() - tipW / 2);
                if (x < 0) x = 0;
                if (x + tipW > screenW) x = screenW - tipW;

                int y = (int) targetRect.top - tipH - tipMargin;
                if (y < 0) {
                    y = (int) targetRect.bottom + tipMargin;
                }

                FrameLayout.LayoutParams params = (LayoutParams) tipLayout.getLayoutParams();
                params.leftMargin = x;
                params.topMargin = y;
                tipLayout.setLayoutParams(params);
                invalidate();
            }
        });
    }

    private void finish() {
        prefs.edit().putBoolean("onboarding_complete", true).apply();
        if (getParent() instanceof ViewGroup) {
            ((ViewGroup) getParent()).removeView(this);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Затемняем весь экран
        canvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);
        // Вырезаем окошко вокруг целевого элемента
        if (targetView != null && !targetRect.isEmpty()) {
            canvas.drawRoundRect(targetRect, 16, 16, clearPaint);
        }
    }
}