package com.teamopensourcesmartglasses.chatgpt.events;

public class UserSettingsChangedEvent {
    private final String openAiKey;
    private final String systemPrompt;
    private final boolean useAutoSend;

    public UserSettingsChangedEvent(String openAiKey, String systemPrompt, boolean useAutoSend) {
        this.openAiKey = openAiKey;
        this.systemPrompt = systemPrompt;
        this.useAutoSend = useAutoSend;
    }

    public String getOpenAiKey() {
        return this.openAiKey;
    }
    public String getSystemPrompt() {
        return this.systemPrompt;
    }
    public boolean getUseAutoSend() {
        return this.useAutoSend;
    }
}
