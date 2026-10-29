package com.avtech.pams.rfq;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RfqRepository extends JpaRepository<Rfq, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Rfq r where r.id = :id and r.deletedAt is null")
    Optional<Rfq> lockById(@Param("id") Long id);

    @Query("select r from Rfq r where r.id = :id and r.deletedAt is null")
    Optional<Rfq> findLive(@Param("id") Long id);

    @Query("select q from RfqQuotation q join fetch q.rfq r where q.id = :id and r.deletedAt is null")
    Optional<RfqQuotation> findQuotation(@Param("id") Long id);

    @Query("""
            select r from Rfq r
            where r.deletedAt is null
              and (:status is null or r.status = :status)
              and (:method is null or r.procurementMethod = :method)
              and (:pr is null or r.purchaseRequestId = :pr)
              and (:search is null or lower(r.rfqNumber) like lower(concat('%', cast(:search as string), '%'))
                   or lower(r.title) like lower(concat('%', cast(:search as string), '%')))
            order by r.createdAt desc, r.id desc
            """)
    Page<Rfq> search(@Param("status") String status, @Param("method") String method, @Param("pr") Long pr,
                     @Param("search") String search, Pageable pageable);
}
