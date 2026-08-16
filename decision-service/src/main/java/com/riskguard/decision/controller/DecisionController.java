package com.riskguard.decision.controller;

import com.riskguard.decision.entity.Decision;
import com.riskguard.decision.repository.DecisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for retrieving evaluated transaction decisions.
 */
@RestController
@RequestMapping("/api/v1/decisions")
@RequiredArgsConstructor
public class DecisionController {

    private final DecisionRepository decisionRepository;

    @GetMapping("/{transactionId}")
    public ResponseEntity<Decision> getDecision(@PathVariable String transactionId) {
        return decisionRepository.findById(transactionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
