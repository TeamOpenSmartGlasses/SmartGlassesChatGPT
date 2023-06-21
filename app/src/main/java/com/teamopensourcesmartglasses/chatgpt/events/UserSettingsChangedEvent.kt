package com.teamopensourcesmartglasses.chatgpt.events

class UserSettingsChangedEvent(
    val openAiKey: String,
    val systemPrompt: String,
    val useAutoSend: Boolean
)