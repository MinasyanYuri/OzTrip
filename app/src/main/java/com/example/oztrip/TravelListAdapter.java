package com.example.oztrip;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

public class TravelListAdapter extends RecyclerView.Adapter<TravelListAdapter.ViewHolder> {
    private List<TravelList> lists;
    private OnListClickListener listener;
    private int selectedIndex = 0;

    public interface OnListClickListener {
        void onListClick(int position);
        void onListRename(int position, String oldName);
    }

    public TravelListAdapter(List<TravelList> lists, OnListClickListener listener) {
        this.lists = lists;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_travel_list, parent, false);
        ViewHolder holder = new ViewHolder(v);

        // Детектор двойного тапа
        GestureDetector detector = new GestureDetector(v.getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                int position = holder.getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onListRename(position, lists.get(position).name);
                }
                return true;
            }
        });

        v.setOnTouchListener((view, event) -> detector.onTouchEvent(event));

        return holder;
    }

    public void setSelectedIndex(int index) {
        int previousIndex = this.selectedIndex;
        this.selectedIndex = index;
        notifyItemChanged(previousIndex);
        notifyItemChanged(selectedIndex);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, @SuppressLint("RecyclerView") int position) {
        TravelList list = lists.get(position);
        holder.text.setText(list.name);

        MaterialCardView card = (MaterialCardView) holder.itemView;

        if (position == selectedIndex) {
            card.setCardBackgroundColor(Color.parseColor("#FF9800"));
            card.setStrokeColor(Color.parseColor("#FFFFFF"));
            card.setStrokeWidth(dpToPx(2, holder.itemView));
            holder.text.setTextColor(Color.WHITE);
            holder.text.setAlpha(1.0f);
        } else {
            card.setCardBackgroundColor(Color.parseColor("#CCFFFFFF"));
            card.setStrokeColor(Color.parseColor("#FFFFFF"));
            card.setStrokeWidth(dpToPx(1, holder.itemView));
            holder.text.setTextColor(Color.parseColor("#B0B0B0"));
            holder.text.setAlpha(0.9f);
        }

        // Обычный клик (переключение)
        card.setOnClickListener(v -> {
            if (selectedIndex != position) {
                int old = selectedIndex;
                selectedIndex = position;
                notifyItemChanged(old);
                notifyItemChanged(selectedIndex);
                listener.onListClick(position);
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            }
        });

        // Долгое нажатие (переименование) – оставляем
        card.setOnLongClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            listener.onListRename(position, list.name);
            return true;
        });
    }

    private int dpToPx(int dp, View v) {
        return (int) (dp * v.getContext().getResources().getDisplayMetrics().density);
    }

    @Override
    public int getItemCount() {
        return lists.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView text;
        ViewHolder(View v) {
            super(v);
            text = v.findViewById(R.id.tvListName);
        }
    }
}