package com.teamopensourcesmartglasses.chatgpt;

import androidx.appcompat.app.AppCompatActivity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class Add_Prompt extends AppCompatActivity {
    private EditText createPromptEditText;
    private RadioGroup radioGroupPrompts;
    private ArrayList<String> prompts;
    private LinearLayout promptLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_prompt);

        createPromptEditText = findViewById(R.id.addPromptTextView_createPrompt);
        radioGroupPrompts = findViewById(R.id.radioGroupPrompts);
        promptLayout = findViewById(R.id.promptLayout);

        prompts = loadPrompts();

        // Add click listener to the "Add Prompt" button
        Button addPromptButton = findViewById(R.id.addPromptButton);
        addPromptButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String prompt = createPromptEditText.getText().toString().trim();

                if (prompt.isEmpty()) {
                    // Prompt is empty, show an error message or take appropriate action
                    Toast.makeText(Add_Prompt.this, "Please enter a prompt", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (prompts.size() >= 5) {
                    // Prompt limit reached, show an error message or take appropriate action
                    Toast.makeText(Add_Prompt.this, "You can add at most 5 prompts", Toast.LENGTH_SHORT).show();
                    return;
                }

                prompts.add(prompt);

                // Create a new RadioButton and add it to the RadioGroup
                RadioButton radioButton = new RadioButton(Add_Prompt.this);
                radioButton.setText(prompt);
                radioGroupPrompts.addView(radioButton);

                // Create a delete button for the prompt
                ImageButton deleteButton = new ImageButton(Add_Prompt.this);
                deleteButton.setImageResource(R.drawable.ic_delete); // Replace with your desired delete icon
                deleteButton.setBackgroundColor(Color.TRANSPARENT);
                deleteButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Remove the prompt and associated views
                        prompts.remove(prompt);
                        radioGroupPrompts.removeView(radioButton);
                        promptLayout.removeView(deleteButton);
                        savePrompts(prompts);
                    }
                });


                // Add the delete button to the layout
                promptLayout.addView(deleteButton);

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
                // Create a new RadioButton for each prompt
                RadioButton radioButton = new RadioButton(Add_Prompt.this);
                radioButton.setText(prompt);
                radioGroupPrompts.addView(radioButton);

                // Create a delete button for the prompt
                Button deleteButton = new Button(Add_Prompt.this);
                deleteButton.setText("Delete");
                deleteButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Remove the prompt and associated views
                        prompts.remove(prompt);
                        radioGroupPrompts.removeView(radioButton);
                        promptLayout.removeView(deleteButton);
                        savePrompts(prompts);
                    }
                });

                // Add the delete button to the layout
                promptLayout.addView(deleteButton);
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
