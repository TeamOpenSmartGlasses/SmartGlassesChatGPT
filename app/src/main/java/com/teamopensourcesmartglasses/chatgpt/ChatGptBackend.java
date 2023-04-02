package com.teamopensourcesmartglasses.chatgpt;

import android.util.Log;

import com.teamopensourcesmartglasses.chatgpt.events.ChatErrorEvent;
import com.teamopensourcesmartglasses.chatgpt.events.ChatReceivedEvent;
import com.teamopensourcesmartglasses.chatgpt.events.OpenAIApiKeyProvidedEvent;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionChunk;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ChatGptBackend {
    public final String TAG = "SmartGlassesChatGpt_ChatGptBackend";
    public final ChatMessage systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(),  "You are a friendly assistant that likes talking about philosophy and constantly thinks of interesting questions for the user");
    private OpenAiService service;
    private final List<ChatMessage> messages = new ArrayList<>();
    // private StringBuffer responseMessageBuffer = new StringBuffer();
    private final int chatGptMaxTokenSize = 2000;
    private final int maxSingleChatTokenSize = 150;
    private final int openAiServiceTimeoutDuration = 110;

//    public static void setApiToken(String token) {
//        Log.d("SmartGlassesChatGpt_ChatGptBackend", "setApiToken: token set");
//        apiToken = token;
//        EventBus.getDefault().post(new OpenAIApiKeyProvidedEvent(token));
//    }

    public ChatGptBackend(){
        EventBus.getDefault().register(this);
        messages.add(systemMessage);
    }

    public void initChatGptService(String token) {
        // Setup ChatGpt with a token
        service = new OpenAiService(token, Duration.ofSeconds(openAiServiceTimeoutDuration));
    }

    public void sendChat(String message){
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
                    // Just use a simple approximation, if current request is more than 90% of max, we clear half of it
                    long tokensUsed = result.getUsage().getTotalTokens();
                    Log.d(TAG, "run: tokens used: " + tokensUsed + "/" + chatGptMaxTokenSize);
                    if (tokensUsed >= chatGptMaxTokenSize * 0.90) {
                        for (int i = 0; i < messages.size() / 2; i++) {
                            messages.remove(1);
                        }
                    }

                    // Send an chat received response
                    ChatMessage response = responses.get(0);
                    EventBus.getDefault().post(new ChatReceivedEvent(response.getContent()));
                    // Add back to chat
                    messages.add(response);
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

    public void clearMessages() {
        messages.clear();
        messages.add(systemMessage);
    }

    @Subscribe
    public void onOpenAIApiKeyProvided(OpenAIApiKeyProvidedEvent event) {
        // Everytime a user submits a token, we reset the service with that api key
        Log.d(TAG, "onOpenAIApiKeyProvided: Got user key " + event.token);
        initChatGptService(event.token);
    }
}
