package com.teamopensourcesmartglasses.chatgpt.utils

import com.theokanning.openai.completion.chat.ChatMessage
import java.time.LocalDateTime

class Message(var tokenCount: Int, val timestamp: LocalDateTime, val chatMessage: ChatMessage) {

    override fun toString(): String {
        return "$tokenCount $chatMessage"
    }
}