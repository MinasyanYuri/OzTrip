package com.oztrip.armenia;

import android.graphics.Color;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    private final List<ChatMessage> messages = new ArrayList<>();

    public interface OnLocationClickListener {
        void onLocationClick(double lat, double lng);
    }

    private OnLocationClickListener locationClickListener;

    public void setOnLocationClickListener(OnLocationClickListener listener) {
        this.locationClickListener = listener;
    }

    public void addMessage(ChatMessage msg) {
        messages.add(msg);
        notifyItemInserted(messages.size() - 1);
    }

    public void replaceLastMessage(ChatMessage newMsg) {
        int pos = messages.size() - 1;
        if (pos >= 0) {
            messages.set(pos, newMsg);
            notifyItemChanged(pos);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);

        if (!msg.isUser) {
            // Сообщение от бота – делаем координаты кликабельными
            SpannableString spannable = new SpannableString(msg.text);
            Pattern pattern = Pattern.compile("\\[coord:([^,\\]]+),([^,\\]]+)\\]");
            Matcher matcher = pattern.matcher(spannable);

            while (matcher.find()) {
                String latStr = matcher.group(1).trim();
                String lngStr = matcher.group(2).trim();
                try {
                    double lat = Double.parseDouble(latStr);
                    double lng = Double.parseDouble(lngStr);
                    ClickableSpan span = new ClickableSpan() {
                        @Override
                        public void onClick(@NonNull View widget) {
                            if (locationClickListener != null) {
                                locationClickListener.onLocationClick(lat, lng);
                            }
                        }
                        @Override
                        public void updateDrawState(@NonNull TextPaint ds) {
                            super.updateDrawState(ds);
                            ds.setUnderlineText(true);
                            ds.setColor(Color.BLUE);
                        }
                    };
                    spannable.setSpan(span, matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } catch (NumberFormatException ignored) {}
            }

            holder.textView.setText(spannable);
            // Убираем LinkMovementMethod, чтобы выделение текста работало
            // holder.textView.setMovementMethod(LinkMovementMethod.getInstance()); // УДАЛЯЕМ

            // Включаем выделение текста
            holder.textView.setTextIsSelectable(true);

            // Обрабатываем клики по координатам вручную
            holder.textView.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    TextView tv = (TextView) v;
                    int x = (int) event.getX();
                    int y = (int) event.getY();
                    x -= tv.getTotalPaddingLeft();
                    y -= tv.getTotalPaddingTop();
                    x += tv.getScrollX();
                    y += tv.getScrollY();

                    Layout layout = tv.getLayout();
                    int line = layout.getLineForVertical(y);
                    int off = layout.getOffsetForHorizontal(line, x);

                    ClickableSpan[] links = spannable.getSpans(off, off, ClickableSpan.class);
                    if (links.length > 0) {
                        links[0].onClick(tv);
                        return true;
                    }
                }
                return false; // даём стандартному обработчику работать
            });

        } else {
            // Сообщение пользователя – обычный текст
            holder.textView.setText(msg.text);
            holder.textView.setTextIsSelectable(true);
        }

// В ChatAdapter.onBindViewHolder после установки текста и спанна
// Настройка выравнивания и фона
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) holder.textView.getLayoutParams();
        if (msg.isUser) {
            holder.textView.setBackgroundResource(R.drawable.bg_message_user);
            holder.textView.setTextColor(Color.WHITE);
            params.removeRule(RelativeLayout.ALIGN_PARENT_START);
            params.addRule(RelativeLayout.ALIGN_PARENT_END);
            params.setMargins(100, 0, 8, 0);
        } else {
            holder.textView.setBackgroundResource(R.drawable.bg_message_bot);
            params.removeRule(RelativeLayout.ALIGN_PARENT_END);
            params.addRule(RelativeLayout.ALIGN_PARENT_START);
            params.setMargins(8, 0, 80, 0);
        }
        holder.textView.setLayoutParams(params);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        ViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.messageText);
        }
    }
}