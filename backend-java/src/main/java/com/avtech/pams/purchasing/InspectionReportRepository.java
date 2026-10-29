package com.avtech.pams.purchasing;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InspectionReportRepository extends JpaRepository<InspectionReport, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from InspectionReport r where r.id = :id")
    Optional<InspectionReport> lockById(@Param("id") Long id);

    List<InspectionReport> findByPurchaseOrderIdOrderByIdAsc(Long purchaseOrderId);

    @Query("""
            select r from InspectionReport r
            where (:acceptance is null or r.acceptanceStatus = :acceptance)
              and (:po is null or r.purchaseOrderId = :po)
              and (:search is null or lower(r.iarNumber) like lower(concat('%', cast(:search as string), '%')))
            order by r.createdAt desc, r.id desc
            """)
    Page<InspectionReport> search(@Param("acceptance") String acceptance, @Param("po") Long po, @Param("search") String search, Pageable pageable);
}
