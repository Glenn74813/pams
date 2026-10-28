package com.avtech.pams.disposal;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DisposalRepository extends JpaRepository<DisposalRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DisposalRequest d where d.id = :id and d.deletedAt is null")
    Optional<DisposalRequest> lockById(@Param("id") Long id);

    /** An open (not finished or turned down) disposal already covering this asset. */
    @Query("""
            select count(d) > 0 from DisposalRequest d
            where d.propertyId = :propertyId and d.deletedAt is null and d.status in ('draft', 'submitted', 'approved')
              and (:exceptId is null or d.id <> :exceptId)
            """)
    boolean openForProperty(@Param("propertyId") Long propertyId, @Param("exceptId") Long exceptId);

    @Query("""
            select d from DisposalRequest d
            where d.deletedAt is null
              and (:onlyUser is null or d.requestedBy = :onlyUser)
              and (:status is null or d.status = :status)
              and (:category is null or d.category = :category)
              and (:search is null or lower(d.disposalNumber) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(d.reason, '')) like lower(concat('%', cast(:search as string), '%')))
            order by d.requestDate desc, d.id desc
            """)
    Page<DisposalRequest> search(@Param("onlyUser") Long onlyUser, @Param("status") String status,
                                 @Param("category") String category, @Param("search") String search, Pageable pageable);
}
