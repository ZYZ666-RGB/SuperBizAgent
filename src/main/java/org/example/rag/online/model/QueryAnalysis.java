package org.example.rag.online.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class QueryAnalysis {
    private QueryType queryType = QueryType.GENERAL_QA;
    private List<String> keywords = new ArrayList<>();
    private List<String> serviceNames = new ArrayList<>();
    private List<String> errorCodes = new ArrayList<>();
    private List<String> components = new ArrayList<>();
    private List<String> alertTypes = new ArrayList<>();
    private Boolean needRag = true;
    private Boolean needRewrite = true;
    private Boolean needHyde = false;
    private Boolean preferBm25 = false;
    private Boolean preferDense = true;
    private Map<String, Object> metadataFilter = new LinkedHashMap<>();
}
