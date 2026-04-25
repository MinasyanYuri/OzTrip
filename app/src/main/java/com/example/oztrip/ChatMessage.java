package com.example.oztrip;

import java.util.Objects;

public class ChatMessage {
    public String text;
    public boolean isUser;
    public long id; // для идентификации в DiffUtil

    public ChatMessage(String text, boolean isUser) {
        this.text = text;
        this.isUser = isUser;
        this.id = System.nanoTime(); // уникальный ID
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatMessage that = (ChatMessage) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}