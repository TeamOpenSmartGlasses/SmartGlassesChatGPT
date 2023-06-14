package com.teamopensourcesmartglasses.chatgpt.events;

import com.teamopensourcesmartglasses.chatgpt.entities.Prompt;

public class PromptDeletedEvent {
    private final Prompt prompt;

    public PromptDeletedEvent(Prompt prompt) {
        this.prompt = prompt;
    }

    public Prompt getPrompt() {
        return this.prompt;
    }
}