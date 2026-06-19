package org.example.rag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private int topK = 3;
    private String model = "qwen3-max";
    private Storage storage = new Storage();
    private Parser parser = new Parser();
    private Chunk chunk = new Chunk();
    private Embedding embedding = new Embedding();
    private Index index = new Index();

    @Getter
    @Setter
    public static class Storage {
        private String baseDir = "./rag-data";
        private String uploadDir = "./rag-data/uploads";
        private String markdownDir = "./rag-data/parsed-md";
        private String chunkDir = "./rag-data/chunks";
        private String logDir = "./rag-data/logs";
    }

    @Getter
    @Setter
    public static class Parser {
        private String mode = "external";
        private String externalUrl = "http://localhost:7001/parse";
        private boolean fallbackToTika = true;
        private int timeoutSeconds = 120;
    }

    @Getter
    @Setter
    public static class Chunk {
        private int targetTokens = 700;
        private int maxTokens = 1000;
        private int minTokens = 80;
        private int overlapTokens = 100;
        private boolean enableParentChild = true;
        private boolean enableContextualEmbedding = true;
    }

    @Getter
    @Setter
    public static class Embedding {
        private String model = "text-embedding-v3";
        private int batchSize = 16;
        private int retryTimes = 3;
    }

    @Getter
    @Setter
    public static class Index {
        private String vectorStore = "milvus";
        private boolean deleteOldBeforeReindex = true;
    }
}
