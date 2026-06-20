package org.example.rag.online.controller;

import org.example.rag.online.AdvancedRagOnlineService;
import org.example.rag.online.model.RagAnswer;
import org.example.rag.online.model.RagQueryRequest;
import org.example.rag.online.model.RagQueryResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RagOnlineController {

    private final AdvancedRagOnlineService advancedRagOnlineService;

    public RagOnlineController(AdvancedRagOnlineService advancedRagOnlineService) {
        this.advancedRagOnlineService = advancedRagOnlineService;
    }

    @PostMapping("/api/rag/search")
    public ResponseEntity<RagQueryResult> search(@RequestBody RagQueryRequest request) {
        return ResponseEntity.ok(advancedRagOnlineService.search(request));
    }

    @PostMapping("/api/rag/answer")
    public ResponseEntity<RagAnswer> answer(@RequestBody RagQueryRequest request) {
        return ResponseEntity.ok(advancedRagOnlineService.answer(request));
    }
}
