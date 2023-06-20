package com.teamopensourcesmartglasses.chatgpt.events;

public class ChatReceivedEvent {
    public final String message;

    public ChatReceivedEvent(String newMessage){
        message = newMessage;
    }
}
