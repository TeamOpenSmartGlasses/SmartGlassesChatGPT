package com.teamopensourcesmartglasses.chatgpt;

import android.os.AsyncTask;
import android.util.Log;

import com.teamopensourcesmartglasses.chatgpt.events.ChatReceivedEvent;
import com.teamopensourcesmartglasses.chatgpt.events.ClearMessagesEvent;
import com.teamopensourcesmartglasses.chatgpt.events.OpenAIApiKeyProvidedEvent;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ChatGptBackend {
    final String TAG = "SmartGlassesChatGpt_ChatGptBackend";
    private final OpenAiService service;
    private String openAiApiKey;
    private final List<ChatMessage> messages = new ArrayList<>();
    private final ChatMessage systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), "You are a dog and will speak as such.");

    public ChatGptBackend(){
        EventBus.getDefault().register(this);

        // ChatGPT config
        String token = "<YOUR TOKEN HERE>";
        service = new OpenAiService(token);
        messages.add(systemMessage);
    }

    public void sendChat(String message){
        // Just an example of how this would work (pulled mostly from openai-java library example)...:
        // 1. Send message to ChatGPT
        // 2. Wait for response
        // 3. On response, post a "ChatGptResponseReceivedEvent" with the data
        // 3a) Subscribe to that event in ChatGptService.java and do something with the data (ie: send reference card to smart glasses)

        class DoGptStuff implements Runnable {
            public void run(){
                Log.d(TAG, "Doing gpt stuff, got message " + message);
                ChatMessage newUserMessage = new ChatMessage(ChatMessageRole.USER.value(), message);
                messages.add(newUserMessage);

                Log.d(TAG, "User message: " + newUserMessage);

                ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                        .model("gpt-3.5-turbo")
                        .messages(messages)
                        .n(1)
                        .build();

                try {
                    Log.d(TAG, "trying chatgpt stuff");
                    List<ChatMessage> responses = service.createChatCompletion(chatCompletionRequest)
                            .getChoices()
                            .stream()
                            .map(ChatCompletionChoice::getMessage)
                            .collect(Collectors.toList());

                    // Send an chat received response
                    String response = responses.get(0).toString();
                    EventBus.getDefault().post(new ChatReceivedEvent(response));
                    // Add back to chat
                    messages.add(new ChatMessage(ChatMessageRole.ASSISTANT.value(), response));
                } catch (Exception e){
                    Log.d(TAG, e.getMessage());
                }
            }
        }
        new Thread(new DoGptStuff()).start();
    }

    @Subscribe
    public void onOpenAIApiKeyProvided(OpenAIApiKeyProvidedEvent event) {
        // A feature for users to input their own key
        openAiApiKey = event.token;
    }

    @Subscribe
    public void onClearMessages(ClearMessagesEvent event) {
        Log.d(TAG, "Clearing chat context");
        messages.clear();
        messages.add(systemMessage);
    }
}
