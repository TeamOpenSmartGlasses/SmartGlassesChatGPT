package com.teamopensourcesmartglasses.chatgpt.events;

import com.teamopensourcesmartglasses.chatgpt.entities.Prompt;

public class PromptAddedEvent {
    private final Prompt prompt;

    public PromptAddedEvent(Prompt prompt) {
        this.prompt = prompt;
    }

    public Prompt getPrompt() {
        return this.prompt;
    }
}
