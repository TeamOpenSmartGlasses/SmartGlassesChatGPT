package com.teamopensourcesmartglasses.chatgpt;

import android.util.Log;

import com.teamopensourcesmartglasses.chatgpt.events.ChatReceivedEvent;
import com.teamopensourcesmartglasses.chatgpt.events.OpenAIApiKeyProvidedEvent;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
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
    final String TAG = "SmartGlassesChatGpt_ChatGptBackend";
    private final OpenAiService service;
    private String openAiApiKey;
    private final List<ChatMessage> messages = new ArrayList<>();

    public ChatGptBackend(){
        EventBus.getDefault().register(this);

        // ChatGPT config
        String token = "";
        service = new OpenAiService(token, Duration.ofSeconds(60));
        final ChatMessage systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), "You are a dog and will speak as such.");
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
                messages.add(new ChatMessage(ChatMessageRole.USER.value(), message));

                Log.d(TAG, "New Message Stack: ");
                for (ChatMessage message : messages) {
                    Log.d(TAG, message.getRole() + ": " + message.getContent());
                }

                // Todo: Change completions to streams
                ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                        .model("gpt-3.5-turbo")
                        .messages(messages)
                        .n(1)
                        .build();

                try {
                    Log.d(TAG, "Running ChatGpt completions request");
                    List<ChatMessage> responses = service.createChatCompletion(chatCompletionRequest)
                            .getChoices()
                            .stream()
                            .map(ChatCompletionChoice::getMessage)
                            .collect(Collectors.toList());

                    // Send an chat received response
                    ChatMessage response = responses.get(0);
                    EventBus.getDefault().post(new ChatReceivedEvent(response.getContent()));
                    // Add back to chat
                    messages.add(response);
                } catch (Exception e){
                    Log.d(TAG, e.getMessage());
                    EventBus.getDefault().post(new ChatReceivedEvent("Something is wrong with openAI service"));
                    e.printStackTrace();
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
}
