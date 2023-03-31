package com.teamopensourcesmartglasses.chatgpt;

import android.os.AsyncTask;
import android.util.Log;

import com.teamopensourcesmartglasses.chatgpt.events.ChatReceivedEvent;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;

import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class ChatGptBackend {
    final String TAG = "SmartGlassesChatGpt_ChatGptBackend";
    private final OpenAiService service;
    public ChatGptBackend(){
        String token = System.getenv("OPENAI_TOKEN");
        if (token == null)
            token = "";
        service = new OpenAiService(token);

        EventBus.getDefault().register(this);
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
                final List<ChatMessage> messages = new ArrayList<>();
                final ChatMessage systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), "You are a dog and will speak as such.");
                messages.add(systemMessage);
                messages.add(newUserMessage);

                ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                        .model("gpt-3.5-turbo")
                        .messages(messages)
                        .n(5)
                        .build();

                try {
                    Log.d(TAG, "trying chatgpt stuff");
                    List<ChatMessage> responses = service.createChatCompletion(chatCompletionRequest)
                            .getChoices()
                            .stream()
                            .map(ChatCompletionChoice::getMessage)
                            .collect(Collectors.toList());

                    Log.d(TAG, responses.toString());
                    // Send an chat received response
                    EventBus.getDefault().post(new ChatReceivedEvent(responses.get(0).toString()));
                } catch (Exception e){
                    Log.d(TAG, e.getMessage());
                }
            }
        }
        new Thread(new DoGptStuff()).start();
    }
}
