package edu.utem.ftmk.llm;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatModel;
import edu.utem.ftmk.config.AppConfig;

import java.time.Duration;

public class LLMService {
    public ChatModel buildModel(String modelTag) {
        return OllamaChatModel.builder()
                .baseUrl(AppConfig.OLLAMA_BASE_URL)
                .modelName(modelTag)
                .timeout(Duration.ofMinutes(10))
                .build();
    }

    public String chat(String modelTag, String systemPrompt, String userPrompt) {
        ChatResponse response = buildModel(modelTag).chat(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userPrompt)
        );
        return response.aiMessage().text();
    }
}