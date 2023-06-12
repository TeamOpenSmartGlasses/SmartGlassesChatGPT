package com.teamopensourcesmartglasses.chatgpt.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class Prompt {
        private String title;
        private String prompt;

        @PrimaryKey(autoGenerate = true)
        private int id;

        public Prompt(String title, String prompt, int id) {
                this.title = title;
                this.prompt = prompt;
                this.id = id;
        }

        public String getTitle() {
                return title;
        }

        public void setTitle(String title) {
                this.title = title;
        }

        public String getPrompt() {
                return prompt;
        }

        public void setPrompt(String prompt) {
                this.prompt = prompt;
        }

        public int getId() {
                return id;
        }

        public void setId(int id) {
                this.id = id;
        }
}