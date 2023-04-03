package com.teamopensourcesmartglasses.chatgpt.UI;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.teamopensourcesmartglasses.chatgpt.ChatGptBackend;
import com.teamopensourcesmartglasses.chatgpt.databinding.FragmentChatgptsetupBinding;

public class ChatGptSetupFragment extends Fragment {

    private FragmentChatgptsetupBinding binding;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentChatgptsetupBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}