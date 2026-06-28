package org.example.evaluation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/eval")
public class EvaluationReportController {

    private final EvaluationReportService evaluationReportService;

    public EvaluationReportController(EvaluationReportService evaluationReportService) {
        this.evaluationReportService = evaluationReportService;
    }

    @GetMapping("/report")
    public ResponseEntity<EvaluationReport> report(@RequestParam(required = false) String namespace) {
        return ResponseEntity.ok(evaluationReportService.currentReport(namespace));
    }

    @PostMapping("/refresh")
    public ResponseEntity<EvaluationReport> refresh(@RequestBody(required = false) EvaluationRunRequest request) {
        return ResponseEntity.ok(evaluationReportService.refresh(request));
    }

    @PostMapping("/run")
    public ResponseEntity<EvaluationReport> run(@RequestBody(required = false) EvaluationRunRequest request) {
        return ResponseEntity.ok(evaluationReportService.run(request));
    }
}
