package com.teamopensourcesmartglasses.chatgpt.utils;
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
    private LinkedList<Message> messageQueue = new LinkedList<>();
    private int totalTokenCount = 0;
    private final int maxTokenCount;
    private final ChatMessage systemMessage;
    // CL100K_BASE is for GPT3.5-Turbo
    private final Encoding encoding = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    public MessageStore(int maxTokenCount, String systemMessage) {
        this.maxTokenCount = maxTokenCount;
        this.systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), systemMessage);

        // We don't need to store the system message into the queue, just the count will do,
        // so we don't have to handle adding/removing it in the logic
        // we will add it back when messages are queried
        this.totalTokenCount += getTokenCount(systemMessage);
    }

    public void addMessage(String role, String message) {
        int tokenCount = getTokenCount(message);

        if (Objects.equals(role, ChatMessageRole.USER.value())) {
            tokenCount += 4; // every message follows <im_start>{role/name}\n{content}<im_end>\n
        } else if (Objects.equals(role, ChatMessageRole.ASSISTANT.value())) {
            tokenCount += 6; // every message follows <im_start>{role/name}\n{content}<im_end>\n
                                // every reply is primed with <im_start>assistant
        }

        totalTokenCount += tokenCount;
        ChatMessage chatMessage = new ChatMessage(role, message);
        messageQueue.add(new Message(tokenCount, LocalDateTime.now(), chatMessage));

        while (totalTokenCount > maxTokenCount) {
            Message lastMessage = messageQueue.removeFirst();
            totalTokenCount -= lastMessage.getTokenCount();
        }
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
        ChatMessage message = mostRecentMessage.getChatMessage();
        addMessage(message.getRole(), prefix + " " + message.getContent());
    }

    public int size() {
        return messageQueue.size();
    }

    private int getTokenCount(String message) {
        return encoding.countTokens(message);
    }

    public Message removeLast() {
        Message message = messageQueue.removeLast();
        totalTokenCount -= getTokenCount(message.getChatMessage().getContent());
        return message;
    }

    public Message removeFirst() {
        Message message = messageQueue.removeFirst();
        totalTokenCount -= getTokenCount(message.getChatMessage().getContent());
        return message;
    }

    public void packMessages() {
        LinkedList<Message> packedMessages = new LinkedList<>();
        if (messageQueue.isEmpty()) {
            return;
        }

        Message prevMessage = messageQueue.get(0);
        for (int i = 1; i < messageQueue.size(); i++) {
            Message currMessage = messageQueue.get(i);
            if (currMessage.getChatMessage().getRole().equals(prevMessage.getChatMessage().getRole())) {
                // append current message's content to previous message's content
                prevMessage.getChatMessage().setContent(prevMessage.getChatMessage().getContent() + " "
                        + currMessage.getChatMessage().getContent());
                prevMessage.setTokenCount(prevMessage.getTokenCount() + currMessage.getTokenCount());
                totalTokenCount -= 4; // every message follows <im_start>{role/name}\n{content}<im_end>\n
            } else {
                // add previous message to compacted messages
                packedMessages.add(prevMessage);
                prevMessage = currMessage;
            }
        }
        // add the last message to compacted messages
        packedMessages.add(prevMessage);

        messageQueue = packedMessages;
    }

    public void resetMessages() {
        messageQueue = new LinkedList<>();
        this.totalTokenCount = getTokenCount(this.systemMessage.getContent());
    }
}
