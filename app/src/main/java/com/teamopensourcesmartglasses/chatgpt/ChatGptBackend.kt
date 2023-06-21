package com.teamopensourcesmartglasses.chatgpt

import android.util.Log
import com.teamopensourcesmartglasses.chatgpt.events.ChatErrorEvent
import com.teamopensourcesmartglasses.chatgpt.events.ChatReceivedEvent
import com.teamopensourcesmartglasses.chatgpt.events.ChatSummarizedEvent
import com.teamopensourcesmartglasses.chatgpt.events.IsLoadingEvent
import com.teamopensourcesmartglasses.chatgpt.events.QuestionAnswerReceivedEvent
import com.teamopensourcesmartglasses.chatgpt.utils.MessageStore
import com.theokanning.openai.completion.chat.ChatCompletionChoice
import com.theokanning.openai.completion.chat.ChatCompletionRequest
import com.theokanning.openai.completion.chat.ChatMessage
import com.theokanning.openai.completion.chat.ChatMessageRole
import com.theokanning.openai.service.OpenAiService
import org.greenrobot.eventbus.EventBus
import java.time.Duration
import java.util.stream.Collectors

class ChatGptBackend {
    val TAG = "SmartGlassesChatGpt_ChatGptBackend"
    private var service: OpenAiService? = null

    //    private final List<ChatMessage> messages = new ArrayList<>();
    private val messages: MessageStore

    // private StringBuffer responseMessageBuffer = new StringBuffer();
    private val chatGptMaxTokenSize =
        3500 // let's play safe and use the 3500 out of 4096, we will leave 500 for custom hardcoded prompts
    private val messageDefaultWordsChunkSize = 100
    private val openAiServiceTimeoutDuration = 110
    private var recordingBuffer = StringBuffer()

    init {
        messages = MessageStore(chatGptMaxTokenSize)
    }

    fun initChatGptService(token: String?, systemMessage: String?) {
        // Setup ChatGpt with a token
        service = OpenAiService(token, Duration.ofSeconds(openAiServiceTimeoutDuration.toLong()))
        messages.setSystemMessage(systemMessage!!)
    }

    fun sendChatToMemory(message: String?) {
        // Add to messages here if it is just to record
        // It should be chunked into a decent block size
        Log.d(TAG, "sendChat: In record mode")
        recordingBuffer.append(message)
        recordingBuffer.append(" ")
        Log.d(TAG, "sendChatToMemory: $recordingBuffer")
        if (getWordCount(recordingBuffer.toString()) > messageDefaultWordsChunkSize) {
            Log.d(TAG, "sendChatToMemory: size is big enough to be a chunk")
            messages.addMessage(ChatMessageRole.USER.value(), recordingBuffer.toString())
            recordingBuffer = StringBuffer()
        }
    }

    fun sendChatToGpt(message: String?, mode: ChatGptAppMode) {
        // Don't run if openAI service is not initialized yet
        if (service == null) {
            EventBus.getDefault()
                .post(ChatErrorEvent("OpenAi Key has not been provided yet. Please do so in the app."))
            return
        }
        chunkRemainingBufferContent()

        // Add the user message and pass the entire message context to chatgpt
        messages.addMessage(ChatMessageRole.USER.value(), message!!)
        runChatGpt(message, mode)
    }

