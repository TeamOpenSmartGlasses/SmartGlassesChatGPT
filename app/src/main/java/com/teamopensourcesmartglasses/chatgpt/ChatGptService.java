package com.teamopensourcesmartglasses.chatgpt;

import android.util.Log;

import com.teamopensmartglasses.sgmlib.DataStreamType;
import com.teamopensmartglasses.sgmlib.SGMCommand;
import com.teamopensmartglasses.sgmlib.SGMLib;
import com.teamopensmartglasses.sgmlib.SmartGlassesAndroidService;
import com.teamopensourcesmartglasses.chatgpt.events.ChatReceivedEvent;
import com.teamopensourcesmartglasses.chatgpt.events.ClearMessagesEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.Timer;
import java.util.UUID;

public class ChatGptService extends SmartGlassesAndroidService {
    public final String TAG = "SmartGlassesChatGpt_ChatGptService";
    static final String appName = "SmartGlassesChatGpt";
    public ChatGptBackend chatGptBackend;

    //our instance of the SGM library
    public SGMLib sgmLib;

    public StringBuffer messageBuffer = new StringBuffer();
    public final int DELAY_MS = 9000; // 9 seconds
    public final Timer timer = new Timer();
    private boolean newScreen = true;
    private boolean userTurnLabelSet = false;
    private int transcriptCounter = 0;
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

        Log.d(TAG, "CHATGPT SERVICE STARTED");

        /* Handle SmartGlassesChatGPT specific things */
        EventBus.getDefault().register(this);
        chatGptBackend = new ChatGptBackend();

        // startTimer();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy called");
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    public void startChatCommandCallback(String args, long commandTriggeredTime) {
        Log.d(TAG,"Start ChatGPT command callback called");

        if (newScreen) {
            newScreen = false;
            sgmLib.startScrollingText("Input prompt");
            Log.d(TAG, "Added a scrolling text view");
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

            // Send transcript every 4 messages
            transcriptCounter += 1;
            // Todo: make this use a timer
            if (transcriptCounter % 4 == 0) {
                chatGptBackend.sendChat(messageBuffer.toString());
                messageBuffer = new StringBuffer();
                Log.d(TAG, "Sent a message to chatgpt backend");
            }

            // resetTimer();
        }
    }

//    private void startTimer() {
//        // If the timer completes, then we send the transcript and clear our buffer
//        timer.schedule(new TimerTask() {
//            @Override
//            public void run() {
//                String message = messageBuffer.toString().trim();
//                if (!message.isEmpty()) {
//                    // chatGptBackend.sendChat(message);
//                    sgmLib.sendReferenceCard("Prompt", message);
//                    Log.d(TAG, "run: message is not empty, sent message" + message);
//                    messageBuffer = new StringBuffer();
//                }
//            }
//        }, DELAY_MS);
//    }

//    private void stopTimer() {
//        timer.cancel();
//    }
//
//    private void resetTimer() {
//        timer.cancel();
//        startTimer();
//        Log.d(TAG, "Timer reseted");
//    }

    @Subscribe
    public void onChatReceived(ChatReceivedEvent event) {
        sgmLib.pushScrollingText(">>> ChatGpt Response:");
        sgmLib.pushScrollingText(event.message);
        userTurnLabelSet = false;
    }
}
