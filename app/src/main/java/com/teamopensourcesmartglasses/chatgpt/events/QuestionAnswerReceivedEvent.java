package com.teamopensourcesmartglasses.chatgpt.events;

public class QuestionAnswerReceivedEvent {
    private final String question;
    private final String answer;

    public QuestionAnswerReceivedEvent(String userQuestion, String gptAnswer) {
        question = userQuestion;
        answer = gptAnswer;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }
}
