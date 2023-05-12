package com.teamopensourcesmartglasses.chatgpt.events;

public class UserSettingsChangedEvent {
    private final String openAiKey;
    private final boolean useAutoSend;

    public UserSettingsChangedEvent(String openAiKey, boolean useAutoSend) {
        this.openAiKey = openAiKey;
        this.useAutoSend = useAutoSend;
    }

    public String getOpenAiKey() {
        return this.openAiKey;
    }

    public boolean getUseAutoSend() {
        return this.useAutoSend;
    }
}
