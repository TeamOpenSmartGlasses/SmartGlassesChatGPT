package com.teamopensourcesmartglasses.chatgpt.utils;
import android.util.Log;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Objects;

public class MessageStore {
    private final String TAG = "MessageStore";
    private LinkedList<Message> messageQueue = new LinkedList<>();
    private int totalTokenCount = 4; // to account for system role extra tokens
    private final int maxTokenCount;
    private ChatMessage systemMessage;
    // CL100K_BASE is for GPT3.5-Turbo
    private final Encoding encoding = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    public MessageStore(int maxTokenCount) {
        this.maxTokenCount = maxTokenCount;
    }

    public void setSystemMessage(String systemMessage) {
        if (this.systemMessage != null) {
            this.totalTokenCount -= getTokenCount(this.systemMessage.getContent());
        }
        this.systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), systemMessage);
        this.totalTokenCount += getTokenCount(systemMessage);
    }

    public String getSystemMessage() {
        return systemMessage.toString();
    }

    public void addMessage(String role, String message) {
        int tokenCount = getTokenCount(message);

        if (Objects.equals(role, ChatMessageRole.USER.value())) {
            tokenCount += 4; // every message follows <im_start>{role/name}\n{content}<im_end>
        } else if (Objects.equals(role, ChatMessageRole.ASSISTANT.value())) {
            tokenCount += 6; // every message follows <im_start>{role/name}\n{content}<im_end>
                                // every reply is primed with <im_start>assistant
        }

        totalTokenCount += tokenCount;
        ChatMessage chatMessage = new ChatMessage(role, message);
        messageQueue.add(new Message(tokenCount, LocalDateTime.now(), chatMessage));

        Log.d(TAG, "addMessage: Added a new message: " + message);
        Log.d(TAG, "addMessage: New token count: " + totalTokenCount);
        // if exceeds new total tokens exceeds the limit, this will evict the old messages
        ensureTotalTokensWithinLimit();
    }

    /**
     * Evicts old messages while total tokens are more than the limit
     */
    private void ensureTotalTokensWithinLimit() {
        while (totalTokenCount > maxTokenCount) {
            Message lastMessage = messageQueue.removeFirst();
            totalTokenCount -= lastMessage.getTokenCount();

            Log.d(TAG, "ensureTotalTokensWithinLimit: Removed a message " + lastMessage.getChatMessage().getContent());
            Log.d(TAG, "ensureTotalTokensWithinLimit: New token count: " + totalTokenCount);
        }
    }

    /**
     * Gets all chat messages including system prompt message in an arraylist
     * @return an array of chat messages
     */
    public ArrayList<ChatMessage> getAllMessages() {
        ArrayList<ChatMessage> result = new ArrayList<>();
        result.add(this.systemMessage);

        for (Message message : messageQueue) {
            result.add(message.getChatMessage());
        }
        return result;
    }

    /**
     * Getting all messages without system prompt is useful so you can inject your own prompt
     * templates for other use-cases
     * @return an array of chat messages without system prompt
     */
    public ArrayList<ChatMessage> getAllMessagesWithoutSystemPrompt() {
        ArrayList<ChatMessage> result = new ArrayList<>();

        for (Message message : messageQueue) {
            result.add(message.getChatMessage());
        }
        return result;
    }

    /**
     * Gets the messages for the last x minutes
     * @param minutes
     * @return array of new messages for the last x minutes
     */
    public ArrayList<ChatMessage> getMessagesByTime(int minutes) {
        ArrayList<ChatMessage> result = new ArrayList<>();
        result.add(this.systemMessage);
        LocalDateTime startTime = LocalDateTime.now().minusMinutes(minutes);

        for (Message message : messageQueue) {
            if (message.getTimestamp().isAfter(startTime)) {
                result.add(message.getChatMessage());
            }
        }
        return result;
    }

    /**
     * Adds a prefix to the last added message
     * @param prefix
     */
    public void addPrefixToLastAddedMessage(String prefix) {
        // Removes latest message, add a prefix, then add it back
        Message mostRecentMessage = removeLatest();
        ChatMessage message = mostRecentMessage.getChatMessage();
        addMessage(message.getRole(), prefix + " " + message.getContent());
    }

    public int size() {
        return messageQueue.size();
    }

    public Message removeOldest() {
        Message message = messageQueue.removeFirst();
        totalTokenCount -= message.getTokenCount();
        return message;
    }

    public Message removeLatest() {
        Message message = messageQueue.removeLast();
        totalTokenCount -= message.getTokenCount();
        return message;
    }

    public void resetMessages() {
        messageQueue = new LinkedList<>();
    }

    private int getTokenCount(String message) {
        return encoding.countTokens(message);
    }
}
