package com.teamopensourcesmartglasses.chatgpt;

import androidx.appcompat.app.AppCompatActivity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class Add_Prompt extends AppCompatActivity {
    private EditText createPromptEditText;
    private RadioGroup radioGroupPrompts;
    private ArrayList<String> prompts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_prompt);

        createPromptEditText = findViewById(R.id.addPromptTextView_createPrompt);
        radioGroupPrompts = findViewById(R.id.radioGroupPrompts);

        prompts = loadPrompts();

        // Add click listener to the "Add Prompt" button
        Button addPromptButton = findViewById(R.id.addPromptButton);
        addPromptButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String prompt = createPromptEditText.getText().toString().trim();
                prompts.add(prompt);

                // Create a new RadioButton and add it to the RadioGroup
                RadioButton radioButton = new RadioButton(Add_Prompt.this);
                radioButton.setText(prompt);
                radioGroupPrompts.addView(radioButton);

                // Make the RadioGroup visible
                radioGroupPrompts.setVisibility(View.VISIBLE);

                // Clear the EditText
                createPromptEditText.setText("");

                // Save the updated prompts list
                savePrompts(prompts);
            }
        });

        // Load and populate saved prompts if available
        populateSavedPrompts();
    }

    private void populateSavedPrompts() {
        if (!prompts.isEmpty()) {
            for (String prompt : prompts) {
                RadioButton radioButton = new RadioButton(Add_Prompt.this);
                radioButton.setText(prompt);
                radioGroupPrompts.addView(radioButton);
            }

            // Make the RadioGroup visible
            radioGroupPrompts.setVisibility(View.VISIBLE);
        }
    }

    private void savePrompts(ArrayList<String> prompts) {
        SharedPreferences sharedPreferences = getSharedPreferences("PromptPreferences", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Set<String> promptSet = new HashSet<>(prompts);
        editor.putStringSet("prompts", promptSet);
        editor.apply();
    }

    private ArrayList<String> loadPrompts() {
        SharedPreferences sharedPreferences = getSharedPreferences("PromptPreferences", MODE_PRIVATE);
        Set<String> promptSet = sharedPreferences.getStringSet("prompts", new HashSet<String>());
        return new ArrayList<>(promptSet);
    }
}
