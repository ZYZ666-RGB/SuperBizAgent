package org.example.evaluation;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "evaluation")
public class EvaluationProperties {

    private String namespace = "eval_report";
    private String reportPath = "target/eval-report.json";
    private double passThreshold = 0.85;
    private double warnThreshold = 0.60;
    private boolean seedRagDemo = true;
    private Llm llm = new Llm();

    @Getter
    @Setter
    public static class Llm {
        private List<String> models = new ArrayList<>(List.of(
                "deepseek-v4-flash",
                "qwen-plus",
                "qwen-turbo"));
        private int maxTokens = 800;
        private double temperature = 0.2;
        private double topP = 0.8;
    }
}
