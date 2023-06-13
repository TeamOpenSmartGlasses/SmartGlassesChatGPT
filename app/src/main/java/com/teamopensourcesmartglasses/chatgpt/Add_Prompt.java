package com.teamopensourcesmartglasses.chatgpt;

import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.teamopensourcesmartglasses.chatgpt.dao.PromptDao;
import com.teamopensourcesmartglasses.chatgpt.database.PromptDatabase;
import com.teamopensourcesmartglasses.chatgpt.entities.Prompt;

import java.util.ArrayList;
import java.util.List;

public class Add_Prompt extends AppCompatActivity {

    private PromptDao promptDao;
    private RadioGroup radioGroupPrompts;
    private FloatingActionButton addPromptFab;
    private EditText titleEditText;
    private EditText descriptionEditText;
    private Button closeButton;

    private List<Prompt> prompts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_prompt);

        // Initialize the views
        radioGroupPrompts = findViewById(R.id.radioGroupPrompts);
        addPromptFab = findViewById(R.id.addPromptFab);
        titleEditText = findViewById(R.id.titleEditText);
        descriptionEditText = findViewById(R.id.descriptionEditText);
        closeButton = findViewById(R.id.closeButton);

        // Initialize the database and get the DAO
        PromptDatabase db = Room.databaseBuilder(getApplicationContext(), PromptDatabase.class, "prompt-database").build();
        promptDao = db.getPromptDao();

        // Load the saved prompts
        loadPrompts();

        // Set up the FAB click listener
        addPromptFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                titleEditText.setVisibility(View.VISIBLE);
                descriptionEditText.setVisibility(View.VISIBLE);
                closeButton.setVisibility(View.VISIBLE);
            }
        });

        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String title = titleEditText.getText().toString();
                String description = descriptionEditText.getText().toString();

                if (!title.isEmpty() && !description.isEmpty()) {
                    addNewPrompt(title, description);
                    Toast.makeText(Add_Prompt.this, "Prompt added successfully", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(Add_Prompt.this, "Please fill out both fields", Toast.LENGTH_SHORT).show();
                }
                titleEditText.setText("");
                descriptionEditText.setText("");
                titleEditText.setVisibility(View.GONE);
                descriptionEditText.setVisibility(View.GONE);
                closeButton.setVisibility(View.GONE);
            }
        });
    }

    private void loadPrompts() {
        // Fetch the prompts from the database in a background thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                prompts = promptDao.getPromptsOrderedById();

                // Update UI on the main thread
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        for (Prompt prompt : prompts) {
                            // Create a new RadioButton and add it to the RadioGroup
                            RadioButton radioButton = new RadioButton(Add_Prompt.this);
                            radioButton.setText(prompt.getTitle());
                            radioGroupPrompts.addView(radioButton);
                        }
                    }
                });
            }
        }).start();
    }

    private void addNewPrompt(String title, String description) {
        // Create a new Prompt instance
        final Prompt prompt = new Prompt(title, description, 0);

        // Insert the prompt into the database in a background thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                promptDao.insertPrompt(prompt);

                // Update UI on the main thread
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Create a new RadioButton and add it to the RadioGroup
                        RadioButton radioButton = new RadioButton(Add_Prompt.this);
                        radioButton.setText(title);
                        radioGroupPrompts.addView(radioButton);

                        // Add prompt to the prompts list
                        prompts.add(prompt);
                    }
                });
            }
        }).start();
    }
}