    private fun runChatGpt(message: String?, mode: ChatGptAppMode) {
        class DoGptStuff : Runnable {
            override fun run() {

                // Build prompt here
                val context: ArrayList<ChatMessage?>
                if (mode !== ChatGptAppMode.Summarize) {
                    context = messages.allMessages
                } else {
                    context = messages.allMessagesWithoutSystemPrompt
                    if (context.isEmpty()) {
                        EventBus.getDefault()
                            .post(ChatErrorEvent("No conversation was recorded, unable to summarize."))
                        return
                    }
                    val startingPrompt =
                        """The following text below is a transcript of a conversation. I need your help to summarize the text below. The transcript will be really messy, your first task is to replace all parts of the text that do not makes sense with words or phrases that makes the most sense,  The transcript will be really messy, but you must not complain about the quality of the transcript, if it is bad, do not bring it up,  No matter what, don't ever complain that the transcript is messy or hard to follow, just tell me the summary and not anything else. After you are done replacing the words with ones that makes sense, I want you to summarize it, You don't need to answer in full sentences as well, be short and concise, just tell me the summary for me in bullet form, each point should be no longer than 20 words long. For the output, I don't want to see the transformed text, I just want the overall summary and it must follow this format, don't put everything in one paragraph, I need it in bullet form as I am working with a really tight schema! 
Detected that the user was talking about 
 - <point 1> 
 - <point 2> 
 - <point 3> and so on 

 The text can be found within the triple dollar signs here: 
 ${"$"}${"$"}$ 
"""
                    context.add(0, ChatMessage(ChatMessageRole.SYSTEM.value(), startingPrompt))
                    val endingPrompt = "\n $$$"
                    context.add(ChatMessage(ChatMessageRole.SYSTEM.value(), endingPrompt))
                }
                Log.d(TAG, "run: messages: ")
                for (message in context) {
                    Log.d(TAG, "run: message: " + message!!.content)
                }

                // Todo: Change completions to streams
                val chatCompletionRequest = ChatCompletionRequest.builder()
                    .model("gpt-3.5-turbo")
                    .messages(context)
                    .n(1)
                    .build()
                EventBus.getDefault().post(IsLoadingEvent())
                try {
                    val result = service!!.createChatCompletion(chatCompletionRequest)
                    val responses = result.choices
                        .stream()
                        .map { obj: ChatCompletionChoice -> obj.message }
                        .collect(Collectors.toList())

                    // Send a chat received response
                    val response = responses[0]
                    Log.d(TAG, "run: ChatGpt response: " + response.content)

                    // Send back to chat UI and internal history
                    if (mode === ChatGptAppMode.Conversation) {
                        EventBus.getDefault().post(ChatReceivedEvent(response.content))
                        messages.addMessage(response.role, response.content)
                    }

                    // Send back one off question and answer
                    if (mode === ChatGptAppMode.Question) {
                        EventBus.getDefault().post(
                            QuestionAnswerReceivedEvent(
                                message!!, response.content
                            )
                        )

                        // Edit the last user message to specify that it was a question
                        messages.addPrefixToLastAddedMessage("User asked a question: ")
                        // Specify on the answer side as well
                        messages.addMessage(
                            response.role,
                            "Assistant responded with an answer: " + response.content
                        )
                    }
                    if (mode === ChatGptAppMode.Summarize) {
                        Log.d(TAG, "run: Sending a chat summarized event to service")
                        EventBus.getDefault().post(ChatSummarizedEvent(response.content))
                    }
                } catch (e: Exception) {
//                    Log.d(TAG, "run: encountered error: " + e.getMessage());
                    EventBus.getDefault()
                        .post(ChatErrorEvent("Check if you had set your key correctly or view the error below" + e.message))
                }

//                Log.d(TAG, "Streaming chat completion");
//                service.streamChatCompletion(chatCompletionRequest)
//                        .doOnError(this::onStreamChatGptError)
//                        .doOnComplete(this::onStreamComplete)
//                        .blockingForEach(this::onItemReceivedFromStream);
            } //            private void onStreamChatGptError(Throwable throwable) {
            //                Log.d(TAG, throwable.getMessage());
            //                EventBus.getDefault().post(new ChatReceivedEvent(throwable.getMessage()));
            //                throwable.printStackTrace();
            //            }
            //
            //            public void onItemReceivedFromStream(ChatCompletionChunk chunk) {
            //                String textChunk = chunk.getChoices().get(0).getMessage().getContent();
            //                Log.d(TAG, "Chunk received from stream: " + textChunk);
            //                EventBus.getDefault().post(new ChatReceivedEvent(textChunk));
            //                responseMessageBuffer.append(textChunk);
            //                responseMessageBuffer.append(" ");
            //            }
            //
            //            public void onStreamComplete() {
            //                String responseMessage = responseMessageBuffer.toString();
            //                messages.add(new ChatMessage(ChatMessageRole.ASSISTANT.value(), responseMessage));
            //                responseMessageBuffer = new StringBuffer();
            //            }
        }
        Thread(DoGptStuff()).start()
    }

    private fun chunkRemainingBufferContent() {
        // If there is still words from a previous record session, then add them into the messageQueue
        if (recordingBuffer.length != 0) {
            Log.d(
                TAG,
                "sendChatToGpt: There are still words from a recording, adding them to chunk"
            )
            messages.addMessage(ChatMessageRole.USER.value(), recordingBuffer.toString())
            recordingBuffer = StringBuffer()
        }
    }

    private fun getWordCount(message: String): Int {
        val words = message.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        return words.size
    }

    private fun clearSomeMessages() {
        for (i in 0 until messages.size() / 2) {
            messages.removeOldest()
        }
    }

    fun summarizeContext() {
//        Log.d(TAG, "summarizeContext: Called");
        chunkRemainingBufferContent()
        runChatGpt(null, ChatGptAppMode.Summarize)
    }

    fun clearConversationContext() {
        messages.resetMessages()
        recordingBuffer = StringBuffer()
    }
}