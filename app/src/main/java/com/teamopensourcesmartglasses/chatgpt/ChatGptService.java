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
import java.util.TimerTask;
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
    private boolean inChat;

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
        UUID startChatCommandUUID = UUID.fromString("c3b5bbfd-4416-4006-8b40-73486ac37aec");
        UUID endChatCommandUUID = UUID.fromString("c3b5bbfd-4416-4006-8b40-73486ac37aed");
        UUID sendChatCommandUUID = UUID.fromString("c3b5bbfd-4416-4006-8b40-73486ac37aee");
        UUID resetChatCommandUUID = UUID.fromString("c3b5bbfd-4416-4006-8b40-73486ac37aef");

        // Define list of phrases to be used to trigger each command
        String[] startChatTriggerPhrases = new String[] { "Start chat session", "Launch chat", "Let's talk", "Hi Michael" };
        String[] endChatTriggerPhrases = new String[] { "End chat session", "Stop chat", "Goodbye" };
        String[] sendChatTriggerPhrases = new String[] { "Send message", "Process message" };
        String[] resetChatTriggerPhrases = new String[] { "Reset chat", "Clear chat" };

        //Create command objects
        SGMCommand startChatCommand = new SGMCommand(appName, startChatCommandUUID, startChatTriggerPhrases, "Start a ChatGPT session for your smart glasses!");
        SGMCommand endChatCommand = new SGMCommand(appName, endChatCommandUUID, endChatTriggerPhrases, "End a ChatGPT session for your smart glasses!");
        SGMCommand sendChatCommand = new SGMCommand(appName, sendChatCommandUUID, sendChatTriggerPhrases, "Send your message to ChatGPT");
        SGMCommand resetChatCommand = new SGMCommand(appName, resetChatCommandUUID, resetChatTriggerPhrases, "Reset your ChatGPT session");

        //Register the command
        sgmLib.registerCommand(startChatCommand, this::startChatCommandCallback);
        sgmLib.registerCommand(endChatCommand, this::endChatCommandCallback);
        sgmLib.registerCommand(sendChatCommand, this::sendChatCommandCallback);
        sgmLib.registerCommand(resetChatCommand, this::resetChatCommandCallback);

        //Subscribe to transcription stream
        sgmLib.subscribe(DataStreamType.TRANSCRIPTION_ENGLISH_STREAM, this::processTranscriptionCallback);

        Log.d(TAG, "CHATGPT SERVICE STARTED");

        /* Handle SmartGlassesChatGPT specific things */
        EventBus.getDefault().register(this);
        chatGptBackend = new ChatGptBackend();

        // startTimer();

        Log.d(TAG, "SERVO START");
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy called");
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    public void startChatCommandCallback(String args, long commandTriggeredTime) {
        Log.d(TAG,"Start ChatGPT command callback called");

        if (!inChat) {
            inChat = true;
            messageBuffer = new StringBuffer();
            sgmLib.startScrollingText("Input prompt");
            Log.d(TAG, "Added a scrolling text view");
        }

        sgmLib.pushScrollingText(">>> Conversation started");
    }
    public void endChatCommandCallback(String args, long commandTriggeredTime) {
        if (!inChat) {
            Log.d(TAG,"Can't end ChatGPT session if it has not started");
            return;
        }

        Log.d(TAG,"End ChatGPT command callback called");

        sgmLib.stopScrollingText();
        inChat = false;
        messageBuffer = new StringBuffer();
    }
    public void sendChatCommandCallback(String args, long commandTriggeredTime) {
        if (!inChat) {
            Log.d(TAG, "Can't send a message if chat has not been started yet");
            return;
        }
        if (messageBuffer.length() == 0) {
            Log.d(TAG, "Can't send a message if there is no prompt");
            return;
        }

        Log.d(TAG,"Send ChatGPT command callback called");
        chatGptBackend.sendChat(messageBuffer.toString());
        messageBuffer = new StringBuffer();
    }
    public void resetChatCommandCallback(String args, long commandTriggeredTime) {
        if (!inChat) {
            Log.d(TAG, "Can't reset a chat session if chat has not been started yet");
            return;
        }

        Log.d(TAG,"Reset ChatGPT command callback called");

        // Clear current buffer and clear chat context
        messageBuffer = new StringBuffer();
        EventBus.getDefault().post(new ClearMessagesEvent());

        // Add a message to the scrolling text view to emphasis previous history is gone
        // Not sure if can call start scrolling view again
        sgmLib.pushScrollingText(">>> Cleared conversation");
    }

    public void processTranscriptionCallback(String transcript, long timestamp, boolean isFinal){
        // We want to send our message in our message buffer when we stop speaking for like 9 seconds
        // If the transcript is finalized, then we add it to our buffer, and reset our timer
        if(isFinal && inChat){
            Log.d(TAG, messageBuffer.toString());
            messageBuffer.append(transcript);
            messageBuffer.append(" ");
            sgmLib.pushScrollingText(transcript);
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
        // sgmLib.pushScrollingText(event.message); didn't work for me, so displaying it in a card for now
        sgmLib.sendReferenceCard("ChatGpt Response:", event.message);
    }
}
