package com.gestionale.industriatech.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String sendMessage(String message) {

        // Questa stringa ritorna la risposta dell'LLM che ho collegato
        return chatClient.prompt()
                .user(message)
                .call()
                .content();

    }
}

