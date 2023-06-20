package com.teamopensourcesmartglasses.chatgpt.events;

public class ChatSummarizedEvent {
    private final String summary;

    public ChatSummarizedEvent(String chatSummary) {
        summary = chatSummary;
    }

    public String getSummary() {
        return summary;
    }
}
