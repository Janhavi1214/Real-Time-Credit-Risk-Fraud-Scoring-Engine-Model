package com.riskguard.decision.repository;

import com.riskguard.decision.entity.Decision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA Repository for persisting and retrieving decisions from PostgreSQL.
 */
@Repository
public interface DecisionRepository extends JpaRepository<Decision, String> {
}
