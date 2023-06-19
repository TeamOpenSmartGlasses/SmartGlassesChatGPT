package com.teamopensourcesmartglasses.chatgpt;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class PromptDetailFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_prompt_detail, container, false);

        TextView promptTitleTextView = view.findViewById(R.id.promptTitleTextView);
        TextView promptDescriptionTextView = view.findViewById(R.id.promptDescriptionTextView);

        Bundle args = getArguments();
        if (args != null) {
            String title = args.getString("title", "");
            String description = args.getString("description", "");

            promptTitleTextView.setText(title);
            promptDescriptionTextView.setText(description);
        }

        return view;
    }
}
