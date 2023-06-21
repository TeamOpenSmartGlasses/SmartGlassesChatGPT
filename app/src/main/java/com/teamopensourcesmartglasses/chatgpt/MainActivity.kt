package com.teamopensourcesmartglasses.chatgpt

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.teamopensmartglasses.sgmlib.SmartGlassesAndroidService
import com.teamopensourcesmartglasses.chatgpt.databinding.ActivityMainBinding
import com.teamopensourcesmartglasses.chatgpt.events.UserSettingsChangedEvent
import org.greenrobot.eventbus.EventBus

class MainActivity : AppCompatActivity() {
    private val TAG = "SmartGlassesChatGpt_MainActivity"
    var mBound = false
    var mService: ChatGptService? = null
    private var binding: ActivityMainBinding? = null
    private var submitButton: Button? = null
    private var openAiKeyText: EditText? = null
    private var autoSendRadioButton: RadioButton? = null
    private var manualSendRadioButton: RadioButton? = null
    private var systemPromptInput: EditText? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(
            layoutInflater
        )
        setContentView(binding!!.root)
        setSupportActionBar(binding!!.toolbar)
        startChatGptService()
        val sharedPreferences = getSharedPreferences("user.config", MODE_PRIVATE)

        // Display our previously saved settings
        openAiKeyText = findViewById(R.id.edittext_openAiKey)
        val savedOpenAiKey = sharedPreferences.getString("openAiKey", "")
        // Show toasts and populate openAI key text field if we have or don't have a key saved
        if (!savedOpenAiKey!!.isEmpty()) {
            openAiKeyText?.setText(savedOpenAiKey)
            Toast.makeText(this, "OpenAI key and other app settings found", Toast.LENGTH_LONG)
                .show()
        } else {
            Toast.makeText(this, "No valid OpenAI key found, please add one", Toast.LENGTH_LONG)
                .show()
        }
        systemPromptInput = findViewById(R.id.editTextTextMultiLine_systemPrompt)
        val defaultSystemPrompt =
            "Act as a the no 1 ambassador for smart glasses and generative pretrained ai! Find ways to bring up these topics as creative as possible!"
        val savedSystemPrompt = sharedPreferences.getString("systemPrompt", defaultSystemPrompt)
        systemPromptInput?.setText(savedSystemPrompt)
        autoSendRadioButton = findViewById(R.id.radioButton_autoSend)
        manualSendRadioButton = findViewById(R.id.radioButton_manualSend)
        val useAutoSendMethod = sharedPreferences.getBoolean("autoSendMessages", true)
        autoSendRadioButton?.isChecked = useAutoSendMethod
        manualSendRadioButton?.isChecked = !useAutoSendMethod

        // UI handlers
        submitButton = findViewById(R.id.submit_button)
        submitButton?.setOnClickListener(View.OnClickListener setOnClickListener@{ v: View? ->
            // Save to shared preference
            val editor = sharedPreferences.edit()
            val openAiApiKey = openAiKeyText?.text.toString().trim { it <= ' ' }
            if (openAiApiKey.isEmpty()) {
                Toast.makeText(this, "OpenAi key cannot be empty", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            editor.putString("openAiKey", openAiApiKey)
            val systemPrompt = systemPromptInput?.text.toString().trim { it <= ' ' }
            if (systemPrompt.isEmpty()) {
                Toast.makeText(this, "System prompt should not be empty", Toast.LENGTH_LONG).show()
            }
            editor.putString("systemPrompt", systemPrompt)
            val useAutoSendMessages = autoSendRadioButton?.isChecked ?: false
            editor.putBoolean("autoSendMessages", useAutoSendMessages)
            editor.apply()
            EventBus.getDefault().post(UserSettingsChangedEvent(openAiApiKey, systemPrompt, useAutoSendMessages))

            // Toast to inform user that key has been saved
            Toast.makeText(this, "Overall settings changed", Toast.LENGTH_LONG).show()
            Toast.makeText(this, "OpenAi key saved for future sessions", Toast.LENGTH_LONG).show()
        })

    }

    /* SGMLib */
    override fun onResume() {
        super.onResume()

        //bind to foreground service
        bindChatGptService()
    }

    override fun onPause() {
        super.onPause()

        //unbind foreground service
        unbindChatGptService()
    }

    fun stopChatGptService() {
        unbindChatGptService()
        if (!isMyServiceRunning(ChatGptService::class.java)) return
        val stopIntent = Intent(this, ChatGptService::class.java)
        stopIntent.action = ChatGptService.ACTION_STOP_FOREGROUND_SERVICE
        startService(stopIntent)
    }

    fun sendChatGptServiceMessage(message: String?) {
        if (!isMyServiceRunning(ChatGptService::class.java)) return
        val messageIntent = Intent(this, ChatGptService::class.java)
        messageIntent.action = message
        startService(messageIntent)
    }

    fun startChatGptService() {
        if (isMyServiceRunning(ChatGptService::class.java)) {
            Log.d(TAG, "Not starting service.")
            return
        }
        Log.d(TAG, "Starting service.")
        val startIntent = Intent(this, ChatGptService::class.java)
        startIntent.action = ChatGptService.ACTION_START_FOREGROUND_SERVICE
        startService(startIntent)
        bindChatGptService()
    }

    //check if service is running
    private fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    fun bindChatGptService() {
        if (!mBound) {
            val intent = Intent(this, ChatGptService::class.java)
            bindService(intent, chatGptAppServiceConnection, BIND_AUTO_CREATE)
        }
    }

    fun unbindChatGptService() {
        if (mBound) {
            unbindService(chatGptAppServiceConnection)
            mBound = false
        }
    }

    /** Defines callbacks for service binding, passed to bindService()  */
    private val chatGptAppServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            className: ComponentName,
            service: IBinder
        ) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val sgmLibServiceBinder = service as SmartGlassesAndroidService.LocalBinder
            mService = sgmLibServiceBinder.service as ChatGptService
            mBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }
}