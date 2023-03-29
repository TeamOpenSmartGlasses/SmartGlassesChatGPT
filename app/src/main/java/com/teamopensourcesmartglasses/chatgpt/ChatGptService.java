package com.teamopensourcesmartglasses.chatgpt;

import android.util.Log;

import com.teamopensmartglasses.sgmlib.DataStreamType;
import com.teamopensmartglasses.sgmlib.SGMCommand;
import com.teamopensmartglasses.sgmlib.SGMLib;
import com.teamopensmartglasses.sgmlib.SmartGlassesAndroidService;

import org.greenrobot.eventbus.EventBus;

import java.util.UUID;

public class ChatGptService extends SmartGlassesAndroidService {
    public final String TAG = "SmartGlassesChatGpt_ChatGptService";
    static final String appName = "SmartGlassesChatGpt";
    public ChatGptBackend chatGptBackend;

    //our instance of the SGM library
    public SGMLib sgmLib;

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

        //Create SGMLib instance with context: this
        sgmLib = new SGMLib(this);

        //Define command with a UUID
        UUID commandUUID = UUID.fromString("c3b5bbfd-4416-4006-8b40-73486ac37aec");

        //Define list of phrases to be used to trigger the command
        //currently just "Chat" as I don't know how our ASR would interpret "CHAT GEE-PEE-TEE"
        String[] triggerPhrases = new String[]{"Chat"};

        //Create command object
        SGMCommand command = new SGMCommand(appName, commandUUID, triggerPhrases, "ChatGPT for your smart glasses!");

        //Register the command
        sgmLib.registerCommand(command, this::chatGptCommandCallback);

        //Subscribe to transcription stream
        sgmLib.subscribe(DataStreamType.TRANSCRIPTION_ENGLISH_STREAM, this::processTranscriptionCallback);

        Log.d(TAG, "CHATGPT SERVICE STARTED");

        /* Handle SmartGlassesChatGPT specific things */

        EventBus.getDefault().register(this);

        chatGptBackend = new ChatGptBackend();

        chatGptBackend.sendChat("blah");

        Log.d(TAG, "SERVO START");
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy called");
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    public void chatGptCommandCallback(String args, long commandTriggeredTime) {
        Log.d(TAG,"ChatGPT command callback called");
    }

    public void processTranscriptionCallback(String transcript, long timestamp, boolean isFinal){
        Log.d(TAG, "Received transcription from SGM");

        // Just an example of what you might want to do here...
        if(isFinal){
            chatGptBackend.sendChat(transcript);
        }
    }

}
