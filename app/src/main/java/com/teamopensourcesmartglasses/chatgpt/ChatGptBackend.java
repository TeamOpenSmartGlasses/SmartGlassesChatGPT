package com.teamopensourcesmartglasses.chatgpt;

import android.util.Log;

import com.teamopensourcesmartglasses.chatgpt.events.ChatErrorEvent;
import com.teamopensourcesmartglasses.chatgpt.events.ChatReceivedEvent;
import com.teamopensourcesmartglasses.chatgpt.events.QuestionAnswerReceivedEvent;
import com.teamopensourcesmartglasses.chatgpt.utils.MessageStore;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;

import org.greenrobot.eventbus.EventBus;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

public class ChatGptBackend {
    public final String TAG = "SmartGlassesChatGpt_ChatGptBackend";
    private OpenAiService service;
//    private final List<ChatMessage> messages = new ArrayList<>();
    private final MessageStore messages;
    // private StringBuffer responseMessageBuffer = new StringBuffer();
    private final int chatGptMaxTokenSize = 3500; // let's play safe and use the 3500 out of 4096
    private final int messageDefaultWordsChunkSize = 100;
    private final int openAiServiceTimeoutDuration = 110;
    private StringBuffer recordingBuffer = new StringBuffer();

//    public static void setApiToken(String token) {
//        Log.d("SmartGlassesChatGpt_ChatGptBackend", "setApiToken: token set");
//        apiToken = token;
//        EventBus.getDefault().post(new OpenAIApiKeyProvidedEvent(token));
//    }

    public ChatGptBackend(){
        messages = new MessageStore(chatGptMaxTokenSize);
    }

    public void initChatGptService(String token, String systemMessage) {
        // Setup ChatGpt with a token
        service = new OpenAiService(token, Duration.ofSeconds(openAiServiceTimeoutDuration));
        messages.setSystemMessage(systemMessage);
    }

    public void sendChatToMemory(String message) {
        // Add to messages here if it is just to record
        // It should be chunked into a decent block size
        Log.d(TAG, "sendChat: In record mode");
        recordingBuffer.append(message);
        recordingBuffer.append(" ");
        Log.d(TAG, "sendChatToMemory: " + recordingBuffer);

        if (getWordCount(recordingBuffer.toString()) > messageDefaultWordsChunkSize) {
            Log.d(TAG, "sendChatToMemory: size is big enough to be a chunk");
            messages.addMessage(ChatMessageRole.USER.value(), recordingBuffer.toString());
            recordingBuffer = new StringBuffer();
        }
    }

    public void sendChatToGpt(String message, ChatGptAppMode mode){
        // Don't run if openAI service is not initialized yet
        if (service == null) {
            EventBus.getDefault().post(new ChatErrorEvent("OpenAi Key has not been provided yet. Please do so in the app."));
            return;
        }

        // If there is still words from a previous record session, then add them into the messageQueue
        if (recordingBuffer.length() != 0) {
            Log.d(TAG, "sendChatToGpt: There are still words from a recording, adding them to chunk");
            messages.addMessage(ChatMessageRole.USER.value(), recordingBuffer.toString());
            recordingBuffer = new StringBuffer();
        }

        // Just an example of how this would work (pulled mostly from openai-java library example)...:
        // 1. Send message to ChatGPT
        // 2. Wait for response
        // 3. On response, post a "ChatGptResponseReceivedEvent" with the data
        // 3a) Subscribe to that event in ChatGptService.java and do something with the data (ie: send reference card to smart glasses)

        class DoGptStuff implements Runnable {
            public void run(){
//                Log.d(TAG, "run: Doing gpt stuff, got message: " + message);
                messages.addMessage(ChatMessageRole.USER.value(), message);

//                Log.d(TAG, "run: New Message Stack: ");
//                for (ChatMessage message : messages) {
//                    Log.d(TAG, message.getRole() + ": " + message.getContent());
//                }

                // Todo: Change completions to streams
                ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                        .model("gpt-3.5-turbo")
                        .messages(messages.getAllMessages())
                        .n(1)
                        .build();

                try {
//                    Log.d(TAG, "run: Running ChatGpt completions request");

//                    Log.d(TAG, "run: Conversation so far");
//                    for (ChatMessage message:
//                            messages.getAllMessages()) {
//                        Log.d(TAG, "run: " + message.getContent());
//                    }

                    ChatCompletionResult result = service.createChatCompletion(chatCompletionRequest);
                    List<ChatMessage> responses = result.getChoices()
                                                        .stream()
                                                        .map(ChatCompletionChoice::getMessage)
                                                        .collect(Collectors.toList());

//                    long tokensUsed = result.getUsage().getTotalTokens();
//                    Log.d(TAG, "run: tokens used: " + tokensUsed + "/" + chatGptMaxTokenSize);

                    // Send a chat received response
                    ChatMessage response = responses.get(0);
//                    Log.d(TAG, "run: " + response.getContent());
//                    Log.d(TAG, "run: " + response.getContent());

                    // Send back to chat UI and internal history
                    if (mode == ChatGptAppMode.Conversation) {
                        EventBus.getDefault().post(new ChatReceivedEvent(response.getContent()));
                        messages.addMessage(response.getRole(), response.getContent());
                    }

                    // Send back one off question and answer
                    if (mode == ChatGptAppMode.Question) {
                        EventBus.getDefault().post(new QuestionAnswerReceivedEvent(message, response.getContent()));

                        // Edit the last user message to specify that it was a question
                        messages.addPrefixToLastAddedMessage("User asked a question: ");
                        // Specify on the answer side as well
                        messages.addMessage(response.getRole(), "Assistant responded with an answer: " + response.getContent());
                    }
                } catch (Exception e){
//                    Log.d(TAG, "run: encountered error: " + e.getMessage());
                    EventBus.getDefault().post(new ChatErrorEvent("Check if you had set your key correctly or view the error below" + e.getMessage()));
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

    private void clearSomeMessages() {
        for (int i = 0; i < messages.size() / 2; i++) {
//            Log.d(TAG, "sendChat: Clearing some chat context");
            messages.removeFirst();
        }
    }

    public void clearConversationContext() {
        messages.resetMessages();
    }
}
