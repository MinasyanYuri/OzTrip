package com.example.oztrip;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TravelListAdapter extends RecyclerView.Adapter<TravelListAdapter.ViewHolder> {
    private List<TravelList> lists;
    private OnListClickListener listener;
    private int selectedIndex = 0;
    private boolean isSheetMode;

    // Поля для режима выбора (только для sheet)
    private boolean selectionMode = false;
    private Set<Integer> selectedPositions = new HashSet<>();
    private OnSelectionChangeListener selectionListener;

    public interface OnListClickListener {
        void onListClick(int position);
        void onListRename(int position, String oldName);
    }

    public interface OnSelectionChangeListener {
        void onSelectionChanged(int selectedCount);
    }

    public TravelListAdapter(List<TravelList> lists, OnListClickListener listener) {
        this(lists, listener, false);
    }

    public TravelListAdapter(List<TravelList> lists, OnListClickListener listener, boolean isSheetMode) {
        this.lists = lists;
        this.listener = listener;
        this.isSheetMode = isSheetMode;
    }

    public void setSelectionChangeListener(OnSelectionChangeListener listener) {
        this.selectionListener = listener;
    }

    public boolean isSelectionMode() { return selectionMode; }

    public void setSelectionMode(boolean enabled) {
        if (selectionMode == enabled) return;
        selectionMode = enabled;
        selectedPositions.clear();
        notifyDataSetChanged();
        if (selectionListener != null) selectionListener.onSelectionChanged(0);
        if (modeChangeListener != null) modeChangeListener.onSelectionModeChanged(enabled);
    }

    public Set<Integer> getSelectedPositions() { return selectedPositions; }

    public void toggleSelection(int position) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position);
        } else {
            selectedPositions.add(position);
        }
        notifyItemChanged(position);
        if (selectionListener != null) selectionListener.onSelectionChanged(selectedPositions.size());
    }

    public void clearSelection() {
        selectedPositions.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = isSheetMode ? R.layout.item_travel_list_sheet : R.layout.item_travel_list;
        View v = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, @SuppressLint("RecyclerView") int position) {
        TravelList list = lists.get(position);
        holder.text.setText(list.name);

        if (isSheetMode) {
            // === РЕЖИМ BOTTOM SHEET ===
            if (selectionMode) {
                // Режим выбора: показываем чекбокс, скрываем иконку
                if (holder.checkBox != null) {
                    holder.checkBox.setVisibility(View.VISIBLE);
                    holder.checkBox.setChecked(selectedPositions.contains(position));
                }
                if (holder.editIcon != null) holder.editIcon.setVisibility(View.GONE);
                holder.itemView.setBackgroundResource(R.drawable.bg_sheet_item);
                holder.text.setTextColor(Color.parseColor("#FFFFFF"));
            } else {
                // Обычный режим
                if (holder.checkBox != null) holder.checkBox.setVisibility(View.GONE);
                if (position == selectedIndex) {
                    // Активная поездка
                    holder.itemView.setBackgroundResource(R.drawable.bg_sheet_item_active);
                    holder.text.setTextColor(Color.WHITE);
                    if (holder.editIcon != null) {
                        holder.editIcon.setVisibility(View.VISIBLE);
                        holder.editIcon.setColorFilter(Color.WHITE);
                    }
                } else {
                    // Неактивная поездка
                    holder.itemView.setBackgroundResource(R.drawable.bg_sheet_item);
                    holder.text.setTextColor(Color.parseColor("#B0B0B0"));
                    if (holder.editIcon != null) holder.editIcon.setVisibility(View.GONE);
                }
            }
        } else {
            // === РЕЖИМ ПОЛЗУНКА (старый код) ===
            MaterialCardView card = (MaterialCardView) holder.itemView;
            if (holder.checkBox != null) holder.checkBox.setVisibility(View.GONE);
            if (position == selectedIndex) {
                card.setCardBackgroundColor(Color.parseColor("#FF9800"));
                card.setStrokeColor(Color.parseColor("#FFFFFF"));
                card.setStrokeWidth(dpToPx(2, holder.itemView));
                holder.text.setTextColor(Color.WHITE);
                holder.text.setAlpha(1.0f);
                if (holder.editIcon != null) {
                    holder.editIcon.setVisibility(View.VISIBLE);
                    holder.editIcon.setColorFilter(Color.WHITE);
                }
            } else {
                card.setCardBackgroundColor(Color.parseColor("#CCFFFFFF"));
                card.setStrokeColor(Color.parseColor("#FFFFFF"));
                card.setStrokeWidth(dpToPx(1, holder.itemView));
                holder.text.setTextColor(Color.parseColor("#B0B0B0"));
                holder.text.setAlpha(0.9f);
                if (holder.editIcon != null) {
                    holder.editIcon.setVisibility(View.GONE);
                }
            }
        }

        // Общие обработчики кликов
        holder.itemView.setOnClickListener(v -> {
            if (isSheetMode && selectionMode) {
                toggleSelection(position);
            } else {
                if (selectedIndex != position) {
                    int old = selectedIndex;
                    selectedIndex = position;
                    notifyItemChanged(old);
                    notifyItemChanged(selectedIndex);
                    listener.onListClick(position);
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                }
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (isSheetMode && !selectionMode) {
                setSelectionMode(true);
                toggleSelection(position);
                return true;
            }
            if (!isSheetMode) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                listener.onListRename(position, list.name);
                return true;
            }
            return false;
        });

        if (holder.editIcon != null) {
            holder.editIcon.setOnClickListener(v -> {
                if (!selectionMode && position == selectedIndex) {
                    listener.onListRename(position, list.name);
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                }
            });
        }

        if (holder.checkBox != null) {
            holder.checkBox.setOnCheckedChangeListener(null);
            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked && !selectedPositions.contains(position)) {
                    selectedPositions.add(position);
                } else if (!isChecked) {
                    selectedPositions.remove(position);
                }
                if (selectionListener != null)
                    selectionListener.onSelectionChanged(selectedPositions.size());
            });
        }
    }

    private int dpToPx(int dp, View v) {
        return (int) (dp * v.getContext().getResources().getDisplayMetrics().density);
    }

    @Override
    public int getItemCount() {
        return lists.size();
    }

    public void setSelectedIndex(int index) {
        int prev = this.selectedIndex;
        this.selectedIndex = index;
        if (prev >= 0 && prev < lists.size()) notifyItemChanged(prev);
        if (index >= 0 && index < lists.size()) notifyItemChanged(index);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView text;
        ImageView editIcon;
        CheckBox checkBox;

        ViewHolder(View v) {
            super(v);
            text = v.findViewById(R.id.tvListName);
            editIcon = v.findViewById(R.id.ivEditList);
            checkBox = v.findViewById(R.id.checkboxSelect);
        }
    }
    public interface OnSelectionModeChangeListener {
        void onSelectionModeChanged(boolean enabled);
    }
    private OnSelectionModeChangeListener modeChangeListener;

    public void setOnSelectionModeChangeListener(OnSelectionModeChangeListener listener) {
        this.modeChangeListener = listener;
    }
}