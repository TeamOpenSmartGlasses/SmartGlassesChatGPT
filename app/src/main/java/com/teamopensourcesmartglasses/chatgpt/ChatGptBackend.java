package com.teamopensourcesmartglasses.chatgpt;

import android.util.Log;

import com.teamopensourcesmartglasses.chatgpt.events.ChatErrorEvent;
import com.teamopensourcesmartglasses.chatgpt.events.ChatReceivedEvent;
import com.teamopensourcesmartglasses.chatgpt.events.QuestionAnswerReceivedEvent;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;

import org.greenrobot.eventbus.EventBus;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ChatGptBackend {
    public final String TAG = "SmartGlassesChatGpt_ChatGptBackend";
    public final ChatMessage systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(),  "You are a friendly assistant that likes talking about philosophy and constantly thinks of interesting questions for the user");
    private OpenAiService service;
    private final List<ChatMessage> messages = new ArrayList<>();
    private StringBuffer recordedConversation = new StringBuffer();
    // private StringBuffer responseMessageBuffer = new StringBuffer();
    private final int chatGptMaxTokenSize = 4096;
    private final int maxSingleChatTokenSize = 200;
    private final int openAiServiceTimeoutDuration = 110;

    // Some rough estimation based on word count
    // 4000 tokens should be enough for 3000 words, we use 2000 just to be conservative
    private final int maxMessagesWordSize = 2000;
    private final int conversationChunkSize = 250;

