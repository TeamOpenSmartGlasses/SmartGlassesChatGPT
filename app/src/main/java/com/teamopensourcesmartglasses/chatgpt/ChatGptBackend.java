package com.teamopensourcesmartglasses.chatgpt;

import android.util.Log;

import com.teamopensourcesmartglasses.chatgpt.events.ChatErrorEvent;
import com.teamopensourcesmartglasses.chatgpt.events.ChatReceivedEvent;
import com.teamopensourcesmartglasses.chatgpt.events.ChatSummarizedEvent;
import com.teamopensourcesmartglasses.chatgpt.events.IsLoadingEvent;
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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ChatGptBackend {
    public final String TAG = "SmartGlassesChatGpt_ChatGptBackend";
    private OpenAiService service;
//    private final List<ChatMessage> messages = new ArrayList<>();
    private final MessageStore messages;
    // private StringBuffer responseMessageBuffer = new StringBuffer();
    private final int chatGptMaxTokenSize = 3500; // let's play safe and use the 3500 out of 4096, we will leave 500 for custom hardcoded prompts
    private final int messageDefaultWordsChunkSize = 100;
    private final int openAiServiceTimeoutDuration = 110;
    private StringBuffer recordingBuffer = new StringBuffer();

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

        chunkRemainingBufferContent();

        // Add the user message and pass the entire message context to chatgpt
        messages.addMessage(ChatMessageRole.USER.value(), message);
        runChatGpt(message, mode);
    }

    private void runChatGpt(String message, ChatGptAppMode mode) {
        class DoGptStuff implements Runnable {
            public void run(){

                // Build prompt here
                ArrayList<ChatMessage> context;
                if (mode != ChatGptAppMode.Summarize) {
                    context = messages.getAllMessages();
                } else {
                    context = messages.getAllMessagesWithoutSystemPrompt();
                    if (context.isEmpty()) {
                        EventBus.getDefault().post(new ChatErrorEvent("No conversation was recorded, unable to summarize."));
                        return;
                    }
                    String startingPrompt = "The following text below is a transcript of a conversation. I need your help to summarize the text below. " +
                            "The transcript will be really messy, your first task is to replace all parts of the text that do not makes sense with words or phrases that makes the most sense,  " +
                            "The transcript will be really messy, but you must not complain about the quality of the transcript, if it is bad, do not bring it up,  " +
                            "No matter what, don't ever complain that the transcript is messy or hard to follow, just tell me the summary and not anything else. " +
                            "After you are done replacing the words with ones that makes sense, I want you to summarize it, " +
                            "You don't need to answer in full sentences as well, be short and concise, just tell me the summary for me in bullet form, each point should be no longer than 20 words long. " +
                            "For the output, I don't want to see the transformed text, I just want the overall summary and it must follow this format, " +
                            "don't put everything in one paragraph, I need it in bullet form as I am working with a really tight schema! \n" +
                            "Detected that the user was talking about \n " +
                            "- <point 1> \n " +
                            "- <point 2> \n " +
                            "- <point 3> and so on \n\n " +
                            "The text can be found within the triple dollar signs here: \n " +
                            "$$$ \n";
                    context.add(0, new ChatMessage(ChatMessageRole.SYSTEM.value(), startingPrompt));
                    String endingPrompt = "\n $$$";
                    context.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), endingPrompt));
                }

                Log.d(TAG, "run: messages: ");
                for (ChatMessage message:
                     context) {
                    Log.d(TAG, "run: message: " + message.getContent());
                }

                // Todo: Change completions to streams
                ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                        .model("gpt-3.5-turbo")
                        .messages(context)
                        .n(1)
                        .build();

                EventBus.getDefault().post(new IsLoadingEvent());

                try {
                    ChatCompletionResult result = service.createChatCompletion(chatCompletionRequest);
                    List<ChatMessage> responses = result.getChoices()
                            .stream()
                            .map(ChatCompletionChoice::getMessage)
                            .collect(Collectors.toList());

                    // Send a chat received response
                    ChatMessage response = responses.get(0);

                    Log.d(TAG, "run: ChatGpt response: " + response.getContent());

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

                    if (mode == ChatGptAppMode.Summarize) {
                        Log.d(TAG, "run: Sending a chat summarized event to service");
                        EventBus.getDefault().post(new ChatSummarizedEvent(response.getContent()));
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
    
    private void chunkRemainingBufferContent() {
        // If there is still words from a previous record session, then add them into the messageQueue
        if (recordingBuffer.length() != 0) {
            Log.d(TAG, "sendChatToGpt: There are still words from a recording, adding them to chunk");
            messages.addMessage(ChatMessageRole.USER.value(), recordingBuffer.toString());
            recordingBuffer = new StringBuffer();
        }
    }

    private int getWordCount(String message) {
        String[] words = message.split("\\s+");
        return words.length;
    }

    private void clearSomeMessages() {
        for (int i = 0; i < messages.size() / 2; i++) {
            messages.removeOldest();
        }
    }

    public void summarizeContext() {
//        Log.d(TAG, "summarizeContext: Called");
        chunkRemainingBufferContent();
        runChatGpt(null, ChatGptAppMode.Summarize);
    }

    public void clearConversationContext() {
        messages.resetMessages();
        recordingBuffer = new StringBuffer();
    }
}
