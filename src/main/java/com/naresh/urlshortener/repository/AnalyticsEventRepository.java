package com.naresh.urlshortener.repository;

import com.naresh.urlshortener.domain.AnalyticsEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEventEntity, Long> {
}
