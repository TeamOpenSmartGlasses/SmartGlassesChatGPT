package com.teamopensourcesmartglasses.chatgpt.utils;
import com.theokanning.openai.completion.chat.ChatMessage;

import java.time.LocalDateTime;

class Message {
    private int tokenCount;
    private final LocalDateTime timestamp;
    private final ChatMessage message;

    public Message(int tokenCount, LocalDateTime timestamp, ChatMessage message) {
        this.tokenCount = tokenCount;
        this.timestamp = timestamp;
        this.message = message;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(int tokenCount) {
        this.tokenCount = tokenCount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public ChatMessage getChatMessage() {
        return message;
    }
}