package org.example.rag.online.model;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
public class RagQueryRequest {
    private String query;
    private String namespace;
    private String sessionId;
    private Integer topK;
    private Boolean enableRewrite;
    private Boolean enableHyde;
    private Boolean enableHybrid;
    private Boolean enableRerank;
    private Boolean enableVerify;
    private Map<String, Object> filters = new LinkedHashMap<>();
    private Boolean debug;
}
