package org.example.rag.online.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "rag.online")
public class RagOnlineProperties {

    private String defaultNamespace = "default";
    private Query query = new Query();
    private Retrieve retrieve = new Retrieve();
    private Fusion fusion = new Fusion();
    private Rerank rerank = new Rerank();
    private Context context = new Context();
    private Verify verify = new Verify();
    private Debug debug = new Debug();

    @Getter
    @Setter
    public static class Query {
        private boolean enableAnalysis = true;
        private boolean enableRewrite = true;
        private int rewriteCount = 3;
        private boolean enableHyde = false;
    }

    @Getter
    @Setter
    public static class Retrieve {
        private boolean enableDense = true;
        private boolean enableSparse = true;
        private boolean enableHybrid = true;
        private int denseTopK = 20;
        private int sparseTopK = 20;
        private int candidatePoolSize = 40;
        private int finalTopK = 6;
    }

    @Getter
    @Setter
    public static class Fusion {
        private String type = "rrf";
        private int rrfK = 60;
    }

    @Getter
    @Setter
    public static class Rerank {
        private boolean enabled = true;
        private String provider = "noop";
        private String model = "gte-rerank-v2";
        private int timeoutSeconds = 30;
        private boolean fallbackToFusion = true;
    }

    @Getter
    @Setter
    public static class Context {
        private int maxContextTokens = 4000;
        private boolean enableCompression = true;
        private boolean enableParentExpansion = true;
    }

    @Getter
    @Setter
    public static class Verify {
        private boolean enabled = true;
        private double minConfidence = 0.55;
        private boolean requireCitation = true;
    }

    @Getter
    @Setter
    public static class Debug {
        private boolean enableTrace = true;
    }
}
