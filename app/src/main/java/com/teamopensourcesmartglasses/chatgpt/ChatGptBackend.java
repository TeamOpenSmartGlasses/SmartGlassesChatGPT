package com.teamopensourcesmartglasses.chatgpt;

import android.util.Log;

import com.teamopensourcesmartglasses.chatgpt.events.ChatErrorEvent;
import com.teamopensourcesmartglasses.chatgpt.events.ChatReceivedEvent;
import com.teamopensourcesmartglasses.chatgpt.events.ChatSummarizedEvent;
import com.teamopensourcesmartglasses.chatgpt.events.QuestionAnswerReceivedEvent;
import com.teamopensourcesmartglasses.chatgpt.utils.MessageStore;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionChunk;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;

import org.greenrobot.eventbus.EventBus;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
            private static final int BUFFER_LENGTH = 5;
            private static final long MIN_INTERVAL = 3000; // 3 seconds
            private static final int CHUNK_SIZE = 120;
            private StringBuffer chatGptResponseStreamBuffer = new StringBuffer();
            private Timer timer = new Timer();
            private boolean inProgress = false;
            private ExecutorService printExecutorService;
            private StringBuffer responseMessage = new StringBuffer();

            public DoGptStuff() {
                printExecutorService = Executors.newSingleThreadExecutor();
            }

            public void run(){

                // Build prompt here
                ArrayList<ChatMessage> context;
                if (mode != ChatGptAppMode.Summarize) {
                    context = messages.getAllMessages();
                } else {
                    context = messages.getAllMessagesWithoutSystemPrompt();
                    String startingPrompt = "The following text below is a transcript of a conversation, but we have no idea who is speaking, " +
                            "but that doesn't matter. I need your help to summarize whatever useful information you can get from the text below. " +
                            "I want you to answer as concise as possible, the topics involved in the text, and I want it in bullet form. " +
                            "You don't need to answer in full sentences as well, just tell me the summary on a very high level. " +
                            "The transcript will also be really messy, but please do not reply with something like 'The conversation was " +
                            "hard to follow' or 'The conversation was messy' or comments like " +
                            "'Detected that the conversation was difficult to make sense of and did not contain much useful information. No clear topics can be detected', " +
                            "just do the best you can to provide a summary of the most important points from the text! \n\n " +
                            "For example, the summary output can look like this \n" +
                            "Detected that the user was talking about \n " +
                            "- <key point 1> \n " +
                            "- <key point 2> \n " +
                            "- <key point 3> and so on \n\n " +
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

                ChatCompletionResult result = service.createChatCompletion(chatCompletionRequest);

                Log.d(TAG, "Streaming chat completion");
                service.streamChatCompletion(chatCompletionRequest)
                        .doOnError(this::onStreamChatGptError)
                        .doOnComplete(this::onStreamComplete)
                        .blockingForEach(this::onItemReceivedFromStream);
            }

            private void onStreamChatGptError(Throwable throwable) {
                Log.d(TAG, throwable.getMessage());
                EventBus.getDefault().post(new ChatErrorEvent(throwable.getMessage()));
                throwable.printStackTrace();
            }

            public synchronized void onItemReceivedFromStream(ChatCompletionChunk chunk) {
                String textChunk = chunk.getChoices().get(0).getMessage().getContent();
                if (textChunk == null) return;

                Log.d(TAG, "Chunk received from stream: " + textChunk);

                responseMessage.append(textChunk);

                // Send back to chat UI and internal history whenever a chunk is ready
                Log.d(TAG, "onItemReceivedFromStream: mode");
                if (mode == ChatGptAppMode.Conversation) {
                    chatGptResponseStreamBuffer.append(textChunk);
                    Log.d(TAG, "onItemReceivedFromStream: " + chatGptResponseStreamBuffer.length());
                    if (chatGptResponseStreamBuffer.length() > CHUNK_SIZE && !inProgress) {
                        scheduleReplyChunkProcessing();
                    }
                }
            }

            private void scheduleReplyChunkProcessing() {
                Log.d(TAG, "scheduleReplyChunkProcessing: Called");
                TimerTask task = new TimerTask() {
                    @Override
                    public synchronized void run() {
                        Log.d(TAG, "processConvoReplyChunk: Called");
                        if (inProgress || chatGptResponseStreamBuffer.length() < CHUNK_SIZE) {
                            return;
                        }
                        inProgress = true;
                        // take front of buffer and send it to glasses event
                        String messageChunk = chatGptResponseStreamBuffer.substring(0, CHUNK_SIZE);
                        chatGptResponseStreamBuffer = new StringBuffer(chatGptResponseStreamBuffer.substring(120, chatGptResponseStreamBuffer.length()));

                        EventBus.getDefault().post(new ChatReceivedEvent(messageChunk));
                        Log.d(TAG, "onStreamComplete: Sent a message: " + messageChunk);

                        inProgress = false;
                    }
                };
                Log.d(TAG, "scheduleReplyChunkProcessing: Scheduled a task");
                timer.schedule(task, MIN_INTERVAL);
            }

            public void onStreamComplete() {
                messages.addMessage(ChatMessageRole.USER.value(), responseMessage.toString());
                responseMessage = new StringBuffer();

                if (mode == ChatGptAppMode.Conversation) {
                    timer.cancel();
                    printExecutorService.execute(() -> {
                        String[] words = chatGptResponseStreamBuffer.toString().split("\\s+");
                        int wordCount = words.length;
                        int groupSize = 23; // depends on glasses size

                        for (int i = 0; i < wordCount; i += groupSize) {
                            // Check if the background thread has been interrupted
                            if (Thread.currentThread().isInterrupted()) {
                                return;
                            }

                            int endIndex = Math.min(i + groupSize, wordCount);
                            String[] group = Arrays.copyOfRange(words, i, endIndex);
                            String groupText = String.join(" ", group);

                            EventBus.getDefault().post(new ChatReceivedEvent(groupText.trim()));
                            Log.d(TAG, "onStreamComplete: Sent a message: " + groupText.trim());

                            try {
                                Thread.sleep(MIN_INTERVAL); // Delay of 3 second (1000 milliseconds)
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                                // Restore interrupted status and return from the thread
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                    });
                }

                // Send back one off question and answer
                if (mode == ChatGptAppMode.Question) {
                    EventBus.getDefault().post(new QuestionAnswerReceivedEvent(message, responseMessage.toString()));

                    // Edit the last user message to specify that it was a question
                    messages.addPrefixToLastAddedMessage("User asked a question: ");
                    // Specify on the answer side as well
                    messages.addMessage(ChatMessageRole.ASSISTANT.value(), "Assistant responded with an answer: " + responseMessage.toString());
                }

                if (mode == ChatGptAppMode.Summarize) {
                    Log.d(TAG, "run: Sending a chat summarized event to service");
                    EventBus.getDefault().post(new ChatSummarizedEvent(responseMessage.toString()));
                }
            }
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
        chunkRemainingBufferContent();
        runChatGpt(null, ChatGptAppMode.Summarize);
    }

    public void clearConversationContext() {
        messages.resetMessages();
    }
}
