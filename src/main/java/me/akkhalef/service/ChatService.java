package me.akkhalef.service;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import me.akkhalef.assistant.ChatAssistant;
import me.akkhalef.store.EmbeddingStoreFactory;
import me.akkhalef.tool.ChatTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Locale;
import java.util.Scanner;

public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final String CHAT_MODEL_PROVIDER =
            environmentOrDefault("CHAT_MODEL_PROVIDER", "ollama").toLowerCase(Locale.ROOT);
    private static final String OLLAMA_BASE_URL = environmentOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
    private static final String OLLAMA_MODEL = environmentOrDefault("OLLAMA_MODEL", "llama3.2:3b");
    private static final int OLLAMA_MAX_TOKENS = Integer.parseInt(environmentOrDefault("OLLAMA_MAX_TOKENS", "256"));
    private static final int OLLAMA_TIMEOUT_SECONDS =
            Integer.parseInt(environmentOrDefault("OLLAMA_TIMEOUT_SECONDS", "300"));
    private static final String OPENAI_MODEL = environmentOrDefault("OPENAI_MODEL", "gpt-4.1");
    public static void main(String[] args) throws Exception {
        EmbeddingStore<TextSegment> embeddingStore = embeddingStore();
        ChatModel chatModel = model();

        try (Scanner scanner = new Scanner(System.in)) {
            ChatAssistant assistant = assistant(embeddingStore, chatModel);

            log.info("Please enter your question:");
            while (true) {
                String question = scanner.nextLine();
                if (question.equals("quit")) {
                    log.info("Goodbye!");
                    break;
                }

                System.out.println(assistant.chat(question));
            }
        }
    }

    private static ChatAssistant assistant(EmbeddingStore<TextSegment> embeddingStore, ChatModel model) {
        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        ContentRetriever contentRetriever = new EmbeddingStoreContentRetriever(embeddingStore, embeddingModel);

        return AiServices.builder(ChatAssistant.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .contentRetriever(contentRetriever)
                .tools(new ChatTools())
                .build();
    }

    private static ChatModel model() {
        return switch (CHAT_MODEL_PROVIDER) {
            case "ollama" -> OllamaChatModel.builder()
                    .baseUrl(OLLAMA_BASE_URL)
                    .modelName(OLLAMA_MODEL)
                    .temperature(0.3)
                    .numPredict(OLLAMA_MAX_TOKENS)
                    .think(false)
                    .returnThinking(false)
                    .timeout(Duration.ofSeconds(OLLAMA_TIMEOUT_SECONDS))
                    .maxRetries(0)
                    .logResponses(true)
                    .logRequests(true)
                    .build();
            case "openai" -> OpenAiChatModel.builder()
                    .apiKey(requiredEnvironment())
                    .modelName(OPENAI_MODEL)
                    .temperature(0.3)
                    .logResponses(true)
                    .logRequests(true)
                    .build();
            default -> throw new IllegalStateException(
                    "CHAT_MODEL_PROVIDER must be either 'ollama' or 'openai', but was '%s'"
                            .formatted(CHAT_MODEL_PROVIDER));
        };
    }

    private static EmbeddingStore<TextSegment> embeddingStore() throws URISyntaxException {
        return EmbeddingStoreFactory.create();
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String requiredEnvironment() {
        String value = System.getenv("OPENAI_API_KEY");
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Environment variable %s must be set".formatted("OPENAI_API_KEY"));
        }
        return value;
    }
}
