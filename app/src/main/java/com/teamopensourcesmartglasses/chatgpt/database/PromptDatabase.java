package com.teamopensourcesmartglasses.chatgpt.database;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.teamopensourcesmartglasses.chatgpt.dao.PromptDao;
import com.teamopensourcesmartglasses.chatgpt.entities.Prompt;

@Database(entities = {Prompt.class}, version = 1, exportSchema = false)
public abstract class PromptDatabase extends RoomDatabase {

    public abstract PromptDao getPromptDao();
    private PromptDao promptDao;
}
