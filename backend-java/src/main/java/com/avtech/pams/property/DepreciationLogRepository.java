package com.avtech.pams.property;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepreciationLogRepository extends JpaRepository<DepreciationLog, Long> {

    List<DepreciationLog> findByPropertyIdOrderByDepreciationDateAscIdAsc(Long propertyId);

    boolean existsByPropertyIdAndPeriod(Long propertyId, String period);
}
