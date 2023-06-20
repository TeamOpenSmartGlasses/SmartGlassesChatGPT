package com.teamopensourcesmartglasses.chatgpt.utils

import android.util.Log
import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.EncodingType
import com.theokanning.openai.completion.chat.ChatMessage
import com.theokanning.openai.completion.chat.ChatMessageRole
import java.time.LocalDateTime
import java.util.LinkedList

class MessageStore(private val maxTokenCount: Int) {
    private val TAG = "MessageStore"
    private var messageQueue = LinkedList<Message>()
    private var totalTokenCount = 4 // to account for system role extra tokens
    private var systemMessage: ChatMessage? = null

    // CL100K_BASE is for GPT3.5-Turbo
    private val encoding =
        Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE)

    fun setSystemMessage(systemMessage: String) {
        if (this.systemMessage != null) {
            totalTokenCount -= getTokenCount(this.systemMessage!!.content)
        }
        this.systemMessage = ChatMessage(ChatMessageRole.SYSTEM.value(), systemMessage)
        totalTokenCount += getTokenCount(systemMessage)
    }

    fun getSystemMessage(): String {
        return systemMessage.toString()
    }

    fun addMessage(role: String?, message: String) {
        var tokenCount = getTokenCount(message)
        if (role == ChatMessageRole.USER.value()) {
            tokenCount += 4 // every message follows <im_start>{role/name}\n{content}<im_end>
        } else if (role == ChatMessageRole.ASSISTANT.value()) {
            tokenCount += 6 // every message follows <im_start>{role/name}\n{content}<im_end>
            // every reply is primed with <im_start>assistant
        }
        totalTokenCount += tokenCount
        val chatMessage = ChatMessage(role, message)
        messageQueue.add(Message(tokenCount, LocalDateTime.now(), chatMessage))
        Log.d(TAG, "addMessage: Added a new message: $message")
        Log.d(TAG, "addMessage: New token count: $totalTokenCount")
        // if exceeds new total tokens exceeds the limit, this will evict the old messages
        ensureTotalTokensWithinLimit()
    }

    /**
     * Evicts old messages while total tokens are more than the limit
     */
    private fun ensureTotalTokensWithinLimit() {
        while (totalTokenCount > maxTokenCount) {
            val lastMessage = messageQueue.removeFirst()
            totalTokenCount -= lastMessage.tokenCount
            Log.d(
                TAG,
                "ensureTotalTokensWithinLimit: Removed a message " + lastMessage.chatMessage.content
            )
            Log.d(TAG, "ensureTotalTokensWithinLimit: New token count: $totalTokenCount")
        }
    }

    /**
     * Gets all chat messages including system prompt message in an arraylist
     * @return an array of chat messages
     */
    val allMessages: ArrayList<ChatMessage?>
        get() {
            val result = ArrayList<ChatMessage?>()
            result.add(systemMessage)
            for (message in messageQueue) {
                result.add(message.chatMessage)
            }
            return result
        }

    /**
     * Getting all messages without system prompt is useful so you can inject your own prompt
     * templates for other use-cases
     * @return an array of chat messages without system prompt
     */
    val allMessagesWithoutSystemPrompt: ArrayList<ChatMessage?>
        get() {
            val result = ArrayList<ChatMessage?>()
            for (message in messageQueue) {
                result.add(message.chatMessage)
            }
            return result
        }

    /**
     * Gets the messages for the last x minutes
     * @param minutes
     * @return array of new messages for the last x minutes
     */
    fun getMessagesByTime(minutes: Int): ArrayList<ChatMessage?> {
        val result = ArrayList<ChatMessage?>()
        result.add(systemMessage)
        val startTime = LocalDateTime.now().minusMinutes(minutes.toLong())
        for (message in messageQueue) {
            if (message.timestamp.isAfter(startTime)) {
                result.add(message.chatMessage)
            }
        }
        return result
    }

    /**
     * Adds a prefix to the last added message
     * @param prefix
     */
    fun addPrefixToLastAddedMessage(prefix: String) {
        // Removes latest message, add a prefix, then add it back
        val mostRecentMessage = removeLatest()
        val message = mostRecentMessage.chatMessage
        addMessage(message!!.role, prefix + " " + message.content)
    }

    fun size(): Int {
        return messageQueue.size
    }

    fun removeOldest(): Message {
        val message = messageQueue.removeFirst()
        totalTokenCount -= message.tokenCount
        return message
    }

    fun removeLatest(): Message {
        val message = messageQueue.removeLast()
        totalTokenCount -= message.tokenCount
        return message
    }

    fun resetMessages() {
        messageQueue = LinkedList()
    }

    private fun getTokenCount(message: String): Int {
        return encoding.countTokens(message)
    }
}