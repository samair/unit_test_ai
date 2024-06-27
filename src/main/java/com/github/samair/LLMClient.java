package com.github.samair;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.*;
import com.azure.core.credential.KeyCredential;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LLMClient {

    private OpenAIClient client;

    LLMClient() {
        var key = System.getenv("openai_key");

        if (key != null) {
            this.client = new OpenAIClientBuilder()
                    .credential(new KeyCredential(key))
                    .buildClient();
        }
    }

    public String generateUnitTestCase(String methodString) {

        String response = null;
        if (client != null) {

            List<ChatRequestMessage> chatMessages = new ArrayList<>();
            chatMessages.add(new ChatRequestSystemMessage("Write java unit tests using mocks where applicable for following method, Do not output markdown."));
            chatMessages.add(new ChatRequestSystemMessage(methodString));

            var options = new ChatCompletionsOptions(chatMessages);
            options.setTemperature(0.0);
            ChatCompletions chatCompletions = client.getChatCompletions("gpt-3.5-turbo", options);

            System.out.printf("Model ID=%s is created at %s.%n", chatCompletions.getId(), chatCompletions.getCreatedAt());
            for (ChatChoice choice : chatCompletions.getChoices()) {
                ChatResponseMessage message = choice.getMessage();
                System.out.printf("Index: %d, Chat Role: %s.%n", choice.getIndex(), message.getRole());
                System.out.println("Message:");
                System.out.println(message.getContent());
                response = message.getContent();
            }
        }

        return response;
    }
}
