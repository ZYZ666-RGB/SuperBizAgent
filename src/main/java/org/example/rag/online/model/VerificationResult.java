package org.example.rag.online.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerificationResult {
    private Boolean supported;
    private Double confidence;
    private String reason;
}
