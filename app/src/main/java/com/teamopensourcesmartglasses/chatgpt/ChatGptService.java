package com.teamopensourcesmartglasses.chatgpt;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.teamopensmartglasses.sgmlib.DataStreamType;
import com.teamopensmartglasses.sgmlib.FocusStates;
import com.teamopensmartglasses.sgmlib.SGMCommand;
import com.teamopensmartglasses.sgmlib.SGMLib;
import com.teamopensmartglasses.sgmlib.SmartGlassesAndroidService;
import com.teamopensourcesmartglasses.chatgpt.events.ChatErrorEvent;
import com.teamopensourcesmartglasses.chatgpt.events.ChatReceivedEvent;
import com.teamopensourcesmartglasses.chatgpt.events.QuestionAnswerReceivedEvent;
import com.teamopensourcesmartglasses.chatgpt.events.UserSettingsChangedEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ChatGptService extends SmartGlassesAndroidService {
    public final String TAG = "SmartGlassesChatGpt_ChatGptService";
    static final String appName = "SmartGlassesChatGpt";

    //our instance of the SGM library
    public SGMLib sgmLib;
    public FocusStates focusState;
    public ChatGptBackend chatGptBackend;

    public StringBuffer messageBuffer = new StringBuffer();
    private boolean userTurnLabelSet = false;
    private boolean chatGptLabelSet = false;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private ExecutorService printExecutorService;
    private Future<?> future;
    private boolean openAiKeyProvided = false;
    private ChatGptAppMode mode = ChatGptAppMode.Inactive;
    private boolean useAutoSend;
    private ArrayList<String> commandWords;
    private String scrollingTextTitle = "";
    private final int messageDisplayDurationMs = 3000;

    public ChatGptService(){
        super(MainActivity.class,
                "chatgpt_app",
                1011,
                appName,
                "ChatGPT for smart glasses", com.google.android.material.R.drawable.notify_panel_notification_icon_bg);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        focusState = FocusStates.OUT_FOCUS;

        /* Handle SGMLib specific things */

        // Create SGMLib instance with context: this
        sgmLib = new SGMLib(this);

        // Define commands
        SGMCommand startChatCommand = new SGMCommand(
                appName,
                UUID.fromString("c3b5bbfd-4416-4006-8b40-12346ac3abcf"),
                new String[] { "conversation" },
                "Start a ChatGPT session for your smart glasses!"
        );
        SGMCommand askGptCommand = new SGMCommand(
                appName,
                UUID.fromString("c367ba2d-4416-8768-8b15-19046ac3a2af"),
                new String[] { "question" },
                "Ask a one shot question to ChatGpt based on your existing context"
        );
        SGMCommand clearContextCommand = new SGMCommand(
                appName,
                UUID.fromString("2b8d1ba0-f114-11ed-a05b-0242ac120003"),
                new String[] { "clear" },
                "Clear your conversation context"
        );
        SGMCommand recordConversationCommand = new SGMCommand(
                appName,
                UUID.fromString("ea89a5ac-6cbd-4867-bd86-1ebce9a27cb3"),
                new String[] { "listen" },
                "Record your conversation so you can ask ChatGpt for questions later on"
        );

        // Save all the wake words so we can detect and remove them during transcriptions with just 1 word
        commandWords = new ArrayList<>();
        commandWords.addAll(startChatCommand.getPhrases());
        commandWords.addAll(askGptCommand.getPhrases());
        commandWords.addAll(clearContextCommand.getPhrases());
        commandWords.addAll(recordConversationCommand.getPhrases());

        // Register the command
        sgmLib.registerCommand(startChatCommand, this::startChatCommandCallback);
        sgmLib.registerCommand(askGptCommand, this::askGptCommandCallback);
        sgmLib.registerCommand(recordConversationCommand, this::recordConversationCommandCallback);
        sgmLib.registerCommand(clearContextCommand, this::clearConversationContextCommandCallback);

        //Subscribe to transcription stream
        sgmLib.subscribe(DataStreamType.TRANSCRIPTION_ENGLISH_STREAM, this::processTranscriptionCallback);

        Log.d(TAG, "onCreate: ChatGPT service started!");

        /* Handle SmartGlassesChatGPT specific things */
        EventBus.getDefault().register(this);
        chatGptBackend = new ChatGptBackend();

        // Putting a separate sharedPreferences here instead of through the event bus from mainActivity
        // so I don't have to deal with waiting for this service to finish its startup
        SharedPreferences sharedPreferences = getSharedPreferences("user.config", Context.MODE_PRIVATE);
        String savedKey = sharedPreferences.getString("openAiKey", "");
        if (savedKey.isEmpty()) {
            // do not start the backend if there is no key, wait for onUserSettingsChanged to init
//            Log.d(TAG, "ChatGptService: No key exists");
            return ;
        }
        String systemPrompt = sharedPreferences.getString("systemPrompt", "");
        if (systemPrompt.isEmpty()) {
//            Log.d(TAG, "onCreate: empty system prompt, setting it to use the default one");
            systemPrompt = "Act as a the no 1 ambassador for smart glasses and generative pretrained ai! Find ways to bring up these topics as creative as possible!";
        }
        chatGptBackend.initChatGptService(savedKey, systemPrompt);
        useAutoSend = sharedPreferences.getBoolean("autoSendMessages", true);
        openAiKeyProvided = true;

//        Log.d(TAG, "onCreate: Saved openAi key found, token = " + savedKey);
//        Log.d(TAG, "onCreate: systemPrompt = " + systemPrompt);
//        Log.d(TAG, "onCreate: useAutoSend = " + useAutoSend);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: Called");
        EventBus.getDefault().unregister(this);
        sgmLib.deinit();
        super.onDestroy();
    }

    public void startChatCommandCallback(String args, long commandTriggeredTime) {
        Log.d(TAG, "startChatCommandCallback: Start ChatGPT command callback called");
        Log.d(TAG, "startChatCommandCallback: OpenAiApiKeyProvided:" + openAiKeyProvided);

        scrollingTextTitle = "Conversation";
        // request to be the in focus app so we can continue to show transcripts
        sgmLib.requestFocus(this::focusChangedCallback);

        mode = ChatGptAppMode.Conversation;
        Log.d(TAG, "startChatCommandCallback: Set app mode to conversation");

        // we might had been in the middle of a question, so when we switch back to a conversation,
        // we reset our messageBuffer
        resetUserMessage();
    }

    public void askGptCommandCallback(String args, long commandTriggeredTime) {
        Log.d(TAG, "askGptCommandCallback: Ask ChatGPT command callback called");
//        Log.d(TAG, "askGptCommandCallback: OpenAiApiKeyProvided:" + openAiKeyProvided);

        // request to be the in focus app so we can continue to show transcripts
        scrollingTextTitle = "Question";
        sgmLib.requestFocus(this::focusChangedCallback);

        mode = ChatGptAppMode.Question;
        Log.d(TAG, "askGptCommandCommand: Set app mode to question");

        // we might had been in the middle of a conversation, so when we switch to a question,
        // we need to reset our messageBuffer
        resetUserMessage();
    }

    public void recordConversationCommandCallback(String args, long commandTriggeredTime) {
        Log.d(TAG, "askGptCommandCallback: Record conversation command callback called");

        scrollingTextTitle = "Listening";
        // request to be the in focus app so we can continue to show transcripts
        sgmLib.requestFocus(this::focusChangedCallback);
        mode = ChatGptAppMode.Record;
        Log.d(TAG, "askGptCommandCommand: Set app mode to record conversation");

        // we might had been in the middle of a conversation, so when we switch to a question,
        // we need to reset our messageBuffer
        resetUserMessage();
    }

    public void clearConversationContextCommandCallback(String args, long commandTriggeredTime) {
        Log.d(TAG, "askGptCommandCallback: Reset conversation context");

        sgmLib.sendReferenceCard("Clear context", "Cleared conversation context");
        mode = ChatGptAppMode.Inactive;

        chatGptBackend.clearConversationContext();

        // we might had been in the middle of a conversation, so when we switch to a question,
        // we need to reset our messageBuffer
        resetUserMessage();
    }

    public void focusChangedCallback(FocusStates focusState){
        Log.d(TAG, "Focus callback called with state: " + focusState);
        this.focusState = focusState;

        sgmLib.stopScrollingText();
        //StartScrollingText to show our translation
        if (focusState.equals(FocusStates.IN_FOCUS)) {
            sgmLib.startScrollingText(scrollingTextTitle);
            Log.d(TAG, "startChatCommandCallback: Added a scrolling text view");

            messageBuffer = new StringBuffer();
        }
    }

    public void processTranscriptionCallback(String transcript, long timestamp, boolean isFinal){
        // Don't execute if we're not in focus or no mode is set
        if (!focusState.equals(FocusStates.IN_FOCUS) || mode == ChatGptAppMode.Inactive){
            return;
        }

        // We can ignore command phrases so it is more accurate, tested that this works
        if (isFinal && commandWords.contains(transcript)) {
            return;
        }

        // If its recording we just save it to memory without even the need to finish the sentence
        // It will be saved as a ChatMessage
        if (isFinal && mode == ChatGptAppMode.Record) {
//            Log.d(TAG, "processTranscriptionCallback: " + transcript);
            chatGptBackend.sendChatToMemory(transcript);
            sgmLib.pushScrollingText(transcript);
        }

        // We want to send our message in our message buffer when we stop speaking for like 9 seconds
        // If the transcript is finalized, then we add it to our buffer, and reset our timer
        if (isFinal && mode != ChatGptAppMode.Record && openAiKeyProvided){
            Log.d(TAG, "processTranscriptionCallback: " + transcript);

            if (useAutoSend) {
                messageBuffer.append(transcript);
                messageBuffer.append(" ");
                // Cancel the scheduled job if we get a new transcript
                if (future != null) {
                    future.cancel(false);
                    Log.d(TAG, "processTranscriptionCallback: Cancelled scheduled job");
                }
                future = executorService.schedule(this::sendMessageToChatGpt, 7, TimeUnit.SECONDS);
            } else {
                if (Objects.equals(transcript, "send message")) {
                    sendMessageToChatGpt();
                } else {
                    messageBuffer.append(transcript);
                    messageBuffer.append(" ");
                }
            }

            if (!userTurnLabelSet) {
                transcript = "User: " + transcript;
                userTurnLabelSet = true;
            }
            sgmLib.pushScrollingText(transcript);
        }
    }

    private void sendMessageToChatGpt() {
        String message = messageBuffer.toString();
        if (!message.isEmpty()) {
            chatGptBackend.sendChatToGpt(message, mode);
            messageBuffer = new StringBuffer();
            Log.d(TAG, "processTranscriptionCallback: Ran scheduled job and sent message");
        } else {
            Log.d(TAG, "processTranscriptionCallback: Can't send because message is empty");
        }
    }

    private void resetUserMessage() {
        // Cancel the scheduled job if we get a new transcript
        if (future != null) {
            future.cancel(false);
            Log.d(TAG, "resetUserMessage: Cancelled scheduled job");
        }
        messageBuffer = new StringBuffer();
    }

    @Subscribe
    public void onChatReceived(ChatReceivedEvent event) {
        printExecutorService = Executors.newSingleThreadExecutor();
        printExecutorService.execute(() -> {
            String[] words = event.message.split("\\s+");
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

                if (!chatGptLabelSet) {
                    sgmLib.pushScrollingText("ChatGpt: " + groupText.trim());
                    chatGptLabelSet = true;
                } else {
                    sgmLib.pushScrollingText(groupText.trim());
                }

                try {
                    Thread.sleep(messageDisplayDurationMs); // Delay of 3 second (1000 milliseconds)
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    // Restore interrupted status and return from the thread
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            chatGptLabelSet = false;
        });
        userTurnLabelSet = false;
    }

    @Subscribe
    public void onQuestionAnswerReceived(QuestionAnswerReceivedEvent event) {
        String body = "Q: " + event.getQuestion() + "\n\n" + "A: " + event.getAnswer();
        sgmLib.sendReferenceCard("AskGpt", body);
        mode = ChatGptAppMode.Inactive;
    }

    @Subscribe
    public void onChatError(ChatErrorEvent event) {
        sgmLib.sendReferenceCard("Something wrong with ChatGpt", event.getErrorMessage());
    }

    @Subscribe
    public void onUserSettingsChanged(UserSettingsChangedEvent event) {
        Log.d(TAG, "onUserSettingsChanged: Enabling ChatGpt with new api key = " + event.getOpenAiKey());
        Log.d(TAG, "onUserSettingsChanged: System prompt = " + event.getSystemPrompt());
        chatGptBackend.initChatGptService(event.getOpenAiKey(), event.getSystemPrompt());
        openAiKeyProvided = true;

        Log.d(TAG, "onUserSettingsChanged: Auto send messages after finish speaking = " + event.getUseAutoSend());
        useAutoSend = event.getUseAutoSend();
    }
}
