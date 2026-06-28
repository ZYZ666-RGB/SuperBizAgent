package org.example.evaluation;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class EvaluationRunRequest {

    private String namespace;
    private List<String> models = new ArrayList<>();
    private Boolean seedRagDemo;
}
