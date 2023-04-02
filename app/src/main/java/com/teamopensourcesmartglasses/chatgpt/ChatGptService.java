package com.teamopensourcesmartglasses.chatgpt;

import android.util.Log;

import com.teamopensmartglasses.sgmlib.DataStreamType;
import com.teamopensmartglasses.sgmlib.SGMCommand;
import com.teamopensmartglasses.sgmlib.SGMLib;
import com.teamopensmartglasses.sgmlib.SmartGlassesAndroidService;
import com.teamopensourcesmartglasses.chatgpt.events.ChatReceivedEvent;

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
    public ChatGptBackend chatGptBackend;

    //our instance of the SGM library
    public SGMLib sgmLib;

    public StringBuffer messageBuffer = new StringBuffer();
    private boolean newScreen = true;
    private boolean userTurnLabelSet = false;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private Future<?> future;

    // Todo: make this app only usable if key is provided

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

        /* Handle SGMLib specific things */

        // Create SGMLib instance with context: this
        sgmLib = new SGMLib(this);

        // Define commands
        // Each command has a UUID, trigger phrases and description
        UUID startChatCommandUUID = UUID.fromString("c3b5bbfd-4416-4006-8b40-12346ac37aec");

        // Define list of phrases to be used to trigger each command
        String[] startChatTriggerPhrases = new String[] { "Start chat session", "Launch chat", "Let's talk", "Hi Michael" };

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

        // startTimer();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: Called");
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    public void startChatCommandCallback(String args, long commandTriggeredTime) {
        Log.d(TAG, "startChatCommandCallback: Start ChatGPT command callback called");

        if (newScreen) {
            newScreen = false;
            sgmLib.startScrollingText("Input prompt");
            Log.d(TAG, "startChatCommandCallback: Added a scrolling text view");
        }

        messageBuffer = new StringBuffer();
        sgmLib.pushScrollingText(">>> Conversation started");
    }

    public void processTranscriptionCallback(String transcript, long timestamp, boolean isFinal){
        // We want to send our message in our message buffer when we stop speaking for like 9 seconds
        // If the transcript is finalized, then we add it to our buffer, and reset our timer
        if(!newScreen && isFinal){
            Log.d(TAG, messageBuffer.toString());
            messageBuffer.append(transcript);
            messageBuffer.append(" ");

            if (!userTurnLabelSet) {
                sgmLib.pushScrollingText(">>> User says:");
                userTurnLabelSet = true;
            }
            sgmLib.pushScrollingText(transcript);

            if (newScreen) return;

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
        sgmLib.pushScrollingText(">>> ChatGpt Response:");
        sgmLib.pushScrollingText(event.message);
        userTurnLabelSet = false;
    }
}
