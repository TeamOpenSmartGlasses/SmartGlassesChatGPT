package com.teamopensourcesmartglasses.chatgpt.events;

public class OpenAIApiKeyProvidedEvent {
    public final String token;
    public OpenAIApiKeyProvidedEvent(String userToken) {
        token = userToken;
    }
}
