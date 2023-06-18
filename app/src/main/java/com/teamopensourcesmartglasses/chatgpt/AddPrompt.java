package com.teamopensourcesmartglasses.chatgpt;

import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
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
import com.teamopensourcesmartglasses.chatgpt.events.PromptAddedEvent;
import com.teamopensourcesmartglasses.chatgpt.events.PromptDeletedEvent;
import com.teamopensourcesmartglasses.chatgpt.events.UserSettingsChangedEvent;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AddPrompt extends AppCompatActivity {

    private PromptDao promptDao;
    private RadioGroup radioGroupPrompts;
    private EditText titleEditText;
    private EditText descriptionEditText;
    private Button closeButton;
    private Button okButton;

    private final List<Prompt> prompts = new ArrayList<>();
    private static final int MAX_PROMPTS = 10;
    private final HashMap<Integer, String> radioButtonDescriptions = new HashMap<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_prompt);

        // Initialize the views
        radioGroupPrompts = findViewById(R.id.radioGroupPrompts);
        FloatingActionButton addPromptFab = findViewById(R.id.addPromptFab);
        titleEditText = findViewById(R.id.titleEditText);
        descriptionEditText = findViewById(R.id.descriptionEditText);
        closeButton = findViewById(R.id.closeButton);
        okButton = findViewById(R.id.okButton);

        // Initialize the database and get the DAO
        PromptDatabase db = Room.databaseBuilder(getApplicationContext(), PromptDatabase.class, "prompt-database").build();
        promptDao = db.getPromptDao();

        // Load the saved prompts
        loadPrompts();


        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                titleEditText.setText("");
                descriptionEditText.setText("");
                titleEditText.setVisibility(View.GONE);
                descriptionEditText.setVisibility(View.GONE);
                closeButton.setVisibility(View.GONE);
                okButton.setVisibility(View.GONE);
            }
        });

        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String title = titleEditText.getText().toString();
                String description = descriptionEditText.getText().toString();

                if (!title.isEmpty() && !description.isEmpty()) {
                    addNewPrompt(title, description);
                    Toast.makeText(AddPrompt.this, "Prompt added successfully", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(AddPrompt.this, "Please fill out both fields", Toast.LENGTH_SHORT).show();
                }
                titleEditText.setText("");
                descriptionEditText.setText("");
                titleEditText.setVisibility(View.GONE);
                descriptionEditText.setVisibility(View.GONE);
                closeButton.setVisibility(View.GONE);
                okButton.setVisibility(View.GONE);
            }
        });

        // When the addPromptFab is clicked, make sure the okButton is also visible
        addPromptFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(prompts.size() >= MAX_PROMPTS) {
                    Toast.makeText(AddPrompt.this, "Maximum number of prompts reached", Toast.LENGTH_SHORT).show();
                    return;
                }
                titleEditText.setVisibility(View.VISIBLE);
                descriptionEditText.setVisibility(View.VISIBLE);
                closeButton.setVisibility(View.VISIBLE);
                okButton.setVisibility(View.VISIBLE);
            }
        });

        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Clear text fields
                titleEditText.setText("");
                descriptionEditText.setText("");

                // Hide text fields and buttons
                titleEditText.setVisibility(View.GONE);
                descriptionEditText.setVisibility(View.GONE);
                closeButton.setVisibility(View.GONE);
                okButton.setVisibility(View.GONE);
            }
        });

        radioGroupPrompts.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                String selectedDescription = radioButtonDescriptions.get(checkedId);
                String selectedTitle = ((RadioButton) findViewById(checkedId)).getText().toString();
                if (selectedDescription != null) {
                    // Save the new system prompt
                    SharedPreferences sharedPreferences = getSharedPreferences("user.config", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("systemPrompt", selectedDescription);
                    editor.putString("systemPromptTitle", selectedTitle);
                    editor.apply();

                    // Notify the rest of the app about the change
                    EventBus.getDefault().post(new UserSettingsChangedEvent(
                            sharedPreferences.getString("openAiKey", ""),
                            selectedDescription,
                            sharedPreferences.getBoolean("autoSendMessages", true)
                    ));
                }
            }
        });
    }

    private void loadPrompts() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Prompt> savedPrompts = promptDao.getPromptsOrderedByTitle();
                final String savedPromptTitle = getSharedPreferences("user.config", Context.MODE_PRIVATE).getString("systemPromptTitle", "");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        radioGroupPrompts.removeAllViews();
                        radioButtonDescriptions.clear();
                        prompts.clear();

                        for (final Prompt prompt : savedPrompts) {
                            // Create a new RadioButton and add it to the RadioGroup
                            final RadioButton radioButton = new RadioButton(AddPrompt.this);
                            radioButton.setText(prompt.getTitle());
                            radioButton.setOnLongClickListener(new View.OnLongClickListener() {
                                @Override
                                public boolean onLongClick(View view) {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(AddPrompt.this);
                                    builder.setMessage("Do you want to delete this prompt?")
                                            .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {
                                                    deletePrompt(prompt);
                                                    radioGroupPrompts.removeView(radioButton);
                                                    prompts.remove(prompt);
                                                    radioButtonDescriptions.remove(radioButton.getId());
                                                    Toast.makeText(AddPrompt.this, "Prompt deleted successfully", Toast.LENGTH_SHORT).show();
                                                }
                                            })
                                            .setNegativeButton("No", null)
                                            .show();
                                    return true;
                                }
                            });
                            radioGroupPrompts.addView(radioButton);

                            // Check if this radio button should be checked
                            if (prompt.getTitle().equals(savedPromptTitle)) {
                                radioButton.setChecked(true);
                            }

                            radioButtonDescriptions.put(radioButton.getId(), prompt.getPrompt());
                            prompts.add(prompt);
                        }

                        // Show a Toast message indicating the number of saved prompts
                        Toast.makeText(AddPrompt.this, "Found " + savedPrompts.size() + " saved prompts", Toast.LENGTH_SHORT).show();
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

                // Post event to EventBus
                EventBus.getDefault().post(new PromptAddedEvent(prompt));

                // Update UI on the main thread
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Create a new RadioButton and add it to the RadioGroup
                        RadioButton radioButton = new RadioButton(AddPrompt.this);
                        radioButton.setText(title);
                        radioButton.setOnLongClickListener(new View.OnLongClickListener() {
                            @Override
                            public boolean onLongClick(View view) {
                                AlertDialog.Builder builder = new AlertDialog.Builder(AddPrompt.this);
                                builder.setMessage("Do you want to delete this prompt?")
                                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                deletePrompt(prompt);
                                                radioGroupPrompts.removeView(radioButton);
                                                prompts.remove(prompt);
                                                radioButtonDescriptions.remove(radioButton.getId());
                                                Toast.makeText(AddPrompt.this, "Prompt deleted successfully", Toast.LENGTH_SHORT).show();
                                            }
                                        })
                                        .setNegativeButton("No", null)
                                        .show();
                                return true;
                            }
                        });
                        radioGroupPrompts.addView(radioButton);
                        radioButtonDescriptions.put(radioButton.getId(), description);
                        // Add prompt to the prompts list
                        prompts.add(prompt);
                    }
                });
            }
        }).start();
    }

    private void deletePrompt(final Prompt prompt) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                promptDao.deletePrompt(prompt);

                // Post event to EventBus
                EventBus.getDefault().post(new PromptDeletedEvent(prompt));

                // Show Toast on the main thread after deleting the prompt
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(AddPrompt.this, "Prompt '" + prompt.getTitle() + "' deleted successfully", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

}