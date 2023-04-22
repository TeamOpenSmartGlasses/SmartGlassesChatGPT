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
import com.teamopensourcesmartglasses.chatgpt.events.OpenAIApiKeyProvidedEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.UUID;
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
//    private boolean newScreen = true;
    private boolean userTurnLabelSet = false;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private Future<?> future;
    private boolean openAiKeyProvided = false;

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
        // Each command has a UUID, trigger phrases and description
        UUID startChatCommandUUID = UUID.fromString("c3b5bbfd-4416-4006-8b40-12346ac3abcf");

        // Define list of phrases to be used to trigger each command
        String[] startChatTriggerPhrases = new String[] { "avocado" };

        //Create command objects
        SGMCommand startChatCommand = new SGMCommand(appName, startChatCommandUUID, startChatTriggerPhrases, "Start a ChatGPT session for your smart glasses!");

        //Register the command
        sgmLib.registerCommand(startChatCommand, this::startChatCommandCallback);

        //Subscribe to transcription stream
        sgmLib.subscribe(DataStreamType.TRANSCRIPTION_ENGLISH_STREAM, this::processTranscriptionCallback);

        Log.d(TAG, "onCreate: ChatGPT service started!");

        /* Handle SmartGlassesChatGPT specific things */
        EventBus.getDefault().register(this);
        chatGptBackend = new ChatGptBackend();

        // Putting a separate sharedPreferences here instead of through the event bus from mainActivity
        // so I don't have to deal with waiting for this service to finish its startup
        SharedPreferences sharedPreferences = getSharedPreferences("user.config", Context.MODE_PRIVATE);
        if (sharedPreferences.contains("openAiKey")) {
            String savedKey = sharedPreferences.getString("openAiKey", "");
            EventBus.getDefault().post(new OpenAIApiKeyProvidedEvent(savedKey));
        } else {
            Log.d(TAG, "ChatGptService: No key exists");
        }

        // startTimer();
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
        if (!openAiKeyProvided) {
            sgmLib.sendReferenceCard("Unable to use ChatGpt", "No openAI key has been provided, please enter a openAI api key in the ChatGpt App");
            return;
        }

        //request to be the in focus app so we can continue to show transcripts
        sgmLib.requestFocus(this::focusChangedCallback);
    }

    public void focusChangedCallback(FocusStates focusState){
        Log.d(TAG, "Focus callback called with state: " + focusState);
        this.focusState = focusState;

        //StartScrollingText to show our translation
        if (focusState.equals(FocusStates.IN_FOCUS)) {
            sgmLib.startScrollingText("Input prompt: ");
            Log.d(TAG, "startChatCommandCallback: Added a scrolling text view");

            messageBuffer = new StringBuffer();
            sgmLib.pushScrollingText(">>> Conversation started");
        }
    }

    public void processTranscriptionCallback(String transcript, long timestamp, boolean isFinal){
        // Don't execute if we're not in focus
        if (!focusState.equals(FocusStates.IN_FOCUS)){
            return;
        }

        // We want to send our message in our message buffer when we stop speaking for like 9 seconds
        // If the transcript is finalized, then we add it to our buffer, and reset our timer
        if (isFinal && openAiKeyProvided){
            Log.d(TAG, "processTranscriptionCallback: " + transcript);
            messageBuffer.append(transcript);
            messageBuffer.append(" ");

            if (!userTurnLabelSet) {
                transcript = "User: " + transcript;
                userTurnLabelSet = true;
            }
            sgmLib.pushScrollingText(transcript);

            // Cancel the scheduled job if we get a new transcript
            if (future != null) {
                future.cancel(false);
                Log.d(TAG, "processTranscriptionCallback: Cancelled scheduled job");
            }

            future = executorService.schedule(() -> {
                String message = messageBuffer.toString();
                
                if (!message.isEmpty()) {
                    chatGptBackend.sendChat(messageBuffer.toString());
                    messageBuffer = new StringBuffer();
                    Log.d(TAG, "processTranscriptionCallback: Ran scheduled job and sent message");
                } else {
                    Log.d(TAG, "processTranscriptionCallback: Message is empty");
                }
            }, 5, TimeUnit.SECONDS);
        }
    }

    @Subscribe
    public void onChatReceived(ChatReceivedEvent event) {
        sgmLib.pushScrollingText("ChatGpt: " + event.message.trim());
        userTurnLabelSet = false;
    }

    @Subscribe
    public void onChatError(ChatErrorEvent event) {
        sgmLib.sendReferenceCard("Something wrong with ChatGpt", event.getErrorMessage());
    }

    @Subscribe
    public void onOpenAIApiKeyProvided(OpenAIApiKeyProvidedEvent event) {
        Log.d(TAG, "onOpenAIApiKeyProvided: Enabling ChatGpt command");
        openAiKeyProvided = true;
        chatGptBackend.clearMessages();
    }
}
