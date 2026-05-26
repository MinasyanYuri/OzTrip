package com.example.oztrip;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class StepByStepOnboarding {
    private final Context context;
    private final List<Step> steps;
    private int currentStep = 0;
    private PopupWindow popupWindow;
    private final View rootView;
    private Drawable originalBackground;
    private View currentHighlightedView;

    public static class Step {
        final int viewId;
        final String description;

        public Step(int viewId, String description) {
            this.viewId = viewId;
            this.description = description;
        }
    }

    public StepByStepOnboarding(Context context, View rootView, List<Step> steps) {
        this.context = context;
        this.rootView = rootView;
        this.steps = steps;
    }

    public void start() {
        if (steps.isEmpty()) return;
        showStep(0);
    }

    private void showStep(int index) {
        dismissPopupAndHighlight();

        if (index >= steps.size()) {
            finishOnboarding();
            return;
        }

        Step step = steps.get(index);
        View targetView = rootView.findViewById(step.viewId);
        if (targetView == null) {
            currentStep = index + 1;
            showStep(currentStep);
            return;
        }

        originalBackground = targetView.getBackground();
        currentHighlightedView = targetView;
        targetView.setBackgroundResource(R.drawable.onboarding_highlight);

        LinearLayout tipLayout = (LinearLayout) LayoutInflater.from(context)
                .inflate(R.layout.onboarding_tip, null);
        TextView tipText = tipLayout.findViewById(R.id.tipText);
        Button btnNext = tipLayout.findViewById(R.id.btnNext);
        Button btnSkip = tipLayout.findViewById(R.id.btnSkip);
        tipText.setText(step.description);

        btnNext.setOnClickListener(v -> {
            currentStep = index + 1;
            showStep(currentStep);
        });

        btnSkip.setOnClickListener(v -> finishOnboarding());

        popupWindow = new PopupWindow(tipLayout,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(false);
        popupWindow.setElevation(24);

        targetView.post(() -> {
            tipLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            int tipWidth = tipLayout.getMeasuredWidth();
            int tipHeight = tipLayout.getMeasuredHeight();

            int[] loc = new int[2];
            targetView.getLocationOnScreen(loc);
            int targetCenterX = loc[0] + targetView.getWidth() / 2;

            int screenWidth = rootView.getWidth();
            int x = targetCenterX - tipWidth / 2;
            if (x < 16) x = 16;
            if (x + tipWidth > screenWidth - 16) x = screenWidth - tipWidth - 16;

            int y = loc[1] - tipHeight - 16;
            if (y < 16) {
                y = loc[1] + targetView.getHeight() + 16;
            }

            popupWindow.showAtLocation(targetView, Gravity.NO_GRAVITY, x, y);
        });
    }

    private void dismissPopupAndHighlight() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
        if (currentHighlightedView != null) {
            if (originalBackground != null) {
                currentHighlightedView.setBackground(originalBackground);
            } else {
                currentHighlightedView.setBackground(null);
            }
            currentHighlightedView = null;
            originalBackground = null;
        }
    }

    private void finishOnboarding() {
        dismissPopupAndHighlight();
        // Флаг НЕ сохраняется, поэтому при следующем запуске обучение появится снова
    }
}