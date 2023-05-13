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
        // We don't need to store the system message into the queue, just the count will do,
        // which is handled in resetMessages(), so we don't have to handle adding/removing it in the logic
        // we will add it back when messages are queried
        this.systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), systemMessage);
        resetMessages();
    }

    public void addMessage(String role, String message) {
//        Log.d(TAG, "addMessage: previous token count: " + this.totalTokenCount);
        int tokenCount = getTokenCount(message);
        if (Objects.equals(role, ChatMessageRole.USER.value())) {
            tokenCount += 4; // every message follows <im_start>{role/name}\n{content}<im_end>\n
        } else if (Objects.equals(role, ChatMessageRole.ASSISTANT.value())) {
            tokenCount += 6; // every message follows <im_start>{role/name}\n{content}<im_end>\n
                                // every reply is primed with <im_start>assistant
        }
//        Log.d(TAG, "addMessage: message to add: " + message);
//        Log.d(TAG, "addMessage: message count: " + tokenCount);
        totalTokenCount += tokenCount;
        ChatMessage chatMessage = new ChatMessage(role, message);
        messageQueue.add(new Message(tokenCount, LocalDateTime.now(), chatMessage));
//        Log.d(TAG, "addMessage: added message to message queue");

        while (totalTokenCount > maxTokenCount) {
//            Log.d(TAG, "addMessage: total more than max, removing first");
            Message lastMessage = messageQueue.removeFirst();
//            Log.d(TAG, "addMessage: " + lastMessage);
            totalTokenCount -= lastMessage.getTokenCount();
//            Log.d(TAG, "addMessage: new token count: " + totalTokenCount);
        }

//        Log.d(TAG, "addMessage: after token count: " + this.totalTokenCount);
//        Log.d(TAG, "addMessage: after message queue: ");
//        for (Message mess:
//                this.messageQueue) {
//            Log.d(TAG, "addMessage: message: " + mess);
//        }
    }

    public ArrayList<ChatMessage> getAllMessages() {
        ArrayList<ChatMessage> result = new ArrayList<>();
        result.add(this.systemMessage);

        for (Message message : messageQueue) {
            result.add(message.getChatMessage());
        }
        return result;
    }

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

    public void addPrefixToLastAddedMessage(String prefix) {
        Message mostRecentMessage = messageQueue.removeLast();
        totalTokenCount -= mostRecentMessage.getTokenCount();
        ChatMessage message = mostRecentMessage.getChatMessage();
        addMessage(message.getRole(), prefix + " " + message.getContent());
    }

    public int size() {
        return messageQueue.size();
    }

    private int getTokenCount(String message) {
        return encoding.countTokens(message);
    }

    public Message removeFirst() {
        Message message = messageQueue.removeFirst();
        totalTokenCount -= getTokenCount(message.getChatMessage().getContent());
        return message;
    }

    public void resetMessages() {
        messageQueue = new LinkedList<>();
        this.totalTokenCount = getTokenCount(this.systemMessage.getContent());
        Log.d(TAG, "resetMessages: system message token count: " + getTokenCount(this.systemMessage.getContent()));
        Log.d(TAG, "resetMessages: new token count: " + this.totalTokenCount);
    }
}
