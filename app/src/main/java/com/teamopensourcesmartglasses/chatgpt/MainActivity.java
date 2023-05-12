package com.teamopensourcesmartglasses.chatgpt;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.os.IBinder;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import com.teamopensourcesmartglasses.chatgpt.databinding.ActivityMainBinding;
import com.teamopensourcesmartglasses.chatgpt.events.UserSettingsChangedEvent;

import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private final String TAG = "SmartGlassesChatGpt_MainActivity";
    boolean mBound;
    public ChatGptService mService;
    private ActivityMainBinding binding;
    private Button submitButton;
    private EditText openAiKeyText;
    private RadioButton autoSendRadioButton;
    private RadioButton manualSendRadioButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        startChatGptService();

        SharedPreferences sharedPreferences = getSharedPreferences("user.config", Context.MODE_PRIVATE);

        // Display our previously saved settings
        openAiKeyText = findViewById(R.id.edittext_input);
        String savedOpenAiKey = sharedPreferences.getString("openAiKey", "");
        // Show toasts and populate openAI key text field if we have or don't have a key saved
        if (!savedOpenAiKey.isEmpty()) {
            openAiKeyText.setText(savedOpenAiKey);
            Toast.makeText(this, "OpenAI key found", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "No valid OpenAI key found, please add one", Toast.LENGTH_LONG).show();
        }

        autoSendRadioButton = findViewById(R.id.radioButton_autoSend);
        manualSendRadioButton = findViewById(R.id.radioButton_manualSend);
        boolean useAutoSendMethod = sharedPreferences.getBoolean("autoSendMessages", true);
        autoSendRadioButton.setChecked(useAutoSendMethod);
        manualSendRadioButton.setChecked(!useAutoSendMethod);

        // UI handlers
        submitButton = findViewById(R.id.submit_button);
        submitButton.setOnClickListener((v) -> {
            // Save to shared preference
            SharedPreferences.Editor editor = sharedPreferences.edit();

            String openAiApiKey = openAiKeyText.getText().toString().trim();
            editor.putString("openAiKey", openAiApiKey);
            boolean useAutoSendMessages = autoSendRadioButton.isChecked();
            editor.putBoolean("autoSendMessages", useAutoSendMessages);
            editor.apply();

            EventBus.getDefault().post(new UserSettingsChangedEvent(openAiApiKey, useAutoSendMessages));

            // Toast to inform user that key has been saved
            Toast.makeText(this, "Overall settings changed", Toast.LENGTH_LONG).show();
            Toast.makeText(this, "OpenAi key saved for future sessions", Toast.LENGTH_LONG).show();
        });
    }

    /* SGMLib */
    @Override
    protected void onResume() {
        super.onResume();

        //bind to foreground service
        bindChatGptService();
    }

    @Override
    protected void onPause() {
        super.onPause();

        //unbind foreground service
        unbindChatGptService();
    }

    public void stopChatGptService() {
        unbindChatGptService();
        if (!isMyServiceRunning(ChatGptService.class)) return;
        Intent stopIntent = new Intent(this, ChatGptService.class);
        stopIntent.setAction(ChatGptService.ACTION_STOP_FOREGROUND_SERVICE);
        startService(stopIntent);
    }

    public void sendChatGptServiceMessage(String message) {
        if (!isMyServiceRunning(ChatGptService.class)) return;
        Intent messageIntent = new Intent(this, ChatGptService.class);
        messageIntent.setAction(message);
        startService(messageIntent);
    }

    public void startChatGptService() {
        if (isMyServiceRunning(ChatGptService.class)){
            Log.d(TAG, "Not starting service.");
            return;
        }
        Log.d(TAG, "Starting service.");
        Intent startIntent = new Intent(this, ChatGptService.class);
        startIntent.setAction(ChatGptService.ACTION_START_FOREGROUND_SERVICE);
        startService(startIntent);
        bindChatGptService();
    }

    //check if service is running
    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public void bindChatGptService(){
        if (!mBound){
            Intent intent = new Intent(this, ChatGptService.class);
            bindService(intent, chatGptAppServiceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    public void unbindChatGptService() {
        if (mBound){
            unbindService(chatGptAppServiceConnection);
            mBound = false;
        }
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection chatGptAppServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            ChatGptService.LocalBinder sgmLibServiceBinder = (ChatGptService.LocalBinder) service;
            mService = (ChatGptService) sgmLibServiceBinder.getService();
            mBound = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };
}