package org.example.rag.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MarkdownBlock {

    private String blockId;
    private String type;
    private String content;
    private String headingPath;
    private Integer headingLevel;
    private Integer startOffset;
    private Integer endOffset;
    private Integer tokenCount;
}