//    public static void setApiToken(String token) {
//        Log.d("SmartGlassesChatGpt_ChatGptBackend", "setApiToken: token set");
//        apiToken = token;
//        EventBus.getDefault().post(new OpenAIApiKeyProvidedEvent(token));
//    }

    public ChatGptBackend(){}

    public void initChatGptService(String token) {
        // Setup ChatGpt with a token
        service = new OpenAiService(token, Duration.ofSeconds(openAiServiceTimeoutDuration));
        messages.clear();
        messages.add(systemMessage);
    }

    public void sendChat(String message, ChatGptAppMode mode){
        // Don't run if openAI service is not initialized yet
        if (service == null) {
            EventBus.getDefault().post(new ChatErrorEvent("OpenAi Key has not been provided yet. Please do so in the app."));
            return;
        }

        // Add to messages here if it is just to record
        if (mode == ChatGptAppMode.Record) {
            Log.d(TAG, "sendChat: In record mode");
            // if it is some very long message, just replace the chat messages with it
            if (getWordCount(message) > maxMessagesWordSize) {
                Log.d(TAG, "sendChat: In record mode, message is larger than the entire word limit, needs to truncate");
                // Delete all old stored context
                recordedConversation = new StringBuffer();
                messages.clear();
                messages.add(systemMessage);

                // Add truncated message
                messages.add(new ChatMessage(ChatMessageRole.USER.value(), "Here is some conversation: " + truncateMessage(message)));
                Log.d(TAG, "sendChat: " + messages.get(messages.size() - 1).getContent());
                return;
            }

            // Otherwise add it to the conversation buffer
            recordedConversation.append(message);

            // Check if conversation buffer forms a big enough chunk
            int conversationWordCount = getWordCount(recordedConversation.toString());
            if (conversationWordCount > conversationChunkSize) {
                Log.d(TAG, "sendChat: In record mode, conversation is long enough to form a chunk");
                // Check if adding chunk to the context history will overflow
                if (getMessagesWordCount() + conversationWordCount >= maxMessagesWordSize) {
                    clearSomeMessages();
                }
                messages.add(new ChatMessage(ChatMessageRole.USER.value(), "Here is some conversation: " + recordedConversation));
                Log.d(TAG, "sendChat: " + messages.get(messages.size() - 1).getContent());
                recordedConversation = new StringBuffer();
            }

            return;
        }

        // If not in recording mode, but recorded buffer has something, we add it into our messages
        if ((mode == ChatGptAppMode.Conversation || mode == ChatGptAppMode.Question)
                && recordedConversation.length() != 0) {
            Log.d(TAG, "sendChat: Detected non empty recorded conversation while not in recording mode, adding them in");
            messages.add(new ChatMessage(ChatMessageRole.USER.value(), "Here is some conversation: " + recordedConversation));
            Log.d(TAG, "sendChat: " + messages.get(messages.size() - 1).getContent());
            recordedConversation  = new StringBuffer();
        }

        // Just an example of how this would work (pulled mostly from openai-java library example)...:
        // 1. Send message to ChatGPT
        // 2. Wait for response
        // 3. On response, post a "ChatGptResponseReceivedEvent" with the data
        // 3a) Subscribe to that event in ChatGptService.java and do something with the data (ie: send reference card to smart glasses)

        class DoGptStuff implements Runnable {
            public void run(){
                Log.d(TAG, "run: Doing gpt stuff, got message: " + message);
                messages.add(new ChatMessage(ChatMessageRole.USER.value(), message));

//                Log.d(TAG, "run: New Message Stack: ");
//                for (ChatMessage message : messages) {
//                    Log.d(TAG, message.getRole() + ": " + message.getContent());
//                }

                // Todo: Change completions to streams
                ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                        .model("gpt-3.5-turbo")
                        .messages(messages)
                        .maxTokens(maxSingleChatTokenSize)
                        .n(1)
                        .build();

                try {
                    Log.d(TAG, "run: Running ChatGpt completions request");
                    ChatCompletionResult result = service.createChatCompletion(chatCompletionRequest);
                    List<ChatMessage> responses = result.getChoices()
                                                        .stream()
                                                        .map(ChatCompletionChoice::getMessage)
                                                        .collect(Collectors.toList());

                    // Make sure there is still space for next messages
                    // Just use a simple approximation, if current request is more than 75% of max, we clear half of it
                    long tokensUsed = result.getUsage().getTotalTokens();
                    Log.d(TAG, "run: tokens used: " + tokensUsed + "/" + chatGptMaxTokenSize);
                    if (tokensUsed >= chatGptMaxTokenSize * 0.75) {
                        clearSomeMessages();
                    }

                    // Send an chat received response
                    ChatMessage response = responses.get(0);
                    Log.d(TAG, "run: " + response.getContent());

                    // Add back to chat UI and internal history
                    if (mode == ChatGptAppMode.Conversation) {
                        EventBus.getDefault().post(new ChatReceivedEvent(response.getContent()));
                        messages.add(response);
                    }

                    // Send back one off question and answer
                    if (mode == ChatGptAppMode.Question) {
                        EventBus.getDefault().post(new QuestionAnswerReceivedEvent(message, response.getContent()));

                        // Edit the last user message to specify that it was a question
                        int lastIndex = messages.size() - 1;
                        ChatMessage lastUserMessage = messages.get(lastIndex);
                        lastUserMessage.setContent("User asked a question: " + lastUserMessage.getContent());
                        messages.set(lastIndex, lastUserMessage);

                        // Specify on the answer side as well
                        response.setContent("Got an answer: " + response.getContent());
                        messages.add(response);
                    }
                } catch (Exception e){
                    Log.d(TAG, "run: encountered error: " + e.getMessage());
                    EventBus.getDefault().post(new ChatErrorEvent(e.getMessage()));
                }

//                Log.d(TAG, "Streaming chat completion");
//                service.streamChatCompletion(chatCompletionRequest)
//                        .doOnError(this::onStreamChatGptError)
//                        .doOnComplete(this::onStreamComplete)
//                        .blockingForEach(this::onItemReceivedFromStream);
            }

//            private void onStreamChatGptError(Throwable throwable) {
//                Log.d(TAG, throwable.getMessage());
//                EventBus.getDefault().post(new ChatReceivedEvent(throwable.getMessage()));
//                throwable.printStackTrace();
//            }
//
//            public void onItemReceivedFromStream(ChatCompletionChunk chunk) {
//                String textChunk = chunk.getChoices().get(0).getMessage().getContent();
//                Log.d(TAG, "Chunk received from stream: " + textChunk);
//                EventBus.getDefault().post(new ChatReceivedEvent(textChunk));
//                responseMessageBuffer.append(textChunk);
//                responseMessageBuffer.append(" ");
//            }
//
//            public void onStreamComplete() {
//                String responseMessage = responseMessageBuffer.toString();
//                messages.add(new ChatMessage(ChatMessageRole.ASSISTANT.value(), responseMessage));
//                responseMessageBuffer = new StringBuffer();
//            }
        }
        new Thread(new DoGptStuff()).start();
    }

    private int getWordCount(String message) {
        String[] words = message.split("\\s+");
        return words.length;
    }

    private int getMessagesWordCount() {
        int count = 0;
        for (ChatMessage message : messages) {
            count += getWordCount(message.getContent());
        }

        return count;
    }

    private String truncateMessage(String message) {
        String[] words = message.split("\\s+");

        // Count the length of the truncated message
        // Determine the starting index
        int startIdx = words.length > maxMessagesWordSize
                ? words.length - maxMessagesWordSize
                : 0;

        /// Concatenate the last 2000 words into a new String
        StringBuilder sb = new StringBuilder();
        for (int i = startIdx; i < words.length; i++) {
            sb.append(words[i]);
            sb.append(" ");
        }
        return sb.toString().trim();
    }

    private void clearSomeMessages() {
        for (int i = 0; i < messages.size() / 2; i++) {
            Log.d(TAG, "sendChat: Tried adding chunk to the messages, but there are too many words, clearing some chat context");
            messages.remove(1);
        }
    }
}
