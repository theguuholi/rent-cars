package com.github.theguuholi.recommendations.catalog;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.repository.Repository;

interface RecommendationRepository extends Repository<RecommendationSnapshot, Instant> {
  Optional<RecommendationSnapshot> findFirstByOrderByAsOfDesc();
}
