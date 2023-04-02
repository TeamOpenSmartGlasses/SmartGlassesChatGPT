package com.teamopensourcesmartglasses.chatgpt.events;

public class ChatErrorEvent {
    private final String message;

    public ChatErrorEvent(String errorMessage) {
        message = errorMessage;
    }

    public String getErrorMessage() {
        return message;
    }
}
