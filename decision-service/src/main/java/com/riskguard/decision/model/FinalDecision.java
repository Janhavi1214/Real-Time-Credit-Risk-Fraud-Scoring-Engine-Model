package com.riskguard.decision.model;

/**
 * Final decision outcome rendered for a financial transaction.
 */
public enum FinalDecision {
    /** Transaction passed all rules and ML risk score is within acceptable bounds. */
    APPROVE,

    /** Transaction triggered suspicious rules or borderline ML risk score. Requires manual review. */
    FLAG,

    /** Transaction triggered hard risk ceiling or critical ML risk threshold. Auto-blocked. */
    BLOCK
}
