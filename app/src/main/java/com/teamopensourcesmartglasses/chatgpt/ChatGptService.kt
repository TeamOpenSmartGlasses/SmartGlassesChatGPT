package com.teamopensourcesmartglasses.chatgpt

import android.os.Binder
import android.util.Log
import com.google.android.material.R
import com.teamopensmartglasses.sgmlib.DataStreamType
import com.teamopensmartglasses.sgmlib.FocusStates
import com.teamopensmartglasses.sgmlib.SGMCommand
import com.teamopensmartglasses.sgmlib.SGMLib
import com.teamopensourcesmartglasses.chatgpt.events.ChatErrorEvent
import com.teamopensourcesmartglasses.chatgpt.events.ChatReceivedEvent
import com.teamopensourcesmartglasses.chatgpt.events.ChatSummarizedEvent
import com.teamopensourcesmartglasses.chatgpt.events.IsLoadingEvent
import com.teamopensourcesmartglasses.chatgpt.events.QuestionAnswerReceivedEvent
import com.teamopensourcesmartglasses.chatgpt.events.UserSettingsChangedEvent
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import java.util.Arrays
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit


class ChatGptService : SmartGlassesAndroidService(
    MainActivity::class.java,
    "chatgpt_app",
    1011,
    appName,
    "ChatGPT for smart glasses", R.drawable.notify_panel_notification_icon_bg
) {

    val TAG = "SmartGlassesChatGpt_ChatGptService"

    //our instance of the SGM library
    var sgmLib: SGMLib? = null
    var focusState: FocusStates? = null
    var chatGptBackend: ChatGptBackend? = null
    var messageBuffer = StringBuffer()
    private var userTurnLabelSet = false
    private var chatGptLabelSet = false
    private val executorService = Executors.newSingleThreadScheduledExecutor()
    private var printExecutorService: ExecutorService? = null
    private var future: Future<*>? = null
    private var openAiKeyProvided = false
    private var mode = ChatGptAppMode.Record // Turn on listening by default
    private var useAutoSend = false
    private var commandWords: ArrayList<String>? = null
    private var scrollingTextTitle = ""
    private val messageDisplayDurationMs = 4000
    private var loadingTimer: Timer? = null
    override fun onCreate() {
        super.onCreate()
        focusState = FocusStates.OUT_FOCUS

        /* Handle SGMLib specific things */

        // Create SGMLib instance with context: this
        sgmLib = SGMLib(this)

        // Define commands
        val startChatCommand = SGMCommand(
            appName,
            UUID.fromString("c3b5bbfd-4416-4006-8b40-12346ac3abcf"), arrayOf("conversation"),
            "Start a ChatGPT session for your smart glasses!"
        )
        val askGptCommand = SGMCommand(
            appName,
            UUID.fromString("c367ba2d-4416-8768-8b15-19046ac3a2af"), arrayOf("question"),
            "Ask a one shot question to ChatGpt based on your existing context"
        )
        val clearContextCommand = SGMCommand(
            appName,
            UUID.fromString("2b8d1ba0-f114-11ed-a05b-0242ac120003"), arrayOf("clear"),
            "Clear your conversation context"
        )
        val recordConversationCommand = SGMCommand(
            appName,
            UUID.fromString("ea89a5ac-6cbd-4867-bd86-1ebce9a27cb3"), arrayOf("listen"),
            "Record your conversation so you can ask ChatGpt for questions later on"
        )
        val summarizeConversationCommand = SGMCommand(
            appName,
            UUID.fromString("9ab3f985-e9d1-4ab2-8d28-0d1e6111bcb4"), arrayOf("summarize"),
            "Summarize your conversation using ChatGpt"
        )

        // Save all the wake words so we can detect and remove them during transcriptions with just 1 word
        commandWords = ArrayList()
        commandWords!!.addAll(startChatCommand.phrases)
        commandWords!!.addAll(askGptCommand.phrases)
        commandWords!!.addAll(clearContextCommand.phrases)
        commandWords!!.addAll(recordConversationCommand.phrases)
        commandWords!!.addAll(summarizeConversationCommand.phrases)

        // Register the command
        sgmLib!!.registerCommand(startChatCommand) { args: String?, commandTriggeredTime: Long ->
            startChatCommandCallback(
                args,
                commandTriggeredTime
            )
        }
        sgmLib!!.registerCommand(askGptCommand) { args: String?, commandTriggeredTime: Long ->
            askGptCommandCallback(
                args,
                commandTriggeredTime
            )
        }
        sgmLib!!.registerCommand(recordConversationCommand) { args: String?, commandTriggeredTime: Long ->
            recordConversationCommandCallback(
                args,
                commandTriggeredTime
            )
        }
        sgmLib!!.registerCommand(clearContextCommand) { args: String?, commandTriggeredTime: Long ->
            clearConversationContextCommandCallback(
                args,
                commandTriggeredTime
            )
        }
        sgmLib!!.registerCommand(summarizeConversationCommand) { args: String?, commandTriggeredTime: Long ->
            summarizeConversationContextCommandCallback(
                args,
                commandTriggeredTime
            )
        }

        //Subscribe to transcription stream
        sgmLib!!.subscribe(DataStreamType.TRANSCRIPTION_ENGLISH_STREAM) { transcript: String, timestamp: Long, isFinal: Boolean ->
            processTranscriptionCallback(
                transcript,
                timestamp,
                isFinal
            )
        }
        Log.d(TAG, "onCreate: ChatGPT service started!")

        /* Handle SmartGlassesChatGPT specific things */EventBus.getDefault().register(this)
        chatGptBackend = ChatGptBackend()

        // Putting a separate sharedPreferences here instead of through the event bus from mainActivity
        // so I don't have to deal with waiting for this service to finish its startup
        val sharedPreferences = getSharedPreferences("user.config", MODE_PRIVATE)
        val savedKey = sharedPreferences.getString("openAiKey", "")
        if (savedKey!!.isEmpty()) {
            // do not start the backend if there is no key, wait for onUserSettingsChanged to init
//            Log.d(TAG, "ChatGptService: No key exists");
            return
        }
        var systemPrompt = sharedPreferences.getString("systemPrompt", "")
        if (systemPrompt!!.isEmpty()) {
//            Log.d(TAG, "onCreate: empty system prompt, setting it to use the default one");
            systemPrompt =
                "Act as a the no 1 ambassador for smart glasses and generative pretrained ai! Find ways to bring up these topics as creative as possible!"
        }
        chatGptBackend!!.initChatGptService(savedKey, systemPrompt)
        useAutoSend = sharedPreferences.getBoolean("autoSendMessages", true)
        openAiKeyProvided = true
        focusState = FocusStates.IN_FOCUS
        recordConversationCommandCallback(null, 0)
        //        Log.d(TAG, "onCreate: Saved openAi key found, token = " + savedKey);
//        Log.d(TAG, "onCreate: systemPrompt = " + systemPrompt);
//        Log.d(TAG, "onCreate: useAutoSend = " + useAutoSend);
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: Called")
        EventBus.getDefault().unregister(this)
        sgmLib!!.deinit()
        super.onDestroy()
    }

    fun startChatCommandCallback(args: String?, commandTriggeredTime: Long) {
        Log.d(TAG, "startChatCommandCallback: Start ChatGPT command callback called")
        Log.d(TAG, "startChatCommandCallback: OpenAiApiKeyProvided:$openAiKeyProvided")
        scrollingTextTitle = "Conversation"
        // request to be the in focus app so we can continue to show transcripts
        sgmLib!!.requestFocus { focusState: FocusStates -> focusChangedCallback(focusState) }
        mode = ChatGptAppMode.Conversation
        Log.d(TAG, "startChatCommandCallback: Set app mode to conversation")

        // we might had been in the middle of a question, so when we switch back to a conversation,
        // we reset our messageBuffer
        resetUserMessage()
    }

    fun askGptCommandCallback(args: String?, commandTriggeredTime: Long) {
        Log.d(TAG, "askGptCommandCallback: Ask ChatGPT command callback called")
        //        Log.d(TAG, "askGptCommandCallback: OpenAiApiKeyProvided:" + openAiKeyProvided);

        // request to be the in focus app so we can continue to show transcripts
        scrollingTextTitle = "Question"
        sgmLib!!.requestFocus { focusState: FocusStates -> focusChangedCallback(focusState) }
        mode = ChatGptAppMode.Question
        Log.d(TAG, "askGptCommandCommand: Set app mode to question")

        // we might had been in the middle of a conversation, so when we switch to a question,
        // we need to reset our messageBuffer
        resetUserMessage()
    }

    fun recordConversationCommandCallback(args: String?, commandTriggeredTime: Long) {
        Log.d(TAG, "askGptCommandCallback: Record conversation command callback called")
        scrollingTextTitle = "Listening"
        // request to be the in focus app so we can continue to show transcripts
        sgmLib!!.requestFocus { focusState: FocusStates -> focusChangedCallback(focusState) }
        mode = ChatGptAppMode.Record
        Log.d(TAG, "askGptCommandCommand: Set app mode to record conversation")

        // we might had been in the middle of a conversation, so when we switch to a question,
        // we need to reset our messageBuffer
        resetUserMessage()
    }

    fun clearConversationContextCommandCallback(args: String?, commandTriggeredTime: Long) {
        Log.d(TAG, "askGptCommandCallback: Reset conversation context")
        if (loadingTimer != null) {
            loadingTimer!!.cancel()
            loadingTimer = null
        }
        sgmLib!!.sendReferenceCard("Clear context", "Cleared conversation context")
        mode = ChatGptAppMode.Record
        chatGptBackend!!.clearConversationContext()

        // we might had been in the middle of a conversation, so when we switch to a question,
        // we need to reset our messageBuffer
        resetUserMessage()
    }

    fun summarizeConversationContextCommandCallback(args: String?, commandTriggeredTime: Long) {
        Log.d(TAG, "askGptCommandCallback: Summarize conversation context")
        scrollingTextTitle = "Summarize"
        sgmLib!!.requestFocus { focusState: FocusStates -> focusChangedCallback(focusState) }
        mode = ChatGptAppMode.Summarize
        chatGptBackend!!.summarizeContext()

        // we might had been in the middle of a conversation, so when we switch to a question,
        // we need to reset our messageBuffer
        resetUserMessage()
    }

    fun focusChangedCallback(focusState: FocusStates) {
        Log.d(TAG, "Focus callback called with state: $focusState")
        this.focusState = focusState
        sgmLib!!.stopScrollingText()
        //StartScrollingText to show our translation
        if (focusState == FocusStates.IN_FOCUS) {
            sgmLib!!.startScrollingText(scrollingTextTitle)
            Log.d(TAG, "startChatCommandCallback: Added a scrolling text view")
            messageBuffer = StringBuffer()
        }
    }

    fun processTranscriptionCallback(transcript: String, timestamp: Long, isFinal: Boolean) {
        // Don't execute if we're not in focus or no mode is set
        var transcript = transcript
        if (focusState != FocusStates.IN_FOCUS || mode === ChatGptAppMode.Inactive) {
            return
        }

        // We can ignore command phrases so it is more accurate, tested that this works
        if (isFinal && commandWords!!.contains(transcript)) {
            return
        }

        // If its recording we just save it to memory without even the need to finish the sentence
        // It will be saved as a ChatMessage
        if (isFinal && mode === ChatGptAppMode.Record) {
            Log.d(TAG, "processTranscriptionCallback: $transcript")
            chatGptBackend!!.sendChatToMemory(transcript)
            sgmLib!!.pushScrollingText(transcript)
        }

        // We want to send our message in our message buffer when we stop speaking for like 9 seconds
        // If the transcript is finalized, then we add it to our buffer, and reset our timer
        if (isFinal && mode !== ChatGptAppMode.Record && openAiKeyProvided) {
            Log.d(TAG, "processTranscriptionCallback: $transcript")
            if (useAutoSend) {
                messageBuffer.append(transcript)
                messageBuffer.append(" ")
                // Cancel the scheduled job if we get a new transcript
                if (future != null) {
                    future!!.cancel(false)
                    Log.d(TAG, "processTranscriptionCallback: Cancelled scheduled job")
                }
                future = executorService.schedule({ sendMessageToChatGpt() }, 7, TimeUnit.SECONDS)
            } else {
                if (transcript == "send message") {
                    sendMessageToChatGpt()
                } else {
                    messageBuffer.append(transcript)
                    messageBuffer.append(" ")
                }
            }
            if (!userTurnLabelSet) {
                transcript = "User: $transcript"
                userTurnLabelSet = true
            }
            sgmLib!!.pushScrollingText(transcript)
        }
    }

    private fun sendMessageToChatGpt() {
        val message = messageBuffer.toString()
        if (!message.isEmpty()) {
            chatGptBackend!!.sendChatToGpt(message, mode)
            messageBuffer = StringBuffer()
            Log.d(TAG, "processTranscriptionCallback: Ran scheduled job and sent message")
        } else {
            Log.d(TAG, "processTranscriptionCallback: Can't send because message is empty")
        }
    }

    private fun resetUserMessage() {
        // Cancel the scheduled job if we get a new transcript
        if (future != null) {
            future!!.cancel(false)
            Log.d(TAG, "resetUserMessage: Cancelled scheduled job")
        }
        messageBuffer = StringBuffer()
    }

    @Subscribe
    fun onChatReceived(event: ChatReceivedEvent) {
        if (loadingTimer != null) {
            loadingTimer!!.cancel()
            loadingTimer = null
        }
        chunkLongMessagesAndDisplay(event.message)
        userTurnLabelSet = false
        mode = ChatGptAppMode.Conversation
    }

    private fun chunkLongMessagesAndDisplay(message: String) {
        val localPrintExecutorService = Executors.newSingleThreadExecutor()
        printExecutorService = localPrintExecutorService
        localPrintExecutorService.execute(Runnable execute@{
            val words =
                message.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val wordCount = words.size
            val groupSize = 15 // depends on glasses size
            var i = 0
            while (i < wordCount) {

                // Check if the background thread has been interrupted
                if (Thread.currentThread().isInterrupted) {
                    return@execute
                }
                val endIndex = Math.min(i + groupSize, wordCount)
                val group = Arrays.copyOfRange(words, i, endIndex)
                val groupText = java.lang.String.join(" ", *group)
                if (!chatGptLabelSet) {
//                    Log.d(TAG, "chunkLongMessagesAndDisplay: " + groupText.trim());
                    sgmLib!!.pushScrollingText("ChatGpt: " + groupText.trim { it <= ' ' })
                    chatGptLabelSet = true
                } else {
                    sgmLib!!.pushScrollingText(groupText.trim { it <= ' ' })
                }
                try {
                    Thread.sleep(messageDisplayDurationMs.toLong()) // Delay of 3 second (1000 milliseconds)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                    // Restore interrupted status and return from the thread
                    Thread.currentThread().interrupt()
                    return@execute
                }
                i += groupSize
            }
            chatGptLabelSet = false
        })
    }

    @Subscribe
    fun onQuestionAnswerReceived(event: QuestionAnswerReceivedEvent) {
        val body = """
            Q: ${event.question}
            
            A: ${event.answer}
            """.trimIndent()
        sgmLib!!.sendReferenceCard("AskGpt", body)
        mode = ChatGptAppMode.Record
    }

    @Subscribe
    fun onChatSummaryReceived(event: ChatSummarizedEvent) {
        Log.d(TAG, "onChatSummaryReceived: Received a chat summarized event")
        if (loadingTimer != null) {
            loadingTimer!!.cancel()
            loadingTimer = null
        }
        val points =
            event.summary.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val localExecutorService = Executors.newSingleThreadExecutor()
        printExecutorService = localExecutorService
        localExecutorService.execute(Runnable execute@{
            for (point in points) {
                if (Thread.currentThread().isInterrupted) {
                    return@execute
                }
                if (!chatGptLabelSet) {
                    sgmLib!!.pushScrollingText("ChatGpt: $point")
                    chatGptLabelSet = true
                } else {
                    sgmLib!!.pushScrollingText(point)
                }
                try {
                    Thread.sleep(messageDisplayDurationMs.toLong()) // Delay of 3 second (1000 milliseconds)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                    // Restore interrupted status and return from the thread
                    Thread.currentThread().interrupt()
                    return@execute
                }
            }
        })
        chatGptLabelSet = false
        //        chunkLongMessagesAndDisplay(event.getSummary());
        mode = ChatGptAppMode.Record
    }


    @Subscribe
    fun onChatError(event: ChatErrorEvent) {
        if (loadingTimer != null) {
            loadingTimer!!.cancel()
            loadingTimer = null
        }
        sgmLib!!.sendReferenceCard("Something wrong with ChatGpt", event.errorMessage)
        mode = ChatGptAppMode.Record
    }

    @Subscribe
    fun onLoading(event: IsLoadingEvent?) {
        // For those features using scrolling text, it might be useful to let the user know that chatgpt is thinking
        if (mode === ChatGptAppMode.Summarize || mode === ChatGptAppMode.Conversation) {
            if (loadingTimer == null) {
                loadingTimer = Timer()
            }
            loadingTimer!!.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    sgmLib!!.pushScrollingText("ChatGpt is thinking...")
                }
            }, 0, 5000)
        }
    }

    @Subscribe
    fun onUserSettingsChanged(event: UserSettingsChangedEvent) {
        Log.d(TAG, "onUserSettingsChanged: Enabling ChatGpt with new api key = " + event.openAiKey)
        Log.d(TAG, "onUserSettingsChanged: System prompt = " + event.systemPrompt)
        chatGptBackend!!.initChatGptService(event.openAiKey, event.systemPrompt)
        openAiKeyProvided = true
        Log.d(
            TAG,
            "onUserSettingsChanged: Auto send messages after finish speaking = " + event.useAutoSend
        )
        useAutoSend = event.useAutoSend
        mode = ChatGptAppMode.Record
        recordConversationCommandCallback(null, 0)
    }

    companion object {
        const val ACTION_START_FOREGROUND_SERVICE = "SGMLIB_ACTION_START_FOREGROUND_SERVICE"
        const val ACTION_STOP_FOREGROUND_SERVICE = "SGMLIB_ACTION_STOP_FOREGROUND_SERVICE"
        const val appName = "SmartGlassesChatGpt"
    }
}