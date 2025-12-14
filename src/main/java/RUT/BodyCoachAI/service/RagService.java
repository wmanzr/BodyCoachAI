package RUT.BodyCoachAI.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PostConstruct;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private EmbeddingModel embeddingModel;
    private EmbeddingStore<TextSegment> embeddingStore;
    private ContentRetriever contentRetriever;

    @PostConstruct
    public void initialize() {
        this.embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        this.embeddingStore = new InMemoryEmbeddingStore<>();

        this.contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(100)
                .minScore(0.6)
                .build();

        loadDocuments();
    }

    private void loadDocuments() {
        try {
            Path documentsPath = Paths.get("src/main/resources/documents");
            File documentsDir = documentsPath.toFile();

            if (!documentsDir.exists() || !documentsDir.isDirectory()) {
                return;
            }

            File[] pdfFiles = documentsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));
            if (pdfFiles == null || pdfFiles.length == 0) {
                return;
            }

            EmbeddingStoreIngestor ingestor =
                    EmbeddingStoreIngestor.builder()
                            .embeddingModel(embeddingModel)
                            .embeddingStore(embeddingStore)
                            .documentSplitter(DocumentSplitters.recursive(1000, 200))
                            .build();

            for (File pdfFile : pdfFiles) {
                String text = extractTextFromPdf(pdfFile);
                if (text != null && !text.trim().isEmpty()) {
                    Document document = Document.from(text);
                    ingestor.ingest(document);
                    log.info("Загружен документ: {} (размер: {} символов)", pdfFile.getName(), text.length());
                } else {
                    log.warn("Не удалось извлечь текст из документа: {}", pdfFile.getName());
                }
            }
            log.info("Всего загружено документов: {}", pdfFiles.length);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String extractTextFromPdf(File pdfFile) {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public ContentRetriever getContentRetriever() {
        return contentRetriever;
    }
}