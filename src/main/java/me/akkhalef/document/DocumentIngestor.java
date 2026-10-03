package me.akkhalef.document;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import me.akkhalef.store.EmbeddingStoreFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;


public class DocumentIngestor {

    private static final Logger logger = LoggerFactory.getLogger(DocumentIngestor.class);
    private static final EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
    private static EmbeddingStore<TextSegment> embeddingStore;

    public static void main(String[] args) throws IOException, URISyntaxException {
        logger.info("Starting Document Ingestor...");
        embeddingStore = createEmbeddingStore();


        List<Path> pdfFiles = getPdfFiles();
        for (Path path : pdfFiles) {
            ingest(path);
        }

    }

    private static void ingest(Path path) throws IOException {
        ApachePdfBoxDocumentParser parser = new ApachePdfBoxDocumentParser();
        Document document = parser.parse(Files.newInputStream(path));

        DocumentSplitter splitter = DocumentSplitters.recursive(2000, 200);
        List<TextSegment> segments = splitter.split(document);
        for (TextSegment segment : segments) {
            segment.metadata().put("filename", path.getFileName().toString());
        }

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);

    }

    private static List<Path> getPdfFiles() throws IOException {
        List<Path> pdfFiles = new ArrayList<>();
        Path rootPath = Paths.get("").toAbsolutePath();
        logger.info("Reading files from {}", rootPath);

        try (Stream<Path> paths = Files.walk(rootPath)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".pdf"))
                    .forEach(pdfFiles::add);
        }

        return pdfFiles;
    }

    private static EmbeddingStore<TextSegment> createEmbeddingStore() throws URISyntaxException {
        return EmbeddingStoreFactory.create();
    }

}
