package com.avtech.pams.adjustment;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from StockAdjustment a where a.id = :id")
    Optional<StockAdjustment> lockById(@Param("id") Long id);

    @Query("""
            select a from StockAdjustment a
            where (:status is null or a.status = :status)
              and (:type is null or a.adjustmentType = :type)
              and (:itemId is null or a.itemId = :itemId)
            order by a.createdAt desc, a.id desc
            """)
    Page<StockAdjustment> search(@Param("status") String status, @Param("type") String type,
                                 @Param("itemId") Long itemId, Pageable pageable);
}
