package me.akkhalef.store;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.infinispan.InfinispanEmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.infinispan.client.hotrod.configuration.ClientIntelligence;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class EmbeddingStoreFactory {

    public static final String INDEX_NAME = "VintageStoreIndex";

    private static final String EMBEDDING_STORE_PROVIDER =
            environmentOrDefault("EMBEDDING_STORE_PROVIDER", "qdrant").toLowerCase(Locale.ROOT);
    private static final String QDRANT_URL = environmentOrDefault("QDRANT_URL", "http://localhost:6334");
    private static final String INFINISPAN_HOST = environmentOrDefault("INFINISPAN_HOST", "localhost");
    private static final int INFINISPAN_PORT = Integer.parseInt(environmentOrDefault("INFINISPAN_PORT", "11222"));

    private EmbeddingStoreFactory() {
    }

    public static EmbeddingStore<TextSegment> create() throws URISyntaxException {
        return switch (EMBEDDING_STORE_PROVIDER) {
            case "qdrant" -> qdrant();
            case "infinispan" -> infinispan();
            default -> throw new IllegalStateException(
                    "EMBEDDING_STORE_PROVIDER must be either 'qdrant' or 'infinispan', but was '%s'"
                            .formatted(EMBEDDING_STORE_PROVIDER));
        };
    }


    private static EmbeddingStore<TextSegment> qdrant() throws URISyntaxException {
        String qdrantHostname = new URI(QDRANT_URL).getHost();
        int qdrantPort = new URI(QDRANT_URL).getPort();

        QdrantGrpcClient.Builder grpcClientBuilder = QdrantGrpcClient.newBuilder(qdrantHostname, qdrantPort, false);
        QdrantClient qdrantClient = new QdrantClient(grpcClientBuilder.build());
        return QdrantEmbeddingStore.builder()
                .client(qdrantClient)
                .collectionName(INDEX_NAME)
                .build();
    }

    private static EmbeddingStore<TextSegment> infinispan() {
        ConfigurationBuilder configuration = new ConfigurationBuilder()
                .addServer()
                .host(INFINISPAN_HOST)
                .port(INFINISPAN_PORT)
                .clientIntelligence(ClientIntelligence.BASIC);

        return InfinispanEmbeddingStore.builder()
                .cacheName(INDEX_NAME)
                .dimension(384)
                .similarity("COSINE")
                .infinispanConfigBuilder(configuration)
                .build();
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

}
