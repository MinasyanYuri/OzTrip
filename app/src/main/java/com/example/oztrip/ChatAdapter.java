package com.example.oztrip;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    private final List<ChatMessage> messages = new ArrayList<>();

    public void setMessages(List<ChatMessage> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        notifyDataSetChanged();
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
        holder.textView.setText(msg.text);
        if (msg.isUser) {
            holder.textView.setBackgroundResource(R.drawable.bg_message_user);
            holder.textView.setTextColor(holder.itemView.getContext()
                    .getResources().getColor(android.R.color.white));
            ((ViewGroup.MarginLayoutParams) holder.textView.getLayoutParams())
                    .setMargins(80, 0, 0, 0);
        } else {
            holder.textView.setBackgroundResource(R.drawable.bg_message_bot);
            holder.textView.setTextColor(holder.itemView.getContext()
                    .getResources().getColor(android.R.color.black));
            ((ViewGroup.MarginLayoutParams) holder.textView.getLayoutParams())
                    .setMargins(0, 0, 80, 0);
        }
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