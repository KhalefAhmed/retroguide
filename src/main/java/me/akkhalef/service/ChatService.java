package me.akkhalef.service;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import me.akkhalef.assistant.ChatAssistant;
import me.akkhalef.tool.ChatTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Scanner;

import static java.lang.System.exit;

public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final String INDEX_NAME = "VintageStoreIndex";
    private static final String QDRANT_URL = "http://localhost:6334";
    private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");

    public static void main(String[] args) throws Exception {
        EmbeddingStore<TextSegment> embeddingStore = embeddingStore();
        ChatModel chatModel = model();

        ChatAssistant assistant = assistant(embeddingStore, chatModel);
        Scanner scanner = new Scanner(System.in);
        String question;

        log.info("Please enter your question:");
        while (true) {
            question = scanner.nextLine();
            if (question.equals("quit")) {
                log.info("Goodbye!");
                break;
            }

            System.out.println(assistant.chat(question));

        }

        scanner.close();
        exit(0);
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

        return OpenAiChatModel.builder()
                .apiKey(OPENAI_API_KEY)
                .modelName(OpenAiChatModelName.GPT_4_1)
                .temperature(0.3)
                .timeout(Duration.ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();
    }


    private static EmbeddingStore<TextSegment> embeddingStore() throws URISyntaxException {
        String qdrantHostname = new URI(QDRANT_URL).getHost();
        int qdrantPort = new URI(QDRANT_URL).getPort();

        QdrantGrpcClient.Builder grpcClientBuilder = QdrantGrpcClient.newBuilder(qdrantHostname, qdrantPort, false);
        QdrantClient qdrantClient = new QdrantClient(grpcClientBuilder.build());
        return QdrantEmbeddingStore.builder()
                .client(qdrantClient)
                .collectionName(INDEX_NAME)
                .build();
    }
}
