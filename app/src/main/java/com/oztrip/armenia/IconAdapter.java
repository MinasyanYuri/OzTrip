package com.oztrip.armenia;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class IconAdapter extends RecyclerView.Adapter<IconAdapter.ViewHolder> {
    private final int[] icons;
    private final OnIconClickListener listener;

    public interface OnIconClickListener {
        void onIconClick(int iconRes);
    }

    public IconAdapter(int[] icons, OnIconClickListener listener) {
        this.icons = icons;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ImageView imageView = new ImageView(parent.getContext());
        // Устанавливаем размер иконки 80dp
        int size = (int) (80 * parent.getContext().getResources().getDisplayMetrics().density);
        imageView.setLayoutParams(new ViewGroup.LayoutParams(size, size));
        imageView.setPadding(20, 20, 20, 20);
        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        return new ViewHolder(imageView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        int resId = icons[position];
        ((ImageView) holder.itemView).setImageResource(resId);
        holder.itemView.setOnClickListener(v -> listener.onIconClick(resId));
    }

    @Override
    public int getItemCount() {
        return icons.length;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}