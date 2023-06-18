package com.teamopensourcesmartglasses.chatgpt.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.teamopensourcesmartglasses.chatgpt.entities.Prompt;
import java.util.List;

@Dao
public interface PromptDao {

    @Insert
    void insertPrompt(Prompt prompt);

    @Update
    void updatePrompt(Prompt prompt);

    @Delete
    void deletePrompt(Prompt prompt);

    @Query("SELECT * FROM Prompt ORDER BY title ASC")
    List<Prompt> getPromptsOrderedByTitle();
}